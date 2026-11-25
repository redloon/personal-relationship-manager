# Improve delivery efficiency by eliminating unnecessary work

Date: 25 November 2026. Replaces the earlier context-growth proposal.

## Priorities and boundaries

1. Do only authorized work and stop at the requested deliverable.
2. Avoid unnecessary fixture setup, investigation and repeated validation.
3. Right-size completion and project-manager review artifacts.
4. Reduce the context cost of necessary work.

Smaller responses and delegation cannot compensate for an oversized task.
Preserve required checks, meaningful visual review and complete evidence. No app
behavior changes, automatic commits, global settings, validation bypasses,
persistent check receipts, validation caches or new orchestration infrastructure.

## Baseline and success

The supplied investigation of session `01a213af-a2f4-72a0-857c-bde5dcf180c9`
reports review-only scope expanding into implementation after narrower feedback
and compaction; an approximately 47-minute review with substantial synthetic-data
setup before reading the handoff skill; two 186-test runs with identical APK
hashes and recorded build-input fingerprints (the second approximately 318s);
three broad checks failing formatting before a fourth passed; 390 tool
submissions and approximately 504,000 shell-output characters over the session.
These are observational inputs, not independently reproduced measurements,
exact token attribution or universal savings estimates.

Success means less unrequested work, setup, duplicate execution/artifacts and
context growth without reducing applicable testing or substantial-work review
quality. Do not authorize extra feature work just to measure a comparison.

## Implementation and acceptance

- [Delivery guidance](../delivery.md) owns scope continuity, workflow examples,
  fixture purpose, artifact placement, validation ordering and evidence reuse.
  Root instructions, development docs, the handoff skill and worker briefs link
  to it rather than duplicating procedures.
- `scripts/measure-rollouts.py` accepts explicit local rollout paths and exports
  privacy-safe aggregate Markdown/JSON. Test wrappers, mirrors, cumulative
  counters, compaction, malformed/missing telemetry, images and worker usage.
- [Runner contract and tested monitoring](../../scripts/host-mcp/README.md#bounded-monitoring)
  preserve default full status, add an 8 KiB summary, retain full diagnostics,
  wait through cleanup and consume every log chunk using integer byte offsets.
- Run focused red-green tooling checks, then `bash .mise/tasks/tooling-test`.
  Runner changes additionally need an idle trusted-host refresh, loaded hash/PID
  and dedicated-AVD verification, focused and full MCP tests against stable APKs.
- Compare independently authorized ordinary and substantial-UX work with the
  baseline when telemetry is available. Report observed setup/artifact counts,
  repeated execution, elapsed time, text volume and aggregate usage separately
  from estimates. Explicitly disclose live-validation and telemetry blocks.

Scratch state, evidence and the primary implementation report belong in
`ignored/delivery-efficiency/`, not additional overlapping durable narratives.
