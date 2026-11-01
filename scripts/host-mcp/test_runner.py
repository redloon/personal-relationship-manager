"""Runner safety and protocol probes; no Android SDK needed."""
import importlib.util
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import threading
import unittest
from unittest.mock import Mock, patch

spec = importlib.util.spec_from_file_location('runner', Path(__file__).with_name('runner.py'))
runner = importlib.util.module_from_spec(spec)
spec.loader.exec_module(runner)


class InstallTests(unittest.TestCase):
    def test_signing_change_resets_only_fixed_packages_after_verification(self):
        calls = []
        def device(*args):
            calls.append(args)
            if len(calls) == 1:
                raise runner.CommandError(['adb'], 1, 'INSTALL_FAILED_UPDATE_INCOMPATIBLE')
            if args[:4] == ('shell', 'pm', 'list', 'packages'):
                return 'package:'+args[4]+'\n'
            return 'Success'
        verify = Mock(side_effect=lambda: calls.append(('verify',)))
        note = Mock()
        runner.install_apks(device, Path('/job'), verify, note)
        self.assertEqual(calls, [
            ('install', '-r', '/job/app.apk'), ('verify',),
            ('shell', 'pm', 'list', 'packages', runner.PACKAGE+'.test'),
            ('uninstall', runner.PACKAGE+'.test'),
            ('shell', 'pm', 'list', 'packages', runner.PACKAGE),
            ('uninstall', runner.PACKAGE),
            ('install', '-r', '/job/app.apk'), ('install', '-r', '/job/test.apk'),
        ])
        note.assert_called_once()

    def test_other_install_errors_never_uninstall(self):
        device = Mock(side_effect=runner.CommandError(['adb'], 1, 'INSTALL_FAILED_INSUFFICIENT_STORAGE'))
        verify = Mock()
        with self.assertRaisesRegex(runner.CommandError, 'INSUFFICIENT_STORAGE'):
            runner.install_apks(device, Path('/job'), verify, Mock())
        self.assertEqual(device.call_count, 1)
        verify.assert_not_called()

    def test_identity_change_blocks_reset(self):
        device = Mock(side_effect=runner.CommandError(['adb'], 1, 'INSTALL_FAILED_UPDATE_INCOMPATIBLE'))
        with self.assertRaisesRegex(ValueError, 'wrong emulator'):
            runner.install_apks(device, Path('/job'), Mock(side_effect=ValueError('wrong emulator')), Mock())
        self.assertEqual(device.call_count, 1)


class RunnerTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.config = dict(sdk=str(self.root/'sdk'), inbox=str(self.root/'inbox'), state=str(self.root/'jobs'), avd='android_tests')
        self.instance = runner.Runner(self.config)

    def test_separate_process_lock_prevents_submit_and_false_interruption(self):
        jid = 'a'*32
        job = self.root/'jobs'/jid
        job.mkdir()
        (job/'status.json').write_text(json.dumps(dict(status='running', phase='tests')))
        code = 'import fcntl,sys; f=open(sys.argv[1],"a"); fcntl.flock(f,fcntl.LOCK_EX); print("locked",flush=True); sys.stdin.read()'
        with subprocess.Popen([sys.executable, '-c', code, str(self.root/'jobs/runner.lock')], stdin=subprocess.PIPE, stdout=subprocess.PIPE, text=True) as child:
            self.assertEqual(child.stdout.readline().strip(), 'locked')
            try:
                other = runner.Runner(self.config)
                self.assertTrue(other.health()['busy'])
                self.assertEqual(other.get_status(jid)['status'], 'running')
                with self.assertRaisesRegex(ValueError, 'Another host/sandbox'):
                    other.start_test('a'*64, 'b'*64)
            finally:
                child.communicate(timeout=5)
        self.assertFalse(self.instance.health()['busy'])
        self.assertEqual(runner.Runner(self.config).get_status(jid)['status'], 'interrupted')

    def test_invalid_submission_releases_lock(self):
        with self.assertRaisesRegex(ValueError, 'Invalid hash'):
            self.instance.start_test('invalid', 'b'*64)
        self.assertFalse(self.instance.health()['busy'])

    def test_logs_can_be_read_after_reconnect_with_integer_offset(self):
        jid = 'b'*32
        job = self.root/'jobs'/jid
        job.mkdir()
        (job/'status.json').write_text(json.dumps(dict(status='passed', phase='finished')))
        (job/'output.log').write_text('hello')
        self.assertEqual(self.instance.read_logs(jid, 2), dict(text='llo', next_offset=5))
        with self.assertRaisesRegex(ValueError, 'Invalid job or offset'):
            self.instance.read_logs(jid, '2')

    def test_failed_command_releases_lock(self):
        jid = 'c'*32
        job = self.root/'jobs'/jid
        job.mkdir()
        self.instance.jobs[jid] = dict(status='running', phase='starting')
        self.instance.active = jid
        self.instance.cancelled = threading.Event()
        self.instance.save(jid)
        process = Mock()
        process.__enter__ = Mock(return_value=process)
        process.__exit__ = Mock(return_value=False)
        process.poll.return_value = 1
        process.returncode = 1
        lock = Mock()
        with patch.object(runner.subprocess, 'Popen', return_value=process):
            self.instance.run(jid, None, lock)
        self.assertEqual(self.instance.get_status(jid)['status'], 'failed')
        self.assertIsNone(self.instance.active)
        lock.close.assert_called_once()

    def test_success_is_published_only_after_cleanup(self):
        # Real subprocesses exercise the runner's command/cleanup boundaries.
        script = '''#!/usr/bin/env python3
import sys
a=sys.argv[1:]
if a[:2] == ['manifest', 'application-id']:
 print('com.idontremember.personalcrm'+('.test' if a[-1].endswith('test.apk') else ''))
elif a == ['devices']: print('emulator-5580 device')
elif a[2:] == ['emu','avd','name']: print('android_tests')
elif a[2:] == ['shell','getprop','sys.boot_completed']: print('1')
elif a[2:] == ['shell','pm','list','instrumentation']: print('com.idontremember.personalcrm.test/com.idontremember.personalcrm.HiltTestRunner')
elif 'instrument' in a: print('OK (3 tests)')
else: print('Success')
'''
        for name in ['platform-tools/adb', 'cmdline-tools/latest/bin/apkanalyzer']:
            path = self.root/'sdk'/name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(script)
            path.chmod(0o755)
        jid = 'd'*32
        (self.root/'jobs'/jid).mkdir()
        self.instance.jobs[jid] = dict(status='running', phase='starting')
        self.instance.active = jid
        self.instance.cancelled = threading.Event()
        self.instance.save(jid)
        lock = (self.root/'jobs/runner.lock').open('a')
        runner.fcntl.flock(lock, runner.fcntl.LOCK_EX)
        original_run = subprocess.run
        observed = []
        def cleanup(*args, **kwargs):
            observed.append(self.instance.get_status(jid))
            self.assertTrue(self.instance.health()['busy'])
            return original_run(*args, **kwargs)
        with patch.object(runner.subprocess, 'run', side_effect=cleanup):
            self.instance.run(jid, None, lock)
        self.assertEqual(len(observed), 3)
        self.assertTrue(all(s == dict(status='running', phase='cleanup') for s in observed))
        self.assertEqual(self.instance.get_status(jid), dict(status='passed', phase='finished', test_count=3))
        self.assertFalse(self.instance.health()['busy'])


if __name__ == '__main__':
    unittest.main()
