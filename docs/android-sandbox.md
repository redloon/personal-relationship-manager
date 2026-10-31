# Android work in Docker Sandbox

This records the setup attempted in the October 2026 sandbox. The host's Android setup is separate: `local.properties` initially pointed to `/home/user/Android/Sdk`, which was absent inside the sandbox.

## Toolchain installed here

- JDK 21: `sudo apt-get install -y openjdk-21-jdk-headless`. The image had JDK 25, while Gradle requests 21; Gradle's Foojay download returned HTTP 403.
- Android command line tools: `commandlinetools-linux-13114758_latest.zip` from `dl.google.com/android/repository/`, unpacked under `/home/agent/android-sdk/cmdline-tools/latest`.
- SDK packages: `platform-tools`, `platforms;android-36`, `build-tools;36.0.0`, `emulator`, `system-images;android-34;google_apis;x86_64`. Accept SDK licenses with `sdkmanager --licenses`.
- Emulator runtime libraries: `libx11-6` and `libx11-xcb1` from apt. Without these, the emulator binary failed to load or stopped during startup.
- AVD: `printf 'no\n' | avdmanager create avd -n pixel_6_api_34 -k 'system-images;android-34;google_apis;x86_64' --device pixel_6 --force`.

Set the ignored `local.properties` to `sdk.dir=/home/agent/android-sdk` in this sandbox. For commands in a fresh shell:

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export ANDROID_HOME=/home/agent/android-sdk
export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"
./gradlew testDebugUnitTest assembleDebugAndroidTest
./scripts/run-tests.sh
./scripts/pre-launch-check.sh
```

The sandbox had no `/dev/kvm`; `emulator -accel-check` reported acceleration unavailable. `scripts/ensure-emulator.sh` uses `-accel off` when `/dev/kvm` is missing and allows more boot time. A reduced AVD (480×800, 1 CPU, 1 GB RAM, 2 GB data, SwiftShader) launched, but after 148 seconds `adb` still showed `emulator-5554` as `offline` and `sys.boot_completed` could not be read. Instrumented execution is therefore unverified here. If launching manually, detach all three standard streams so the command returns:

```bash
emulator -avd pixel_6_api_34 -accel off -no-window -no-audio -gpu swiftshader_indirect -no-boot-anim -no-snapshot \
  </dev/null >test-results/emulator-sandbox.log 2>&1 &
disown
```

Check that `adb devices` reports the emulator as `device` and `adb shell getprop sys.boot_completed` returns `1` before instrumented tests. A software-emulated first boot may be slow. If this sandbox cannot run the emulator reliably, use the host's working Android setup for `./scripts/run-tests.sh`; do not treat a successful APK build as an instrumented test pass.

## Host result and runner behavior

The host has an SDK at `~/Android/Sdk`. The full instrumented suite completed successfully there on 2026-10-31, including backup/restore, Quick Add, event linking, and Settings tests. The Settings selector test now scrolls controls into view before tapping, which matters on the host emulator's compact app surface.

On a host shell where `sdkmanager` is missing from `PATH`, the runner now derives the SDK path from `ANDROID_HOME`, `ANDROID_SDK_ROOT`, `local.properties`, the `emulator` executable, or `~/Android/Sdk`, then adds command-line tools to `PATH`. Keep the ignored `local.properties` machine-specific; its SDK path in the sandbox is not a host path.

The runner writes each timed command's output to `test-results/` and tails the file for live progress. This avoids keeping the build alive through a `tee` pipe if a child inherits stdout. Ctrl+C cleanup and timeout behavior were checked with short process probes. When diagnosing failures, inspect the matching `build-*.log`, `install-*.log`, or `test-output-*.txt` file.

## Make the next sandbox faster

The reusable setup is JDK 21, Android command line tools, the exact SDK packages above, emulator runtime libraries, and a writable Android SDK and Gradle cache. Docker Sandbox **kits** can package tools and runtime settings; a small Android tool mixin or kit set is a candidate once these versions and commands are proven. Keep the SDK path inside the sandbox stable, or generate the ignored `local.properties` on startup. Cache SDK downloads and Gradle artifacts across sessions if kit data persistence is available.

Host acceleration is a separate decision: verify that the Docker Sandbox runtime can expose `/dev/kvm` before changing host settings. If it cannot, keep software emulation or run instrumented tests on the host. The Docker CLI/socket being present inside a sandbox does not itself expose KVM.
