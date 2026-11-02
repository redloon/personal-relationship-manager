# Host handoff: refresh and validate batched Android QA

Implementation is in the shared checkout. The sandbox can build APKs and drive
MCP, but cannot execute host commands. Do not implement a second runner.

## Refresh (host agent)

Live v4 QA exposed a false clipping rejection for a visible Compose sheet footer.
The Python patch now clips descendants only at known scrollable viewports, not
all virtual accessibility ancestors. Two regressions pass, and a second idle
refresh is needed to verify the corrected selector on the emulator.


1. Read `scripts/host-mcp/README.md`. Coordinate with the sandbox integration
   owner; do not refresh while a test/session is active. No host app builds.
2. In the host checkout, run:

   ```bash
   bash scripts/host-mcp/install.sh --refresh-only --sandbox codex-personal-crm
   ```

   Verify the sandbox name using `sbx ls` first if it differs.
3. Reconnect the host MCP client if used. Confirm gateway health reports
   `version=4`, AVD `android_tests`, and the source hash in the installed
   `runner.sha256`. Tell the sandbox agent refresh is complete so it can run
   focused/full Android tests and the new interactive flow through the gateway.

Expected installed v4 source hash for this implementation:
`3c439642b5b83cf3a023307f1bef8fef0847cf1f35bf32fca624e4920d0491d8`.

The sandbox integration owner stages fresh APKs and owns all emulator jobs.
Do not run a competing direct-ADB session or restage its inbox.

## If sandbox is unavailable: host-side runtime validation

Use the installed MCP tools through a host client, holding sessions through the
runner (not a separate lock-holder). Use the current staged manifest hashes. Current validated app SHA-256 is
`0b08fdf884fc6aa461ed12b71a79de3c9bb4b9c68f494a0c7856664d8501cc43`;
current test APK is
`7426b9d10c66a439d2f5b3a0f548cf91870ba0e54ec52ffd99d685d77fd362ed`.
Re-read the inbox manifest in case the integration owner has staged a newer pair.

- Start session, poll ready, inspect: screenshot must be image content and tree
  must include `dashboard_search` as a resource ID.
- Submit batches of stable-selector taps, whole-string ASCII input, assertions,
  waits and screenshot checkpoints. Poll batch status; inspect final screenshot.
- Create fictional person/note; recall by note text; open person history; create
  and complete an explicit follow-up. Check keyboard-open scrolling and Back.
- Verify a stale reference is refused after an action, a deliberately failed
  assertion stops later mutations, and Unicode is rejected before any batch step.
- Start a recording, navigate, stop it, verify decodable MP4 and shared path.
  Repeat with session end while recording and verify cleanup/artifact retention.
- Verify a second MCP connection cannot start tests/sessions while session owns
  emulator; after end, terminal status and health must show cleanup completed.
- Run `bash .mise/tasks/qa-report OUTBOX/SESSION/manifest.json --evidence BATCH.json`
  in the sandbox or shared checkout. Preserve report and exact hashes/job IDs.

Do not call a successful shell tap a verified UI outcome. Mutations explicitly
return `verified:false`; follow with assertions or visual inspection.

## Cleanup only after replacement runtime validation passes

On the host, review the dry run then execute the bounded cleanup helper:

```bash
python3 scripts/host-mcp/cleanup-trial.py
python3 scripts/host-mcp/cleanup-trial.py --apply
```

It acquires the runner lock, verifies the dedicated emulator, removes only named
trial Swift/tool files, per-device CLI state and device recordings, and retains
`ignored/agent-android-trial/` evidence. It refuses symlinks, unexpected runtime
folders and state belonging to another app. It never signals historical PIDs.

Record results in `ignored/android-qa-validation/report.md`, including cleanup
output. Keep host runtime validation distinct from local fake-SDK protocol tests.
