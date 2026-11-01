#!/usr/bin/env bash
# One host entry point for installation and refresh.
set -euo pipefail
SOURCE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
exec python3 "$SOURCE_DIR/setup.py" "$@"
