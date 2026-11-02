#!/usr/bin/env python3
"""Remove only the named host compatibility-trial installation after validation.

Dry run by default. Run on the host with --apply after replacement QA passes.
Trial evidence in ignored/agent-android-trial is deliberately retained.
"""
import argparse
import fcntl
import json
from pathlib import Path
import shutil
import subprocess


def local_targets(temporary=Path('/tmp'), home=Path.home()):
    return [temporary/'personal-crm-agent-android-trial', temporary/'agent-android-swift',
            temporary/'agent-android-swift.tar.gz', *sorted(temporary.glob('agent-trial-frame-*.png')),
            home/'.agent-android/run/emulator-5580']


def remove_local(targets):
    # Refuse unexpected content rather than deleting somebody else's renamed tool.
    for path in targets:
        if path.is_symlink():
            raise ValueError(f'Refusing symlink: {path}')
        if not path.exists():
            continue
        if path.name == 'personal-crm-agent-android-trial':
            if not (path/'Package.swift').is_file() or not (path/'.git').exists():
                raise ValueError('Unrecognized agent-android checkout')
        if path.name == 'agent-android-swift' and not (path/'usr/bin/swift').is_file():
            raise ValueError('Unrecognized portable Swift installation')
        if path.name == 'emulator-5580':
            state = path/'state.json'
            if not state.is_file() or json.loads(state.read_text()).get('sessionPackage') != 'com.idontremember.personalcrm':
                raise ValueError('agent-android state is not owned by this app trial')
    for path in targets:
        if path.is_dir():
            shutil.rmtree(path)
        elif path.exists():
            path.unlink()


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apply',action='store_true')
    parser.add_argument('--config',type=Path,default=Path.home()/'.local/share/android-tests-mcp/config.json')
    args=parser.parse_args()
    targets=local_targets()
    print('Host targets:\n'+'\n'.join(str(p) for p in targets))
    print('Device targets: /sdcard/agent-android-trial.mp4, /sdcard/agent-android-trial-walkthrough.mp4')
    if not args.apply:
        print('Dry run. Evidence retained. Use --apply on the host after replacement validation.')
        return
    # Do not remove a runtime/check-out being used by a live host process.
    prefixes=['/tmp/personal-crm-agent-android-trial','/tmp/agent-android-swift/']
    for proc in Path('/proc').glob('[0-9]*/cmdline'):
        try:
            argv=proc.read_bytes().split(b'\0')
        except (OSError,PermissionError):
            continue
        if any(arg.decode(errors='replace').startswith(prefix) for arg in argv for prefix in prefixes):
            raise RuntimeError(f'Trial tool still in use by PID {proc.parent.name}; finish it first')
    config=json.loads(args.config.read_text())
    if config['avd']!='android_tests':
        raise ValueError('Unexpected AVD configuration')
    with (Path(config['state'])/'runner.lock').open('a') as lock:
        fcntl.flock(lock,fcntl.LOCK_EX|fcntl.LOCK_NB)
        adb=Path(config['sdk'])/'platform-tools/adb'
        def device(*arguments):
            return subprocess.run([str(adb),'-s','emulator-5580',*arguments],check=True,
                                  capture_output=True,text=True,timeout=15).stdout
        if device('emu','avd','name').splitlines()[0]!='android_tests':
            raise ValueError('Wrong emulator; refusing cleanup')
        device('shell','rm','-f','/sdcard/agent-android-trial.mp4','/sdcard/agent-android-trial-walkthrough.mp4')
        remove_local(targets)
    print('Trial runtime removed; report/screenshots/video in shared checkout retained.')


if __name__=='__main__':
    main()
