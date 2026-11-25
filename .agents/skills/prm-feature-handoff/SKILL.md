---
name: prm-feature-handoff
description: Hand off substantial PRM Android UX work with asserted walkthroughs, inspected captures, and a review pack. Use for new journeys, substantial screen redesigns, multi-screen interactions, or explicit project-manager artifact requests, not ordinary localized completion or commit-only tasks. Use prm-release for releases.
---

# PRM Feature Handoff

Treat `AGENTS.md`, `docs/development.md`, and `scripts/host-mcp/README.md` as the
sources of truth. Preserve unrelated work and use `bash .mise/tasks/TASK` when
`mise` is unavailable.

Follow `docs/delivery.md` to select this workflow; do not expand task or commit
authorization. One integration owner handles validation, not a handoff worker by
default. Keep working artifacts in `ignored/`.

1. Inspect the working tree and isolate the authorized feature from unrelated changes.
2. Run required checks using the validation sequence in `docs/development.md`.
   Commit only if explicitly authorized; let the mandatory hook own the final
   broad gate for coherent staged inputs. Restore any exact-path temporary stash
   immediately after a commit.
3. Stage the frozen snapshot with `mise run test-stage`. Confirm provenance
   identifies the intended source fingerprint and revision (including dirty inputs).
4. For user-visible changes, use one Android MCP interactive session to assert
   and capture the important changed states. Inspect every selected capture,
   end the session through cleanup, and generate a `qa-report` review pack.
5. If visual review finds a defect, add a regression test, fix it, rerun the
   affected checks and regenerate affected evidence. Commit corrections only if
   authorized.
6. Run focused instrumented tests while iterating, then one full Android MCP
   suite for the final applicable inputs. Reuse matching evidence across a new
   commit only under the fingerprint, both-APK-hash and scope rules in
   `docs/delivery.md`; preserve the original job provenance and record the commit
   association separately. Poll through cleanup and consume every log chunk using
   integer byte offsets. Leave the runner idle.

Report the commit hash when authorized, clickable primary QA report, `check` result, final MCP job ID and
test count, APK hashes, and any unrelated dirty files left untouched. Do not
claim visual or runtime validation from a build alone.
