# AGENTS.md

- Treat disrupting a human as a big deal. If you can finish your work yourself, you should do that and not waste someones time and delay the project by stopping in the middle with a half-finished report.
- Always test theories with simple probes/test-scripts to confirm your assumptions about behavior or solutions is correct, for example when stating that a certain bash command combination should work in a particular way, test a simplified version to ensure the real world behavior matches your understanding.
- Check `docs/decisions.md` for important, non-obvious product decisions before changing related behavior.

## Sandbox environment

- Usually you will be inside a Docker Sandbox environment.
- If you don't have access to `mise` command, you can read `mise.toml` to get the direct commands to run.

## Development

- Validation is key: if you can't prove that your code works, then assume it doesn't.
- Assume most code doesn't need to handle migrations. Too often you've overbuilt migration code for features which are still in progress, and therefore had no users yet.

## UI conventions

- Inspect sibling screens and `ui/components` before UI work. Reuse shared controls such as `SearchField` and `SettingsButton`, and fix repeated interactions in those components.
- Test user-visible behavior and explain intentional deviations from shared patterns.

## Android tests in a sandbox

- Use `android-tests` MCP for instrumented tests; build inside the sandbox and run APKs on the dedicated host emulator. Read `scripts/host-mcp/README.md` for setup and the tool contract.
- Call `health`, then `mise run test-stage`; pass the printed hashes to `start_test` with an optional fully qualified class/method filter. Poll status and read all log chunks. Do not restage while another agent/job is using the inbox.
- Run a focused test while iterating and the full suite before reporting instrumented validation. Report job ID, test count, and result; a build alone is not a test pass.
- Discover gateway tools if needed. If MCP is unavailable, diagnose attachment/setup using the runbook; do not silently fall back to launching an emulator inside the sandbox or executing checkout scripts on the host.
- The installed host runner is a separate trusted snapshot. Repo edits do not update it; host setup/refresh uses `python3 scripts/host-mcp/setup.py --sandbox NAME`; setup refreshes idle host runner processes; a sandbox restart alone does not reload them. Confirm health PID/hash and AVD after refresh. Never broaden it to host builds without the user's agreement.
- For code-only runner refresh, use `bash scripts/host-mcp/install.sh --refresh-only` (optionally `--sandbox NAME`), then reconnect MCP. Version 3 serializes jobs across host/sandbox connections and resets incompatible debug signatures only on the verified dedicated emulator. Never apply that reset to a phone. Pass log offsets as integers; terminal status includes completed cleanup.

## context on your human partner

I tend to over-engineer and build too many features, for now it's your job to help keep this project constrained to the MVP. We only want to build features which help us test core hypotheses, not little quality of life things which don't move the needle.

## text

Always be concise. Words are expensive, my time reading is expensive. Do not waste it with superfluous language.

## Code

Simple solutions are our favorite. We don't reach for complicated when simple gets us 80% of the quality for 20% of the work. The Pareto principle is our guide.
