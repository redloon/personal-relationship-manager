# AGENTS.md

- Treat disrupting a human as a big deal. If you can finish your work yourself, you should do that and not waste someones time and delay the project by stopping in the middle with a half-finished report.
- Always test theories with simple probes/test-scripts to confirm your assumptions about behavior or solutions is correct, for example when stating that a certain bash command combination should work in a particular way, test a simplified version to ensure the real world behavior matches your understanding.
- Check `docs/decisions.md` for important, non-obvious product decisions before changing related behavior.

## Sandbox environment

- Usually you will be inside a Docker Sandbox environment.
- Canonical commands live in `.mise/tasks/`; if `mise` is unavailable, run the task with `bash .mise/tasks/TASK`. See `docs/development.md`.

## Development

- Red-green TDD: write tests first so you can quickly find out if something actually works.
- Validation is key: if you can't prove that your code works, then assume it doesn't.
- Assume most code doesn't need to handle migrations. Too often you've overbuilt migration code for features which are still in progress, and therefore had no users yet.

## Parallel development and validation

- Use isolated Git worktrees by default for parallel implementation. Agree on interfaces, file ownership, and integration order before editing; one owner coordinates shared schema changes.
- Workers run focused red-green JVM checks in their own worktree. One integration owner freezes the combined source snapshot and runs `check`, staging, and Android MCP jobs; no concurrent edits or competing Gradle builds in that checkout.
- Run `mise run check` before handoff or committing Android changes. It runs JVM tests, builds both APKs, lint, and static checks; any failure blocks a clean-validation claim. Repeat broad checks only after new changes or an unresolved failure.
- Hooks validate staged inputs, not an unrelated working tree. Android commits require relevant worktree inputs to match the index and a full check; tooling commits run tooling tests. See `docs/development.md`.
- Reusable worker instructions live in `.codex/agents/android-feature-worker.toml`. Give each worker a bounded task and require exact validation evidence; building an APK does not prove runtime behavior.

## UI conventions

- Read `docs/usability-principles.md` before UI changes or audits. Preserve confirmed human preferences; keep implementation defaults distinct from preferences.
- Inspect sibling screens and `ui/components` before UI work. Reuse shared controls such as `SearchField` and `SettingsButton`, and fix repeated interactions in those components.
- Test user-visible behavior and explain intentional deviations from shared patterns.

## Android tests in a sandbox

- Use `android-tests` MCP for instrumented tests; build inside the sandbox and execute APKs on the dedicated host emulator. Follow `scripts/host-mcp/README.md`; never substitute a sandbox emulator, a phone, or host execution of checkout scripts.
- After focused JVM checks pass for a coherent block, coordinate exclusive staging/test access, call `health`, run `mise run test-stage`, and submit its APK hashes to `start_test`. Do not restage while another job uses the shared inbox.
- Run focused tests while iterating, then the full suite before reporting instrumented validation. Poll through cleanup and read all log chunks using integer offsets. Report job ID, nonzero test count, and result; a build alone is not a test pass.
- Diagnose unavailable MCP with bounded probes from the runbook. For stale attachment errors (`client is closing: EOF` / `connection closed`), follow its recovery path. If another agent owns the issue, give it the evidence and continue independent work instead of repeatedly calling discovery/health. Report any remaining runtime-validation block explicitly.
- The installed host runner is a separate trusted snapshot; repo edits or sandbox restarts do not reload it. Use the runbook's setup/refresh procedure and confirm health PID/hash and dedicated AVD afterward. Never broaden it to host builds without the user's agreement or reset signatures on a phone.

## context on your human partner

The capture/recall loop has proved useful. We are now investing in excellent usability, forgotten-context recall, voluntary follow-ups, and reliable recovery; see docs/product-intent.md and docs/decisions.md. Keep solutions simple and avoid speculative infrastructure or automatically generated relationship obligations.

## text

Always be concise. Words are expensive, my time reading is expensive. Do not waste it with superfluous language.

## Code

Simple solutions are our favorite. We don't reach for complicated when simple gets us 80% of the quality for 20% of the work. The Pareto principle is our guide.
