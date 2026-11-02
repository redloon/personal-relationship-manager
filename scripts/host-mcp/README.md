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
- Tools listed but calls return `client is closing: EOF` or `connection closed`: the gateway may retain a dead attachment, even while host MCP health works. From the host, identify the sandbox with `sbx ls`, then run `sbx mcp load android-tests --sandbox NAME`. This reloads the attachment without restarting the feature agent or rebuilding/restaging APKs. Coordinate with the test owner first if a job might be active. Retry gateway health and confirm runner version/hash, AVD, and busy state. If submission previously lost its response, recover the job ID/status before submitting again; do not assume it never started. Reconnect Codex only if the live reload does not restore its connection. Avoid repeated reloads or a runner refresh for this transport error alone.

  If the sandbox agent cannot access the host, emit this warning (substitute a verified sandbox name; otherwise leave `NAME` and include `sbx ls`):

  ```text
  WARNING: Android instrumented tests are blocked: MCP gateway calls fail with <exact error>.
  On the host, run: sbx mcp load android-tests --sandbox NAME
  Find NAME with: sbx ls
  Coordinate with the test owner if a job may be active. After reload, I will retry health and recover any existing job before submitting tests.
  ```

  Continue independent implementation/JVM checks while waiting; report instrumented validation as blocked until actual tests complete. Never report a build or successful health check as a test pass.
- No SDK tools: install Command-line Tools (latest) in Android Studio; rerun with `--sdk`. Setup does not install Android Studio, Java, or sbx itself.
- Acceleration failure: address host virtualization/KVM access before retrying. Never substitute a physical phone.
- Port 5580 occupied: setup fails and names the conflicting AVD. Close its host window, or use the exact ADB stop command printed by setup, then retry. Renaming a saved AVD does not stop its running process. Setup never kills unrelated emulators.
- Graphics: automatic startup uses SwiftShader, verified by the user with passing tests. Occasional color-buffer warnings remained without preventing tests.
- Hash errors: check inbox sharing and restage. `manifest.json` records staged hashes; private host copies are verified before installation.

Host code never executes checkout scripts or Gradle. APKs run in a dedicated emulator without personal data/accounts. Keep the installed runner and job state outside sandbox-shared paths. No custom HTTP listener or network rule is needed: sbx's host MCP gateway handles transport.

Verified by the user: MCP emulator execution, failure diagnosis, and passing reruns with SwiftShader. Revalidate a focused test and full suite after refresh. A deliberate failing assertion/repair is useful when changing runner result handling, not during ordinary setup.

Runner v3 verified through the installed stdio MCP server: an earlier sandbox-signed APK triggered recovery and 3 Dashboard tests passed (`d929fb976df340ea89e2800499b4a803`); the full suite passed 66 tests (`f34c902c7657419fb92011dbb610f6f9`). A second connection was refused while jobs were active and could poll their status. Local probes: `python3 -m unittest discover -s scripts/host-mcp -p 'test_*.py'`.


## Interactive QA (runner v4)

Runner v4 adds a Python/ADB backend; Swift and agent-android are not runtime
requirements. APKs are still built in the sandbox. Refresh the installed snapshot
while idle, reconnect tools, and confirm health version 4 and its full-module
source fingerprint. The older v3 evidence above describes historical test runs.

1. Run `check`, call health, and stage the APK pair while the runner is idle.
2. Call `start_session(app_sha256, test_sha256)` and poll its `job_id` until
   `phase=ready`. This clears disposable app data and launches the normal app.
3. Call `inspect(session_id)` for compact accessibility JSON plus a screenshot.
4. Submit predictable actions together with `execute_steps(session_id, steps)`.
   It returns a batch `job_id`; poll status and read logs through completion.
   Inspect the returned final snapshot and use `get_artifact` for its screenshot.
5. Call `end_session`, then poll through terminal cleanup. A second connection
   cannot operate the emulator while the owning process holds its shared lock.

| Tool | Contract |
| --- | --- |
| `start_session` | Verified APK hashes; returns `session_id` and `job_id`. |
| `inspect` | `session_id`; returns snapshot and MCP image content. |
| `execute_steps` | `session_id`, list of 1–20 steps; asynchronous, two-minute batch deadline, stops on first failure. |
| `start_recording` | `session_id`; returns `recording_id`, maximum 120 seconds. |
| `stop_recording` | `session_id`, `recording_id`; finalizes MP4 and returns metadata/shared path. |
| `list_artifacts` | `job_id` (session, batch, or test); returns retained capture metadata. |
| `get_artifact` | `job_id`, `artifact_id`; returns PNG image content or MP4 metadata/shared path. |
| `end_session` | `session_id`; requests cleanup. Poll `get_status` until terminal. |

Example batch:

```json
[
  {"action":"tap","target":{"id":"dashboard_search"}},
  {"action":"type","text":"Lantern"},
  {"action":"wait_for","target":{"text":"Note · Mira Trial"},"timeout":10},
  {"action":"screenshot","label":"Search results"}
]
```

Actions: `tap`, `long_press`, `swipe`, `type`, `key`, `wait_for`, `assert`,
`screenshot`. Exact targets combine `id`, `text`, and/or `description`; ambiguous
matches fail. `target:{"ref":"e3"}` or `target:{"x":100,"y":200}` requires the
current top-level `snapshot_id`. Every input action invalidates refs/coordinates;
resolve stable selectors again instead of reusing old reference numbers. A new
inspection also invalidates earlier refs, even if that inspection fails.

`type` appends a whole printable-ASCII string, optionally tapping `target` first;
it does not replace existing text. Unicode/newlines are rejected before any
batch action. `key` accepts back, enter/return, home, recents, delete, tab, escape
and a bounded `times` count. Swipe accepts `direction` or explicit `from`/`to`
coordinate objects with snapshot ID. Waits/assertions accept `exists:false`;
wait timeout is at most ten seconds. Mutations return `verified:false` because
an injected tap does not prove navigation/save succeeded. Follow important
mutations with assertions or visual inspection. Known clipping and exposed
keyboard bounds are checked; occlusion detection is best effort.

Only one operation may be pending per session. Failed batches remain failed,
retain partial results and a final screenshot when possible, and never replay
mutations automatically. Cancelling a batch leaves its session available;
cancelling/ending the session closes it. Sessions expire after ten idle minutes
or 45 minutes total. Transport reconnects can recover durable statuses/artifacts;
continued actions require the same owning runner process. `operation_pending`
in session status identifies a command that outlasted its tool-call wait.

Captures live in private job state and are atomically exported to the sibling
`ignored/mcp-artifacts/SESSION_ID/` folder by default (`outbox` config overrides
it). Images return through MCP; video links use the shared filesystem, not inline
video bytes. `manifest.json` records hashes, APK provenance and timestamps. Test
failures get a best-effort screenshot without replacing the original failure.
End/cancel/expiry attempts to finalize recording and reports cleanup errors.

Generate a review pack without Mise:

```bash
bash .mise/tasks/qa-report ignored/mcp-artifacts/SESSION_ID/manifest.json \
  --evidence ignored/my-batch-status.json
```

The evidence file is a saved batch status JSON object. The task verifies artifact
hashes and emits `review.md` with image/video links and validation limitations.
Keep review artifacts for human review; no background deletion is performed.

For the first host refresh and removal of the old Swift trial, follow
[`docs/plans/android-qa-host-validation.md`](../../docs/plans/android-qa-host-validation.md).
Cleanup is deferred until replacement runtime validation succeeds. Preserve the
trial report/media; never remove shared Swift caches or unrelated device state.

### Live v4 validation (2026-11-02)

Verified through the actual sandbox gateway on `android_tests` / `emulator-5580`:
batched capture/recall/profile/follow-up journeys, MCP screenshot images, safe
failure/cancellation, stale-reference and Unicode rejection, explicit MP4 stop,
and recording finalization on session end. The clean 16-step demo fully decoded
(25.44 seconds, 402 frames); review pack and decode evidence are retained in
`ignored/android-qa-validation/`.

A real Compose sheet exposed that virtual accessibility ancestor bounds do not
necessarily clip drawn children. The driver now limits descendant clipping to
known scrollable viewports, retaining screen/disabled/keyboard guards. A new
17-step batch saved a follow-up by stable ID without coordinate fallback on the
corrected installed fingerprint
`3c439642b5b83cf3a023307f1bef8fef0847cf1f35bf32fca624e4920d0491d8`.
Focused job `b9bd859464d442af9f9fa475b4df2cd9` passed four tests; full job
`3223034f07c44987b2733693ee3dd02b` passed **94 tests**. Terminal cleanup and all
log chunks were consumed. Keep these instrumented results distinct from the
interactive demo and the SDK-free stdio protocol test.
