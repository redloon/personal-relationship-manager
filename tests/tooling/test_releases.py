"""Release commands in disposable Git repos; no production keys or publishing."""
import fcntl
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

SOURCE = Path(__file__).resolve().parents[2]


class ReleaseTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)/'repo'
        self.root.mkdir()
        self.env = {k: v for k, v in os.environ.items() if not k.startswith(('GIT_', 'CRM_BUILD_LOCK'))}
        self.env.update(GIT_CONFIG_NOSYSTEM='1', GIT_CONFIG_GLOBAL=os.devnull)
        for directory in ['.mise', 'scripts']:
            shutil.copytree(SOURCE/directory, self.root/directory)
        self.git('init', '-q')
        self.git('config', 'user.name', 'Release Test')
        self.git('config', 'user.email', 'release@example.invalid')
        self.write('.gitignore', 'ignored/\n.gradle/\napp/build/\napp/release-signing.properties\njdk/\nsdk/\n__pycache__/\n')
        self.write('app/version.properties', 'versionName=0.1.0\nversionCode=2\n')
        self.write('jdk/release', 'JAVA_VERSION="21.0.8"\n')
        self.executable('jdk/bin/java', '#!/bin/sh\nexit 0\n')
        self.env.update(JAVA_HOME=str(self.root/'jdk'), ANDROID_HOME=str(self.root/'sdk'),
                        TASK_LOG=str(self.root/'ignored/calls'))
        self.executable('.mise/tasks/verify', '#!/bin/sh\nmkdir -p ignored\necho verify >> "$TASK_LOG"\n')
        self.executable('gradlew', '''#!/bin/sh
mkdir -p ignored app/build/outputs/apk/release
printf '%s\\n' "$@" >> "$TASK_LOG"
if [ -n "${FAIL_BUILD:-}" ]; then echo "${SECRET_PROBE:-build failure}"; exit 17; fi
printf '%s' "${APK_KIND:-signed}" > app/build/outputs/apk/release/app-release.apk
''')
        self.certificate = 'a'*64
        self.executable('sdk/build-tools/36.0.0/apksigner', '''#!/usr/bin/env python3
import os,sys
from pathlib import Path
if Path(sys.argv[-1]).read_text() != 'signed': sys.exit(1)
print('Signer #1 certificate SHA-256 digest: '+os.environ.get('CERT_PROBE','a'*64))
''')
        self.executable('sdk/cmdline-tools/latest/bin/apkanalyzer', '''#!/usr/bin/env python3
import os,sys
from pathlib import Path
v=dict(line.split('=') for line in Path('app/version.properties').read_text().splitlines())
print({'application-id':os.environ.get('PACKAGE_PROBE','com.idontremember.personalcrm'),
       'version-name':v['versionName'], 'version-code':v['versionCode'],
       'debuggable':os.environ.get('DEBUG_PROBE','false')}[sys.argv[2]])
''')
        key = Path(self.tmp.name)/'disposable.keystore'
        key.write_bytes(b'test key')
        self.secret = 'private-test-password'
        self.write('app/release-signing.properties', f'storeFile={key}\nstorePassword={self.secret}\nkeyAlias=release\nkeyPassword={self.secret}\ncertificateSha256={self.certificate}\n')
        self.commit()
        remote = Path(self.tmp.name)/'upstream.git'
        subprocess.run(['git', 'init', '--bare', '-q', str(remote)], check=True, env=self.env)
        self.git('remote', 'add', 'origin', str(remote))
        self.git('push', '-q', 'origin', 'HEAD')

    def write(self, name, text):
        path = self.root/name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text)
        return path

    def executable(self, name, text):
        self.write(name, text).chmod(0o755)

    def git(self, *args):
        return subprocess.check_output(['git', *args], cwd=self.root, env=self.env, stderr=subprocess.STDOUT).decode().strip()

    def commit(self):
        self.git('add', '.')
        self.git('-c', 'core.hooksPath=/dev/null', 'commit', '-qm', 'Fixture')

    def task(self, name, *args):
        return subprocess.run(['bash', str(self.root/'.mise/tasks'/name), *args], cwd='/tmp',
                              env=self.env, text=True, capture_output=True)

    def refused(self, expected):
        result = self.task('release-build')
        self.assertNotEqual(result.returncode, 0, result.stdout)
        self.assertIn(expected, result.stderr)
        self.assertFalse((self.root/'ignored/releases/v0.1.0').exists())
        return result

    def test_standard_bumps_and_initial_minor_release(self):
        for start, bump, expected in [('0.0.0','minor','0.1.0'), ('1.2.3','patch','1.2.4'),
                                      ('1.2.3','minor','1.3.0'), ('1.2.3','major','2.0.0')]:
            self.write('app/version.properties', f'versionName={start}\nversionCode=1\n')
            result = self.task('release-version', bump)
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertEqual((self.root/'app/version.properties').read_text(), f'versionName={expected}\nversionCode=2\n')
        before = (self.root/'app/version.properties').read_bytes()
        self.assertNotEqual(self.task('release-version','pre').returncode, 0)
        self.assertEqual((self.root/'app/version.properties').read_bytes(), before)

    def test_invalid_versions_and_exhausted_codes_are_unchanged(self):
        for name, code in [('1.0.0-rc1', '2'),('01.2.3','2'),('1.2','2'),('1.2.3','2100000000'),('1.2.3','0')]:
            path=self.write('app/version.properties',f'versionName={name}\nversionCode={code}\n')
            before=path.read_bytes()
            self.assertNotEqual(self.task('release-version','patch').returncode,0)
            self.assertEqual(path.read_bytes(),before)

    def test_version_bump_holds_build_lock(self):
        (self.root/'.gradle').mkdir()
        with (self.root/'.gradle/agent-build.lock').open('a') as lock:
            fcntl.flock(lock, fcntl.LOCK_EX)
            self.env['BUILD_LOCK_TIMEOUT']='0.1'
            self.assertNotEqual(self.task('release-version','minor').returncode,0)
        self.assertIn('versionName=0.1.0', (self.root/'app/version.properties').read_text())

    def test_signed_bundle_provenance_and_overwrite_refusal(self):
        result=self.task('release-build')
        self.assertEqual(result.returncode,0,result.stderr)
        bundle=self.root/'ignored/releases/v0.1.0'
        manifest=json.loads((bundle/'manifest.json').read_text())
        self.assertEqual(manifest['apk'], 'prm-0.1.0.apk')
        apk=bundle/manifest['apk']
        self.assertEqual(manifest['git_revision'],self.git('rev-parse','HEAD'))
        self.assertEqual(manifest['certificate_sha256'],self.certificate)
        self.assertEqual(manifest['app_sha256'],hashlib.sha256(apk.read_bytes()).hexdigest())
        self.assertIn(manifest['app_sha256'],(bundle/'SHA256SUMS').read_text())
        self.assertFalse(apk.stat().st_mode & 0o222)
        self.assertNotIn(self.secret, ''.join(p.read_text() for p in bundle.iterdir()))
        calls=(self.root/'ignored/calls').read_text()
        self.assertTrue(calls.startswith('verify\n'))
        self.assertIn('lintRelease\nassembleRelease\n',calls)
        self.assertIn('--no-configuration-cache',calls)
        self.assertNotIn(self.secret,calls)
        before={p.name:p.read_bytes() for p in bundle.iterdir()}
        result=self.task('release-build')
        self.assertNotEqual(result.returncode,0)
        self.assertIn('overwrite',result.stderr)
        self.assertEqual(before,{p.name:p.read_bytes() for p in bundle.iterdir()})
        self.assertEqual(self.git('tag','--list'),'')

    def test_dirty_tree_and_zero_version_refused(self):
        self.write('untracked','source')
        self.refused('clean, committed')
        (self.root/'untracked').unlink()
        self.write('app/version.properties','versionName=0.0.0\nversionCode=1\n')
        self.commit()
        self.refused('0.0.0')

    def test_local_and_upstream_duplicate_tags_refused(self):
        self.git('tag','v0.1.0')
        self.refused('already tagged')
        self.git('push','-q','origin','v0.1.0')
        self.git('tag','-d','v0.1.0')
        self.refused('already tagged')

    def test_stale_version_and_nonincreasing_code_refused(self):
        self.git('tag','v0.1.0')
        self.write('app/version.properties','versionName=0.0.9\nversionCode=3\n')
        self.commit()
        self.refused('older')
        self.write('app/version.properties','versionName=0.2.0\nversionCode=2\n')
        self.commit()
        self.refused('version code must increase')

    def test_unavailable_upstream_is_blocking(self):
        self.git('remote','set-url','origin',str(self.root/'absent.git'))
        self.refused('git failed')

    def test_invalid_credentials_failed_build_and_unsigned_output(self):
        for name, value, error in [('FAIL_BUILD','1','failed'),('APK_KIND','unsigned','apksigner failed'),
                                    ('CERT_PROBE','b'*64,'certificate'),('DEBUG_PROBE','true','debuggable'),
                                    ('PACKAGE_PROBE','wrong.package','application-id')]:
            with self.subTest(name=name):
                self.env[name]=value
                self.env['SECRET_PROBE']=self.secret
                result=self.refused(error)
                self.assertNotIn(self.secret,result.stderr+result.stdout)
                self.env.pop(name)
        (self.root/'app/release-signing.properties').unlink()
        self.refused('release-signing.properties')

    def test_changed_inputs_during_build_refuse_export(self):
        path=self.root/'gradlew'
        path.write_text(path.read_text()+'echo changed >> app/version.properties\n')
        self.commit()
        self.refused('changed during')

    def test_long_failure_keeps_first_diagnostic_and_redacts_passwords(self):
        spec=importlib.util.spec_from_file_location('releases',SOURCE/'scripts/releases.py')
        releases=importlib.util.module_from_spec(spec);spec.loader.exec_module(releases)
        script='import os,sys; print("FIRST_DIAGNOSTIC"); print("x"*20000); print("LAST_DIAGNOSTIC",os.environ["SECRET_PROBE"]); sys.exit(17)'
        with patch.dict(os.environ,SECRET_PROBE=self.secret):
            with self.assertRaises(ValueError) as caught:
                releases.run(self.root,[sys.executable,'-c',script],secrets=[self.secret])
        diagnostic=str(caught.exception)
        self.assertIn('FIRST_DIAGNOSTIC',diagnostic)
        self.assertIn('LAST_DIAGNOSTIC',diagnostic)
        self.assertNotIn(self.secret,diagnostic)

    def test_successful_metadata_excludes_stderr_diagnostics(self):
        spec=importlib.util.spec_from_file_location('releases',SOURCE/'scripts/releases.py')
        releases=importlib.util.module_from_spec(spec);spec.loader.exec_module(releases)
        script='import sys; print("com.idontremember.personalcrm"); print("Java startup diagnostic",file=sys.stderr)'
        self.assertEqual(releases.run(self.root,[sys.executable,'-c',script]),'com.idontremember.personalcrm')

    def test_nested_gradle_inherits_release_lock_through_export(self):
        path=self.root/'sdk/cmdline-tools/latest/bin/apkanalyzer'
        path.write_text(path.read_text().replace('v=dict', '''import fcntl
with Path('.gradle/agent-build.lock').open('a') as probe:
 try: fcntl.flock(probe,fcntl.LOCK_EX|fcntl.LOCK_NB)
 except BlockingIOError: pass
 else: raise RuntimeError('artifact verification lost build lock')
v=dict'''))
        result=self.task('release-build')
        self.assertEqual(result.returncode,0,result.stderr)


if __name__ == '__main__':
    unittest.main()
