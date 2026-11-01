"""Trusted APK-only host runner. Install outside the shared checkout."""
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import threading
import time
import uuid

PACKAGE = 'com.idontremember.personalcrm'
SOURCE_SHA256 = hashlib.sha256(Path(__file__).read_bytes()).hexdigest()

RUNNER = PACKAGE + '.test/' + PACKAGE + '.HiltTestRunner'

class Runner:
    def __init__(self, config):
        self.sdk = Path(config['sdk']).resolve()
        self.inbox = Path(config['inbox']).resolve()
        self.state = Path(config['state']).resolve()
        self.avd = config['avd']
        self.state.mkdir(parents=True, exist_ok=True)
        self.lock = threading.Lock()
        self.active = None
        self.jobs = {}
        for p in self.state.glob('*/status.json'):
            job = json.loads(p.read_text())
            if job['status'] == 'running':
                job.update(status='interrupted', phase='server restarted')
            self.jobs[p.parent.name] = job

    def health(self):
        return dict(version=2, pid=os.getpid(), source_sha256=SOURCE_SHA256, state=str(self.state), sdk_available=(self.sdk/'platform-tools/adb').is_file(), avd=self.avd)

    def save(self, jid):
        p = self.state/jid/'status.json'
        tmp = p.with_suffix('.tmp')
        tmp.write_text(json.dumps(self.jobs[jid]))
        tmp.replace(p)

    def start_test(self, app_sha256, test_sha256, filter=None):
        if filter and not re.fullmatch(r'com\.idontremember\.personalcrm\.[A-Za-z0-9_.$]+(?:#[A-Za-z0-9_$]+)?', filter):
            raise ValueError('Invalid test filter')
        with self.lock:
            if self.active:
                raise ValueError('Runner busy')
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
            self.jobs[jid] = dict(status='running', phase='starting')
            self.active = jid
            self.cancelled = threading.Event()
            self.save(jid)
            threading.Thread(target=self.run, args=(jid, filter), daemon=True).start()
            return dict(job_id=jid)

    def get_status(self, job_id):
        return dict(self.jobs[job_id])

    def read_logs(self, job_id, offset=0):
        if job_id not in self.jobs or offset < 0:
            raise ValueError('Invalid job or offset')
        p = self.state/job_id/'output.log'
        data = p.read_bytes() if p.exists() else b''
        chunk = data[offset:offset+32768]
        return dict(text=chunk.decode(errors='replace'), next_offset=offset+len(chunk))

    def cancel(self, job_id):
        if job_id not in self.jobs:
            raise ValueError('Unknown job')
        if self.active == job_id:
            self.cancelled.set()
        return self.get_status(job_id)

    def run(self, jid, filter):
        directory = self.state/jid
        deadline = time.monotonic()+900
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
                    raise RuntimeError('Command failed: '+str(p.returncode))
                return output.decode(errors='replace')
        def device(*args):
            return call([adb, '-s', serial, *args])
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
            for name in ['app.apk', 'test.apk']: device('install', '-r', str(directory/name))
            if RUNNER not in device('shell', 'pm', 'list', 'instrumentation'): raise ValueError('Unexpected instrumentation runner')
            device('shell', 'pm', 'clear', PACKAGE)
            for permission in ['READ_CONTACTS', 'WRITE_CONTACTS']:
                device('shell', 'pm', 'grant', PACKAGE, 'android.permission.'+permission)
            device('logcat', '-c')
            self.jobs[jid]['phase'] = 'tests'; self.save(jid)
            args = [adb, '-s', serial, 'shell', 'am', 'instrument', '-w']
            if filter: args += ['-e', 'class', filter]
            output = call(args+[RUNNER], 180)
            if not re.search(r'OK \([1-9][0-9]* tests?\)', output) or 'FAILURES' in output or 'INSTRUMENTATION_FAILED' in output:
                raise RuntimeError('Tests failed or completion missing')
            self.jobs[jid].update(status='passed', phase='finished')
        except Exception as error:
            self.jobs[jid].update(status='cancelled' if self.cancelled.is_set() else 'failed', phase='finished', error=str(error))
        finally:
            for args in [('shell', 'am', 'force-stop', PACKAGE+'.test'), ('shell', 'am', 'force-stop', PACKAGE), ('logcat', '-d', '-t', '2000')]:
                if not device_verified:
                    break
                try:
                    with (directory/'output.log').open('ab') as log:
                        subprocess.run([adb, '-s', serial, *args], stdout=log, stderr=log, timeout=15)
                except Exception: pass
            with self.lock:
                self.save(jid)
                self.active = None

if __name__ == '__main__':
    import sys
    from mcp.server.fastmcp import FastMCP
    runner = Runner(json.loads(Path(sys.argv[1]).read_text()))
    server = FastMCP('android-tests')
    for name in ['health', 'start_test', 'get_status', 'read_logs', 'cancel']:
        server.tool()(getattr(runner, name))
    server.run(transport='stdio')
