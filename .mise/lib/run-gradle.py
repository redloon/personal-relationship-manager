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
        'app/src', 'app/schemas', 'app/build.gradle.kts', 'app/proguard-rules.pro',
        'build.gradle.kts', 'settings.gradle.kts', 'gradle.properties', 'gradle',
        'gradlew', '.mise', 'mise.toml', 'scripts/android-sdk-env.sh',
    ], cwd=ROOT).split(b'\0')
    digest = hashlib.sha256()
    for name in sorted(set(paths) - {b''}):
        path = ROOT / name.decode('utf-8', errors='surrogateescape')
        digest.update(name + b'\0')
        digest.update(path.read_bytes() if path.is_file() else b'<missing>')
        digest.update(b'\0')
    return digest.digest()


def main():
    before = fingerprint()
    result = subprocess.run([str(ROOT / 'gradlew'), *sys.argv[1:]], cwd=ROOT)
    changed = before != fingerprint()
    if changed:
        print('Build inputs changed during Gradle execution. Freeze edits and rerun validation.', file=sys.stderr)
    return result.returncode or (1 if changed else 0)


if __name__ == '__main__':
    sys.exit(main())
