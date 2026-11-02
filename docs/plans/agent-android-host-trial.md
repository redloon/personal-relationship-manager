# Host-agent prompt: agent-android compatibility trial

> Evaluation completed; report and media are in `ignored/agent-android-trial/`.
> The implementation uses Python/ADB instead of a Swift runtime dependency.
> See `android-qa-host-validation.md` for replacement validation and deferred
> trial cleanup. The original prompt below is retained as historical context.

Evaluate agent-android for interactive QA of our Android app. Run the trial;
do not implement the full integration yet.

- Repository: `/home/user/work/personal-crm` (resolve its host location if different).
- Upstream: https://github.com/squaduplabs/agent-android
- Pinned revision: `bd51e5cded46519e2aaee8094df56d2f1b6b0859`

Read `AGENTS.md`, `docs/development.md`, and `scripts/host-mcp/README.md`.
Preserve all existing uncommitted work.

## Boundaries

- Use only dedicated emulator serial `emulator-5580`, AVD `android_tests`.
- Verify both identities before modifying emulator state.
- Never touch phones or other emulators.
- Coordinate with any agent repairing MCP.
- Do not refresh/reconfigure MCP or change app source during this trial.
- Build app APKs inside the sandbox, not on the host.
- You may build/install the pinned agent-android CLI outside the checkout.
- Record exact commands, versions, failures, and workarounds.

## 1. Establish exclusive access

Read `~/.local/share/android-tests-mcp/config.json`.
Acquire its state directory's `runner.lock` using the same exclusive `flock`
mechanism as the existing runner, and hold it throughout device operations.
If busy, do independent tool setup first; do not interrupt an active job.
Pass `--serial emulator-5580` explicitly to every device-scoped agent-android
command. Do not invoke the runner's test submission while holding its lock.

## 2. Install and verify the tool

Inspect upstream installation instructions and build the pinned revision.
Check Swift availability and report any installation requirements.
Run its unit tests and doctor command.
Do not automatically apply doctor suggestions that change emulator settings.

## 3. Launch our normal debug app

Package: `com.idontremember.personalcrm`.

Use the current installed debug app if its build identity can be established;
otherwise use the sandbox-staged APK, verify its manifest hash, and follow
the existing runner's package/signature safeguards. Coordinate staging with
the sandbox agent; do not build on the host or assume an old staged APK matches
current source. Record the APK hash used.

Use fictional data only. No contact imports or personal-data copies.

## 4. Exercise actual UI flows

Capture an initial snapshot and screenshot, then:

- Create a fictional person and capture a distinctive note.
- Find that note through universal search.
- Open the person's profile and inspect their history.
- Create and complete an explicit follow-up.
- Exercise keyboard-open scrolling, Back, and cancelled editing.
- Exercise a system permission dialog if available without source changes.

Prefer accessibility element references; document coordinate fallbacks.
Inspect screenshots, not just command exit codes.
Verify spaces, quotes, apostrophes, and literal `%s` type correctly.
Confirm Unicode input fails clearly rather than corrupting text.
Check whether existing Compose test tags appear in snapshots.
Test stale references after navigation without risking destructive actions.
Measure approximate snapshot/action latency and repeat one journey.

## 5. Prove artifact feasibility

Save screenshots under `ignored/agent-android-trial/` in the shared checkout.
Record a 30–60 second actual walkthrough using `adb screenrecord`.
Track the exact recorder process; stop gracefully and verify the MP4 plays.
Do not kill unrelated adb or screenrecord processes.
Confirm the files are visible from the sandbox; distinguish filesystem
visibility from successful image/video presentation through MCP.

## 6. Handoff

Create a concise report in `ignored/agent-android-trial/report.md` containing:

- Pinned revision, toolchain, emulator identity, and app identity.
- Pass/fail evidence for each capability, or a concrete reason it was blocked.
- Screenshot and video paths.
- Missing Compose identifiers and other compatibility problems.
- Any settings changed, restored afterward.
- Recommendation: adopt, adopt with small fixes, or reject.
- Minimum MCP adapter work still required.

Release the lock and clean up recording processes on every exit.
A successful trial is evidence-based, not merely a successful CLI build.
