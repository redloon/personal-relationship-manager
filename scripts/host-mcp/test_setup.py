"""Configuration probes; no host tools required."""
import importlib.util
import json
from pathlib import Path
import unittest
import subprocess
import tempfile
import select
import shutil
import signal
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
            import hashlib
            digest = hashlib.sha256()
            for name in setup.RUNTIME_FILES:
                self.assertEqual((dest/name).read_bytes(), (setup.SOURCE/name).read_bytes())
                digest.update(name.encode()+b'\0')
                digest.update((dest/name).read_bytes())
            self.assertEqual((dest/'runner.sha256').read_text().strip(), digest.hexdigest())

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

    def test_refresh_stops_initialized_stdio_runner_with_stdin_open(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for name in setup.RUNTIME_FILES:
                shutil.copyfile(setup.SOURCE/name, root/name)
            config = dict(sdk=str(root/'sdk'), inbox=str(root/'inbox'),
                          state=str(root/'jobs'), avd='android_tests')
            (root/'config.json').write_text(json.dumps(config))
            with tempfile.TemporaryFile() as errors, patch.object(setup, 'DEST', root), patch.object(setup, 'STOP_GRACE_SECONDS', .15, create=True):
                process = subprocess.Popen([sys.executable, str(root/'runner.py'), str(root/'config.json')],
                                           stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=errors)
                try:
                    request = dict(jsonrpc='2.0', id=1, method='initialize', params=dict(
                        protocolVersion='2025-03-26', capabilities={},
                        clientInfo=dict(name='refresh-regression', version='1')))
                    process.stdin.write((json.dumps(request)+'\n').encode())
                    process.stdin.flush()
                    self.assertTrue(select.select([process.stdout], [], [], 5)[0], 'MCP startup timed out')
                    self.assertEqual(json.loads(process.stdout.readline())['id'], 1)
                    setup.stop_runners(config)
                    self.assertIn(process.wait(timeout=2), (0, -signal.SIGKILL))
                    self.assertFalse(process.stdin.closed, 'refresh must work while the gateway keeps stdin open')
                finally:
                    if process.poll() is None:
                        process.kill()
                    process.wait(timeout=5)
                    process.stdin.close()
                    process.stdout.close()

    def test_refresh_forces_verified_idle_process_that_ignores_sigterm(self):
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory)
            script=root/'runner.py'
            script.write_text('import signal,time; signal.signal(signal.SIGTERM,signal.SIG_IGN); print("ready",flush=True); time.sleep(60)')
            process=subprocess.Popen([sys.executable,str(script),str(root/'config.json')],
                                     stdin=subprocess.PIPE,stdout=subprocess.PIPE)
            try:
                self.assertTrue(select.select([process.stdout],[],[],5)[0])
                self.assertEqual(process.stdout.readline().strip(),b'ready')
                with patch.object(setup,'DEST',root), patch.object(setup,'STOP_GRACE_SECONDS',.05):
                    setup.stop_runners(dict(state=str(root/'jobs')))
                self.assertEqual(process.wait(timeout=2),-signal.SIGKILL)
            finally:
                if process.poll() is None:process.kill()
                process.wait(timeout=5)
                process.stdin.close();process.stdout.close()

    def test_active_job_refuses_every_shutdown_signal(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            script = root/'runner.py'
            script.write_text('import time; time.sleep(60)')
            status = root/'jobs'/'job'/'status.json'
            status.parent.mkdir(parents=True)
            status.write_text(json.dumps(dict(status='running', phase='cleanup')))
            with patch.object(setup, 'DEST', root), patch.object(setup, 'terminate_runner') as terminate:
                process = subprocess.Popen([sys.executable, str(script), str(root/'config.json')])
                try:
                    with self.assertRaisesRegex(RuntimeError, 'Finish/cancel'):
                        setup.stop_runners(dict(state=str(root/'jobs')))
                    terminate.assert_not_called()
                    self.assertIsNone(process.poll())
                finally:
                    process.kill()
                    process.wait(timeout=5)

    def test_changed_identity_is_not_signalled(self):
        for signum in (signal.SIGTERM, signal.SIGKILL):
            with self.subTest(signal=signum), patch.object(setup.os, 'pidfd_open', None, create=True), patch.object(setup, 'process_identity', return_value='new'), patch.object(setup.os, 'kill') as kill:
                setup.terminate_runner(123, 'old', signum)
                kill.assert_not_called()

    def test_pidfd_rechecks_identity_before_force_stop(self):
        with patch.object(setup.os, 'pidfd_open', return_value=42, create=True), patch.object(setup.signal, 'pidfd_send_signal', create=True) as send, patch.object(setup.os, 'close') as close, patch.object(setup, 'process_identity', return_value='new'):
            setup.terminate_runner(123, 'old', signal.SIGKILL)
            send.assert_not_called()
            close.assert_called_once_with(42)

    def test_unrelated_process_survives_refresh(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            with patch.object(setup, 'DEST', root):
                process = subprocess.Popen([sys.executable, '-c', 'import time; time.sleep(60)'])
                try:
                    setup.stop_runners(dict(state=str(root/'jobs')))
                    self.assertIsNone(process.poll())
                finally:
                    process.kill()
                    process.wait(timeout=5)

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
