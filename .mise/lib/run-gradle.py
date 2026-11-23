"""Reject validation against sources that changed while Gradle ran.

The outer flock serializes task invocations, but editors must still coordinate.
Only Git-visible source inputs are hashed: generated build outputs are excluded.
"""
import hashlib
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]


def fingerprint():
    paths = subprocess.check_output([
        'git', 'ls-files', '-z', '--cached', '--others', '--exclude-standard', '--',
        'app/src', 'app/schemas', 'app/build.gradle.kts', 'app/version.properties', 'app/proguard-rules.pro',
        'app/lint-baseline.xml', 'app/ktlint-baseline.xml',
        'build.gradle.kts', 'settings.gradle.kts', 'gradle.properties', 'gradle',
        'gradlew', '.mise', 'mise.toml', '.editorconfig', 'scripts/android-sdk-env.sh',
        'scripts/stage-mcp-apks.py', 'scripts/releases.py',
    ], cwd=ROOT).split(b'\0')
    digest = hashlib.sha256()
    for name in sorted(set(paths) - {b''}):
        path = ROOT / name.decode('utf-8', errors='surrogateescape')
        digest.update(name + b'\0')
        digest.update(path.read_bytes() if path.is_file() else b'<missing>')
        digest.update(b'\0')
    return digest.digest()


def main():
    stage_apks = "--stage-apks" in sys.argv
    stage_app = "--stage-app" in sys.argv
    args = [arg for arg in sys.argv[1:] if arg not in {"--stage-apks", "--stage-app"}]
    before = fingerprint()
    result = subprocess.run([str(ROOT / 'gradlew'), *args], cwd=ROOT)
    changed = before != fingerprint()
    if changed:
        print('Build inputs changed during Gradle execution. Freeze edits and rerun validation.', file=sys.stderr)
    if result.returncode or changed:
        return result.returncode or 1
    if stage_apks or stage_app:
        import importlib.util
        import json
        spec = importlib.util.spec_from_file_location('stage', ROOT / 'scripts/stage-mcp-apks.py')
        stage = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(stage)
        revision = subprocess.run(['git', 'rev-parse', '--verify', 'HEAD'], cwd=ROOT, capture_output=True, text=True)
        provenance = dict(source_sha256=before.hex(), git_revision=revision.stdout.strip() or None)
        manifest = stage.stage(ROOT / 'ignored/mcp-inbox', artifacts={'app': stage.ARTIFACTS['app']} if stage_app else stage.ARTIFACTS, provenance=provenance)
        if before != fingerprint():
            (ROOT / 'ignored/mcp-inbox/manifest.json').unlink(missing_ok=True)
            print('Build inputs changed during APK staging. Rerun against frozen sources.', file=sys.stderr)
            return 1
        print(json.dumps(manifest, indent=2))
    return 0


if __name__ == '__main__':
    sys.exit(main())
