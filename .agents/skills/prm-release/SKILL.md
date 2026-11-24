---
name: prm-release
description: Build, validate, tag, and publish PRM Android releases using the repository's sandbox, signing, Android MCP, and host handoff workflow. Use for release preparation, release failures, signed APK validation, tagging, or GitHub publication in this repository.
---

# PRM Release

Own the release workflow for the user. Read `docs/releases.md` first and treat it,
`.mise/tasks/`, and `scripts/releases.py` as the sources of truth. Do not duplicate
their implementation in the skill.

## Resume intelligently

Inspect the current version, Git status/history/remotes/tags, signing configuration,
release bundle, and MCP job evidence. Continue from the first incomplete gate; do
not ask the user to repeat completed work or rebuild an existing read-only bundle.

Use `bash .mise/tasks/TASK` when `mise` is unavailable.

## Execution boundaries

Perform all work available inside the sandbox, including repository edits, checks,
commits requested by the user, release builds, APK staging, MCP execution, complete
log consumption, screenshot review, and local annotated tag creation.

Interrupt the user only for an action that requires the host or a secret unavailable
to the sandbox:

- creating or recovering the authoritative host signing key;
- securely copying that key into a new persistent sandbox;
- SSH pushes from the host;
- browser-only GitHub publication or downloaded-asset verification.

Give one short, copy-safe host command block for the exact remaining action. Never
request, print, commit, or place signing passwords or private keys in the checkout.

The repository remote uses HTTPS for fetch and SSH for push. Release builds fetch in
the sandbox; pushes run on the host. Do not replace this split configuration with a
single SSH or HTTPS URL.

## Release invariants

- Keep application ID `com.redloon.prm`, key alias `prm`, and the release signing
  certificate stable.
- Require a clean committed source snapshot and successful release verification.
- Preserve the exported read-only bundle; never overwrite or silently regenerate it.
- Validate and publish the APK hash recorded in `manifest.json`.
- Point the release tag at the manifest's `git_revision`, not necessarily current
  `HEAD`; post-release documentation commits may be newer.
- Push the release commit before its tag. Never publish a tag or GitHub release until
  signed-APK recovery validation passes.
- Do not install debug and release APKs interchangeably on a real phone; their
  signatures differ. The phone dogfood workflow remains debug-signed unless the user
  explicitly chooses a backed-up migration to release signing.

## Validation evidence

For the signed APK, stage the existing bundle with `test-stage-app`, then use Android
MCP recovery testing. Poll through cleanup, consume every log chunk using integer
offsets without dumping large logs into conversation, and inspect all retained
screenshots. Require all three cases—saved, edited, and deleted—to pass with distinct
before/after PIDs and no cleanup errors.

Report the job ID, test count, APK SHA-256, manifest commit, case results, cleanup,
and screenshot review. Keep build success distinct from runtime validation.

## Failure handling

Diagnose the first real failure and fix the workflow rather than repeatedly asking
the user to rerun commands. Preserve user changes and credentials.

If Gradle cannot delete package or test outputs on the shared workspace filesystem,
stop Gradle and atomically move `app/build` into `ignored/stale-builds/` before
retrying. Keep the move inside the repository mount; moving to `/tmp` or `..` crosses
filesystems and can leave a partial build tree.

Stop once the exact remaining action is host-only, publication is complete, or a
real safety decision requires the user.
