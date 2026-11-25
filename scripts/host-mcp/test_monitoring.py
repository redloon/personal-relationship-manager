import json
import unittest
from pathlib import Path
import tempfile

from monitoring import monitor
from runner import Runner


class MonitorTests(unittest.TestCase):
    def test_bounded_wait_continuation_and_complete_logs_through_cleanup(self):
        calls, chunks, changes = [], [], []
        statuses = iter([
            dict(status='running', phase='tests'),
            dict(status='running', phase='tests'),
            dict(status='running', phase='cleanup'),
            dict(status='passed', phase='finished', test_count=1),
        ])
        available = b''

        def call(name, args):
            nonlocal available
            calls.append((name, args))
            if name == 'get_status':
                self.assertEqual(args['detail'], 'summary')
                self.assertEqual(args['wait_seconds'], 30)
                status = next(statuses)
                if status['phase'] == 'cleanup':
                    available = b'first\n'
                if status['status'] == 'passed':
                    available = b'first\ncleanup\n'
                return status
            offset = args['offset']
            self.assertIs(type(offset), int)
            text = available[offset:offset+4]
            return dict(text=text.decode(), next_offset=offset+len(text))

        state = None
        for expected in (False, False, False, True):
            result = monitor(call, 'job', chunks.append, state=state, on_change=changes.append, max_polls=1, max_chunks=8)
            state = result['continuation']
            self.assertEqual(result['complete'], expected)
        self.assertEqual(''.join(chunk['text'] for chunk in chunks), 'first\ncleanup\n')
        self.assertEqual(len(changes), 3)
        self.assertEqual(state['offset'], 14)
        self.assertNotIn('start_test', [name for name, args in calls])

    def test_terminal_with_budget_exhaustion_resumes_without_resubmission(self):
        retained = []

        def call(name, args):
            if name == 'get_status':
                return dict(status='cancelled', phase='finished', error='x'*20000)
            offset = args['offset']
            return dict(text='x' if offset < 3 else '', next_offset=min(offset+1, 3))

        first = monitor(call, 'job', retained.append, max_polls=1, max_chunks=1)
        self.assertFalse(first['complete'])
        self.assertLessEqual(len(json.dumps(first)), 12000)
        second = monitor(call, 'job', retained.append, state=first['continuation'], max_polls=1, max_chunks=8)
        self.assertTrue(second['complete'])
        self.assertEqual(len(retained), 3)
        self.assertEqual(second['status']['status'], 'cancelled')

    def test_sink_failure_does_not_advance_checkpoint_and_bad_offsets_fail(self):
        state = dict(job_id='job', offset=0)

        def call(name, args):
            return dict(status='failed', phase='finished') if name == 'get_status' else dict(text='log', next_offset=3)

        def broken(chunk):
            raise OSError('disk full')

        with self.assertRaises(OSError):
            monitor(call, 'job', broken, state=state, max_polls=1)
        self.assertEqual(state['offset'], 0)
        for offset in ('3', True, -1, 0):
            def bad_call(name, args):
                return dict(status='interrupted', phase='finished') if name == 'get_status' else dict(text='log', next_offset=offset)
            with self.assertRaises(ValueError):
                monitor(bad_call, 'job', lambda chunk: None, max_polls=1)
        with self.assertRaises(ValueError):
            monitor(call, 'other', lambda chunk: None, state=state)

    def test_monitoring_timeout_or_transport_failure_never_submits(self):
        names = []

        def call(name, args):
            names.append(name)
            raise TimeoutError('transport timeout')

        with self.assertRaises(TimeoutError):
            monitor(call, 'job', lambda chunk: None)
        self.assertEqual(names, ['get_status'])
        for kwargs in (dict(max_polls=0), dict(max_polls=True), dict(max_chunks=0), dict(wait_seconds=31)):
            with self.assertRaises(ValueError):
                monitor(call, 'job', lambda chunk: None, **kwargs)

    def test_real_log_chunks_use_byte_offsets_and_keep_every_returned_chunk(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            runner = Runner(dict(sdk=str(root/'sdk'), inbox=str(root/'inbox'), state=str(root/'jobs'), avd='android_tests'))
            job_id = 'a'*32
            (root/'jobs'/job_id).mkdir()
            runner.jobs[job_id] = dict(status='passed', phase='finished', test_count=1)
            runner.save(job_id)
            data = ('😀'*20000+'cleanup\n').encode()
            (root/'jobs'/job_id/'output.log').write_bytes(data)
            chunks = []
            def call(name, arguments):
                return getattr(runner, name)(**arguments)
            first = monitor(call, job_id, chunks.append, max_polls=1, max_chunks=1, wait_seconds=0)
            self.assertFalse(first['complete'])
            self.assertEqual(first['continuation']['offset'], 32768)
            second = monitor(call, job_id, chunks.append, state=first['continuation'], max_polls=1, wait_seconds=0)
            self.assertTrue(second['complete'])
            self.assertEqual(second['continuation']['offset'], len(data))
            self.assertEqual([chunk['offset'] for chunk in chunks], [0, 32768, 65536])
            self.assertEqual(''.join(chunk['text'] for chunk in chunks), data.decode())


if __name__ == '__main__':
    unittest.main()
