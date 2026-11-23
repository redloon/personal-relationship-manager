#!/usr/bin/env python3
"""Read-only workflow diagnostics; does not install tools, hooks or dependencies."""
import importlib.metadata
import json
import os
from pathlib import Path
import platform
import re
import shutil
import subprocess
import sys
import tomllib

ROOT = Path(__file__).resolve().parents[1]


def hook_errors(root):
    result = subprocess.run(['git', 'config', '--path', '--get', 'core.hooksPath'], cwd=root,
                            capture_output=True, text=True)
    configured = Path(result.stdout.strip()) if result.returncode == 0 else None
    if configured is None or (root / configured).resolve() != (root / '.githooks').resolve():
        return ['Project hooks are not configured; run bash .mise/tasks/hooks-install.']
    if not os.access(root / configured / 'pre-commit', os.X_OK):
        return ['Configured pre-commit hook is missing or not executable.']
    return []


def main():
    errors = hook_errors(ROOT)
    tools = {name: shutil.which(name) for name in ['bash', 'git', 'flock', 'uv']}
    errors += [f'Missing tool: {name}' for name, path in tools.items() if not path]
    java = Path(os.environ.get('JAVA_HOME', '/missing'))
    release = (java / 'release').read_text() if (java / 'release').is_file() else ''
    if not re.search(r'^JAVA_VERSION="21[."]', release, re.MULTILINE):
        errors.append('JDK 21 is required.')
    sdk = Path(os.environ.get('ANDROID_HOME', '/missing'))
    if not (sdk / 'platforms/android-36/android.jar').is_file():
        errors.append('Android SDK platform 36 is missing.')
    if platform.python_version() != '3.12.15':
        errors.append('Expected tooling Python 3.12.15; run bash .mise/tasks/tooling-setup.')
    dependencies = {}
    for name, expected in re.findall(r'^([\w-]+)==([^\s\\]+)', (ROOT / 'scripts/host-mcp/requirements.lock').read_text(), re.MULTILINE):
        try:
            actual = importlib.metadata.version(name)
        except importlib.metadata.PackageNotFoundError:
            actual = None
        dependencies[name] = actual
        if actual != expected:
            errors.append(f'{name}: expected {expected}, found {actual}; run bash .mise/tasks/tooling-setup.')
    versions = tomllib.loads((ROOT / 'gradle/libs.versions.toml').read_text())['versions']
    print(json.dumps(dict(python=platform.python_version(), java_home=str(java), sdk=str(sdk),
                          gradle=(ROOT / 'gradle/wrapper/gradle-wrapper.properties').read_text().split('distributionUrl=')[-1].splitlines()[0],
                          versions=versions, tools=tools, protocol_dependencies=dependencies, errors=errors), indent=2))
    return int(bool(errors))


if __name__ == '__main__':
    sys.exit(main())
