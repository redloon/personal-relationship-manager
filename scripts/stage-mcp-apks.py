#!/usr/bin/env python3
"""Stage the sandbox-built APK pair for android-tests; print MCP arguments."""
import hashlib
import json
from pathlib import Path
import shutil
import argparse
import re

ROOT = Path(__file__).resolve().parent.parent
ARTIFACTS = {
    'app': ROOT / 'app/build/outputs/apk/debug/app-debug.apk',
    'test': ROOT / 'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk',
}


def stage(inbox, artifacts=ARTIFACTS, provenance=None):
    for source in artifacts.values():
        if not source.is_file():
            raise FileNotFoundError(f'{source}: build both APKs first')
    inbox.mkdir(parents=True, exist_ok=True)
    hashes = {}
    for name, source in artifacts.items():
        temporary = inbox / f'{name}.apk.tmp'
        shutil.copyfile(source, temporary)
        hashes[f'{name}_sha256'] = hashlib.sha256(temporary.read_bytes()).hexdigest()
        temporary.replace(inbox / f'{name}.apk')
    if provenance is not None:
        hashes['provenance'] = provenance
    temporary = inbox / 'manifest.json.tmp'
    temporary.write_text(json.dumps(hashes, indent=2) + '\n')
    temporary.replace(inbox / 'manifest.json')
    if 'test' not in artifacts:
        (inbox / 'test.apk').unlink(missing_ok=True)
    return hashes


def stage_release(inbox, apk):
    apk = apk.resolve()
    original = json.loads((apk.parent/'manifest.json').read_text())
    digest = hashlib.sha256(apk.read_bytes()).hexdigest()
    if original.get('app_sha256') != digest:
        raise ValueError('Release APK hash does not match original manifest')
    if original.get('apk') != apk.name or original.get('application_id') != 'com.idontremember.personalcrm':
        raise ValueError('Release APK identity does not match original manifest')
    if not re.fullmatch('[0-9a-f]{64}', original.get('source_sha256', '')) or not re.fullmatch('[0-9a-f]{40}|[0-9a-f]{64}', original.get('git_revision', '')):
        raise ValueError('Release bundle has no valid original source provenance')
    if (apk.parent/'SHA256SUMS').read_text() != f'{digest}  {apk.name}\n':
        raise ValueError('Release checksum does not match manifest')
    provenance = dict(source_sha256=original['source_sha256'], git_revision=original['git_revision'], release_manifest=original)
    return stage(inbox, artifacts={'app': apk}, provenance=provenance)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--release-apk', type=Path)
    args = parser.parse_args()
    print(json.dumps(stage_release(ROOT/'ignored/mcp-inbox', args.release_apk) if args.release_apk else stage(ROOT/'ignored/mcp-inbox'), indent=2))
