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

For code-only updates to an existing installation, avoid SDK provisioning:

```bash
bash scripts/host-mcp/install.sh --refresh-only
# Add --sandbox NAME to also reload its gateway connection.
```

Refresh stops idle installed runners, including host Codex connections. Reconnect MCP or restart/resume Codex afterward. Confirm health `version=3` and `source_sha256` matches `~/.local/share/android-tests-mcp/runner.sha256`.

## Agent workflow

1. Discover the `android-tests` tools and call `health()`. Check `sdk_available`, `avd=android_tests`, the configured inbox, and `busy=false`. Health confirms the connection/configuration, not emulator readiness.
2. Run `mise run test-stage` on the host or inside the sandbox; use its printed hashes in `start_test`, optionally filtering to `com.idontremember.personalcrm.AppLaunchTest`.
3. Poll status and read logs through MCP. Run without a filter for the full suite. Report job ID, test count and outcome.

Do not restage while another agent/job uses the shared inbox. Host and sandbox must share `ignored/mcp-inbox`; private-clone setups need an explicitly shared inbox configured on the host.

If mise is unavailable, use the equivalent commands:

```bash
bash scripts/gradle.sh assembleDebug assembleDebugAndroidTest --no-daemon --console=plain
python3 scripts/stage-mcp-apks.py
```

## Agent tool contract

MCP registration: `android-tests`. Installed code/config: `~/.local/share/android-tests-mcp`.

| Tool | Input and result |
| --- | --- |
| `health` | No arguments; reports version, PID, loaded source hash, job storage/inbox, missing SDK tools, configured AVD/serial, and whether any connection has a job active. Does not prove emulator readiness. |
| `start_test` | `app_sha256`, `test_sha256`, optional `filter` (fully qualified class or class#method). Returns `job_id`. Omit filter for full suite. |
| `get_status` | `job_id`; returns phase and status: running, passed, failed, cancelled, or interrupted; failures include command/error text; passes include `test_count`. Jobs can be polled after reconnecting. |
| `read_logs` | `job_id`, integer byte `offset` (initially 0); returns text and integer next_offset, at most 32 KiB. Pass that integer directly to the next call. Read until offset stops advancing, then check again after completion. |
| `cancel` | `job_id`; requests cancellation. Cleanup may take up to 45 seconds; a busy response means wait before submitting again. |

Phases: starting → emulator → install → tests → cleanup → finished. Instrumentation is limited to 180 seconds; whole job to 15 minutes plus bounded cleanup. Successful completion requires a nonzero test count and an instrumentation OK marker. Record job ID, count and outcome in validation reports. Logs currently flush after each command completes, not continuously. Terminal status is published after cleanup. A shared file lock prevents host and sandbox connections from running tests simultaneously; cancel through the connection that submitted the job.

Host and sandbox debug keys may differ. On `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, the runner rechecks emulator identity, logs a warning, removes only this app and its test package from the dedicated emulator, and retries once. Test app data is disposable and cleared each run anyway. Other install errors fail with the command and Android error. Physical-phone installs never perform this reset: keep using the same signing key/environment for the phone or back up its data before intentionally uninstalling.

## Troubleshooting and boundaries

- Machine configuration: `~/.local/share/android-tests-mcp/config.json`; job logs: its `state` directory. Setup preserves existing values. For a moved checkout, update `inbox` before rerunning setup.
- No tools: inspect `sbx mcp inspect android-tests`, confirm the sandbox name, and reconnect Codex. An existing registration is preserved; it must point to the installed runner and config under `~/.local/share/android-tests-mcp`.
- Host Codex has a separate registration from sbx: inspect it with `codex mcp get android-tests`. Both should use the same installed runner/config. If health hangs, check it in the main session before inferring a runner failure from a subagent timeout; record whether any test job was actually created.
- No SDK tools: install Command-line Tools (latest) in Android Studio; rerun with `--sdk`. Setup does not install Android Studio, Java, or sbx itself.
- Acceleration failure: address host virtualization/KVM access before retrying. Never substitute a physical phone.
- Port 5580 occupied: setup fails and names the conflicting AVD. Close its host window, or use the exact ADB stop command printed by setup, then retry. Renaming a saved AVD does not stop its running process. Setup never kills unrelated emulators.
- Graphics: automatic startup uses SwiftShader, verified by the user with passing tests. Occasional color-buffer warnings remained without preventing tests.
- Hash errors: check inbox sharing and restage. `manifest.json` records staged hashes; private host copies are verified before installation.

Host code never executes checkout scripts or Gradle. APKs run in a dedicated emulator without personal data/accounts. Keep the installed runner and job state outside sandbox-shared paths. No custom HTTP listener or network rule is needed: sbx's host MCP gateway handles transport.

Verified by the user: MCP emulator execution, failure diagnosis, and passing reruns with SwiftShader. Revalidate a focused test and full suite after refresh. A deliberate failing assertion/repair is useful when changing runner result handling, not during ordinary setup.

Runner v3 verified through the installed stdio MCP server: an earlier sandbox-signed APK triggered recovery and 3 Dashboard tests passed (`d929fb976df340ea89e2800499b4a803`); the full suite passed 66 tests (`f34c902c7657419fb92011dbb610f6f9`). A second connection was refused while jobs were active and could poll their status. Local probes: `python3 -m unittest discover -s scripts/host-mcp -p 'test_*.py'`.

