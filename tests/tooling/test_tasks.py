"""Task contract tests use a fake Gradle wrapper; never run devices or stage APKs."""
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

SOURCE = Path(__file__).resolve().parents[2]

class TasksTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.env = {key: value for key, value in os.environ.items() if not key.startswith('GIT_')}
        self.env.update(GIT_CONFIG_NOSYSTEM='1', GIT_CONFIG_GLOBAL=os.devnull)
        for directory in ('.mise', 'scripts'):
            shutil.copytree(SOURCE / directory, self.root / directory)
        subprocess.run(['git', 'init', '-q', str(self.root)], check=True, env=self.env)
        self.java = self.root / 'jdk'
        (self.java / 'bin').mkdir(parents=True)
        (self.java / 'release').write_text('JAVA_VERSION="21.0.8"\n')
        (self.java / 'bin/java').write_text('#!/bin/sh\nexit 0\n')
        (self.java / 'bin/java').chmod(0o755)
        self.env.update(JAVA_HOME=str(self.java), TASK_LOG=str(self.root / 'calls'))
        self.wrapper('printf "%s\\n" "$@" >> "$TASK_LOG"\n')

    def wrapper(self, body):
        path = self.root / 'gradlew'
        path.write_text('#!/usr/bin/env bash\nset -eu\n' + body)
        path.chmod(0o755)

    def task(self, name, *args):
        return subprocess.run([str(self.root / '.mise/tasks' / name), *args], cwd='/tmp',
                              env=self.env, text=True, capture_output=True)

    def test_check_invokes_gradle_once_with_tests_builds_and_lint(self):
        self.wrapper('echo invocation >> "$TASK_LOG"\nprintf "%s\\n" "$@" >> "$TASK_LOG"\n')
        result = self.task('check')
        self.assertEqual(result.returncode, 0, result.stderr + result.stdout)
        calls = (self.root / 'calls').read_text().splitlines()
        self.assertEqual(calls.count('invocation'), 1)
        self.assertTrue({'testDebugUnitTest', 'assembleDebug', 'assembleDebugAndroidTest', 'lintDebug', 'ktlintCheck'} <= set(calls))

    def test_staging_holds_build_lock_and_records_provenance(self):
        self.wrapper("""mkdir -p app/build/outputs/apk/debug app/build/outputs/apk/androidTest/debug
printf app > app/build/outputs/apk/debug/app-debug.apk
printf test > app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
""")
        stage = self.root / 'scripts/stage-mcp-apks.py'
        stage.write_text(stage.read_text().replace('    inbox.mkdir', """    import fcntl
    with (ROOT / '.gradle/agent-build.lock').open('a') as probe:
        try:
            fcntl.flock(probe, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError:
            pass
        else:
            raise AssertionError('staging lost the build lock')
    inbox.mkdir"""))
        result = self.task('test-stage')
        self.assertEqual(result.returncode, 0, result.stderr)
        import json
        manifest = json.loads((self.root / 'ignored/mcp-inbox/manifest.json').read_text())
        self.assertEqual(len(manifest['provenance']['source_sha256']), 64)
        self.assertIn('git_revision', manifest['provenance'])

    def test_gradle_failure_is_blocking(self):
        self.wrapper('exit 17\n')
        self.assertEqual(self.task('check').returncode, 17)

    def test_direct_task_preserves_arguments_outside_repo(self):
        result = self.task('unit', '--tests', 'a name with spaces')
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual((self.root / 'calls').read_text().splitlines(),
                         ['testDebugUnitTest', '--tests', 'a name with spaces'])

    def test_codex_task_uses_daily_launcher_from_repository_root(self):
        binary_directory = self.root / 'bin'
        binary_directory.mkdir()
        launcher = binary_directory / 'codex-sbx'
        launcher.write_text('#!/usr/bin/env bash\nprintf "%s\\n" "$PWD" "$@" > "$TASK_LOG"\n')
        launcher.chmod(0o755)
        self.env['PATH'] = str(binary_directory) + os.pathsep + self.env['PATH']

        result = self.task('codex', 'resume', '--all', 'two words')

        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual((self.root / 'calls').read_text().splitlines(),
                         [str(self.root), '--', 'resume', '--all', 'two words'])

    def test_source_change_during_build_is_rejected(self):
        (self.root / 'app/src').mkdir(parents=True)
        (self.root / 'app/src/test.kt').write_text('before')
        self.wrapper('echo after > app/src/test.kt\n')
        result = self.task('build')
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('changed during', result.stderr)

    def test_lint_baseline_change_during_build_is_rejected(self):
        (self.root / 'app').mkdir()
        (self.root / 'app/lint-baseline.xml').write_text('<issues/>')
        self.wrapper('echo changed > app/lint-baseline.xml\n')
        result = self.task('build')
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('changed during', result.stderr)

    def test_schema_change_during_build_is_rejected(self):
        (self.root / 'app/schemas').mkdir(parents=True)
        (self.root / 'app/schemas/2.json').write_text('{}')
        self.wrapper('echo changed > app/schemas/2.json\n')
        result = self.task('build')
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('changed during', result.stderr)

    def test_instrumentation_requires_nonzero_success_and_no_failure_markers(self):
        log = self.root / 'instrumentation.txt'
        for output, expected in [
            ('OK (3 tests)\nINSTRUMENTATION_CODE: -1\n', 0), ('OK (1 test)\nINSTRUMENTATION_CODE: -1\n', 0),
            ('INSTRUMENTATION_FAILED: runner missing\n', 1),
            ('OK (0 tests)\n', 1), ('', 1),
            ('FAILURES!!!\nOK (3 tests)\n', 1),
            ('INSTRUMENTATION_RESULT: shortMsg=Process crashed.\n', 1),
        ]:
            with self.subTest(output=output):
                log.write_text(output)
                result = subprocess.run(['python3', str(self.root / '.mise/lib/check-instrumentation.py'), str(log)],
                                        capture_output=True, text=True, env=self.env)
                self.assertEqual(result.returncode, expected, result.stderr)

    def test_build_lock_times_out(self):
        import fcntl
        (self.root / '.gradle').mkdir()
        with (self.root / '.gradle/agent-build.lock').open('w') as lock:
            fcntl.flock(lock, fcntl.LOCK_EX)
            self.env['BUILD_LOCK_TIMEOUT'] = '0.1'
            result = self.task('build')
        self.assertNotEqual(result.returncode, 0)
        self.assertFalse((self.root / 'calls').exists())

    @unittest.skipUnless(Path('/.dockerenv').exists() or Path('/var/run/sbx').is_dir(), 'sandbox-specific guard')
    def test_local_emulator_tasks_refuse_sandbox(self):
        for name in ('test', 'test-class', 'emulator'):
            result = self.task(name)
            self.assertNotEqual(result.returncode, 0)
            self.assertIn('MCP', result.stderr)
        self.assertFalse((self.root / 'calls').exists())

    def test_sdk_home_fallback(self):
        sdk = self.root / 'android-sdk/platform-tools'
        sdk.mkdir(parents=True)
        env = dict(self.env, HOME=str(self.root), ANDROID_HOME='', ANDROID_SDK_ROOT='', PATH=os.defpath)
        result = subprocess.run(['bash', '-c', 'source scripts/android-sdk-env.sh; echo "$ANDROID_HOME"'],
                                cwd=self.root, env=env, capture_output=True, text=True)
        self.assertEqual(result.stdout.strip(), str(sdk.parent))

if __name__ == '__main__':
    unittest.main()
