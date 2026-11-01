#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/android-sdk-env.sh"
mapfile -t PHONES < <(adb devices | awk 'NR > 1 && $2 == "device" && $1 !~ /^emulator-/ {print $1}')
if [[ ${#PHONES[@]} -ne 1 ]]; then
    echo "Connect exactly one authorized physical phone (found ${#PHONES[@]}). Emulators are ignored." >&2
    exit 1
fi
bash "$SCRIPT_DIR/gradle.sh" assembleDebug --no-daemon --console=plain
# Explicit serial: Gradle installDebug otherwise targets emulators too.
# Never uninstall on signature mismatch: that would erase the phone's app data.
if ! adb -s "${PHONES[0]}" install -r "$SCRIPT_DIR/../app/build/outputs/apk/debug/app-debug.apk"; then
    echo "Phone install failed. If Android reports incompatible signatures, rebuild using the environment/key that last installed the app. Do not uninstall unless you have backed up the app data; uninstalling erases it." >&2
    exit 1
fi
