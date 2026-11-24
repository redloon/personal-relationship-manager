"""Real subprocess probes for result parsing, live logs and deadlines."""
import importlib.util
from pathlib import Path
import subprocess
import sys
import tempfile
import threading
import time
import unittest

import runner


class ResultTests(unittest.TestCase):
    def test_requires_terminal_success_and_rejects_mixed_results(self):
        good = 'OK (3 tests)\nINSTRUMENTATION_CODE: -1\n'
        self.assertEqual(runner.instrumentation_result(good), 3)
        for output in ['', 'OK (0 tests)\nINSTRUMENTATION_CODE: -1', 'OK (3 tests)',
                       good + 'INSTRUMENTATION_ABORTED: cancelled',
                       good + 'INSTRUMENTATION_RESULT: shortMsg=Process crashed.',
                       good + 'FAILURES!!!', good + 'INSTRUMENTATION_RESULT: stream=FAILURES!!!',
                       good + 'INSTRUMENTATION_CODE: 0',
                       good + 'INSTRUMENTATION_STATUS_CODE: -2',
                       good + 'INSTRUMENTATION_FAILED: error']:
            with self.subTest(output=output), self.assertRaises(ValueError):
                runner.instrumentation_result(output)

    def test_individual_results_and_durations(self):
        results = runner.InstrumentationResults()
        results.feed('INSTRUMENTATION_STATUS: class=example.Test\nINSTRUMENTATION_STATUS: test=works\nINSTRUMENTATION_STATUS_CODE: 1\n', now=10)
        results.feed('INSTRUMENTATION_STATUS: class=example.Test\nINSTRUMENTATION_STATUS: test=works\nINSTRUMENTATION_STATUS_CODE: 0\n', now=12.5)
        self.assertEqual(results.tests, [dict(class_name='example.Test', test_name='works', outcome='passed', duration_seconds=2.5)])


class ProvenanceTests(unittest.TestCase):
    def test_manifest_is_optional_hash_matched_and_rejects_symlinks(self):
        import json
        import os
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            self.assertIsNone(runner.read_provenance(root, 'a'*64, 'b'*64))
            manifest = root/'manifest.json'
            data = dict(app_sha256='a'*64, test_sha256='b'*64,
                        provenance=dict(source_sha256='c'*64, git_revision='d'*40, unexpected='omit'))
            manifest.write_text(json.dumps(data))
            self.assertEqual(runner.read_provenance(root, 'a'*64, 'b'*64),
                             dict(source_sha256='c'*64, git_revision='d'*40))
            self.assertIsNone(runner.read_provenance(root, 'e'*64, 'b'*64))
            manifest.rename(root/'other.json')
            manifest.symlink_to(root/'other.json')
            with self.assertRaises(OSError):
                runner.read_provenance(root, 'a'*64, 'b'*64)

    def test_manifest_requires_a_bounded_regular_file_and_valid_fingerprints(self):
        import json
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            manifest = root/'manifest.json'
            manifest.write_text('x'*65537)
            with self.assertRaises(ValueError):
                runner.read_provenance(root, 'a'*64, 'b'*64)
            manifest.write_text(json.dumps(dict(app_sha256='a'*64, test_sha256='b'*64,
                                                provenance=dict(source_sha256='invalid', git_revision=None))))
            with self.assertRaises(ValueError):
                runner.read_provenance(root, 'a'*64, 'b'*64)


class CommandTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.directory = Path(self.tmp.name)
        self.cancelled = threading.Event()

    def test_timeout_preserves_partial_output(self):
        with self.assertRaisesRegex(RuntimeError, 'timed out'):
            runner.run_command([sys.executable, '-u', '-c', 'import time; print("partial"); time.sleep(5)'],
                               self.directory, self.cancelled, time.monotonic()+5, .2)
        self.assertEqual((self.directory/'output.log').read_text(), 'partial\n')

    def test_cancellation_streams_and_preserves_partial_output(self):
        errors = []
        def run():
            try:
                runner.run_command([sys.executable, '-u', '-c', 'import time; print("live"); time.sleep(5)'],
                                   self.directory, self.cancelled, time.monotonic()+5, 5)
            except RuntimeError as error:
                errors.append(str(error))
        thread = threading.Thread(target=run)
        thread.start()
        try:
            until = time.monotonic()+2
            while time.monotonic() < until:
                path = self.directory/'output.log'
                if path.exists() and path.read_text() == 'live\n':
                    break
                time.sleep(.02)
            else:
                self.fail('output was not streamed while command ran')
        finally:
            self.cancelled.set()
            thread.join(timeout=3)
        self.assertEqual(errors, ['cancelled'])

    def test_whole_job_deadline_takes_precedence(self):
        started = time.monotonic()
        with self.assertRaisesRegex(RuntimeError, 'timed out'):
            runner.run_command([sys.executable, '-c', 'import time; time.sleep(5)'],
                               self.directory, self.cancelled, started+.15, 5)
        self.assertLess(time.monotonic()-started, 1)


class RunnerDeadlineTests(unittest.TestCase):
    def test_timeout_retains_partial_results_and_finishes_cleanup(self):
        import hashlib
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            script = """#!/usr/bin/env python3
import sys,time
a=sys.argv[1:]
if a[:2] == ['manifest', 'application-id']:
 print('com.redloon.prm'+('.test' if a[-1].endswith('test.apk') else ''))
elif a == ['devices']: print('emulator-5580 device')
elif a[2:] == ['emu','avd','name']: print('android_tests')
elif a[2:] == ['shell','getprop','sys.boot_completed']: print('1')
elif a[2:] == ['shell','pm','list','instrumentation']: print('com.redloon.prm.test/com.redloon.prm.HiltTestRunner')
elif 'instrument' in a:
 print('partial instrumentation output',flush=True)
 time.sleep(5)
else: print('Success')
"""
            for name in ['platform-tools/adb', 'cmdline-tools/latest/bin/apkanalyzer']:
                path = root/'sdk'/name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(script)
                path.chmod(0o755)
            inbox = root/'inbox'
            inbox.mkdir()
            for name in ['app.apk', 'test.apk']:
                (inbox/name).write_bytes(b'apk')
            instance = runner.Runner(dict(sdk=str(root/'sdk'), inbox=str(inbox), state=str(root/'jobs'), avd='android_tests'))
            digest = hashlib.sha256(b'apk').hexdigest()
            jid = instance.start_test(digest, digest, timeout_seconds=1)['job_id']
            status = instance.get_status(jid, wait_seconds=5)
            self.assertEqual(status['status'], 'failed')
            self.assertEqual(status['error'], 'timed out')
            self.assertIn('partial instrumentation output', instance.read_logs(jid)['text'])
            self.assertEqual(instance.read_logs(jid, log='logcat')['text'], 'Success\n')
            self.assertIn('cleanup', status['timings'])
            self.assertGreaterEqual(status['timings']['tests'], 1)
            self.assertFalse(instance.health()['busy'])


if __name__ == '__main__':
    unittest.main()
