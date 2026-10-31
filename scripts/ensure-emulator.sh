#!/usr/bin/env bash
# ensure-emulator.sh — start a headless emulator if one isn't already running.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/android-sdk-env.sh"
EMULATOR_NAME="${AVD_NAME:-pixel_6_api_34}"
BOOT_TIMEOUT=120
if [ ! -e /dev/kvm ]; then
    BOOT_TIMEOUT=480
fi

# A connected device can still be in the middle of its first boot.
EXISTING_EMULATOR=0
EMULATOR_SERIAL=$(adb devices | awk '$1 ~ /^emulator-[0-9]+$/ && $2 == "device" { print $1; exit }')
if [ -n "$EMULATOR_SERIAL" ]; then
    EXISTING_EMULATOR=1
    export ANDROID_SERIAL="$EMULATOR_SERIAL"
    echo "Emulator connected; checking boot completion"
fi

if [ "$EXISTING_EMULATOR" -eq 0 ]; then

# Look for available AVDs
AVDS=$(emulator -list-avds 2>/dev/null || true)
if [ -z "$AVDS" ]; then
    echo "No AVDs found. Creating a default one..."
    # Try to create a basic AVD
    if command -v sdkmanager &>/dev/null; then
        SYSTEM_IMAGE="system-images;android-34;google_apis;x86_64"
        echo "Installing Android 34 Google APIs x86_64 system image (accept its licenses if prompted)..."
        if ! sdkmanager --install "$SYSTEM_IMAGE"; then
            echo "ERROR: Android system image installation failed. Check SDK licenses and network access."
            exit 1
        fi
        echo "no" | avdmanager create avd -n "$EMULATOR_NAME" -k "$SYSTEM_IMAGE" --force || {
            echo "ERROR: Could not create AVD. Please create one manually."
            exit 1
        }
    else
        echo "ERROR: No AVDs found and Android SDK command-line tools are unavailable."
        echo "Set ANDROID_HOME or ANDROID_SDK_ROOT to your SDK directory, or add its cmdline-tools/latest/bin to PATH."
        echo "Then install the system image and create an AVD with:"
        echo "  sdkmanager --install 'system-images;android-34;google_apis;x86_64'"
        echo "  avdmanager create avd -n pixel_6_api_34 -k 'system-images;android-34;google_apis;x86_64'"
        exit 1
    fi
fi

# Use the first available AVD if the named one doesn't exist
if ! emulator -list-avds 2>/dev/null | grep -q "^${EMULATOR_NAME}$"; then
    EMULATOR_NAME=$(emulator -list-avds 2>/dev/null | head -1)
    echo "Using AVD: $EMULATOR_NAME"
fi

echo "Starting headless emulator: $EMULATOR_NAME ..."

# Docker Sandboxes commonly lack /dev/kvm. The emulator can still run with
# software acceleration, though first boot is slower.
ACCEL_ARGS=()
if [ ! -e /dev/kvm ]; then
    ACCEL_ARGS=(-accel off)
    echo "No /dev/kvm; using software emulation"
fi

# Launch fully detached from this script's stdio. A background emulator that
# inherits stdout/stderr keeps the caller's output pipe open forever, so the
# calling script/agent tool call appears to hang long after the work finished.
# Redirect all three stdio fds to a log file / /dev/null and disown the job.
RESULTS_DIR="$(cd "$SCRIPT_DIR/.." && pwd)/test-results"
mkdir -p "$RESULTS_DIR"
EMU_LOG="$RESULTS_DIR/emulator-$(date +%Y%m%d_%H%M%S).log"
echo "Emulator log: $EMU_LOG"
emulator -avd "$EMULATOR_NAME" \
    "${ACCEL_ARGS[@]}" \
    -no-window \
    -no-audio \
    -gpu swiftshader_indirect \
    -no-boot-anim \
    -no-snapshot \
    < /dev/null > "$EMU_LOG" 2>&1 &
EMU_PID=$!
disown "$EMU_PID" 2>/dev/null || true

fi

echo "Waiting for emulator to boot (timeout: ${BOOT_TIMEOUT}s)..."

# Select an emulator explicitly, even if a physical phone is also connected.
ELAPSED=0
while [ -z "$EMULATOR_SERIAL" ] && [ "$ELAPSED" -lt "$BOOT_TIMEOUT" ]; do
    EMULATOR_SERIAL=$(adb devices | awk '$1 ~ /^emulator-[0-9]+$/ && $2 == "device" { print $1; exit }')
    if [ -n "$EMULATOR_SERIAL" ]; then break; fi
    sleep 2
    ELAPSED=$((ELAPSED + 2))
done
if [ -z "$EMULATOR_SERIAL" ]; then
    echo "ERROR: No emulator appeared within ${BOOT_TIMEOUT}s"
    if [ "$EXISTING_EMULATOR" -eq 0 ]; then kill "$EMU_PID" 2>/dev/null || true; fi
    exit 1
fi
export ANDROID_SERIAL="$EMULATOR_SERIAL"

# Poll for boot_completed
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

echo "ERROR: Emulator failed to boot within ${BOOT_TIMEOUT}s"
if [ "$EXISTING_EMULATOR" -eq 0 ]; then kill "$EMU_PID" 2>/dev/null || true; fi
exit 1
