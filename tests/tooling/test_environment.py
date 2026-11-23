"""Environment diagnostics detect hook drift without changing Git config."""
import importlib.util
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

SOURCE = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location('environment', SOURCE / 'scripts/check-environment.py')
environment = importlib.util.module_from_spec(spec)
spec.loader.exec_module(environment)


class EnvironmentTests(unittest.TestCase):
    def test_read_only_hook_diagnostics(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            subprocess.run(['git', 'init', '-q', str(root)], check=True)
            self.assertTrue(environment.hook_errors(root))
            subprocess.run(['git', 'config', '--local', 'core.hooksPath', '.githooks'], cwd=root, check=True)
            before = (root/'.git/config').read_bytes()
            self.assertTrue(environment.hook_errors(root))
            (root/'.githooks').mkdir()
            hook = root/'.githooks/pre-commit'
            hook.write_text('#!/bin/bash\nexit 0\n')
            hook.chmod(0o755)
            self.assertEqual(environment.hook_errors(root), [])
            self.assertEqual((root/'.git/config').read_bytes(), before)
