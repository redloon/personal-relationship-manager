#!/usr/bin/env bash
# ensure-emulator.sh — start a headless emulator if one isn't already running.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
EMULATOR_NAME="${AVD_NAME:-pixel_6_api_34}"
BOOT_TIMEOUT=120

# Check if any emulator is already connected
if adb devices | grep -q 'emulator.*device$'; then
    echo "✓ Emulator already running"
    adb devices | grep 'emulator'
    exit 0
fi

# Look for available AVDs
AVDS=$(emulator -list-avds 2>/dev/null || true)
if [ -z "$AVDS" ]; then
    echo "No AVDs found. Creating a default one..."
    # Try to create a basic AVD
    if command -v sdkmanager &>/dev/null; then
        sdkmanager --install "system-images;android-34;google_apis;x86_64" 2>/dev/null || true
        echo "no" | avdmanager create avd -n "$EMULATOR_NAME" -k "system-images;android-34;google_apis;x86_64" --force 2>/dev/null || {
            echo "ERROR: Could not create AVD. Please create one manually."
            exit 1
        }
    else
        echo "ERROR: No AVDs found and sdkmanager not available."
        echo "Please create an AVD: avdmanager create avd -n pixel_6_api_34 -k 'system-images;android-34;google_apis;x86_64'"
        exit 1
    fi
fi

# Use the first available AVD if the named one doesn't exist
if ! emulator -list-avds 2>/dev/null | grep -q "^${EMULATOR_NAME}$"; then
    EMULATOR_NAME=$(emulator -list-avds 2>/dev/null | head -1)
    echo "Using AVD: $EMULATOR_NAME"
fi

echo "Starting headless emulator: $EMULATOR_NAME ..."

# Launch fully detached from this script's stdio. A background emulator that
# inherits stdout/stderr keeps the caller's output pipe open forever, so the
# calling script/agent tool call appears to hang long after the work finished.
# Redirect all three stdio fds to a log file / /dev/null and disown the job.
RESULTS_DIR="$(cd "$SCRIPT_DIR/.." && pwd)/test-results"
mkdir -p "$RESULTS_DIR"
EMU_LOG="$RESULTS_DIR/emulator-$(date +%Y%m%d_%H%M%S).log"
echo "Emulator log: $EMU_LOG"
emulator -avd "$EMULATOR_NAME" \
    -no-window \
    -no-audio \
    -gpu swiftshader_indirect \
    -no-boot-anim \
    -no-snapshot \
    < /dev/null > "$EMU_LOG" 2>&1 &
EMU_PID=$!
disown "$EMU_PID" 2>/dev/null || true

echo "Waiting for emulator to boot (timeout: ${BOOT_TIMEOUT}s)..."

# Wait for the device to appear (bounded — plain `adb wait-for-device` blocks
# forever if the emulator dies during startup)
if ! timeout "$BOOT_TIMEOUT" adb wait-for-device; then
    echo "ERROR: No device appeared within ${BOOT_TIMEOUT}s (see $EMU_LOG)"
    kill $EMU_PID 2>/dev/null || true
    exit 1
fi

# Poll for boot_completed
ELAPSED=0
while [ "$ELAPSED" -lt "$BOOT_TIMEOUT" ]; do
    BOOT=$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)
    if [ "$BOOT" = "1" ]; then
        echo "✓ Emulator booted successfully (took ${ELAPSED}s)"
        # Disable animations for reliable UI tests
        adb shell settings put global window_animation_scale 0.0
        adb shell settings put global transition_animation_scale 0.0
        adb shell settings put global animator_duration_scale 0.0
        exit 0
    fi
    sleep 2
    ELAPSED=$((ELAPSED + 2))
    echo "  ... still booting (${ELAPSED}s)"
done

echo "ERROR: Emulator failed to boot within ${BOOT_TIMEOUT}s (see $EMU_LOG)"
kill $EMU_PID 2>/dev/null || true
exit 1