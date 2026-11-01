# Host Android MCP runner

## Host setup or refresh

Prerequisites: Linux x86_64, Python 3.10+ with venv, Java, a logged-in `sbx`, and Android SDK **Command-line Tools (latest)** (install through Android Studio → SDK Manager → SDK Tools).

From this checkout on the **host**, find your sandbox name with `sbx ls`, then run:

```bash
bash scripts/host-mcp/install.sh --sandbox YOUR_SANDBOX_NAME
```

For a nonstandard SDK location, add `--sdk /absolute/path/to/sdk`.

This installs a trusted runner snapshot outside the shared checkout, generates machine-specific config, installs the Android image/emulator, creates the dedicated AVD if missing, checks acceleration, registers MCP if absent, and attaches it. Review Android licenses when prompted. Existing configuration and AVDs are preserved. No manual emulator startup is needed.

The same command refreshes an installation or attaches a new sandbox. Run with no test job active: setup stops only matching installed runner processes, reloads MCP, and refuses to interrupt persisted running jobs.

A sandbox restart alone does **not** reliably refresh host MCP processes. After setup, call health and confirm `avd=android_tests`, the runner PID and source hash. If tools need reconnection, reconnect Codex; do not keep restarting the emulator.

## Agent workflow

1. Discover the `android-tests` tools and call `health()`.
2. Run `mise run test-stage` inside the sandbox; use its printed hashes in `start_test`, optionally filtering to `com.idontremember.personalcrm.AppLaunchTest`.
3. Poll status and read logs through MCP. Run without a filter for the full suite. Report job ID, test count and outcome.

Do not restage while another agent/job uses the shared inbox. Host and sandbox must share `ignored/mcp-inbox`; private-clone setups need an explicitly shared inbox configured on the host.

If mise is unavailable, use the equivalent commands:

```bash
./gradlew assembleDebug assembleDebugAndroidTest --no-daemon --console=plain
python3 scripts/stage-mcp-apks.py
```

## Agent tool contract

MCP registration: `android-tests`. Installed code/config: `~/.local/share/android-tests-mcp`.

| Tool | Input and result |
| --- | --- |
| `health` | No arguments; reports version, process PID, loaded source hash, job storage, SDK availability and configured AVD. Does not prove emulator readiness. |
| `start_test` | `app_sha256`, `test_sha256`, optional `filter` (fully qualified class or class#method). Returns `job_id`. Omit filter for full suite. |
| `get_status` | `job_id`; returns phase and status: running, passed, failed, cancelled, or interrupted; failures include error text. |
| `read_logs` | `job_id`, byte `offset` (initially 0); returns text and next_offset, at most 32 KiB. Read until offset stops advancing, then check again after completion. |
| `cancel` | `job_id`; requests cancellation. Cleanup may take up to 45 seconds; a busy response means wait before submitting again. |

Phases: starting → emulator → install → tests → finished. Instrumentation is limited to 180 seconds; whole job to 15 minutes plus bounded cleanup. Successful completion requires a nonzero test count and an instrumentation OK marker. Record job ID, count and outcome in validation reports. Logs currently flush after each command completes, not continuously. A terminal result can appear before final logcat cleanup finishes; drain logs again after cleanup before diagnosing a failure.

## Troubleshooting and boundaries

- Machine configuration: `~/.local/share/android-tests-mcp/config.json`; job logs: its `state` directory. Setup preserves existing values. For a moved checkout, update `inbox` before rerunning setup.
- No tools: inspect `sbx mcp inspect android-tests`, confirm the sandbox name, and reconnect Codex. An existing registration is preserved; it must point to the installed runner and config under `~/.local/share/android-tests-mcp`.
- No SDK tools: install Command-line Tools (latest) in Android Studio; rerun with `--sdk`. Setup does not install Android Studio, Java, or sbx itself.
- Acceleration failure: address host virtualization/KVM access before retrying. Never substitute a physical phone.
- Port 5580 occupied: setup fails and names the conflicting AVD. Close its host window, or use the exact ADB stop command printed by setup, then retry. Renaming a saved AVD does not stop its running process. Setup never kills unrelated emulators.
- Graphics: automatic startup uses SwiftShader, verified by the user with passing tests. Occasional color-buffer warnings remained without preventing tests.
- Hash errors: check inbox sharing and restage. `manifest.json` records staged hashes; private host copies are verified before installation.

Host code never executes checkout scripts or Gradle. APKs run in a dedicated emulator without personal data/accounts. Keep the installed runner and job state outside sandbox-shared paths. No custom HTTP listener or network rule is needed: sbx's host MCP gateway handles transport.

Verified by the user: MCP emulator execution, failure diagnosis, and passing reruns with SwiftShader. Revalidate a focused test and full suite after refresh. A deliberate failing assertion/repair is useful when changing runner result handling, not during ordinary setup.

