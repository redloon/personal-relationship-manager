"""Run inside a sandbox; emit a verified-state archive on stdout."""
import hashlib
import io
import json
import os
from pathlib import Path
import shutil
import sqlite3
import sys
import tarfile
import tempfile
import time

home = Path(os.environ.get('CODEX_HOME') or str(Path.home() / '.codex'))
manifest = {'home': str(home), 'files': {}, 'changed_during_copy': []}
with tempfile.TemporaryDirectory(prefix='codex-rescue-') as temporary:
    stage = Path(temporary)
    sources = []
    for name in ('sessions', 'archived_sessions', 'history.jsonl', 'session_index.jsonl', 'config.toml'):
        path = home / name
        if path.is_dir():
            sources.extend(p for p in path.rglob('*') if p.is_file())
        elif path.is_file():
            sources.append(path)
    for source in sources:
        relative = source.relative_to(home)
        target = stage / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        before = source.stat()
        shutil.copyfile(source, target)
        digest = hashlib.sha256(target.read_bytes()).hexdigest()
        after = source.stat()
        if (before.st_size, before.st_mtime_ns) != (after.st_size, after.st_mtime_ns):
            manifest['changed_during_copy'].append(str(relative))
        else:
            if hashlib.sha256(source.read_bytes()).hexdigest() != digest:
                manifest['changed_during_copy'].append(str(relative))
        manifest['files'][str(relative)] = {'bytes': target.stat().st_size, 'sha256': digest}
    for source in sorted(home.glob('*.sqlite')):
        target = stage / source.name
        started = time.monotonic()
        def progress(*args):
            if time.monotonic() - started > 60:
                raise TimeoutError('SQLite backup exceeded 60 seconds: ' + source.name)
        with sqlite3.connect(source.as_uri() + '?mode=ro', uri=True) as src:
            with sqlite3.connect(target) as dst:
                src.backup(dst, pages=256, progress=progress)
                if dst.execute('PRAGMA quick_check').fetchone()[0] != 'ok':
                    raise RuntimeError('Invalid SQLite backup: ' + source.name)
        manifest['files'][source.name] = {'bytes': target.stat().st_size,
                                         'sha256': hashlib.sha256(target.read_bytes()).hexdigest()}
    with tarfile.open(fileobj=sys.stdout.buffer, mode='w|gz') as archive:
        for name in manifest['files']:
            archive.add(stage / name, arcname=name, recursive=False)
        data = json.dumps(manifest, indent=2).encode()
        info = tarfile.TarInfo('manifest.json')
        info.size = len(data)
        info.mode = 0o600
        archive.addfile(info, io.BytesIO(data))
