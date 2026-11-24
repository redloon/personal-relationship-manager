#!/usr/bin/env python3
"""Install a standalone launcher copy; repository moves cannot break it."""
import os
from pathlib import Path
import shutil

os.umask(0o077)
source = Path(__file__).resolve().parent
root = Path(os.environ.get('CODEX_SBX_HOME', str(Path.home() / '.local/share/codex-sbx')))
destination = root / 'launcher'
destination.mkdir(parents=True, exist_ok=True, mode=0o700)
for name in ('launcher.py', 'rescue.py', 'export_state.py'):
    target = destination / name
    temporary = target.with_suffix('.installing')
    shutil.copyfile(source / name, temporary)
    temporary.chmod(0o700)
    temporary.replace(target)
bin_directory = Path.home() / '.local/bin'
bin_directory.mkdir(parents=True, exist_ok=True)
command = bin_directory / 'codex-sbx'
if command.exists() and not command.is_symlink():
    raise SystemExit('Refusing to replace an existing non-symlink codex-sbx command')
if command.is_symlink():
    command.unlink()
command.symlink_to(destination / 'launcher.py')
runtime = root / 'runtime/bin/sbx'
sbx_command = bin_directory / 'sbx'
if runtime.exists() and not sbx_command.exists() and not sbx_command.is_symlink():
    sbx_command.symlink_to(runtime)
    print('Installed upgraded sbx command:', sbx_command)
print('Installed:', command)
print('State and history:', root)

rescue = bin_directory / 'codex-sbx-rescue'
if rescue.exists() and not rescue.is_symlink():
    raise SystemExit('Refusing to replace an existing non-symlink rescue command')
if rescue.is_symlink(): rescue.unlink()
rescue.symlink_to(destination / 'rescue.py')
