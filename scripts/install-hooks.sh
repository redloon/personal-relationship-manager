#!/usr/bin/env bash
# install-hooks.sh — set up git hooks for the project.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
HOOKS_DIR="$PROJECT_DIR/.git/hooks"

cat > "$HOOKS_DIR/pre-commit" << 'HOOK'
#!/usr/bin/env bash
# Pre-commit hook: run static checks before allowing commit.
# Skip with: git commit --no-verify

echo "Running pre-commit checks..."
if ! ./scripts/pre-launch-check.sh; then
    echo ""
    echo "❌ Pre-commit checks failed. Fix issues or use --no-verify to skip."
    exit 1
fi
HOOK

chmod +x "$HOOKS_DIR/pre-commit"
echo "✅ Git pre-commit hook installed."