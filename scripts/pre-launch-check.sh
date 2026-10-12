#!/usr/bin/env bash
# pre-launch-check.sh — lightweight checks that catch runtime crashes
# without needing an emulator. Run this before every commit.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$PROJECT_DIR"

ERRORS=0

echo "=== Pre-Launch Checks ==="
echo ""

# 1. Build must succeed
echo "▶ Building debug APK..."
if ! ./gradlew assembleDebug --no-daemon --quiet 2>/dev/null; then
    echo "  ❌ Build failed"
    ERRORS=$((ERRORS + 1))
else
    echo "  ✅ Build OK"
fi

# 2. Build instrumented test APK (catches test compilation issues)
echo "▶ Building test APK..."
if ! ./gradlew assembleDebugAndroidTest --no-daemon --quiet 2>/dev/null; then
    echo "  ❌ Test APK build failed"
    ERRORS=$((ERRORS + 1))
else
    echo "  ✅ Test APK OK"
fi

# 3. Lint check (catches common issues)
echo "▶ Running lint..."
if ! ./gradlew lintDebug --no-daemon --quiet 2>/dev/null; then
    echo "  ⚠ Lint warnings (not blocking)"
else
    echo "  ✅ Lint OK"
fi

# 4. Static analysis: check for known anti-patterns
echo "▶ Scanning for known issues..."

# Check NavDisplay has SaveableStateHolder decorator
NAV_FILE="app/src/main/java/com/idontremember/personalcrm/ui/navigation/AppNavigation.kt"
if [ -f "$NAV_FILE" ]; then
    if grep -q "rememberViewModelStoreNavEntryDecorator" "$NAV_FILE" && \
       ! grep -q "rememberSaveableStateHolderNavEntryDecorator" "$NAV_FILE"; then
        echo "  ❌ NavDisplay uses ViewModelStore decorator without SaveableStateHolder — will crash at launch"
        ERRORS=$((ERRORS + 1))
    else
        echo "  ✅ Navigation decorators OK"
    fi
fi

# Check AndroidManifest has the application class
MANIFEST="app/src/main/AndroidManifest.xml"
if [ -f "$MANIFEST" ]; then
    if ! grep -q "android:name=" "$MANIFEST"; then
        echo "  ⚠ No android:name in manifest — Hilt may not initialize"
    fi
fi

echo ""
if [ "$ERRORS" -gt 0 ]; then
    echo "❌ $ERRORS issue(s) found. Fix before deploying."
    exit 1
else
    echo "✅ All checks passed."
    exit 0
fi