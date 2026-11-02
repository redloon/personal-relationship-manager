"""Trusted APK-only host runner. Install outside the shared checkout."""
import hashlib
import fcntl
import json
import os
from pathlib import Path
import re
import subprocess
import threading
import time
import uuid

from sessions import SessionTools, InteractiveSession

PACKAGE = 'com.idontremember.personalcrm'
def source_fingerprint():
    names = ['runner.py', 'sessions.py', 'interaction.py', 'captures.py', 'requirements.txt']
    digest = hashlib.sha256()
    for name in names:
        path = Path(__file__).with_name(name)
        digest.update(name.encode()+b'\0')
        if path.is_file():
            digest.update(path.read_bytes())
    return digest.hexdigest()


SOURCE_SHA256 = source_fingerprint()

RUNNER = PACKAGE + '.test/' + PACKAGE + '.HiltTestRunner'


class CommandError(RuntimeError):
    def __init__(self, args, returncode, output):
        self.output = output
        super().__init__(f'Command failed ({returncode}): {" ".join(args)}\n{output[-2000:]}')


def install_apks(device, directory, verify, note):
    """Only reset these two disposable packages on the verified test emulator."""
    def install():
        for name in ['app.apk', 'test.apk']:
            device('install', '-r', str(directory/name))
    try:
        install()
    except CommandError as error:
        if 'INSTALL_FAILED_UPDATE_INCOMPATIBLE' not in error.output:
            raise
        verify()
        note('Debug signing key changed (host/sandbox build). Resetting only the app and test packages on the dedicated test emulator; its app data is disposable.\n')
        for package in [PACKAGE+'.test', PACKAGE]:
            # A missing test package is normal if only the app was installed.
            if f'package:{package}' in device('shell', 'pm', 'list', 'packages', package).splitlines():
                device('uninstall', package)
        install()

class Runner(SessionTools):
    def __init__(self, config):
        self.sdk = Path(config['sdk']).resolve()
        self.inbox = Path(config['inbox']).resolve()
        self.state = Path(config['state']).resolve()
        self.avd = config['avd']
        if self.avd != 'android_tests':
            raise ValueError('Only the dedicated android_tests AVD is supported')
        self.outbox = Path(config.get('outbox', self.inbox.parent/'mcp-artifacts')).absolute()
        self.sessions = {}
        self.state.mkdir(parents=True, exist_ok=True)
        self.lock = threading.Lock()
        self.active = None
        self.jobs = {}
        # Host Codex and the sandbox gateway may each have a runner process.
        # Do not treat another process's live jobs as interrupted.
        with (self.state/'runner.lock').open('a') as lock:
            try:
                fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
            except BlockingIOError:
                pass
            else:
                for p in self.state.glob('*/status.json'):
                    job = json.loads(p.read_text())
                    if job['status'] == 'running':
                        job.update(status='interrupted', phase='server restarted')
                        p.write_text(json.dumps(job))

    def health(self):
        with (self.state/'runner.lock').open('a') as lock:
            try:
                fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
                busy = False
            except BlockingIOError:
                busy = True
        tools = ['platform-tools/adb', 'emulator/emulator', 'cmdline-tools/latest/bin/apkanalyzer']
        missing = [name for name in tools if not (self.sdk/name).is_file()]
        return dict(version=4, pid=os.getpid(), source_sha256=SOURCE_SHA256, state=str(self.state), inbox=str(self.inbox), sdk_available=not missing, missing_sdk_tools=missing, avd=self.avd, serial='emulator-5580', busy=busy, capabilities=['tests', 'interactive', 'batch', 'screenshots', 'recording'], outbox=str(self.outbox), readiness='Test submission checks emulator identity and boot; health does not prove emulator readiness.')

    def save(self, jid):
        p = self.state/jid/'status.json'
        tmp = p.with_name('status.'+uuid.uuid4().hex+'.tmp')
        tmp.write_text(json.dumps(self.jobs[jid]))
        tmp.replace(p)

    def start_test(self, app_sha256: str, test_sha256: str, filter: str | None = None):
        if filter and not re.fullmatch(r'com\.idontremember\.personalcrm\.[A-Za-z0-9_.$]+(?:#[A-Za-z0-9_$]+)?', filter):
            raise ValueError('Invalid test filter')
        return self._submit(app_sha256, test_sha256, filter)

    def _submit(self, app_sha256, test_sha256, filter, interactive=False):
        with self.lock:
            if self.active:
                raise ValueError(f'Runner busy with job {self.active}; wait for cleanup before submitting or restaging.')
            job_lock = (self.state/'runner.lock').open('a')
            try:
                fcntl.flock(job_lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
            except BlockingIOError:
                job_lock.close()
                raise ValueError('Another host/sandbox connection is running tests or cleaning up. Wait before submitting or restaging.')
            try:
                return self._start_test(app_sha256, test_sha256, filter, job_lock, interactive)
            except Exception:
                job_lock.close()
                raise

    def _start_test(self, app_sha256, test_sha256, filter, job_lock, interactive=False):
        jid = uuid.uuid4().hex
        directory = self.state/jid
        directory.mkdir(mode=0o700)
        for name, digest in [('app.apk', app_sha256), ('test.apk', test_sha256)]:
            if not re.fullmatch('[0-9a-f]{64}', digest):
                raise ValueError('Invalid hash')
            fd = os.open(self.inbox/name, os.O_RDONLY | os.O_NOFOLLOW | os.O_NONBLOCK)
            import stat
            with os.fdopen(fd, 'rb') as source:
                info = os.fstat(source.fileno())
                if not stat.S_ISREG(info.st_mode) or info.st_size > 256*1024*1024:
                    raise ValueError('Invalid APK file')
                data = source.read(256*1024*1024+1)
            if hashlib.sha256(data).hexdigest() != digest:
                raise ValueError('APK hash mismatch')
            (directory/name).write_bytes(data)
        self.jobs[jid] = dict(status='running', phase='starting', app_sha256=app_sha256, test_sha256=test_sha256)
        if interactive:
            self.jobs[jid]['kind'] = 'session'
        self.active = jid
        self.cancelled = threading.Event()
        if interactive:
            self.sessions[jid] = InteractiveSession(self, jid)
        self.save(jid)
        threading.Thread(target=self.run, args=(jid, filter, job_lock), daemon=True).start()
        return dict(job_id=jid, **({'session_id': jid} if interactive else {}))

    def get_status(self, job_id: str):
        if not re.fullmatch('[0-9a-f]{32}', job_id):
            raise ValueError('Invalid job ID')
        # Allow polling a job after reconnecting to another runner process.
        path = self.state/job_id/'status.json'
        if not path.is_file():
            raise ValueError('Unknown job')
        return json.loads(path.read_text())

    def read_logs(self, job_id: str, offset: int = 0):
        self.get_status(job_id)
        if not isinstance(offset, int) or offset < 0:
            raise ValueError('Invalid job or offset')
        p = self.state/job_id/'output.log'
        data = p.read_bytes() if p.exists() else b''
        chunk = data[offset:offset+32768]
        return dict(text=chunk.decode(errors='replace'), next_offset=offset+len(chunk))

    def cancel(self, job_id: str):
        status = self.get_status(job_id)
        if status.get('kind') == 'batch' and status['status'] == 'running':
            session = self.sessions.get(status['session_id'])
            if session is None:
                raise ValueError('Cancel through the connection owning this session')
            session.batch_cancelled.set()
            return self.get_status(job_id)
        if status['status'] == 'running' and self.active != job_id:
            raise ValueError('This job belongs to another connection; cancel through the connection that started it.')
        if self.active == job_id:
            self.cancelled.set()
        return self.get_status(job_id)

    def run(self, jid, filter, job_lock):
        directory = self.state/jid
        interactive = self.jobs[jid].get('kind') == 'session'
        deadline = time.monotonic()+(2880 if interactive else 900)
        adb = str(self.sdk/'platform-tools/adb')
        serial = 'emulator-5580'
        device_verified = False
        def call(args, timeout=60):
            with (directory/'output.log').open('ab') as log, (directory/'command.log').open('wb') as capture:
                with subprocess.Popen(args, stdout=capture, stderr=subprocess.STDOUT, stdin=subprocess.DEVNULL) as p:
                    end = min(deadline, time.monotonic()+timeout)
                    while p.poll() is None:
                        if self.cancelled.is_set() or time.monotonic() > end:
                            p.kill(); p.wait()
                            raise RuntimeError('cancelled' if self.cancelled.is_set() else 'timed out')
                        time.sleep(.1)
                output = (directory/'command.log').read_bytes()
                log.write(output)
                if p.returncode:
                    raise CommandError(args, p.returncode, output.decode(errors='replace'))
                return output.decode(errors='replace')
        def device(*args):
            return call([adb, '-s', serial, *args])
        def verify():
            if device('emu', 'avd', 'name').splitlines()[0] != self.avd:
                raise ValueError('Emulator identity changed; refusing to modify packages')
        def note(message):
            with (directory/'output.log').open('a') as log:
                log.write(message)
        try:
            analyzer = self.sdk/'cmdline-tools/latest/bin/apkanalyzer'
            for name, expected in [('app.apk', PACKAGE), ('test.apk', PACKAGE+'.test')]:
                if call([str(analyzer), 'manifest', 'application-id', str(directory/name)]).strip() != expected:
                    raise ValueError('Unexpected APK package')
            self.jobs[jid]['phase'] = 'emulator'; self.save(jid)
            devices = call([adb, 'devices'])
            if serial in devices:
                actual_avd = device('emu', 'avd', 'name').splitlines()[0]
                if actual_avd != self.avd:
                    raise ValueError(f'Port 5580 belongs to {actual_avd}; expected {self.avd}. Close that host emulator window, then retry. Renaming an AVD does not stop its running process.')
            else:
                with (directory/'emulator.log').open('ab') as log:
                    subprocess.Popen([str(self.sdk/'emulator/emulator'), '-avd', self.avd, '-port', '5580', '-no-window', '-no-audio', '-no-snapshot', '-gpu', 'swiftshader'], stdin=subprocess.DEVNULL, stdout=log, stderr=log, start_new_session=True)
            boot_end = time.monotonic()+120
            while True:
                try:
                    if device('shell', 'getprop', 'sys.boot_completed').strip() == '1': break
                except RuntimeError:
                    if self.cancelled.is_set(): raise
                if time.monotonic() > boot_end: raise RuntimeError('Emulator boot timed out')
                time.sleep(2)
            if device('emu', 'avd', 'name').splitlines()[0] != self.avd:
                raise ValueError('Emulator identity changed during boot')
            device_verified = True
            self.jobs[jid]['phase'] = 'install'; self.save(jid)
            install_apks(device, directory, verify, note)
            if RUNNER not in device('shell', 'pm', 'list', 'instrumentation'): raise ValueError('Unexpected instrumentation runner')
            device('shell', 'pm', 'clear', PACKAGE)
            for permission in ['READ_CONTACTS', 'WRITE_CONTACTS']:
                device('shell', 'pm', 'grant', PACKAGE, 'android.permission.'+permission)
            device('logcat', '-c')
            if interactive:
                launch = device('shell', 'am', 'start', '-W', '-n', PACKAGE+'/.ui.MainActivity')
                if re.search(r'Error|Exception', launch):
                    raise RuntimeError('App launch failed: '+launch[-2000:])
                def device_binary(*args):
                    # Every interactive device operation, including cleanup,
                    # re-verifies the emulator and keeps stdout binary-safe.
                    identity = subprocess.run([adb, '-s', serial, 'emu', 'avd', 'name'],
                                              stdin=subprocess.DEVNULL, stdout=subprocess.PIPE,
                                              stderr=subprocess.PIPE, check=True, timeout=10).stdout
                    if identity.decode(errors='replace').splitlines()[0] != self.avd:
                        raise ValueError('Emulator identity changed; refusing operation')
                    completed = subprocess.run([adb, '-s', serial, *args], stdin=subprocess.DEVNULL,
                                               stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=20)
                    if completed.returncode:
                        raise CommandError(args, completed.returncode, completed.stderr.decode(errors='replace'))
                    return completed.stdout
                try:
                    self.sessions[jid].run(device_binary)
                except RuntimeError as error:
                    if str(error) == 'session cancelled':
                        result = dict(status='passed' if self.jobs[jid].get('end_requested') else 'cancelled', phase='finished')
                    elif 'timeout' in str(error) or 'duration' in str(error):
                        result = dict(status='expired', phase='finished', error=str(error))
                    else:
                        raise
                else:
                    result = dict(status='passed', phase='finished')
                return
            self.jobs[jid]['phase'] = 'tests'; self.save(jid)
            args = [adb, '-s', serial, 'shell', 'am', 'instrument', '-w']
            if filter: args += ['-e', 'class', filter]
            output = call(args+[RUNNER], 180)
            if not re.search(r'OK \([1-9][0-9]* tests?\)', output) or 'FAILURES' in output or 'INSTRUMENTATION_FAILED' in output:
                raise RuntimeError('Tests failed or completion missing')
            result = dict(status='passed', phase='finished', test_count=int(re.search(r'OK \(([1-9][0-9]*) tests?\)', output).group(1)))
        except Exception as error:
            result = dict(status='cancelled' if self.cancelled.is_set() else 'failed', phase='finished', error=str(error))
        finally:
            if not interactive and device_verified and 'result' in locals() and result.get('status') == 'failed':
                try:
                    from captures import Captures
                    def capture_device(*args):
                        verify()
                        return subprocess.run([adb, '-s', serial, *args], check=True,
                                              stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=15).stdout
                    Captures(directory, self.outbox, jid, self.jobs[jid].get('app_sha256', ''),
                             self.jobs[jid].get('test_sha256', ''), capture_device).screenshot('test-failure')
                except Exception as error:
                    result['capture_error'] = str(error)
            if interactive:
                try:
                    self.sessions[jid].close()
                except Exception as error:
                    result['cleanup_error'] = str(error)
            self.jobs[jid]['phase'] = 'cleanup'; self.save(jid)
            for args in [('shell', 'am', 'force-stop', PACKAGE+'.test'), ('shell', 'am', 'force-stop', PACKAGE), ('logcat', '-d', '-t', '2000')]:
                if not device_verified:
                    break
                try:
                    identity = subprocess.run([adb, '-s', serial, 'emu', 'avd', 'name'],
                                              stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=10, check=True).stdout
                    if identity.decode(errors='replace').splitlines()[0] != self.avd:
                        break
                    with (directory/'output.log').open('ab') as log:
                        subprocess.run([adb, '-s', serial, *args], stdout=log, stderr=log, timeout=15)
                except Exception: pass
            if self.jobs[jid].get('capture_errors') and result.get('status') == 'passed':
                result.update(status='failed', error='Recording cleanup failed; see capture_errors')
            with self.lock:
                self.jobs[jid].update(result)
                self.save(jid)
                self.active = None
                job_lock.close()

if __name__ == '__main__':
    import sys
    from mcp.server.fastmcp import FastMCP
    runner = Runner(json.loads(Path(sys.argv[1]).read_text()))
    server = FastMCP('android-tests')
    for name in ['health', 'start_test', 'get_status', 'read_logs', 'cancel',
                 'start_session', 'end_session', 'inspect', 'execute_steps',
                 'start_recording', 'stop_recording', 'list_artifacts', 'get_artifact']:
        server.tool()(getattr(runner, name))
    import signal
    def shutdown(signum, frame):
        raise SystemExit(0)
    signal.signal(signal.SIGTERM, shutdown)
    signal.signal(signal.SIGINT, shutdown)
    try:
        server.run(transport='stdio')
    finally:
        if runner.active:
            runner.cancelled.set()
            until = time.monotonic()+45
            while runner.active and time.monotonic() < until:
                time.sleep(.1)
