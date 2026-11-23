"""Exercise staged checks against disposable indexes, never the checkout's index."""
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

REPO = Path(__file__).resolve().parents[2]
CHECKER = REPO / 'scripts/check-staged.py'
INSTALLER = REPO / 'scripts/install-hooks.sh'


class StagedChecksTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.env = {key: value for key, value in os.environ.items() if not key.startswith('GIT_')}
        self.env.update(GIT_CONFIG_NOSYSTEM='1', GIT_CONFIG_GLOBAL=os.devnull)
        self.git('init', '-q')
        self.git('config', 'user.email', 'fixture@example.invalid')
        self.git('config', 'user.name', 'Fixture')

    def tearDown(self):
        self.temp.cleanup()

    def git(self, *args):
        return subprocess.run(['git', *args], cwd=self.root, env=self.env, check=True,
                              capture_output=True, text=True).stdout.strip()

    def write(self, name, content, executable=False):
        path = self.root / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content)
        if executable:
            path.chmod(0o755)
        return path

    def stage(self, *names):
        self.git('add', '--', *names)

    def task(self, name, body='exit 0'):
        path = '.mise/tasks/' + name
        self.write(path, '#!/usr/bin/env bash\n#MISE description="Fixture task"\n' + body + '\n', executable=True)
        self.stage(path)
        if name == 'check' and not (self.root / '.mise/tasks/tooling-test').exists():
            self.task('tooling-test')

    def check(self):
        return subprocess.run(['python3', str(CHECKER)], cwd=self.root, env=self.env,
                              capture_output=True, text=True)

    def test_cheap_checks_do_not_run_broad_validation_or_require_coherence(self):
        self.task('check', 'touch full-ran')
        self.write('app/src/main/Test.kt', 'class Test\n')
        self.stage('app/src/main/Test.kt')
        self.write('app/src/main/Test.kt', 'class Unstaged\n')
        result = subprocess.run(['python3', str(CHECKER), '--syntax-only'], cwd=self.root,
                                env=self.env, capture_output=True, text=True)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertFalse((self.root / 'full-ran').exists())

    def test_uses_index_contents_instead_of_fixed_worktree(self):
        self.write('bad.py', 'def broken(:\n')
        self.stage('bad.py')
        self.write('bad.py', 'answer = 42\n')
        result = self.check()
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('bad.py', result.stdout + result.stderr)

    def test_parses_supported_formats_and_shell_without_executing(self):
        self.write('a.py', 'answer = 42\n')
        self.write('a.toml', '[answer]\nvalue = 42\n')
        self.write('a.xml', '<answer value="42"/>\n')
        self.write('a.json', '{"answer": 42}\n')
        self.write('a.sh', '#!/bin/bash\ntouch should-not-run\n')
        self.stage('a.py', 'a.toml', 'a.xml', 'a.json', 'a.sh')
        self.task('tooling-test', 'touch tooling-ran')
        result = self.check()
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertFalse((self.root / 'should-not-run').exists())
        self.assertTrue((self.root / 'tooling-ran').exists())

    def test_invalid_formats_fail(self):
        for suffix, content in [('json', '{'), ('toml', '[broken'), ('xml', '<broken>'), ('sh', 'if then\n')]:
            with self.subTest(suffix=suffix):
                name = 'invalid.' + suffix
                self.write(name, content)
                self.stage(name)
                result = self.check()
                self.assertNotEqual(result.returncode, 0)
                self.assertIn(name, result.stdout + result.stderr)
                self.git('rm', '--cached', '--', name)

    def test_staged_whitespace_fails(self):
        self.write('note.md', 'trailing spaces  \n')
        self.stage('note.md')
        result = self.check()
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('whitespace', (result.stdout + result.stderr).lower())

    def test_task_must_be_executable_in_index(self):
        self.write('.mise/tasks/check', '#!/bin/bash\nexit 0\n')
        self.stage('.mise/tasks/check')
        result = self.check()
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('executable', result.stdout + result.stderr)

    def test_executable_task_requires_a_shebang(self):
        self.write('.mise/tasks/example', 'exit 0\n', executable=True)
        self.stage('.mise/tasks/example')
        result = self.check()
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('shebang', result.stdout + result.stderr)

    def test_malformed_mise_metadata_is_not_hidden_by_shell_comments(self):
        self.write('.mise/tasks/example', '#!/bin/bash\n#MISE description=[\nexit 0\n', executable=True)
        self.stage('.mise/tasks/example')
        result = self.check()
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('#MISE', result.stdout + result.stderr)

    def test_ignored_build_outputs_do_not_block_android_check(self):
        self.write('.gitignore', 'app/build/\n')
        self.write('app/src/main/Test.kt', 'class Test\n')
        self.stage('.gitignore', 'app/src/main/Test.kt')
        self.write('app/build/generated.json', '{unimportant')
        self.task('check', 'touch full-ran')
        result = self.check()
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertTrue((self.root / 'full-ran').exists())

    def test_index_changes_during_validation_require_a_rerun(self):
        self.task('check', 'printf "changed\\n" >> app/src/main/Test.kt\ngit add app/src/main/Test.kt')
        self.write('app/src/main/Test.kt', 'class Test\n')
        self.stage('app/src/main/Test.kt')
        result = self.check()
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('index changed', result.stdout + result.stderr)

    def test_android_change_requires_coherent_tracked_and_untracked_inputs(self):
        self.task('check', 'touch full-ran')
        self.write('app/src/main/Test.kt', 'class Test\n')
        self.stage('app/src/main/Test.kt')
        self.write('app/src/main/Test.kt', 'class Changed\n')
        result = self.check()
        self.assertNotEqual(result.returncode, 0)
        self.assertFalse((self.root / 'full-ran').exists())
        self.stage('app/src/main/Test.kt')
        self.write('app/src/main/Untracked.kt', 'class Untracked\n')
        self.write('scripts/host-mcp/README.md', 'Independent documentation in progress\n')
        result = self.check()
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('Untracked.kt', result.stdout + result.stderr)
        self.assertFalse((self.root / 'full-ran').exists())

    def test_android_build_runs_once_and_rechecks_source_coherence(self):
        self.task('check', 'printf "changed\\n" >> app/src/main/Test.kt')
        self.write('app/src/main/Test.kt', 'class Test\n')
        self.stage('app/src/main/Test.kt')
        result = self.check()
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('Test.kt', result.stdout + result.stderr)

    def test_tooling_commit_ignores_unrelated_dirty_android_inputs(self):
        self.write('app/src/main/Test.kt', 'class Original\n')
        self.write('gradle/libs.versions.toml', '[versions]\nexample = "1"\n')
        self.stage('app/src/main/Test.kt', 'gradle/libs.versions.toml')
        self.git('commit', '-qm', 'fixture')
        self.write('gradle/libs.versions.toml', '[versions]\nexample = "2"\n')
        self.write('app/src/main/Test.kt', 'class Unstaged\n')
        self.write('app/src/main/Untracked.kt', 'class Untracked\n')
        self.write('scripts/host-mcp/README.md', 'Independent documentation in progress\n')
        self.write('scripts/example.py', 'answer = 42\n')
        self.stage('scripts/example.py')
        self.task('tooling-test', 'touch tooling-ran')
        result = self.check()
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertTrue((self.root / 'tooling-ran').exists())

    def test_mixed_changes_run_android_and_tooling_checks(self):
        self.task('check', 'touch full-ran')
        self.task('tooling-test', 'touch tooling-ran')
        self.write('app/src/main/Test.kt', 'class Test\n')
        self.write('scripts/example.py', 'answer = 42\n')
        self.stage('app/src/main/Test.kt', 'scripts/example.py')
        result = self.check()
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertTrue((self.root / 'full-ran').exists())
        self.assertTrue((self.root / 'tooling-ran').exists())

    def test_android_task_failure_is_not_a_pass(self):
        self.task('check', 'exit 7')
        self.write('build.gradle.kts', '// fixture\n')
        self.stage('build.gradle.kts')
        result = self.check()
        self.assertNotEqual(result.returncode, 0)

    def test_doc_only_change_does_not_require_android_environment(self):
        self.write('docs/a file with spaces.md', 'Documentation\n')
        self.stage('docs/a file with spaces.md')
        result = self.check()
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)

    def test_deleted_sources_are_not_parsed(self):
        self.write('deleted.py', 'answer=42\n')
        self.stage('deleted.py')
        # A disposable fixture commit gives the deletion a base revision.
        self.git('commit', '-qm', 'fixture')
        self.git('rm', '--', 'deleted.py')
        self.task('tooling-test')
        self.assertEqual(self.check().returncode, 0)

    def test_tooling_failure_is_not_a_pass(self):
        self.write('scripts/example.py', 'answer=42\n')
        self.stage('scripts/example.py')
        self.task('tooling-test', 'exit 8')
        self.assertNotEqual(self.check().returncode, 0)


class HookInstallTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.env = {key: value for key, value in os.environ.items() if not key.startswith('GIT_')}
        self.env.update(GIT_CONFIG_NOSYSTEM='1', GIT_CONFIG_GLOBAL=os.devnull)
        self.git('init', '-q')
        self.git('config', 'user.email', 'fixture@example.invalid')
        self.git('config', 'user.name', 'Fixture')
        (self.root / 'scripts').mkdir()
        shutil.copy2(INSTALLER, self.root / 'scripts/install-hooks.sh')
        (self.root / '.githooks').mkdir()
        hook = self.root / '.githooks/pre-commit'
        hook.write_text('#!/bin/bash\nexit 0\n')
        hook.chmod(0o755)

    def tearDown(self):
        self.temp.cleanup()

    def git(self, *args):
        return subprocess.run(['git', *args], cwd=self.root, env=self.env, check=True, capture_output=True, text=True).stdout.strip()

    def install(self, root=None):
        target = root or self.root
        return subprocess.run(['bash', 'scripts/install-hooks.sh'], cwd=target, env=self.env, capture_output=True, text=True)

    def test_install_is_idempotent(self):
        for _ in range(2):
            result = self.install()
            self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertEqual(self.git('config', '--get', 'core.hooksPath'), '.githooks')

    def test_conflicting_hook_and_path_are_preserved(self):
        hook = self.root / '.git/hooks/pre-commit'
        hook.write_text('#!/bin/bash\n# existing\n')
        hook.chmod(0o755)
        self.assertNotEqual(self.install().returncode, 0)
        self.assertIn('# existing', hook.read_text())
        hook.unlink()
        self.git('config', 'core.hooksPath', 'custom-hooks')
        self.assertNotEqual(self.install().returncode, 0)
        self.assertEqual(self.git('config', '--get', 'core.hooksPath'), 'custom-hooks')

    def test_install_works_from_linked_worktree(self):
        self.git('add', '--', 'scripts/install-hooks.sh', '.githooks/pre-commit')
        self.git('commit', '-qm', 'fixture')
        other = self.root / 'linked'
        self.git('worktree', 'add', '-q', '-b', 'linked', str(other))
        result = self.install(other)
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertEqual(self.git('config', '--get', 'core.hooksPath'), '.githooks')


if __name__ == '__main__':
    unittest.main()
