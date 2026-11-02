#!/usr/bin/env bash
# Point Git at tracked hooks without overwriting an existing hook setup.
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(git -C "$SCRIPT_DIR/.." rev-parse --show-toplevel)"
cd "$PROJECT_DIR"

if [[ ! -x .githooks/pre-commit ]]; then
    echo "Tracked .githooks/pre-commit is missing or not executable." >&2
    exit 1
fi
CONFIGURED_HOOKS="$(git config --get core.hooksPath || true)"
if [[ -n "$CONFIGURED_HOOKS" && "$CONFIGURED_HOOKS" != '.githooks' ]]; then
    printf 'Preserving existing core.hooksPath: %s\n' "$CONFIGURED_HOOKS" >&2
    exit 1
fi
if [[ "$CONFIGURED_HOOKS" == '.githooks' ]]; then
    echo 'Tracked Git hooks are already configured.'
    exit 0
fi

CURRENT_HOOKS="$(git rev-parse --git-path hooks)"
if [[ -d "$CURRENT_HOOKS" ]]; then
    for EXISTING_HOOK in "$CURRENT_HOOKS"/*; do
        [[ -e "$EXISTING_HOOK" ]] || continue
        [[ "$EXISTING_HOOK" == *.sample ]] && continue
        if [[ -f "$EXISTING_HOOK" && -x "$EXISTING_HOOK" ]]; then
            printf 'Preserving existing hook: %s\n' "$EXISTING_HOOK" >&2
            exit 1
        fi
    done
fi

git config --local core.hooksPath .githooks
echo 'Tracked Git hooks configured (.githooks).'
