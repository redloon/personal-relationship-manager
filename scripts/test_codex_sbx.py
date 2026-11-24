import importlib.util
import pathlib
import tempfile
import unittest
import os
import json
import hashlib
import sqlite3
import subprocess
import sys
import tarfile
from unittest.mock import patch
import contextlib
import io
import runpy

SPEC = importlib.util.spec_from_file_location('codex_sbx', pathlib.Path(__file__).with_name('codex-sbx') / 'launcher.py')
module = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(module)
RESCUE_SPEC = importlib.util.spec_from_file_location('rescue', pathlib.Path(module.__file__).with_name('rescue.py'))
rescue = importlib.util.module_from_spec(RESCUE_SPEC)
RESCUE_SPEC.loader.exec_module(rescue)


class LauncherTests(unittest.TestCase):
    def test_config_preserves_proxy_and_discards_host_commands(self):
        host = {'model': 'example', 'model_reasoning_effort': 'high',
                'model_provider': 'host', 'mcp_servers': {'local': {'command': '/host/tool'}},
                'plugins': {'foo': {'enabled': True}}, 'tui': {'theme': 'dark'}}
        base = {'approval_policy': 'never', 'model_provider': 'sandboxd',
                'mcp_servers': {'mcp-gateway': {'url': 'http://gateway'}}}
        config, skipped = module.merge_config(host, base)
        self.assertEqual(config['model'], 'example')
        self.assertEqual(config['model_provider'], 'sandboxd')
        self.assertEqual(config['mcp_servers'], base['mcp_servers'])
        self.assertIn('plugins', skipped)
        self.assertEqual(module.toml_load(module.toml_dump(config)), config)
        self.assertIn('\n[mcp_servers.mcp-gateway]\n', module.toml_dump(config))

    def test_deterministic_names_and_separate_history(self):
        a, b = pathlib.Path('/tmp/a/repo'), pathlib.Path('/tmp/b/repo')
        self.assertEqual(module.sandbox_name(a), 'codex-sbx-repo-' + hashlib.sha256(str(a).encode()).hexdigest()[:12])
        self.assertNotEqual(module.history_directory(a), module.history_directory(b))

    def test_discovery_ignores_legacy_and_refuses_ambiguity(self):
        path = pathlib.Path('/tmp/repo')
        legacy = dict(name='legacy', agent='codex', workspaces=[str(path)], mounts=[])
        durable = dict(legacy, name='durable', mounts=['/history:/home/agent/codex-history'])
        with patch.object(module, 'run', return_value=subprocess.CompletedProcess([], 0, stdout=json.dumps([legacy, durable]))):
            self.assertEqual(module.discover(path), 'durable')
        with patch.object(module, 'run', return_value=subprocess.CompletedProcess([], 0, stdout=json.dumps([durable, dict(durable, name='other')]))):
            with self.assertRaises(ValueError): module.discover(path)

    def test_create_mount_and_settings_refresh(self):
        with tempfile.TemporaryDirectory() as directory:
            path = pathlib.Path(directory)
            configs = json.dumps(dict(base='model_provider = "sandboxd"\n', projects={str(path): dict(trust_level='trusted')}))
            with patch.object(module, 'ROOT', path / 'host'), patch.object(module, 'discover', return_value=None), patch.object(module, 'run') as run, patch.object(module, 'remote', return_value=subprocess.CompletedProcess([], 0, stdout=configs)) as remote:
                module.prepare(path)
                creation = run.call_args.args
                self.assertIn(str(module.history_directory(path)) + ':/home/agent/codex-history', creation)
                written = module.toml_load(remote.call_args.args[2])
                self.assertEqual(written['projects'][str(path)]['trust_level'], 'trusted')
                self.assertEqual(written['model_provider'], 'sandboxd')

    def test_refresh_removes_preferences_and_preserves_local_routing(self):
        with tempfile.TemporaryDirectory() as directory:
            root = pathlib.Path(directory)
            host = root / 'config.toml'
            host.write_text('model = "host-model"\n')
            local = json.dumps(dict(base='model = "kit-model"\nmodel_provider = "sandboxd"\n', projects={}))
            with patch.dict(os.environ, {'CODEX_HOME': str(root)}), patch.object(module, 'discover', return_value='existing'), patch.object(module, 'run') as run, patch.object(module, 'remote', return_value=subprocess.CompletedProcess([], 0, stdout=local)) as remote:
                module.prepare(root)
                self.assertEqual(module.toml_load(remote.call_args.args[2])['model'], 'host-model')
                host.write_text('')
                module.prepare(root)
                self.assertEqual(module.toml_load(remote.call_args.args[2])['model'], 'kit-model')
                run.assert_not_called()

    def test_real_export_import_and_tamper_detection(self):
        with tempfile.TemporaryDirectory() as directory:
            root = pathlib.Path(directory)
            home = root / 'codex'
            session = home / 'sessions/2026/test.jsonl'
            session.parent.mkdir(parents=True)
            session.write_text('{"type":"session_meta"}\n')
            with sqlite3.connect(home / 'state_5.sqlite') as db:
                db.execute('CREATE TABLE example (value TEXT)')
                db.execute("INSERT INTO example VALUES ('preserved')")
            output = subprocess.check_output([sys.executable, str(pathlib.Path(module.__file__).with_name('export_state.py'))], env={**os.environ, 'CODEX_HOME': str(home)})
            backup = root / 'backup'
            backup.mkdir()
            (backup / 'state.tar.gz').write_bytes(output)
            with tarfile.open(backup / 'state.tar.gz') as archive:
                manifest = json.load(archive.extractfile('manifest.json'))
                self.assertEqual(manifest['changed_during_copy'], [])
                for name, record in manifest['files'].items():
                    self.assertEqual(hashlib.sha256(archive.extractfile(name).read()).hexdigest(), record['sha256'])
                snapshot = root / 'snapshot.sqlite'
                snapshot.write_bytes(archive.extractfile('state_5.sqlite').read())
            with sqlite3.connect(snapshot) as db:
                self.assertEqual(db.execute('SELECT value FROM example').fetchone(), ('preserved',))
            manifest['archive_sha256'] = hashlib.sha256(output).hexdigest()
            (backup / 'verified.json').write_text(json.dumps(manifest))
            with patch.object(rescue, 'ROOT', root / 'host'):
                entry = root / 'history'
                rescue.import_backup(backup, entry)
                rescue.import_backup(backup, entry)  # Identical files are idempotent.
                target = entry / 'sessions/2026/test.jsonl'
                self.assertEqual(target.read_bytes(), session.read_bytes())
                self.assertFalse((entry / 'state_5.sqlite').exists())
                target.write_text('newer data')
                with self.assertRaises(ValueError): rescue.import_backup(backup, entry)
                (backup / 'state.tar.gz').write_bytes(output + b'tampered')
                with self.assertRaises(ValueError): rescue.import_backup(backup, entry)

    def test_main_forwards_agent_arguments_as_separate_values(self):
        with tempfile.TemporaryDirectory(prefix='workspace with spaces ') as directory:
            root = pathlib.Path(directory)
            with patch.object(module, 'ROOT', root / 'state'), patch.object(module, 'prepare', return_value='sandbox-name'), patch.object(module.os, 'execvp') as execute, patch.object(module.sys, 'argv', ['codex-sbx', str(root), '--', 'resume', '--all', 'two words']):
                module.main()
            self.assertEqual(execute.call_args.args[1], [module.SBX, 'run', '--name', 'sandbox-name', '--', 'resume', '--all', 'two words'])

    def test_install_is_standalone_and_repeatable(self):
        with tempfile.TemporaryDirectory() as directory:
            root = pathlib.Path(directory)
            installer = pathlib.Path(module.__file__).with_name('install.py')
            with patch.object(pathlib.Path, 'home', return_value=root), patch.dict(os.environ, {'CODEX_SBX_HOME': str(root / 'state')}), contextlib.redirect_stdout(io.StringIO()):
                runpy.run_path(str(installer))
                runpy.run_path(str(installer))
            command = root / '.local/bin/codex-sbx'
            self.assertTrue(command.is_symlink())
            self.assertEqual(command.resolve(), root / 'state/launcher/launcher.py')
            self.assertTrue(command.resolve().with_name('export_state.py').exists())


if __name__ == '__main__':
    unittest.main()
