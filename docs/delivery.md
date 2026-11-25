# Right-sized delivery

## Scope and continuity

Do only the authorized work. For multi-stage tasks keep one short task-state note
in `ignored/`: deliverable and acceptance criteria, exclusions, confirmed human
preferences, unauthorized recommendations, snapshot, evidence, active jobs, owner,
and next action. Carry these distinctions through compaction and worker briefs.
At task boundaries check that the next action serves the deliverable; stop when
it is complete. Local feedback does not authorize the surrounding redesign.
Resolve discoverable facts yourself; ask only when materially different
interpretations would produce materially different work.

| Authorization example | Next authorized action | Not authorized |
|---|---|---|
| Review-only approval | Inspect and report findings | Implement findings |
| Narrower follow-up feedback | Address only the named feedback within its stated scope | Implement the rest of a review |
| Implementation approval | Implement the approved changes and validate | Commit or add adjacent improvements |
| Commit-only authorization | Verify coherent staged inputs and commit the approved work | Redesign or implement recommendations |

## Select the workflow

| Scope | Setup and completion |
|---|---|
| Read-only review | Identify the build and suitable existing APKs; rebuild only when availability, provenance, or the review objective requires it. No automatic implementation-completion gates. |
| Localized change | Required checks, relevant focused tests, targeted runtime/visual verification, concise result and limitations. No default recording, review pack, or handoff worker. |
| New journey, substantial screen redesign, multi-screen interaction change, or explicit project-manager artifact request | Use `prm-feature-handoff`: asserted walkthrough, inspected changed-state captures, review pack. |
| Small data-integrity fix | Strong regression/runtime tests as risk requires; no polished visual pack merely because risk is high. |

Risk sets testing rigor; presentation scope sets artifact needs. Commit
authorization is separate from all four workflows. One validation owner freezes
the integrated snapshot; no concurrent edits or competing builds in that checkout.
Keep small or tightly coupled work local. Delegate only bounded independent work
that improves delivery, using isolated worktrees, explicit file ownership and
compact briefs. No recursive handoff delegation. Measure aggregate usage, not
just the parent's output.

## Fixtures and artifacts

Give every fixture dimension a review purpose. Start with enough data to exercise
the relevant states; add records only to answer another concrete question.
Unrelated background records are not a default review requirement. Existing
population regression-test coverage remains intact. Use existing test-only
fixture patterns where the supported workflow permits repetitive setup, then
verify representative data in the real UI. Do not add production seeding APIs
or a general fixture framework for one review.

Keep scratch plans, fixture inventories, logs and implementation-validation notes
in `ignored/`. Preserve durable human preferences in authoritative product docs.
Produce one primary requested report linking evidence, not overlapping narratives.
Capture materially changed states rather than touring unrelated functionality.

## Validation without repetition

Follow focused red-green tests, coherent integration, required broad checks,
runtime validation and applicable handoff. Fix all reported formatting issues,
then run the focused formatting gate (`bash .mise/tasks/gradle ktlintCheck`)
before restarting broad validation. Never bypass staged hooks.

When a coherent commit is authorized, let its mandatory hook own the final broad
gate rather than automatically running the same manual gate immediately before
it. Otherwise run the required standalone `check`. Corrections invalidate the
affected evidence. Canonical staging remains unchanged.

Record source fingerprint, both APK hashes, command/filter, result, test count
and job ID. A new commit alone does not invalidate runtime evidence: identical
applicable source fingerprints, both APK hashes and test scope permit associating
the original job with a verified commit. Keep its original revision/provenance;
record the association separately, never rewrite history. Missing/unverifiable
inputs, changed inputs or a distinct test objective require a rerun. The tested
`scripts/delivery_evidence.py` predicate demonstrates this conservative rule;
it is not a cache or a replacement for verifying the target snapshot.

## Context and monitoring

Search before reading; start ordinary reads around 1–2k output tokens and expand
deliberately. Do not truncate required evidence. Avoid rereading unchanged docs
or full diffs; navigate to authoritative procedures rather than copying them.
Use small probes to distinguish theories; record rejected explanations and do
not repeat unchanged recovery attempts after compaction.

Use the [runner monitoring examples](../scripts/host-mcp/README.md#bounded-monitoring)
to wait, suppress unchanged intermediate status, retain complete logs and return
continuation state. Never resubmit just because monitoring timed out. Summary
status is navigation, not independent proof of success. Batch predictable UI
actions with explicit assertions. Inspect screenshots at decision points and
every selected QA capture; avoid fetching unchanged captures repeatedly.

## Measurement

Run `python3 scripts/measure-rollouts.py PATH... --format json` (or `markdown`)
with explicit rollout files. It is SDK-free and exports aggregate metrics only,
not transcript text, paths, prompts, credentials or source content. See its
limitations in the output. Retain raw evidence locally in `ignored/`.
Compare ordinary small-change and substantial-UX tasks only when those tasks
are independently authorized; do not manufacture feature work for a benchmark.
Separate observed counts from the supplied investigation, and report incomplete
telemetry rather than inventing token attribution or universal savings.
