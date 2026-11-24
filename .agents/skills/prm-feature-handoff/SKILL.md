---
name: prm-feature-handoff
description: Finish and hand off PRM Android feature work with a coherent commit, visual QA artifacts, and Android MCP validation. Use when completing or committing an Android feature in this repository; use prm-release for releases.
---

# PRM Feature Handoff

Treat `AGENTS.md`, `docs/development.md`, and `scripts/host-mcp/README.md` as the
sources of truth. Preserve unrelated work and use `bash .mise/tasks/TASK` when
`mise` is unavailable.

1. Inspect the working tree and isolate the feature from unrelated changes.
2. Run `mise run check`. If committing is in scope, commit the coherent feature
   before visual QA; never include unrelated files. Restore any exact-path
   temporary stash immediately after the commit.
3. Stage that commit with `mise run test-stage`. Confirm the staged provenance
   names the intended Git revision.
4. For user-visible changes, use one Android MCP interactive session to assert
   and capture the important changed states. Inspect every selected capture,
   end the session through cleanup, and generate a `qa-report` review pack.
5. If visual review finds a defect, add a regression test, fix it, rerun the
   required checks, commit the correction, and regenerate evidence from the new
   commit.
6. Run focused instrumented tests while iterating, then one full Android MCP
   suite against the final commit. Poll through cleanup and consume every log
   chunk using integer offsets. Leave the runner idle.

Report the commit hash, clickable QA report, `check` result, final MCP job ID and
test count, APK hashes, and any unrelated dirty files left untouched. Do not
claim visual or runtime validation from a build alone.
