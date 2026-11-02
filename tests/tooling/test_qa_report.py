"""Review packs must link verified captures and describe evidence honestly."""
import hashlib
import json
from pathlib import Path
import subprocess
import tempfile
import unittest

TASK = Path(__file__).resolve().parents[2]/'.mise/tasks/qa-report'


class QaReportTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.owner = 'a'*32
        self.directory = self.root/self.owner
        self.directory.mkdir()
        self.manifest = self.directory/'manifest.json'
        self.artifacts = []
        self.add_artifact('b'*32, 'image/png', '.png', b'PNG binary', 'Search [results]')
        self.add_artifact('c'*32, 'video/mp4', '.mp4', b'MP4 binary', 'Walkthrough')
        self.write_manifest()

    def add_artifact(self, identifier, mime, suffix, data, label):
        path = self.directory/(identifier+suffix)
        path.write_bytes(data)
        self.artifacts.append(dict(artifact_id=identifier, owner_id=self.owner,
            app_sha256='d'*64, test_sha256='e'*64, sha256=hashlib.sha256(data).hexdigest(),
            bytes=len(data), mime_type=mime, label=label, captured_at='2026-11-02T08:00:00+00:00',
            relative_path=self.owner+'/'+path.name, shared_path=str(path)))

    def write_manifest(self):
        self.manifest.write_text(json.dumps(dict(owner_id=self.owner, artifacts=self.artifacts)))

    def run_task(self, *args):
        return subprocess.run([str(TASK), str(self.manifest), *args], capture_output=True, text=True, cwd='/tmp')

    def test_direct_task_creates_review_with_verified_images_video_and_provenance(self):
        result = self.run_task()
        self.assertEqual(result.returncode, 0, result.stderr)
        report = (self.directory/'review.md').read_text()
        self.assertIn('![Search \\[results\\]]', report)
        self.assertIn('bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb.png', report)
        self.assertIn('[Watch Walkthrough]', report)
        self.assertIn('d'*64, report)
        self.assertIn('2026-11-02', report)
        self.assertIn('not a regression-suite pass', report)
        self.assertIn(self.artifacts[0]['sha256'], report)

    def test_optional_output_and_evidence_preserve_failure_and_steps(self):
        evidence = self.root/'evidence.json'
        evidence.write_text(json.dumps(dict(status='failed', steps=[
            dict(index=0, action='tap', status='passed'),
            dict(index=1, action='assert', status='failed', error='Result missing')],
            error='Expected result did not appear')))
        output = self.root/'chosen review.md'
        result = self.run_task('--output', str(output), '--evidence', str(evidence))
        self.assertEqual(result.returncode, 0, result.stderr)
        report = output.read_text()
        self.assertIn('failed', report)
        self.assertIn('Result missing', report)
        self.assertIn('Expected result did not appear', report)
        self.assertIn('assert', report)
        self.assertIn(self.owner+'/', report)

    def test_bash_fallback_and_unverified_actions_are_not_failures(self):
        evidence = self.root/'evidence.json'
        evidence.write_text(json.dumps(dict(status='passed', results=[
            dict(action='tap', verified=False), dict(action='assert', verified=True)])))
        result = subprocess.run(['bash', str(TASK), str(self.manifest), '--evidence', str(evidence)],
            capture_output=True, text=True, cwd='/tmp')
        self.assertEqual(result.returncode, 0, result.stderr)
        report = (self.directory/'review.md').read_text()
        self.assertIn('tap — action sent; outcome not asserted', report)
        self.assertIn('assert — verified', report)

    def test_relative_only_manifest_works_when_copied(self):
        for item in self.artifacts:
            del item['shared_path']
        self.write_manifest()
        self.assertEqual(self.run_task().returncode, 0)

    def test_path_traversal_and_outside_shared_path_are_rejected(self):
        for field, value in [('relative_path', '../secret'), ('shared_path', '/etc/passwd')]:
            with self.subTest(field=field):
                original = self.artifacts[0][field]
                self.artifacts[0][field] = value
                self.write_manifest()
                result = self.run_task()
                self.assertNotEqual(result.returncode, 0)
                self.assertFalse((self.directory/'review.md').exists())
                self.artifacts[0][field] = original

    def test_tampered_capture_is_rejected(self):
        Path(self.artifacts[0]['shared_path']).write_bytes(b'changed')
        result = self.run_task()
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('hash', result.stderr)

    def test_symlink_capture_outside_directory_is_rejected(self):
        path = Path(self.artifacts[0]['shared_path'])
        external = self.root/'external'
        external.write_bytes(path.read_bytes())
        path.unlink()
        path.symlink_to(external)
        self.assertNotEqual(self.run_task().returncode, 0)

    def test_unknown_mime_and_missing_metadata_are_rejected(self):
        self.artifacts[0]['mime_type'] = 'text/html'
        self.write_manifest()
        self.assertNotEqual(self.run_task().returncode, 0)
        del self.artifacts[0]['mime_type']
        self.write_manifest()
        self.assertNotEqual(self.run_task().returncode, 0)

    def test_output_cannot_overwrite_manifest_or_capture(self):
        for output in [self.manifest, Path(self.artifacts[0]['shared_path'])]:
            with self.subTest(output=output):
                before = output.read_bytes()
                self.assertNotEqual(self.run_task('--output', str(output)).returncode, 0)
                self.assertEqual(output.read_bytes(), before)

    def test_empty_manifest_is_clear_without_claiming_validation(self):
        self.artifacts = []
        self.write_manifest()
        result = self.run_task()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn('No captures', (self.directory/'review.md').read_text())


if __name__ == '__main__':
    unittest.main()
