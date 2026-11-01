#!/usr/bin/env bash
# Resolve the SDK for this environment, without storing a shared sdk.dir.
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/android-sdk-env.sh"
cd "$SCRIPT_DIR/.."
exec ./gradlew "$@"
