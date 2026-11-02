"""Session ownership and command lifecycle without Android."""
import queue
import tempfile
import threading
import time
import unittest
from pathlib import Path
from unittest.mock import Mock

from sessions import InteractiveSession


class SessionTests(unittest.TestCase):
    def make(self):
        owner = Mock()
        owner.cancelled = threading.Event()
        owner.jobs = {'a'*32: {'status': 'running'}}
        owner.state = Path(self.addCleanupTemp())
        owner.inbox = owner.state/'inbox'
        owner.outbox = owner.state/'outbox'
        owner.lock = threading.Lock()
        session = InteractiveSession(owner, 'a'*32)
        session.interaction = Mock()
        session.captures = Mock()
        return owner, session

    def addCleanupTemp(self):
        tmp = tempfile.TemporaryDirectory()
        self.addCleanup(tmp.cleanup)
        return tmp.name

    def test_only_one_pending_operation(self):
        _, session = self.make()
        session.enqueue('inspect')
        with self.assertRaisesRegex(ValueError, 'pending'):
            session.enqueue('inspect')

    def test_session_cancellation_interrupts_check(self):
        owner, session = self.make()
        owner.cancelled.set()
        with self.assertRaisesRegex(RuntimeError, 'cancelled'):
            session.check()

    def test_idle_and_total_timeouts(self):
        _, session = self.make()
        session.touched = time.monotonic()-601
        with self.assertRaisesRegex(RuntimeError, 'idle'):
            session.check()
        session.touched = time.monotonic()
        session.started = time.monotonic()-2701
        with self.assertRaisesRegex(RuntimeError, 'duration'):
            session.check()

    def test_close_marks_pending_request_failed(self):
        _, session = self.make()
        request = session.enqueue('inspect')
        session.close()
        self.assertTrue(request['done'].is_set())
        self.assertIn('closed', request['error'])
        session.close()
        session.captures.close.assert_called_once()

    def test_inspection_errors_are_recoverable(self):
        _, session = self.make()
        session.interaction.inspect.side_effect = ValueError('screen unavailable')
        request = session.enqueue('inspect')
        session.process(request)
        self.assertEqual(request['error'], 'screen unavailable')
        self.assertTrue(request['done'].is_set())
        self.assertFalse(session.pending)
        session.enqueue('inspect')

    def test_batch_stops_on_first_failure_and_keeps_session(self):
        owner, session = self.make()
        batch = 'b'*32
        owner.jobs[batch] = {'status': 'running'}
        session.interaction.perform.side_effect = [{'action': 'tap'}, ValueError('ambiguous')]
        session.interaction.inspect.return_value = {'snapshot_id': 'snapshot'}
        session.captures.screenshot.return_value = {'artifact_id': 'capture'}
        request = session.enqueue('batch', batch_id=batch, steps=[{'action': 'tap'}]*3)
        session.process(request)
        self.assertEqual(session.interaction.perform.call_count, 2)
        result = owner.jobs[batch]
        self.assertEqual(result['status'], 'failed')
        self.assertEqual(result['failed_step'], 1)
        self.assertEqual(len(result['results']), 1)
        self.assertEqual(result['final_snapshot']['snapshot_id'], 'snapshot')
        self.assertFalse(owner.cancelled.is_set())

    def test_batch_cancellation_does_not_cancel_session(self):
        owner, session = self.make()
        session.batch_cancelled.set()
        with self.assertRaisesRegex(RuntimeError, 'batch cancelled'):
            session.check_batch()
        self.assertFalse(owner.cancelled.is_set())


if __name__ == '__main__':
    unittest.main()
