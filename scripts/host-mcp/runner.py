"""Trusted APK-only host runner. Install outside the shared checkout."""
import hashlib
import fcntl
import json
import os
from pathlib import Path
import re
import stat
import subprocess
import threading
import time
import uuid

from sessions import SessionTools, InteractiveSession
from instrumentation import instrumentation_result, InstrumentationResults

PACKAGE = 'com.redloon.prm'
def source_fingerprint():
    names = ['runner.py', 'sessions.py', 'interaction.py', 'captures.py', 'instrumentation.py', 'recovery.py', 'requirements.txt']
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


def run_command(args, directory, cancelled, deadline, timeout=60, progress=None):
    """Tail a regular file to avoid pipe deadlocks and retain output on every exit."""
    with (directory/'command.log').open('wb') as capture, (directory/'output.log').open('ab', buffering=0) as log:
        with (directory/'command.log').open('rb') as reader:
            def drain():
                data = reader.read()
                if data:
                    log.write(data)
                    if progress:
                        progress(data.decode(errors='replace'))
            with subprocess.Popen(args, stdout=capture, stderr=subprocess.STDOUT, stdin=subprocess.DEVNULL) as process:
                end = min(deadline, time.monotonic()+timeout)
                try:
                    while process.poll() is None:
                        drain()
                        if cancelled.is_set() or time.monotonic() >= end:
                            process.kill()
                            process.wait()
                            raise RuntimeError('cancelled' if cancelled.is_set() else 'timed out')
                        time.sleep(.05)
                finally:
                    if process.poll() is None:
                        process.kill()
                        process.wait()
                    drain()
            output = (directory/'command.log').read_text(errors='replace')
            if process.returncode:
                raise CommandError(args, process.returncode, output)
            return output


def read_regular_file(path, limit):
    fd = os.open(path, os.O_RDONLY | os.O_NOFOLLOW | os.O_NONBLOCK)
    with os.fdopen(fd, 'rb') as source:
        info = os.fstat(source.fileno())
        if not stat.S_ISREG(info.st_mode) or info.st_size > limit:
            raise ValueError('Invalid file type or size')
        data = source.read(limit+1)
        if len(data) > limit:
            raise ValueError('File grew beyond size limit')
        return data


def read_provenance(inbox, app_sha256, test_sha256):
    try:
        manifest = json.loads(read_regular_file(inbox/'manifest.json', 65536))
    except FileNotFoundError:
        return None
    if manifest.get('app_sha256') != app_sha256 or manifest.get('test_sha256') != test_sha256:
        return None
    value = manifest.get('provenance')
    if value is None:
        return None
    source = value.get('source_sha256', '')
    revision = value.get('git_revision')
    if not re.fullmatch('[0-9a-f]{64}', source) or (revision is not None and not re.fullmatch('[0-9a-f]{40}|[0-9a-f]{64}', revision)):
        raise ValueError('Invalid source provenance')
    result = dict(source_sha256=source, git_revision=revision)
    release = value.get('release_manifest')
    if release is not None:
        if not isinstance(release, dict) or release.get('app_sha256') != app_sha256 or release.get('git_revision') != revision or release.get('source_sha256') != source:
            raise ValueError('Invalid release provenance')
        result['release_manifest'] = release
    return result


def install_apks(device, directory, verify, note, app_only=False):
    """Only reset these two disposable packages on the verified test emulator."""
    def install():
        for name in (['app.apk'] if app_only else ['app.apk', 'test.apk']):
            device('install', '-r', str(directory/name))
    try:
        install()
    except CommandError as error:
        if not any(reason in error.output for reason in ['INSTALL_FAILED_UPDATE_INCOMPATIBLE', 'INSTALL_FAILED_VERSION_DOWNGRADE']):
            raise
        verify()
        note('APK signing certificate or version changed. Resetting only the app and test packages on the dedicated test emulator; its app data is disposable.\n')
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
        return dict(version=6, pid=os.getpid(), source_sha256=SOURCE_SHA256, state=str(self.state), inbox=str(self.inbox), sdk_available=not missing, missing_sdk_tools=missing, avd=self.avd, serial='emulator-5580', busy=busy, capabilities=['tests', 'recovery', 'interactive', 'batch', 'screenshots', 'recording'], outbox=str(self.outbox), readiness='Test submission checks emulator identity and boot; health does not prove emulator readiness.')

    def save(self, jid):
        p = self.state/jid/'status.json'
        tmp = p.with_name('status.'+uuid.uuid4().hex+'.tmp')
        tmp.write_text(json.dumps(self.jobs[jid]))
        tmp.replace(p)

    def start_test(self, app_sha256: str, test_sha256: str, filter: str | None = None, timeout_seconds: int | None = None):
        if filter and not re.fullmatch(r'com\.redloon\.prm\.[A-Za-z0-9_.$]+(?:#[A-Za-z0-9_$]+)?', filter):
            raise ValueError('Invalid test filter')
        timeout_seconds = (180 if filter else 900) if timeout_seconds is None else timeout_seconds
        if type(timeout_seconds) is not int or not 1 <= timeout_seconds <= 900:
            raise ValueError('timeout_seconds must be an integer from 1 to 900')
        return self._submit(app_sha256, test_sha256, filter, timeout_seconds=timeout_seconds)

    def start_recovery_test(self, app_sha256: str, case: str | None = None, timeout_seconds: int | None = None):
        from recovery import CASES
        if case is not None and (not isinstance(case, str) or case not in CASES):
            raise ValueError('case must be saved_records, edited_records or deleted_records')
        timeout_seconds = 900 if timeout_seconds is None else timeout_seconds
        if type(timeout_seconds) is not int or not 1 <= timeout_seconds <= 900:
            raise ValueError('timeout_seconds must be an integer from 1 to 900')
        return self._submit(app_sha256, None, None, timeout_seconds=timeout_seconds, recovery=True, case=case)

    def _submit(self, app_sha256, test_sha256, filter, interactive=False, timeout_seconds=900, recovery=False, case=None):
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
                return self._start_test(app_sha256, test_sha256, filter, job_lock, interactive, timeout_seconds, recovery, case)
            except Exception:
                job_lock.close()
                raise

    def _start_test(self, app_sha256, test_sha256, filter, job_lock, interactive=False, timeout_seconds=900, recovery=False, case=None):
        jid = uuid.uuid4().hex
        directory = self.state/jid
        directory.mkdir(mode=0o700)
        artifacts = [('app.apk', app_sha256)] + ([] if recovery else [('test.apk', test_sha256)])
        for name, digest in artifacts:
            if not isinstance(digest, str) or not re.fullmatch('[0-9a-f]{64}', digest):
                raise ValueError('Invalid hash')
            data = read_regular_file(self.inbox/name, 256*1024*1024)
            if hashlib.sha256(data).hexdigest() != digest:
                raise ValueError('APK hash mismatch')
            (directory/name).write_bytes(data)
        self.jobs[jid] = dict(status='running', phase='starting', app_sha256=app_sha256, test_sha256=test_sha256, timeout_seconds=timeout_seconds, filter=filter)
        provenance = read_provenance(self.inbox, app_sha256, test_sha256)
        if provenance is not None:
            self.jobs[jid]['provenance'] = provenance
        if recovery:
            self.jobs[jid].update(kind='recovery', case=case)
        if interactive:
            self.jobs[jid]['kind'] = 'session'
        self.active = jid
        self.cancelled = threading.Event()
        if interactive:
            self.sessions[jid] = InteractiveSession(self, jid)
        self.save(jid)
        threading.Thread(target=self.run, args=(jid, filter, job_lock), daemon=True).start()
        return dict(job_id=jid, **({'session_id': jid} if interactive else {}))

    def get_status(self, job_id: str, wait_seconds: int = 0):
        if not re.fullmatch('[0-9a-f]{32}', job_id):
            raise ValueError('Invalid job ID')
        # Allow polling a job after reconnecting to another runner process.
        path = self.state/job_id/'status.json'
        if not path.is_file():
            raise ValueError('Unknown job')
        if type(wait_seconds) is not int or not 0 <= wait_seconds <= 30:
            raise ValueError('wait_seconds must be an integer from 0 to 30')
        until = time.monotonic()+wait_seconds
        while True:
            status = json.loads(path.read_text())
            if status['status'] != 'running' or time.monotonic() >= until:
                return status
            time.sleep(min(.1, max(0, until-time.monotonic())))

    def read_logs(self, job_id: str, offset: int = 0, log: str = 'output'):
        self.get_status(job_id)
        if not isinstance(offset, int) or offset < 0:
            raise ValueError('Invalid job or offset')
        if log not in ('output', 'logcat'):
            raise ValueError('log must be output or logcat')
        p = self.state/job_id/(log+'.log')
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
        recovery = self.jobs[jid].get('kind') == 'recovery'
        started = phase_started = time.monotonic()
        deadline = started+(2880 if interactive else 1200)
        self.jobs[jid]['timings'] = {}
        def phase(name):
            nonlocal phase_started
            now = time.monotonic()
            self.jobs[jid]['timings'][self.jobs[jid]['phase']] = round(now-phase_started, 3)
            phase_started = now
            self.jobs[jid]['phase'] = name
            self.save(jid)
        adb = str(self.sdk/'platform-tools/adb')
        serial = 'emulator-5580'
        device_verified = False
        def call(args, timeout=60, progress=None):
            return run_command(args, directory, self.cancelled, deadline, timeout, progress)
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
            artifacts = [('app.apk', PACKAGE)] + ([] if recovery else [('test.apk', PACKAGE+'.test')])
            for name, expected in artifacts:
                if call([str(analyzer), 'manifest', 'application-id', str(directory/name)]).strip() != expected:
                    raise ValueError('Unexpected APK package')
            phase('emulator')
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
            phase('install')
            install_apks(device, directory, verify, note, app_only=recovery)
            if not recovery and RUNNER not in device('shell', 'pm', 'list', 'instrumentation'): raise ValueError('Unexpected instrumentation runner')
            device('shell', 'pm', 'clear', PACKAGE)
            # Only the debug instrumentation harness seeds Android Contacts.
            # Normal release APKs intentionally never request WRITE_CONTACTS.
            permissions = ['READ_CONTACTS'] if recovery else ['READ_CONTACTS', 'WRITE_CONTACTS']
            for permission in permissions:
                device('shell', 'pm', 'grant', PACKAGE, 'android.permission.'+permission)
            device('logcat', '-c')
            if recovery:
                from recovery import run_recovery
                from captures import Captures
                phase('recovery')
                deadline = min(deadline, time.monotonic()+self.jobs[jid]['timeout_seconds'])
                def check():
                    if self.cancelled.is_set() or time.monotonic() >= deadline:
                        raise RuntimeError('cancelled' if self.cancelled.is_set() else 'recovery timed out')
                def verified_device(*args):
                    check()
                    verify()
                    return device(*args)
                def capture_device(*args):
                    check()
                    verify()
                    completed = subprocess.run([adb, '-s', serial, *args], stdin=subprocess.DEVNULL,
                                               stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                                               check=True, timeout=min(20, max(.1, deadline-time.monotonic())))
                    check()
                    return completed.stdout
                captures = Captures(directory, self.outbox, jid, self.jobs[jid]['app_sha256'], None, capture_device)
                self.jobs[jid]['tests'] = []
                def progress(value):
                    tests = self.jobs[jid]['tests']
                    existing = next((i for i, entry in enumerate(tests) if entry['case'] == value['case']), None)
                    if existing is None:
                        tests.append(dict(value))
                    else:
                        tests[existing] = dict(value)
                    self.save(jid)
                launch = verified_device('shell', 'am', 'start', '-W', '-n', PACKAGE+'/.ui.MainActivity')
                if re.search(r'Error|Exception', launch):
                    raise RuntimeError('App launch failed: '+launch[-2000:])
                results = run_recovery(verified_device, self.jobs[jid].get('case'), check, note, progress,
                                       lambda label: captures.screenshot(label))
                if not results or any(entry.get('status') != 'passed' for entry in results):
                    raise ValueError('Recovery suite did not complete nonzero passing cases')
                result = dict(status='passed', phase='finished', test_count=len(results))
                return
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
            phase('tests')
            args = [adb, '-s', serial, 'shell', 'am', 'instrument', '-w', '-r']
            if filter: args += ['-e', 'class', filter]
            results = InstrumentationResults()
            self.jobs[jid]['tests'] = results.tests
            def progress(text):
                results.feed(text)
                self.save(jid)
            output = call(args+[RUNNER], self.jobs[jid].get('timeout_seconds', 900), progress)
            result = dict(status='passed', phase='finished', test_count=instrumentation_result(output))
        except Exception as error:
            result = dict(status='cancelled' if self.cancelled.is_set() else 'failed', phase='finished', error=str(error))
        finally:
            phase('cleanup')
            cleanup_deadline = time.monotonic()+120
            def cleanup_call(*args):
                remaining = cleanup_deadline-time.monotonic()
                if remaining <= 0:
                    raise RuntimeError('Cleanup timed out')
                return subprocess.run([adb, '-s', serial, *args], stdin=subprocess.DEVNULL,
                                      check=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                                      timeout=min(15, remaining)).stdout
            def cleanup_verify():
                identity = cleanup_call('emu', 'avd', 'name').decode(errors='replace').splitlines()
                if not identity or identity[0] != self.avd:
                    raise ValueError('Emulator identity changed during cleanup')
            if not interactive and device_verified and 'result' in locals() and result.get('status') == 'failed':
                try:
                    from captures import Captures
                    def capture_device(*args):
                        cleanup_verify()
                        return cleanup_call(*args)
                    Captures(directory, self.outbox, jid, self.jobs[jid].get('app_sha256', ''),
                             self.jobs[jid].get('test_sha256', ''), capture_device).screenshot('test-failure')
                except Exception as error:
                    result['capture_error'] = str(error)
            if interactive:
                try:
                    self.sessions[jid].close()
                except Exception as error:
                    result['cleanup_error'] = str(error)
            cleanup_errors = []
            for args in [('shell', 'am', 'force-stop', PACKAGE+'.test'), ('shell', 'am', 'force-stop', PACKAGE), ('logcat', '-d')]:
                if not device_verified:
                    break
                try:
                    cleanup_verify()
                    with (directory/('logcat.log' if args[0] == 'logcat' else 'output.log')).open('ab') as log:
                        # Keep full logcat off the MCP progress stream.
                        remaining = cleanup_deadline-time.monotonic()
                        if remaining <= 0:
                            raise RuntimeError('Cleanup timed out')
                        subprocess.run([adb, '-s', serial, *args], stdin=subprocess.DEVNULL,
                                       stdout=log, stderr=log, timeout=min(15, remaining), check=True)
                except Exception as error:
                    cleanup_errors.append(str(error))
            if cleanup_errors:
                result['cleanup_errors'] = cleanup_errors
                if result.get('status') == 'passed':
                    result.update(status='failed', error='Device cleanup failed')
            if self.jobs[jid].get('capture_errors') and result.get('status') == 'passed':
                result.update(status='failed', error='Recording cleanup failed; see capture_errors')
            self.jobs[jid]['timings']['cleanup'] = round(time.monotonic()-phase_started, 3)
            self.jobs[jid]['timings']['total'] = round(time.monotonic()-started, 3)
            with self.lock:
                try:
                    self.jobs[jid].update(result)
                    self.save(jid)
                finally:
                    self.active = None
                    job_lock.close()

if __name__ == '__main__':
    import sys
    from mcp.server.fastmcp import FastMCP
    runner = Runner(json.loads(Path(sys.argv[1]).read_text()))
    server = FastMCP('android-tests')
    for name in ['health', 'start_test', 'start_recovery_test', 'get_status', 'read_logs', 'cancel',
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
