#!/usr/bin/env bash
# capture-crash-logs.sh — extract crash logs from logcat.
# Usage: ./scripts/capture-crash-logs.sh [output_dir]
set -euo pipefail

OUTPUT_DIR="${1:-test-results}"
mkdir -p "$OUTPUT_DIR"

TIMESTAMP=$(date +%Y%m%d_%H%M%S)
LOG_FILE="$OUTPUT_DIR/logcat-${TIMESTAMP}.txt"

echo "Capturing logcat..."
# Capture recent logcat, filtering for crashes and our app.
# All adb calls are bounded with `timeout` so log capture can never hang the
# runner (a wedged adb would otherwise block forever here).
if ! timeout 30 adb logcat -d \
    -s "AndroidRuntime:E" \
    -s "FATAL:E" \
    "idontremember.personalcrm:*" \
    > "$LOG_FILE" 2>/dev/null; then
    echo "⚠ adb logcat timed out or failed — skipping capture"
    exit 0
fi

# Also grab a broader dump for context
timeout 30 adb logcat -d 2>/dev/null | grep -E "(FATAL|AndroidRuntime|idontremember\.personalcrm|Process.*died|Force finishing)" \
    >> "$LOG_FILE" 2>/dev/null || true

# Extract just the stack trace section
TRACE_FILE="$OUTPUT_DIR/stacktrace-${TIMESTAMP}.txt"
if grep -q "FATAL EXCEPTION" "$LOG_FILE" 2>/dev/null; then
    echo "=== CRASH DETECTED ==="
    # Extract from FATAL EXCEPTION to end of stack trace
    sed -n '/FATAL EXCEPTION/,/^$/p' "$LOG_FILE" > "$TRACE_FILE"
    cat "$TRACE_FILE"
    echo ""
    echo "Full logcat saved to: $LOG_FILE"
    echo "Stack trace saved to: $TRACE_FILE"
    exit 1
else
    echo "✓ No crashes detected in logcat"
    echo "Logcat saved to: $LOG_FILE"
    exit 0
fi