#!/usr/bin/env bash
# Locate the Android SDK and add its command-line tools to PATH.
# Source this file from scripts that use adb, emulator, sdkmanager or avdmanager.

_SDK_SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
_SDK_PROJECT_DIR="$(cd "$_SDK_SCRIPT_DIR/.." && pwd)"
_SDK_CANDIDATES=()

[[ -n "${ANDROID_HOME:-}" ]] && _SDK_CANDIDATES+=("$ANDROID_HOME")
[[ -n "${ANDROID_SDK_ROOT:-}" ]] && _SDK_CANDIDATES+=("$ANDROID_SDK_ROOT")

if [[ -f "$_SDK_PROJECT_DIR/local.properties" ]]; then
    _SDK_LOCAL_DIR="$(sed -n 's/^[[:space:]]*sdk\.dir[[:space:]]*=[[:space:]]*//p' \
        "$_SDK_PROJECT_DIR/local.properties" | tail -n 1 | sed 's/\\:/:/g; s/\\\\/\\/g')"
    [[ -n "$_SDK_LOCAL_DIR" ]] && _SDK_CANDIDATES+=("$_SDK_LOCAL_DIR")
fi

# If emulator is already on PATH, derive its SDK root (often the only Android
# tool directory users have added to PATH).
if command -v emulator >/dev/null 2>&1; then
    _SDK_EMULATOR_PATH="$(command -v emulator)"
    [[ "$_SDK_EMULATOR_PATH" = */emulator/emulator ]] && \
        _SDK_CANDIDATES+=("${_SDK_EMULATOR_PATH%/emulator/emulator}")
fi

_SDK_CANDIDATES+=("$HOME/Android/Sdk" "$HOME/Android/sdk" "$HOME/android-sdk")
for _SDK_CANDIDATE in "${_SDK_CANDIDATES[@]}"; do
    if [[ -d "$_SDK_CANDIDATE" && ( -d "$_SDK_CANDIDATE/platform-tools" || -d "$_SDK_CANDIDATE/emulator" || -d "$_SDK_CANDIDATE/cmdline-tools" ) ]]; then
        ANDROID_HOME="$_SDK_CANDIDATE"
        ANDROID_SDK_ROOT="$_SDK_CANDIDATE"
        break
    fi
done

if [[ -n "${ANDROID_HOME:-}" && -d "$ANDROID_HOME" ]]; then
    export ANDROID_HOME ANDROID_SDK_ROOT
    PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH"
    if [[ -d "$ANDROID_HOME/cmdline-tools" ]]; then
        _SDK_CMDLINE_DIR="$(find "$ANDROID_HOME/cmdline-tools" -mindepth 1 -maxdepth 1 -type d \
            ! -name 'latest' -print 2>/dev/null | sort -V | tail -n 1)"
        [[ -x "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" ]] && \
            PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"
        [[ -n "$_SDK_CMDLINE_DIR" && -d "$_SDK_CMDLINE_DIR/bin" ]] && \
            PATH="$_SDK_CMDLINE_DIR/bin:$PATH"
    fi
    [[ -d "$ANDROID_HOME/tools/bin" ]] && PATH="$ANDROID_HOME/tools/bin:$PATH"
    export PATH
fi

unset _SDK_SCRIPT_DIR _SDK_PROJECT_DIR _SDK_CANDIDATES _SDK_LOCAL_DIR \
    _SDK_EMULATOR_PATH _SDK_CANDIDATE _SDK_CMDLINE_DIR
