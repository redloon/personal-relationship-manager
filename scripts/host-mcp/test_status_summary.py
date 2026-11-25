import json
from pathlib import Path
import tempfile
import unittest

from runner import Runner
from unittest.mock import patch


class SummaryTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.runner = Runner(dict(sdk=str(self.root/'sdk'), inbox=str(self.root/'inbox'), state=str(self.root/'jobs'), avd='android_tests'))
        self.jid = 'a'*32
        self.path = self.root/'jobs'/self.jid/'status.json'
        self.path.parent.mkdir()

    def status(self, value, **kwargs):
        self.path.write_text(json.dumps(value))
        return self.runner.get_status(self.jid, **kwargs)

    def test_compatibility_and_unknown_counts(self):
        original = dict(status='running', phase='cleanup', tests=[dict(outcome='passed')], test_count=1, snapshot={'secret': True})
        self.assertEqual(self.status(original), original)
        summary = self.status(original, detail='summary')
        self.assertEqual(summary['job_id'], self.jid)
        self.assertEqual(summary['kind'], 'test')
        self.assertEqual(summary['status'], 'running')
        self.assertEqual(summary['phase'], 'cleanup')
        self.assertEqual(summary['test_count'], 1)
        self.assertNotIn('tests', summary)
        self.assertNotIn('snapshot', summary)
        self.assertIsNone(self.status(dict(status='running', phase='starting'), detail='summary')['observed_test_count'])
        with self.assertRaisesRegex(ValueError, 'detail'):
            self.runner.get_status(self.jid, detail='invalid')

    def test_adversarial_failures_and_provenance_are_bounded(self):
        huge = '\u0000😀"\\'*10000
        original = dict(status='failed', phase='finished', tests=[dict(outcome='failed', class_name=huge, test_name=huge) for _ in range(20)],
                        error=huge, cleanup_errors=[huge]*20, capture_errors=[huge]*20,
                        app_sha256='b'*64, test_sha256='c'*64,
                        provenance=dict(source_sha256='d'*64, git_revision='e'*40, release_manifest=dict(secret=huge)),
                        timings=dict(total=12.5), results=[dict(action='tap', snapshot=huge)]*100, final_snapshot=huge)
        summary = self.status(original, detail='summary')
        self.assertLessEqual(len(json.dumps(summary).encode()), 8192)
        self.assertEqual(summary['failure_count'], 61)
        self.assertEqual(len(summary['failure_examples']), 3)
        self.assertEqual(summary['failures_omitted'], 58)
        self.assertTrue(summary['truncated'])
        self.assertEqual(summary['action_count'], 100)
        self.assertEqual(summary['provenance']['source_sha256'], 'd'*64)
        self.assertEqual(summary['timings']['total'], 12.5)
        self.assertEqual(self.runner.get_status(self.jid), original)

    def test_recovery_batch_cancelled_and_interrupted(self):
        for status in ('running', 'passed', 'failed', 'cancelled', 'interrupted'):
            original = dict(kind='recovery', status=status, phase='cleanup', tests=[dict(case='restore', status='passed'), dict(case='restart', status='failed')])
            summary = self.status(original, detail='summary')
            self.assertEqual(summary['status'], status)
            self.assertEqual(summary['observed_test_count'], 2)
            self.assertEqual(summary['failure_count'], 1)
        summary = self.status(dict(kind='batch', session_id='b'*32, status='failed', phase='finished', failed_step=1, results=[dict(action='tap')]), detail='summary')
        self.assertEqual(summary['session_id'], 'b'*32)
        self.assertEqual(summary['failed_step'], 1)

    def test_summary_waits_through_cleanup_with_existing_wait_bounds(self):
        self.path.write_text(json.dumps(dict(status='running', phase='cleanup')))
        def finish(unused):
            self.path.write_text(json.dumps(dict(status='passed', phase='finished', test_count=1)))
        with patch('runner.time.sleep', side_effect=finish):
            summary = self.runner.get_status(self.jid, wait_seconds=1, detail='summary')
        self.assertEqual(summary['status'], 'passed')
        for invalid in (-1, 31, True, '1'):
            with self.assertRaises(ValueError):
                self.runner.get_status(self.jid, wait_seconds=invalid, detail='summary')

    def test_untrusted_scalar_sizes_cannot_break_bound(self):
        original = dict(status='failed', phase='finished', test_count=10**4000,
                        failed_step=10**4000, timings=dict(total=10**4000), error='bad')
        summary = self.status(original, detail='summary')
        self.assertLessEqual(len(json.dumps(summary, indent=2).encode()), 8192)

    def test_size_fallback_preserves_valid_identity_and_hashes(self):
        huge = '😀'*10000
        original = dict(status=huge, phase=huge, kind=huge, session_id='f'*32,
                        filter=huge, case=huge, app_sha256='b'*64, test_sha256='c'*64,
                        provenance=dict(source_sha256='d'*64, git_revision='e'*40),
                        error=huge, cleanup_error=huge, capture_error=huge)
        summary = self.status(original, detail='summary')
        self.assertLessEqual(len(json.dumps(summary, indent=2).encode()), 8192)
        for key in ('app_sha256', 'test_sha256', 'provenance', 'session_id'):
            self.assertEqual(summary[key], original[key])


if __name__ == '__main__':
    unittest.main()
