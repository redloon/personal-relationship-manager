#!/usr/bin/env bash
# run-tests.sh — build, install, run instrumented tests, and capture logs.
# This is the single command the agent invokes to validate the app.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
RESULTS_DIR="$PROJECT_DIR/test-results"
mkdir -p "$RESULTS_DIR"

# Parse arguments
FILTER="${1:-}"   # Optional: specific test class or method
TIMEOUT="${TEST_TIMEOUT:-300}"    # Instrumented-test timeout in seconds
BUILD_TIMEOUT="${BUILD_TIMEOUT:-900}"  # Per-gradle-step timeout in seconds
START_TIME=$SECONDS

echo "============================================"
echo "  Personal CRM — Automated Test Runner"
echo "============================================"
echo ""

# Step 1: Ensure emulator is running
echo "▶ Step 1/6: Ensuring emulator is available..."
if ! bash "$SCRIPT_DIR/ensure-emulator.sh"; then
    echo "FATAL: Could not start emulator"
    exit 1
fi

# Wait a moment for the emulator to fully settle
sleep 3

# Step 2: Clean previous build artifacts (optional, speeds up fresh builds)
echo ""
echo "▶ Step 2/6: Building debug APK..."
cd "$PROJECT_DIR"
# Every step is bounded by `timeout` so no single step can hang the runner:
# a wedged gradle/adb/emulator fails loudly instead of stalling forever.
if ! timeout "$BUILD_TIMEOUT" ./gradlew assembleDebug assembleDebugAndroidTest --no-daemon --quiet 2>&1 | tail -5; then
    echo "❌ Build failed (or timed out after ${BUILD_TIMEOUT}s)"
    exit 1
fi
echo "✓ Build complete ($((SECONDS - START_TIME))s elapsed)"

# Step 3: Install APKs
echo ""
echo "▶ Step 3/6: Installing APKs on emulator..."
if ! timeout "$BUILD_TIMEOUT" ./gradlew installDebug installDebugAndroidTest --no-daemon --quiet 2>&1 | tail -5; then
    echo "❌ Install failed (or timed out after ${BUILD_TIMEOUT}s)"
    exit 1
fi
echo "✓ APKs installed ($((SECONDS - START_TIME))s elapsed)"

# Start from a clean app state each run. NOTE: the `-e clearPackageData true`
# instrumentation arg used below is a no-op without the Android Test
# Orchestrator (not installed here), so we clear explicitly.
# pm clear also resets runtime permissions, so it must run before the grants.
adb shell pm clear com.idontremember.personalcrm 2>/dev/null || true

# Grant runtime permissions (factory-state AVDs have none granted; the system
# permission dialog otherwise covers the UI and compose tests time out).
for perm in READ_CONTACTS WRITE_CONTACTS; do
    adb shell pm grant com.idontremember.personalcrm android.permission.$perm 2>/dev/null || true
done

# Step 4: Clear logcat before test run
echo ""
echo "▶ Step 4/6: Clearing logcat..."
adb logcat -c 2>/dev/null || true

# Step 5: Run instrumented tests
echo ""
echo "▶ Step 5/6: Running instrumented tests..."

TEST_COMMAND="adb shell am instrument -w"
TEST_COMMAND="$TEST_COMMAND -e clearPackageData true"

if [ -n "$FILTER" ]; then
    TEST_COMMAND="$TEST_COMMAND -e class $FILTER"
fi

TEST_COMMAND="$TEST_COMMAND com.idontremember.personalcrm.test/com.idontremember.personalcrm.HiltTestRunner"

TIMESTAMP=$(date +%Y%m%d_%H%M%S)
TEST_OUTPUT="$RESULTS_DIR/test-output-${TIMESTAMP}.txt"

echo "  Command: $TEST_COMMAND"
echo "  Output:  $TEST_OUTPUT"
echo ""

# Run tests with timeout
set +e
timeout "$TIMEOUT" $TEST_COMMAND 2>&1 | tee "$TEST_OUTPUT"
TEST_EXIT=$?
set -e

# Parse results from output
PASS_COUNT=$(grep -c 'OK (' "$TEST_OUTPUT" 2>/dev/null || echo "0")
FAILURES=$(grep -c 'FAILURES' "$TEST_OUTPUT" 2>/dev/null || echo "0")
ERRORS=$(grep -E '(^FAILURES|^Tests run:.*Failures: [1-9]|ERROR)' "$TEST_OUTPUT" 2>/dev/null || true)

echo ""
echo "============================================"
echo "  Test Results"
echo "============================================"

if [ "$TEST_EXIT" -eq 124 ]; then
    echo "⚠ Tests TIMED OUT after ${TIMEOUT}s"
fi
if [ -n "$ERRORS" ] || [ "$TEST_EXIT" -ne 0 ]; then
    echo "❌ Some tests FAILED"
    echo ""

    # Step 6: Capture crash logs on failure
    echo "▶ Step 6/6: Capturing crash logs..."
    bash "$SCRIPT_DIR/capture-crash-logs.sh" "$RESULTS_DIR" || true

    echo ""
    echo "Wall time: $((SECONDS - START_TIME))s"
    echo "Test output: $TEST_OUTPUT"
    exit 1
else
    echo "✅ All tests PASSED"
    echo ""

    # Still capture logs for reference
    echo "▶ Step 6/6: Capturing logs for reference..."
    bash "$SCRIPT_DIR/capture-crash-logs.sh" "$RESULTS_DIR" || true

    echo ""
    echo "Wall time: $((SECONDS - START_TIME))s"
    echo "Test output: $TEST_OUTPUT"
    exit 0
fi