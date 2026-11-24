#!/usr/bin/env python3
"""Verified sandbox backup and transcript import, separate from daily launch."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import tarfile
from datetime import datetime, timezone
ROOT = Path(os.environ.get('CODEX_SBX_HOME', str(Path.home() / '.local/share/codex-sbx')))
def run(*args, **kwargs):
    return subprocess.run(list(args), check=True, **kwargs)

def backup(name):
    destination = ROOT / 'backups' / (datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S%fZ') + '-' + name)
    destination.mkdir(parents=True, mode=0o700)
    archive = destination / 'state.tar.gz'
    with archive.open('xb') as output:
        # Backups must remain possible with the currently running daemon before an upgrade.
        run(os.environ.get('CODEX_SBX_BACKUP_BIN', 'sbx'), 'exec', name, 'python3', '-c',
            Path(__file__).resolve().with_name('export_state.py').read_text(), stdout=output)
    with tarfile.open(archive) as saved:
        manifest = json.load(saved.extractfile('manifest.json'))
        for filename, record in manifest['files'].items():
            stream = saved.extractfile(filename)
            digest = hashlib.file_digest(stream, 'sha256').hexdigest()
            if digest != record['sha256'] or saved.getmember(filename).size != record['bytes']:
                raise RuntimeError('Backup verification failed: ' + filename)
    manifest['archive_sha256'] = hashlib.sha256(archive.read_bytes()).hexdigest()
    (destination / 'verified.json').write_text(json.dumps(manifest, indent=2) + '\n')
    count = sum(p.startswith(('sessions/', 'archived_sessions/')) for p in manifest['files'])
    print(f'Verified {count} transcripts: {destination}', flush=True)
    if manifest['changed_during_copy']:
        print('Changed during copy; run backup again before transition: ' + ', '.join(manifest['changed_during_copy']), file=sys.stderr)
    return destination


def import_backup(destination, history):
    """Import transcripts only; never transplant auth or live SQLite databases."""
    archive = Path(destination) / 'state.tar.gz'
    verified = json.loads((Path(destination) / 'verified.json').read_text())
    with archive.open('rb') as stream:
        if hashlib.file_digest(stream, 'sha256').hexdigest() != verified['archive_sha256']:
            raise ValueError('Backup archive checksum mismatch')
    history = Path(history)
    candidates = []
    with tarfile.open(archive) as saved:
        for filename, record in verified['files'].items():
            relative = Path(filename)
            if relative.is_absolute() or '..' in relative.parts:
                raise ValueError('Unsafe backup path')
            if relative.parts[0] not in ('sessions', 'archived_sessions'):
                continue
            member = saved.getmember(filename)
            if not member.isfile():
                raise ValueError('Backup contains a non-file transcript')
            data = saved.extractfile(member).read()
            if hashlib.sha256(data).hexdigest() != record['sha256']:
                raise ValueError('Transcript checksum mismatch')
            target = history / relative
            if not target.resolve().is_relative_to(history.resolve()):
                raise ValueError('Transcript path escapes history directory')
            if target.exists() and target.read_bytes() != data:
                raise ValueError('Refusing to overwrite different transcript: ' + str(target))
            candidates.append((target, data))
        for target, data in candidates:
            target.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
            if not target.exists():
                with target.open('xb') as output:
                    output.write(data)
    print(f'Imported {len(candidates)} verified transcripts into {history}')



def main():
    os.umask(0o077)
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest='command', required=True)
    commands.add_parser('backup').add_argument('sandbox')
    importer = commands.add_parser('import')
    importer.add_argument('backup')
    importer.add_argument('history_directory', type=Path)
    args = parser.parse_args()
    if args.command == 'backup': backup(args.sandbox)
    else: import_backup(args.backup, args.history_directory)

if __name__ == '__main__':
    try: main()
    except (ValueError, RuntimeError, subprocess.CalledProcessError) as error:
        sys.exit(str(error))
