#!/usr/bin/env python3
"""Local versioning and signed release export. Never commits, tags or publishes."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile

ROOT = Path(__file__).resolve().parents[1]
PACKAGE = 'com.idontremember.personalcrm'
SEMVER = r'(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)'
MAX_CODE = 2100000000


def properties(text):
    # Deliberately small, shared subset of Java .properties: UTF-8, key=value,
    # no continuations or escapes. Signing paths are absolute Unix paths.
    result = {}
    for line in text.splitlines():
        line = line.strip()
        if not line or line.startswith(('#', '!')):
            continue
        key, separator, value = line.partition('=')
        if not separator or key.strip() in result or '\\' in line:
            raise ValueError('Expected unique key=value properties without escapes')
        result[key.strip()] = value.strip()
    return result


def version(text):
    data = properties(text)
    if set(data) != {'versionName', 'versionCode'} or not re.fullmatch(SEMVER, data['versionName']):
        raise ValueError('Expected an ordinary MAJOR.MINOR.PATCH version')
    if not re.fullmatch(r'[1-9][0-9]*', data['versionCode']) or not 1 <= int(data['versionCode']) <= MAX_CODE:
        raise ValueError('Invalid Android version code')
    return data['versionName'], int(data['versionCode'])


def bump(root, part):
    path = root/'app/version.properties'
    name, code = version(path.read_text())
    values = list(map(int, name.split('.')))
    index = ['major', 'minor', 'patch'].index(part)
    values[index] += 1
    values[index+1:] = [0] * (2-index)
    if code >= MAX_CODE:
        raise ValueError('Android version code exhausted')
    name = '.'.join(map(str, values))
    fd, temporary = tempfile.mkstemp(prefix='.version-', dir=path.parent)
    try:
        with os.fdopen(fd, 'w') as output:
            output.write(f'versionName={name}\nversionCode={code+1}\n')
            output.flush()
            os.fsync(output.fileno())
        os.chmod(temporary, 0o644)
        os.replace(temporary, path)
    finally:
        Path(temporary).unlink(missing_ok=True)
    return name, code+1


def run(root, args, *, secrets=(), inherited_lock=False):
    result = subprocess.run([str(a) for a in args], cwd=root, text=True,
                            stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                            pass_fds=(9,) if inherited_lock else ())
    standard_output, diagnostics = result.stdout, result.stderr
    for secret in secrets:
        if secret:
            standard_output = standard_output.replace(secret, '<redacted>')
            diagnostics = diagnostics.replace(secret, '<redacted>')
    output = standard_output + diagnostics
    if result.returncode:
        # Git/SDK diagnostics are useful; signing output is redacted before use.
        diagnostic = output if len(output) <= 16000 else output[:8000] + '\n...\n' + output[-8000:]
        raise ValueError(f'{Path(args[0]).name} failed (exit {result.returncode}):\n{diagnostic}')
    return standard_output.strip()


def clean(root):
    if run(root, ['git', 'status', '--porcelain', '--untracked-files=all']):
        raise ValueError('Release requires a clean, committed source tree')
    return run(root, ['git', 'rev-parse', '--verify', 'HEAD'])


def snapshot(root):
    digest = hashlib.sha256()
    for name in subprocess.check_output(['git', 'ls-files', '-z'], cwd=root).split(b'\0'):
        if name:
            path = root/os.fsdecode(name)
            digest.update(name+b'\0'+path.read_bytes()+b'\0')
    return digest.hexdigest()


def verify_tags(root, name, code):
    # Explicit origin: manual SOP configures this upstream once. Fetch failure
    # is blocking. Do not force/rewrite local tags or publish anything.
    run(root, ['git', 'fetch', '--tags', 'origin'])
    tags = run(root, ['git', 'tag', '--list']).splitlines()
    for tag in tags:
        match = re.fullmatch('v'+SEMVER, tag)
        if not match:
            continue
        previous = tuple(map(int, match.groups()))
        current = tuple(map(int, name.split('.')))
        if previous == current:
            raise ValueError(f'Version already tagged: {tag}')
        if previous > current:
            raise ValueError(f'Version is older than existing release {tag}')
        old_name, old_code = version(run(root, ['git', 'show', f'refs/tags/{tag}:app/version.properties']))
        if old_name != tag[1:]:
            raise ValueError(f'Tag/version metadata mismatch: {tag}')
        if code <= old_code:
            raise ValueError(f'Android version code must increase beyond {tag} ({old_code})')


def signing(root):
    path = root/'app/release-signing.properties'
    if not path.is_file():
        raise ValueError('Create the Git-ignored app/release-signing.properties; see docs/releases.md')
    config = properties(path.read_text())
    required = {'storeFile', 'storePassword', 'keyAlias', 'keyPassword', 'certificateSha256'}
    if set(config) != required or not all(config.values()):
        raise ValueError('Incomplete signing configuration')
    if not Path(config['storeFile']).is_absolute() or not Path(config['storeFile']).is_file():
        raise ValueError('Signing storeFile must name an existing absolute path outside the checkout')
    if Path(config['storeFile']).resolve().is_relative_to(root.resolve()):
        raise ValueError('Keep the signing key outside the checkout')
    certificate = config['certificateSha256'].replace(':', '').lower()
    if not re.fullmatch('[0-9a-f]{64}', certificate):
        raise ValueError('Invalid certificateSha256')
    tracked = subprocess.run(['git', 'ls-files', '--error-unmatch', 'app/release-signing.properties'],
                             cwd=root, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    ignored = subprocess.run(['git', 'check-ignore', '-q', 'app/release-signing.properties'], cwd=root)
    if tracked.returncode == 0 or ignored.returncode:
        raise ValueError('Signing configuration must be Git-ignored and untracked')
    return config, certificate


def sdk_tools():
    sdk = Path(os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT', ''))
    analyzer = sdk/'cmdline-tools/latest/bin/apkanalyzer'
    signers = list((sdk/'build-tools').glob('*/apksigner'))
    if not analyzer.is_file() or not signers:
        raise ValueError('Android apkanalyzer and apksigner are required')
    signer = max(signers, key=lambda p: tuple(int(n) for n in p.parent.name.split('.')))
    return analyzer, signer


def inspect_apk(root, apk, name, code, certificate):
    analyzer, signer = sdk_tools()
    output = run(root, [signer, 'verify', '--verbose', '--print-certs', apk])
    certificates = re.findall(r'^Signer #\d+ certificate SHA-256 digest: ([0-9a-fA-F]+)$', output, re.M)
    if [c.lower() for c in certificates] != [certificate]:
        raise ValueError('APK certificate does not match the expected signing certificate')
    for field, expected in [('application-id', PACKAGE), ('version-name', name), ('version-code', str(code)), ('debuggable', 'false')]:
        if run(root, [analyzer, 'manifest', field, apk]) != expected:
            raise ValueError(f'Unexpected APK {field}')


def build(root):
    commit = clean(root)
    name, code = version((root/'app/version.properties').read_text())
    if name == '0.0.0':
        raise ValueError('Bump the development version above 0.0.0 first')
    destination = root/'ignored/releases'/f'v{name}'
    if destination.exists():
        raise ValueError(f'Refusing to overwrite release bundle: {destination}')
    print(f'Verifying release {name} (Android code {code}) at {commit}.', flush=True)
    verify_tags(root, name, code)
    config, certificate = signing(root)
    secrets = [config['storePassword'], config['keyPassword']]
    before = snapshot(root)
    signing_before = hashlib.sha256((root/'app/release-signing.properties').read_bytes()).digest()
    print('Running verify.', flush=True)
    run(root, [root/'.mise/tasks/verify'], secrets=secrets, inherited_lock=True)
    # Verification is read-only. Avoid exporting a stale APK after a failed build.
    apk = root/'app/build/outputs/apk/release/app-release.apk'
    apk.unlink(missing_ok=True)
    print('Running release lint and signed APK build.', flush=True)
    run(root, [root/'.mise/tasks/gradle', 'lintRelease', 'assembleRelease', '-PreleaseSigning=true',
               '--no-configuration-cache', '--console=plain'], secrets=secrets, inherited_lock=True)
    if not apk.is_file():
        raise ValueError('Build did not produce a signed release APK')
    print('Verifying signed APK and exporting the bundle.', flush=True)
    inspect_apk(root, apk, name, code, certificate)
    if clean(root) != commit or snapshot(root) != before or hashlib.sha256((root/'app/release-signing.properties').read_bytes()).digest() != signing_before:
        raise ValueError('Release inputs changed during validation; freeze edits and retry')
    destination.parent.mkdir(parents=True, exist_ok=True)
    temporary = Path(tempfile.mkdtemp(prefix='.export-', dir=destination.parent))
    try:
        filename = f'prm-{name}.apk'
        exported = temporary/filename
        shutil.copyfile(apk, exported)
        checksum = hashlib.sha256(exported.read_bytes()).hexdigest()
        inspect_apk(root, exported, name, code, certificate)
        manifest = dict(version=name, version_code=code, application_id=PACKAGE, git_revision=commit,
                        source_sha256=before, certificate_sha256=certificate, apk=filename, app_sha256=checksum)
        (temporary/'SHA256SUMS').write_text(f'{checksum}  {filename}\n')
        (temporary/'manifest.json').write_text(json.dumps(manifest, indent=2)+'\n')
        if clean(root) != commit or snapshot(root) != before:
            raise ValueError('Release inputs changed during export')
        # The shared build lock excludes competing release commands. Publish
        # the complete read-only directory in one atomic filesystem rename.
        if destination.exists() or destination.is_symlink():
            raise ValueError(f'Refusing to overwrite release bundle: {destination}')
        for path in temporary.iterdir():
            path.chmod(0o444)
        temporary.chmod(0o555)
        temporary.rename(destination)
    finally:
        if temporary.exists():
            temporary.chmod(0o755)
            shutil.rmtree(temporary)
    return destination


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('command', choices=['version', 'build'])
    parser.add_argument('part', nargs='?', choices=['patch', 'minor', 'major'])
    args = parser.parse_args()
    try:
        if args.command == 'version':
            if not args.part:
                parser.error('version requires patch, minor or major')
            name, code = bump(ROOT, args.part)
            print(f'Version {name}, Android code {code}. Review and commit app/version.properties.')
        else:
            if args.part:
                parser.error('build does not accept a version increment')
            print(build(ROOT))
    except (OSError, ValueError) as error:
        print(f'Release refused: {error}', file=sys.stderr)
        return 1
    return 0


if __name__ == '__main__':
    sys.exit(main())
