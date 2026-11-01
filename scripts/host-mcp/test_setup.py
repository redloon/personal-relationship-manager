"""Configuration probes; no host tools required."""
import importlib.util
import json
from pathlib import Path
import unittest
import subprocess
import tempfile
import sys
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('setup', Path(__file__).with_name('setup.py'))
setup = importlib.util.module_from_spec(spec)
spec.loader.exec_module(setup)

class SetupTests(unittest.TestCase):
    def test_defaults(self):
        config = setup.configuration({'sdk': '/sdk'})
        self.assertEqual(config['avd'], 'android_tests')
        self.assertEqual(config['state'], str(setup.DEST/'jobs'))

    def test_preserves_configuration(self):
        config = dict(sdk='/sdk', inbox='/inbox', state='/jobs', avd='custom')
        self.assertEqual(setup.configuration(config), config)
        self.assertEqual(setup.configuration(config, '/other')['sdk'], '/other')

class RefreshTests(unittest.TestCase):
    def test_code_only_refresh_skips_sdk_provisioning_and_sandbox(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            dest = root/'installed'
            dest.mkdir()
            sdk = root/'sdk'
            for name in ['sdkmanager', 'avdmanager', 'apkanalyzer']:
                path = sdk/'cmdline-tools/latest/bin'/name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.touch()
            (dest/'config.json').write_text(json.dumps(dict(sdk=str(sdk), inbox=str(root/'inbox'), state=str(dest/'jobs'), avd='android_tests')))
            with patch.object(setup, 'DEST', dest), patch.object(sys, 'argv', ['setup.py', '--refresh-only']), patch.object(setup, 'stop_runners'), patch.object(setup, 'run') as run:
                setup.main()
            commands = [[str(a) for a in call.args] for call in run.call_args_list]
            self.assertEqual(len(commands), 2)  # venv and dependencies only
            self.assertFalse(any('sbx' in c or any('sdkmanager' in a for a in c) for c in commands))
            self.assertEqual((dest/'runner.py').read_bytes(), (setup.SOURCE/'runner.py').read_bytes())
            self.assertTrue((dest/'runner.sha256').is_file())

    def test_refresh_without_pidfd(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            script = root/'runner.py'
            script.write_text('import time; time.sleep(60)')
            with patch.object(setup, 'DEST', root), patch.object(setup.os, 'pidfd_open', None, create=True):
                process = subprocess.Popen(['python3', str(script), str(root/'config.json')])
                try:
                    setup.stop_runners({'state': str(root/'jobs')})
                    self.assertEqual(process.wait(timeout=5), -15)
                finally:
                    if process.poll() is None:
                        process.kill()
                    process.wait()

    def test_changed_identity_is_not_signalled(self):
        with patch.object(setup.os, 'pidfd_open', None, create=True), patch.object(setup, 'process_identity', return_value='new'), patch.object(setup.os, 'kill') as kill:
            setup.terminate_runner(123, 'old')
            kill.assert_not_called()

class EmulatorChecks(unittest.TestCase):
    def check(self, outputs):
        with patch.object(setup, 'run', side_effect=[subprocess.CompletedProcess([], 0, stdout=o) for o in outputs]) as run:
            setup.check_emulator(Path('/sdk'), 'android_tests')
            return run.call_args_list

    def test_empty_port(self):
        self.assertEqual(len(self.check(['List of devices attached\n'])), 1)

    def test_correct_emulator(self):
        self.assertEqual(len(self.check(['emulator-5580 device', 'android_tests\nOK'])), 2)

    def test_wrong_emulator(self):
        with self.assertRaisesRegex(RuntimeError, 'android_mcp_probe'):
            self.check(['emulator-5580 device', 'android_mcp_probe\nOK'])

if __name__ == '__main__':
    unittest.main()
