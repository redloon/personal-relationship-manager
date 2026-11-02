# Batched Android QA implementation checkpoint

## Implemented

- Runner v4: exclusive interactive sessions, asynchronous action batches,
  accessibility snapshots, expiring references, explicit assertions and bounded
  waits. No Swift runtime or persistent test instrumentation dependency.
- Binary screenshots/MCP images, bounded recordings, retained artifact manifests,
  verified export and Markdown review packs (`bash .mise/tasks/qa-report`).
- Compose resource IDs across app entrypoints/sheets/dialogs; reminder control
  reuses the shared labeled switch row. Four external-accessibility tests added.
- Runner installation copies/fingerprints every runtime module. Tooling checks
  include host-runner tests. Reusable Android worker documents exploratory QA.
- Host-only trial cleanup helper defaults to dry run and retains old evidence.

## Validation so far

- 39 tooling tests pass.
- 60 SDK-free host-runner tests pass with MCP dependencies installed, including
  actual stdio protocol testing against a fake SDK. Without those dependencies,
  the protocol test explicitly skips; remaining host tests run normally.
- `check`: 43 JVM tests, both APKs, lint and source guards pass. An incremental
  lint FIR crash was resolved by a fresh single-worker/no-configuration-cache
  invocation; no lint gate was disabled.
- External accessibility focused job `4f03042ecefc4b32898324f68fa8cbae` passed
  four tests; all log chunks consumed.
- Initial integrated full job `63cedf9b94254fd3976a42aa5ee4af2a` ran 94 tests
  with one tag-picker interaction failure. Shared picker helpers now scroll and
  wait for the picker before typing. Focused picker rerun
  `117b22ecd47541269fe02155145690b5` passed one test. Final full rerun
  `8ce85c3c778b4696a9241af243b9dbc5` passed **94 tests**. All log chunks read.
- Final APK hashes: app
  `0b08fdf884fc6aa461ed12b71a79de3c9bb4b9c68f494a0c7856664d8501cc43`, test
  `7426b9d10c66a439d2f5b3a0f548cf91870ba0e54ec52ffd99d685d77fd362ed`.
- Temporary implementation worktrees have been removed after integrating their
  deliverables. No new changes have been committed.

## Real interactive QA (2026-11-02)

Host v4 refresh confirmed with matching base fingerprint. Session
`9ecdcaedfe89442a974a33e1af0802af` passed through terminal cleanup. Real batches
covered person/note creation, recall, history, explicit follow-up completion,
safe batch failure, stale refs, Unicode rejection, and batch cancellation.
Screenshots arrived as MCP images. The 16-step clean demo passed and exported a
fully decoded **25.44-second video**. The qa-report task verified actual artifact
hashes and generated `ignored/android-qa-validation/review.md`. Session-end
recording also finalized and fully decoded (1.23 seconds). Detailed evidence is
in `ignored/android-qa-validation/report.md`.

Live QA found a driver false clipping rejection for a clearly visible Compose
footer. A visually checked coordinate tap proved the app Save worked. Regression
failed before the local fix; the driver now clips only known scroll viewports.
39 tooling tests and 62 host checks pass (protocol optional in base Python); real
stdio protocol test separately passes with dependencies installed.

Second refresh confirmed the fixed fingerprint
`3c439642b5b83cf3a023307f1bef8fef0847cf1f35bf32fca624e4920d0491d8`.
Patched session `136116d2624b4ac295b5993adf41e2bb` ended passed. Its 17-step
batch `8e8d3d2bfb5c4d86b62c56b21eac0ad1` saved a follow-up via stable ID,
without coordinate fallback, and asserted the resulting persisted text. All
session logs consumed. Focused runner job `b9bd859464d442af9f9fa475b4df2cd9`
passed four accessibility tests; all logs consumed.

Final full suite `3223034f07c44987b2733693ee3dd02b` passed **94 tests**
on the corrected installed runner; terminal cleanup finished and all logs read.

## Complete

Host cleanup dry run and apply succeeded. `ignored/android-qa-validation/cleanup.log`
confirms all named trial toolchain/CLI targets were removed and original trial
report/media retained. Runner v4 remains healthy and idle after cleanup; the new
demo and review pack remain accessible. No required implementation or runtime QA
work remains. Changes remain uncommitted.
