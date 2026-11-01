#!/usr/bin/env python3
"""Run on the host: install, provision, register, and attach Android MCP."""
import argparse
import json
import os
from pathlib import Path
import platform
import shutil
import subprocess
import signal
import time

SOURCE = Path(__file__).resolve().parent
ROOT = SOURCE.parent.parent
DEST = Path.home() / '.local/share/android-tests-mcp'


def run(*args, **kwargs):
    return subprocess.run([str(a) for a in args], check=True, **kwargs)


def configuration(existing, sdk=None):
    config = dict(existing)
    if sdk:
        config['sdk'] = str(Path(sdk).expanduser().resolve())
    if not config.get('sdk'):
        candidates = [os.environ.get('ANDROID_HOME'), os.environ.get('ANDROID_SDK_ROOT'), str(Path.home()/'Android/Sdk')]
        config['sdk'] = next((str(Path(p).resolve()) for p in candidates if p and (Path(p)/'cmdline-tools/latest/bin/sdkmanager').is_file()), '')
    config.setdefault('inbox', str(ROOT/'ignored/mcp-inbox'))
    config.setdefault('state', str(DEST/'jobs'))
    config.setdefault('avd', 'android_tests')
    return config


def process_identity(pid):
    proc = Path(f'/proc/{pid}')
    try:
        argv = (proc/'cmdline').read_bytes().rstrip(b'\0').decode().split('\0')
        if proc.stat().st_uid != os.getuid() or len(argv) != 3:
            return None
        if argv[1:] != [str(DEST/'runner.py'), str(DEST/'config.json')]:
            return None
        # Field 22 is starttime. Split after comm, which may contain spaces.
        return (proc/'stat').read_text().rsplit(')', 1)[1].split()[19]
    except (OSError, UnicodeError):
        return None


def terminate_runner(pid, identity):
    try:
        if callable(getattr(os, 'pidfd_open', None)) and callable(getattr(signal, 'pidfd_send_signal', None)):
            fd = os.pidfd_open(pid)
            try:
                if process_identity(pid) == identity:
                    signal.pidfd_send_signal(fd, signal.SIGTERM)
            finally:
                os.close(fd)
        elif process_identity(pid) == identity:
            # Some Python builds omit pidfd. Recheck command, owner and start
            # time immediately before signalling rather than trusting a PID.
            os.kill(pid, signal.SIGTERM)
    except ProcessLookupError:
        pass


def stop_runners(config):
    """Match exact installed script/config arguments, never process-name substrings."""
    matches = {}
    for proc in Path('/proc').iterdir():
        if proc.name.isdigit():
            pid = int(proc.name)
            identity = process_identity(pid)
            if identity is not None:
                matches[pid] = identity
    if matches:
        for status in Path(config['state']).glob('*/status.json'):
            if json.loads(status.read_text()).get('status') == 'running':
                raise RuntimeError(f'Finish/cancel the active job before refresh: {status}')
    for pid, identity in matches.items():
        terminate_runner(pid, identity)
    end = time.monotonic()+10
    while any(process_identity(pid) == identity for pid, identity in matches.items()):
        if time.monotonic() > end:
            raise RuntimeError('Runner did not exit after SIGTERM; refresh stopped')
        time.sleep(.1)


def check_emulator(sdk, expected):
    adb = sdk/'platform-tools/adb'
    devices = run(adb, 'devices', capture_output=True, text=True, timeout=15).stdout
    if not any(line.split()[:1] == ['emulator-5580'] for line in devices.splitlines()):
        return
    lines = run(adb, '-s', 'emulator-5580', 'emu', 'avd', 'name', capture_output=True, text=True, timeout=15).stdout.splitlines()
    actual = lines[0] if lines else '(unknown)'
    if actual != expected:
        raise RuntimeError(f'Port 5580 is occupied by {actual}; expected {expected}. '
                           f'Close that emulator window or run on the host: "{adb}" -s emulator-5580 emu kill. '
                           'Then rerun setup. No emulator was stopped automatically.')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--sandbox', required=True, help='Existing sandbox name (see sbx ls)')
    parser.add_argument('--sdk', help='Host Android SDK; otherwise preserve config or detect Android SDK')
    args = parser.parse_args()
    for command in ['java', 'sbx', 'python3']:
        if not shutil.which(command):
            parser.error(f'Missing {command}; install it on the host first')
    path = DEST/'config.json'
    existing = json.loads(path.read_text()) if path.exists() else {}
    config = configuration(existing, args.sdk)
    sdk = Path(config['sdk'])
    for tool in ['sdkmanager', 'avdmanager', 'apkanalyzer']:
        if not config['sdk'] or not (sdk/'cmdline-tools/latest/bin'/tool).is_file():
            parser.error('Install Android SDK Command-line Tools (latest) using Android Studio SDK Manager; rerun with --sdk /absolute/sdk/path')
    if platform.system() != 'Linux' or platform.machine() != 'x86_64':
        parser.error('Automatic provisioning currently supports Linux x86_64 hosts only')
    for name in ['sdk', 'inbox', 'state']:
        if not Path(config[name]).is_absolute():
            parser.error(f'config {name} must be an absolute path')
    # Verify host login/daemon before making changes. No sandbox restart here.
    run('sbx', 'ls')
    stop_runners(config)
    DEST.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(config, indent=2)+'\n')
    Path(config['inbox']).mkdir(parents=True, exist_ok=True)
    run('python3', '-m', 'venv', DEST/'venv')
    run(DEST/'venv/bin/pip', 'install', '-r', SOURCE/'requirements.txt')
    for name in ['runner.py', 'requirements.txt']:
        shutil.copyfile(SOURCE/name, DEST/name)
    import hashlib
    (DEST/'runner.sha256').write_text(hashlib.sha256((DEST/'runner.py').read_bytes()).hexdigest()+'\n')
    print('Review SDK licenses if prompted.', flush=True)
    run(sdk/'cmdline-tools/latest/bin/sdkmanager', '--licenses')
    image = 'system-images;android-34;google_apis;x86_64'
    run(sdk/'cmdline-tools/latest/bin/sdkmanager', 'platform-tools', 'emulator', image)
    emulator = sdk/'emulator/emulator'
    avds = run(emulator, '-list-avds', capture_output=True, text=True).stdout.splitlines()
    if config['avd'] not in avds:
        run(sdk/'cmdline-tools/latest/bin/avdmanager', 'create', 'avd', '--name', config['avd'], '--package', image, input='no\n', text=True)
    run(emulator, '-accel-check')
    check_emulator(sdk, config['avd'])
    # Existing registrations use the same installed paths; don't overwrite silently.
    result = subprocess.run(['sbx', 'mcp', 'inspect', 'android-tests'], capture_output=True, text=True)
    if result.returncode:
        run('sbx', 'mcp', 'add', 'android-tests', '--command', DEST/'venv/bin/python', '--args', f'{DEST}/runner.py,{path}')
    run('sbx', 'mcp', 'load', 'android-tests', '--sandbox', args.sandbox)
    print('Setup complete. Ask sandboxed Codex to call android-tests health, then run mise run test-stage and submit AppLaunchTest.')
    print('Idle host runners were refreshed. Call health and confirm avd=android_tests and the new runner PID. No sandbox restart is required; reconnect tools if needed.')


if __name__ == '__main__':
    main()
