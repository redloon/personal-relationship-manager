#!/usr/bin/env python3
"""Validate the index; run broader checks only on a coherent working snapshot."""
import argparse
import ast
import json
import os
from pathlib import Path
import subprocess
import sys
import tomllib
import xml.etree.ElementTree as ET


ROOT_BUILD_FILES = {
    'build.gradle', 'build.gradle.kts', 'settings.gradle', 'settings.gradle.kts',
    'gradle.properties', 'gradlew', 'gradlew.bat',
}


def git(*args, cwd=None):
    return subprocess.run(['git', *args], cwd=cwd, capture_output=True, check=True).stdout


def android_input(path):
    return path.startswith(('app/', 'gradle/')) or path in ROOT_BUILD_FILES or path == '.editorconfig'


def tooling_input(path):
    if android_input(path) or Path(path).suffix in {'.md', '.rst'}:
        return False
    return path.startswith(('scripts/', 'tests/tooling/', '.mise/', '.githooks/', '.codex/')) or path == 'mise.toml' or Path(path).suffix in {'.py', '.sh', '.toml'}


def verification_input(path):
    return android_input(path) or tooling_input(path)


def index_entries():
    entries = {}
    for record in git('ls-files', '--stage', '-z').split(b'\0'):
        if not record:
            continue
        metadata, path = record.split(b'\t', 1)
        mode, digest, stage = metadata.split()
        if stage != b'0':
            raise ValueError('Resolve unmerged index entries before committing.')
        entries[os.fsdecode(path)] = (mode.decode(), digest.decode())
    return entries


def coherence_errors(predicate):
    changed = git('diff', '--name-only', '-z').split(b'\0')
    untracked = git('ls-files', '--others', '--exclude-standard', '-z').split(b'\0')
    paths = sorted({os.fsdecode(path) for path in changed + untracked if path and predicate(os.fsdecode(path))})
    return ['Working input differs from the index or is untracked: ' + path for path in paths]


def syntax_errors(paths, entries):
    errors = []
    for path in paths:
        if path not in entries:  # A staged deletion has no contents to parse.
            continue
        mode = entries[path][0]
        is_task = path.startswith(('.mise/tasks/', '.githooks/')) and not path.endswith('.md')
        if is_task and mode != '100755':
            errors.append(path + ': task/hook must be executable in the index (git add --chmod=+x).')
        if mode not in {'100644', '100755'}:
            continue
        data = git('show', ':' + path)
        if is_task and not data.startswith(b'#!'):
            errors.append(path + ': executable task/hook requires a shebang.')
            continue
        suffix = Path(path).suffix
        try:
            if is_task:
                metadata = '\n'.join(line[len('#MISE'):].strip() for line in data.decode('utf-8').splitlines() if line.startswith('#MISE'))
                try:
                    tomllib.loads(metadata)
                except ValueError as error:
                    raise ValueError('#MISE metadata: ' + str(error)) from error
            if suffix == '.py' or (is_task and b'python' in data.split(b'\n', 1)[0]):
                ast.parse(data, filename=path)
            elif suffix == '.toml':
                tomllib.loads(data.decode('utf-8'))
            elif suffix == '.xml':
                ET.fromstring(data)
            elif suffix == '.json':
                json.loads(data)
            elif suffix == '.sh' or (is_task and (b'bash' in data.split(b'\n', 1)[0] or b'/sh' in data.split(b'\n', 1)[0])):
                result = subprocess.run(['bash', '-n'], input=data, capture_output=True)
                if result.returncode:
                    errors.append(path + ': ' + result.stderr.decode('utf-8', errors='replace').strip())
        except (SyntaxError, ValueError, UnicodeError, ET.ParseError) as error:
            errors.append(path + ': ' + str(error))
    return errors


def report(errors):
    for error in errors:
        print(error, file=sys.stderr)
    return 1


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--syntax-only', action='store_true', help='Only staged syntax and whitespace; no builds')
    args = parser.parse_args()
    try:
        root = Path(os.fsdecode(git('rev-parse', '--show-toplevel')).strip())
        os.chdir(root)
        whitespace = subprocess.run(['git', 'diff', '--cached', '--check'], capture_output=True, text=True)
        if whitespace.returncode:
            return report(['Staged whitespace/conflict-marker check failed:', whitespace.stdout.strip(), whitespace.stderr.strip()])
        entries = index_entries()
        paths = [os.fsdecode(path) for path in git('diff', '--cached', '--name-only', '--diff-filter=ACMRD', '-z').split(b'\0') if path]
        errors = syntax_errors(paths, entries)
        if errors:
            return report(errors)
        if args.syntax_only:
            print('Staged syntax and whitespace checks passed.')
            return 0
        has_android = any(android_input(path) for path in paths)
        has_tooling = any(tooling_input(path) for path in paths)
        tasks = []
        if has_android:
            tasks.append('.mise/tasks/check')
        if has_tooling:
            tasks.append('.mise/tasks/tooling-test')
        if not tasks:
            print('Staged checks passed; no build or tooling inputs changed.')
            return 0
        predicate = verification_input if has_android else tooling_input
        errors = coherence_errors(predicate)
        if errors:
            return report(errors + ['Stage the intended verification inputs or move unrelated work to another worktree.'])
        before = {path: value for path, value in entries.items() if predicate(path)}
        for task in tasks:
            if not Path(task).is_file() or not os.access(task, os.X_OK):
                return report([task + ': verification task is missing or not executable.'])
            print('Running ' + task, flush=True)
            result = subprocess.run([str(root / task)])
            if result.returncode:
                return report([task + ' failed (exit ' + str(result.returncode) + ').'])
            errors = coherence_errors(predicate)
            after = {path: value for path, value in index_entries().items() if predicate(path)}
            if before != after:
                errors.append('The index changed during verification; rerun against a stable snapshot.')
            if errors:
                return report(errors)
        print('Staged checks passed for the verified snapshot.')
        return 0
    except (subprocess.CalledProcessError, OSError, ValueError) as error:
        return report(['Staged checks failed: ' + str(error)])


if __name__ == '__main__':
    sys.exit(main())
