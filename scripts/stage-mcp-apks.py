#!/usr/bin/env python3
"""Stage the sandbox-built APK pair for android-tests; print MCP arguments."""
import hashlib
import json
from pathlib import Path
import shutil

ROOT = Path(__file__).resolve().parent.parent
ARTIFACTS = {
    'app': ROOT / 'app/build/outputs/apk/debug/app-debug.apk',
    'test': ROOT / 'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk',
}


def stage(inbox, artifacts=ARTIFACTS):
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
    (inbox / 'manifest.json').write_text(json.dumps(hashes, indent=2) + '\n')
    return hashes


if __name__ == '__main__':
    print(json.dumps(stage(ROOT / 'ignored/mcp-inbox'), indent=2))
