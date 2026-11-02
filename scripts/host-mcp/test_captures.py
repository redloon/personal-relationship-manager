"""Capture transport and artifact safety probes; no Android SDK required."""
import hashlib
import importlib.util
import json
import subprocess
from pathlib import Path
import tempfile
import unittest
from unittest.mock import Mock

spec = importlib.util.spec_from_file_location('captures', Path(__file__).with_name('captures.py'))
captures = importlib.util.module_from_spec(spec)
spec.loader.exec_module(captures)

PNG = b'\x89PNG\r\n\x1a\n\x00\xffbinary\r\n'
MP4 = b'\x00\x00\x00\x18ftypmp42\x00\xffrecording'


class Device:
    def __init__(self):
        self.calls = []
        self.running = True
        self.size = len(MP4)

    def __call__(self, *args):
        self.calls.append(args)
        if args == ('exec-out', 'screencap', '-p'):
            return PNG
        if args[0] == 'shell' and 'screenrecord --time-limit' in args[-1]:
            return b'123 456\n'
        if args[0] == 'shell' and '# stop-recording' in args[-1]:
            return b'stopped\n' if self.running else b'finished\n'
        if args[0] == 'shell' and args[-1].startswith('wc -c < '):
            return str(self.size).encode()
        if args[0:2] == ('exec-out', 'cat'):
            return MP4
        return b''


class CapturesTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.device = Device()
        self.directory = self.root/'private'/'job'
        self.outbox = self.root/'shared'
        self.capture = captures.Captures(self.directory, self.outbox, 'a'*32, 'b'*64, 'c'*64, self.device)

    def test_screenshot_preserves_binary_and_publishes_hash(self):
        result = self.capture.screenshot('Keyboard open')
        self.assertEqual(result['sha256'], hashlib.sha256(PNG).hexdigest())
        self.assertEqual(result['mime_type'], 'image/png')
        self.assertEqual(result['label'], 'Keyboard open')
        self.assertEqual(result['app_sha256'], 'b'*64)
        self.assertEqual(self.capture.artifact_file(result['artifact_id']).read_bytes(), PNG)
        self.assertEqual((self.outbox/result['relative_path']).read_bytes(), PNG)
        self.assertEqual(self.capture.list_artifacts(), [result])

    def test_artifacts_survive_reinstantiation(self):
        result = self.capture.screenshot()
        reopened = captures.Captures(self.directory, self.outbox, 'a'*32, 'b'*64, 'c'*64, self.device)
        self.assertEqual(reopened.list_artifacts(), [result])
        self.assertEqual(reopened.artifact_file(result['artifact_id']).read_bytes(), PNG)

    def test_unknown_and_path_traversal_artifact_ids_rejected(self):
        for value in ['../../secret', 'f'*32]:
            with self.assertRaises(ValueError):
                self.capture.artifact_file(value)

    def test_outbox_symlink_is_rejected_without_touching_destination(self):
        target = self.root/'target'
        target.mkdir()
        self.outbox.symlink_to(target, target_is_directory=True)
        with self.assertRaises((ValueError, OSError)):
            self.capture.screenshot()
        self.assertEqual(list(target.iterdir()), [])

    def test_outbox_owner_symlink_is_rejected(self):
        self.outbox.mkdir()
        target = self.root/'target'
        target.mkdir()
        (self.outbox/('a'*32)).symlink_to(target, target_is_directory=True)
        with self.assertRaises((ValueError, OSError)):
            self.capture.screenshot()
        self.assertEqual(list(target.iterdir()), [])

    def test_modified_private_artifact_cannot_be_retrieved(self):
        result = self.capture.screenshot()
        self.capture.artifact_file(result['artifact_id']).write_bytes(b'changed')
        with self.assertRaisesRegex(ValueError, 'hash'):
            self.capture.artifact_file(result['artifact_id'])

    def test_private_artifact_symlink_is_rejected(self):
        result = self.capture.screenshot()
        path = self.capture.artifact_file(result['artifact_id'])
        path.unlink()
        path.symlink_to(self.root/'secret')
        with self.assertRaises((ValueError, OSError)):
            self.capture.artifact_file(result['artifact_id'])

    def test_empty_or_non_png_capture_is_not_published(self):
        self.capture.device = Mock(return_value=b'not a png')
        with self.assertRaises(ValueError):
            self.capture.screenshot()
        self.assertEqual(self.capture.list_artifacts(), [])

    def test_recording_uses_bounded_device_process_and_graceful_stop(self):
        recording = self.capture.start_recording()
        with self.assertRaisesRegex(ValueError, 'active'):
            self.capture.start_recording()
        result = self.capture.stop_recording(recording['recording_id'])
        self.assertEqual(result['mime_type'], 'video/mp4')
        self.assertEqual(self.capture.artifact_file(result['artifact_id']).read_bytes(), MP4)
        commands = '\n'.join(args[-1] for args in self.device.calls if args[0] == 'shell')
        self.assertIn('--time-limit 120', commands)
        self.assertIn('kill -2', commands)
        self.assertIn('456', commands)
        self.assertIn('/proc/123', commands)
        self.assertIn('rm -f', commands)
        self.assertNotIn('pkill', commands)

    def test_autoexpired_recording_is_collected(self):
        recording = self.capture.start_recording()
        self.device.running = False
        result = self.capture.stop_recording(recording['recording_id'])
        self.assertEqual(result['mime_type'], 'video/mp4')

    def test_wrong_recording_id_cannot_stop_recorder(self):
        self.capture.start_recording()
        self.device.calls.clear()
        with self.assertRaises(ValueError):
            self.capture.stop_recording('f'*32)
        self.assertEqual(self.device.calls, [])

    def test_oversized_recording_is_not_downloaded(self):
        recording = self.capture.start_recording()
        self.device.size = 128*1024*1024+1
        with self.assertRaisesRegex(ValueError, 'size'):
            self.capture.stop_recording(recording['recording_id'])
        self.assertFalse(any(args[0:2] == ('exec-out', 'cat') for args in self.device.calls))

    def test_reused_pid_is_not_signalled_and_reports_error(self):
        recording = self.capture.start_recording()
        original = self.device
        def device(*args):
            if args[0] == 'shell' and '# stop-recording' in args[-1]:
                return b'identity-mismatch\n'
            return original(*args)
        self.capture.device = device
        with self.assertRaisesRegex(RuntimeError, 'identity'):
            self.capture.stop_recording(recording['recording_id'])

    def test_stop_shell_signals_only_matching_pid_starttime_and_path(self):
        for mismatch in [None, 'starttime', 'path', 'zombie']:
            with self.subTest(mismatch=mismatch):
                recording = self.capture.start_recording()
                active = dict(self.capture.recording)
                proc = self.root/'proc'/active['pid']
                proc.mkdir(parents=True, exist_ok=True)
                (proc/'comm').write_text('screenrecord\n')
                fields = ['123', '(screenrecord)', 'Z' if mismatch == 'zombie' else 'S'] + ['0']*18 + [
                    '999' if mismatch == 'starttime' else active['starttime']]
                (proc/'stat').write_text(' '.join(fields))
                remote_path = '/wrong.mp4' if mismatch == 'path' else active['path']
                (proc/'cmdline').write_bytes(('\0'.join(['screenrecord', '--time-limit', '120', remote_path])+'\0').encode())
                marker = self.root/'signalled'
                marker.unlink(missing_ok=True)
                original = self.device
                def device(*args):
                    if args[0] == 'shell' and '# stop-recording' in args[-1]:
                        script = args[-1].replace('/proc/'+active['pid'], str(proc))
                        prefix = 'kill() { echo "$*" > '+str(marker)+'; rm '+str(proc/'stat')+'; };\n'
                        result = subprocess.run(['sh', '-c', prefix+script], check=True, capture_output=True)
                        return result.stdout
                    return original(*args)
                self.capture.device = device
                if mismatch in ['starttime', 'path']:
                    with self.assertRaisesRegex(RuntimeError, 'identity'):
                        self.capture.stop_recording(recording['recording_id'])
                    self.assertFalse(marker.exists())
                    self.capture.recording = None
                else:
                    self.capture.stop_recording(recording['recording_id'])
                    if mismatch == 'zombie':
                        self.assertFalse(marker.exists())
                    else:
                        self.assertEqual(marker.read_text().strip(), '-2 123')
                self.capture.device = original

    def test_close_collects_active_recording_and_is_idempotent(self):
        self.capture.start_recording()
        self.capture.close()
        self.capture.close()
        self.assertEqual(len(self.capture.list_artifacts()), 1)

    def test_identifiers_and_hashes_are_validated(self):
        for owner, app, test in [('bad', 'b'*64, 'c'*64), ('a'*32, 'bad', 'c'*64)]:
            with self.assertRaises(ValueError):
                captures.Captures(self.directory, self.outbox, owner, app, test, self.device)


if __name__ == '__main__':
    unittest.main()
