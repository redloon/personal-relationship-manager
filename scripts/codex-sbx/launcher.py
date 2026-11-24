#!/usr/bin/env python3
"""Host-side launcher. Python 3.11+, no third-party dependencies."""
import argparse
import fcntl
import hashlib
import json
import os
import re
from pathlib import Path
import subprocess
import sys
import tomllib

WORKLOAD = 'sbx/codex:0.155.1'
SESSIONS = 'sbx/codex-sessions:1.0.0'
ROOT = Path(os.environ.get('CODEX_SBX_HOME', str(Path.home() / '.local/share/codex-sbx')))
SBX = os.environ.get('CODEX_SBX_BIN', str(ROOT / 'runtime/bin/sbx') if (ROOT / 'runtime/bin/sbx').exists() else 'sbx')
SAFE = {'model', 'model_reasoning_effort', 'model_verbosity', 'personality',
        'model_reasoning_summary', 'hide_agent_reasoning', 'show_raw_agent_reasoning',
        'web_search', 'tui', 'history', 'check_for_update_on_startup',
        'suppress_unstable_features_warning', 'file_opener', 'compact_prompt',
        'developer_instructions', 'user_instructions'}


def run(*args, **kwargs):
    return subprocess.run(list(args), check=True, **kwargs)


def toml_load(text):
    return tomllib.loads(text)


def toml_dump(data):
    def key(k):
        return k if re.fullmatch(r'[A-Za-z0-9_-]+', k) else json.dumps(k, ensure_ascii=False)
    def value(v):
        if isinstance(v, str):
            return json.dumps(v, ensure_ascii=False)
        if isinstance(v, bool):
            return str(v).lower()
        if isinstance(v, (int, float)):
            return repr(v)
        if isinstance(v, list):
            return '[' + ', '.join(value(x) for x in v) + ']'
        if isinstance(v, dict):
            return '{' + ', '.join(key(k) + ' = ' + value(x) for k, x in v.items()) + '}'
        raise ValueError('Unsupported TOML value: ' + type(v).__name__)
    lines = []
    def table(mapping, path=()):
        if path:
            lines.append('[' + '.'.join(key(k) for k in path) + ']')
        for k, v in mapping.items():
            if not isinstance(v, dict):
                lines.append(key(k) + ' = ' + value(v))
        for k, v in mapping.items():
            if isinstance(v, dict):
                table(v, (*path, k))
    table(data)
    return '\n'.join(lines) + '\n'


def merge_config(host, base):
    result = dict(base)
    result.update({k: v for k, v in host.items() if k in SAFE})
    result.update(approval_policy='never', sandbox_mode='danger-full-access')
    return result, sorted(set(host) - SAFE)


def remote(name, code, *args, **kwargs):
    return run(SBX, 'exec', name, 'python3', '-c', code, *args, **kwargs)


def sandbox_name(path):
    path = path.resolve()
    basename = re.sub(r'[^a-z0-9-]+', '-', path.name.lower()).strip('-')[:40] or 'workspace'
    return 'codex-sbx-' + basename + '-' + hashlib.sha256(str(path).encode()).hexdigest()[:12]


def history_directory(path):
    return ROOT / 'history' / sandbox_name(path)


def discover(path):
    sandboxes = json.loads(run(SBX, '--no-input', 'ls', '--format', 'json', capture_output=True, text=True).stdout)
    matches = [item['name'] for item in sandboxes
               if item.get('agent') == 'codex' and str(path) in item.get('workspaces', [])
               and any(mount.split(':')[1:2] == ['/home/agent/codex-history'] for mount in item.get('mounts', []))]
    if len(matches) > 1:
        raise ValueError('Ambiguous sessions-enabled sandboxes: ' + ', '.join(matches))
    return matches[0] if matches else None


def prepare(path):
    name = discover(path)
    if name is None:
        name = sandbox_name(path)
        history = history_directory(path)
        history.mkdir(parents=True, exist_ok=True, mode=0o700)
        command = [SBX, '--no-input', 'create', WORKLOAD, str(path), '--name', name,
                   '--kit', SESSIONS, '--mount', f'{history}:/home/agent/codex-history',
                   '--cpus', '4', '--memory', '8g']
        if os.environ.get('CODEX_SBX_MCP'):
            command.extend(['--static-mcp', os.environ['CODEX_SBX_MCP']])
        run(*command)
    remote(name, '''import pathlib
p=pathlib.Path.home()/'.codex/sessions'
assert p.is_symlink(), 'Sessions kit did not link persistent storage'
assert str(p.resolve()).startswith('/home/agent/codex-history/'), 'Unexpected session storage' ''')
    # Retain the kit-generated config once; refresh preferences from this baseline each launch.
    code = '''import pathlib,json,tomllib
p=pathlib.Path.home()/'.codex'
base=p/'codex-sbx-base.toml'
if not base.exists(): base.write_bytes((p/'config.toml').read_bytes())
current=tomllib.loads((p/'config.toml').read_text())
print(json.dumps(dict(base=base.read_text(), projects=current.get('projects',{}))))'''
    local = json.loads(remote(name, code, capture_output=True, text=True).stdout)
    host_home = Path(os.environ.get('CODEX_HOME') or str(Path.home() / '.codex'))
    host_file = host_home / 'config.toml'
    host = toml_load(host_file.read_text()) if host_file.exists() else {}
    config, skipped = merge_config(host, toml_load(local['base']))
    if local['projects']:
        config['projects'] = local['projects']
    if skipped:
        print('Host settings kept sandbox-local or unsupported: ' + ', '.join(skipped), file=sys.stderr)
    code = '''import os,pathlib,sys
p=pathlib.Path.home()/'.codex/config.toml'
tmp=p.with_suffix('.tmp'); tmp.write_text(sys.argv[1]); tmp.chmod(0o600); tmp.replace(p)'''
    remote(name, code, toml_dump(config))
    return name


def main():
    os.umask(0o077)
    ROOT.mkdir(parents=True, exist_ok=True, mode=0o700)
    parser = argparse.ArgumentParser(description=__doc__, usage='codex-sbx [REPO_PATH] [-- CODEX_ARGS...]')
    parser.add_argument('arguments', nargs=argparse.REMAINDER)
    args = parser.parse_args().arguments
    with (ROOT / 'launch.lock').open('a') as lock:
        fcntl.flock(lock, fcntl.LOCK_EX)
        path = Path(args.pop(0)).resolve() if args and args[0] != '--' else Path.cwd()
        if not path.is_dir(): parser.error('Repository path must be an existing directory')
        git = subprocess.run(['git', '-C', str(path), 'rev-parse', '--show-toplevel'], capture_output=True, text=True)
        if git.returncode == 0: path = Path(git.stdout.strip())
        if args and args[0] == '--': args.pop(0)
        name = prepare(path)
    os.execvp(SBX, [SBX, 'run', '--name', name, '--', *args])


if __name__ == '__main__':
    try:
        main()
    except (ValueError, RuntimeError, subprocess.CalledProcessError) as error:
        sys.exit(str(error))
