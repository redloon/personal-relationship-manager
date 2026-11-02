import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

spec=importlib.util.spec_from_file_location('cleanup_trial',Path(__file__).with_name('cleanup-trial.py'))
cleanup=importlib.util.module_from_spec(spec)
spec.loader.exec_module(cleanup)


class CleanupTests(unittest.TestCase):
    def test_named_targets_only_and_evidence_preserved(self):
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory)
            temporary=root/'tmp'; temporary.mkdir()
            home=root/'home'; home.mkdir()
            (temporary/'agent-android-swift.tar.gz').write_bytes(b'archive')
            (temporary/'agent-trial-frame-01.png').write_bytes(b'frame')
            other=temporary/'other-swift'; other.mkdir()
            evidence=home/'ignored/agent-android-trial/report.md'
            evidence.parent.mkdir(parents=True);evidence.write_text('retain')
            cleanup.remove_local(cleanup.local_targets(temporary,home))
            self.assertTrue(other.is_dir())
            self.assertTrue(evidence.is_file())
            self.assertFalse((temporary/'agent-android-swift.tar.gz').exists())

    def test_symlink_and_unrecognized_tool_refused_before_deletions(self):
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory)
            safe=root/'agent-android-swift.tar.gz';safe.touch()
            unknown=root/'agent-android-swift';unknown.mkdir()
            with self.assertRaises(ValueError): cleanup.remove_local([safe,unknown])
            self.assertTrue(safe.exists())
            link=root/'link';link.symlink_to(unknown)
            with self.assertRaises(ValueError): cleanup.remove_local([link])

    def test_other_app_state_is_preserved(self):
        with tempfile.TemporaryDirectory() as directory:
            state=Path(directory)/'emulator-5580';state.mkdir()
            (state/'state.json').write_text(json.dumps({'sessionPackage':'other.app'}))
            with self.assertRaises(ValueError): cleanup.remove_local([state])
            self.assertTrue(state.exists())


if __name__=='__main__': unittest.main()
