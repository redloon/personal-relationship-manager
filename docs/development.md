# Development workflow

## Commands

Executable tasks in `.mise/tasks/` are the source of truth. Use `mise run TASK`, or run the same file with Bash when mise is unavailable. Prerequisites are Bash, Python 3.11+, Git, and `flock` (util-linux on Linux). Builds also require JDK 21 and the Android SDK. Tasks discover JDK 21 from `JAVA_HOME`, PATH, standard Linux installs, or Mise; SDK discovery includes `~/android-sdk` and `~/Android/Sdk`. Keep machine-specific SDK paths out of shared `local.properties`.

| Task | Without mise | Purpose |
|---|---|---|
| `build` | `bash .mise/tasks/build` | Build the debug app APK |
| `unit` | `bash .mise/tasks/unit` | Run JVM tests without an emulator |
| `gradle` | `bash .mise/tasks/gradle TASK [OPTIONS]` | Run Gradle with project SDK discovery |
| `check` | `bash .mise/tasks/check` | JVM tests, app and test APKs, lint, and static checks |
| `test-stage` | `bash .mise/tasks/test-stage` | Build and hash APKs for Android MCP |
| `hooks-install` | `bash .mise/tasks/hooks-install` | Install the project Git hooks |
| `qa-report` | `bash .mise/tasks/qa-report MANIFEST [--evidence JSON]` | Generate a verified screenshot/video review pack |
| `tooling-test` | `bash .mise/tasks/tooling-test` | Test task, hook, and development-tool behavior |

Run a focused JVM test during implementation:

```bash
bash .mise/tasks/unit --tests '*CaptureContextTest'
```

`check` combines JVM tests, both APK builds, and lint in one Gradle invocation, then performs static checks. Any failed step fails the check. Gradle tasks serialize per checkout and reject source changes during execution, including exported Room schemas. If compilation generates a changed schema, review it and rerun against the stable inputs. The lock does not stop editors; workers must still coordinate. This check does not run instrumented tests or prove that a screen works on Android.

Legacy build/test scripts such as `scripts/gradle.sh` and `scripts/pre-launch-check.sh` forward to the canonical tasks. Do not maintain a second implementation there. The `hooks-install` task uses `scripts/install-hooks.sh` as its installer helper. Legacy `test` / `test-class` helpers run a local host emulator and reject Docker execution; sandbox development uses Android MCP exclusively.

## Parallel work

1. Give each worker an isolated Git worktree by default, a bounded feature, explicit file ownership, and agreed interface signatures. One owner coordinates shared schema changes and integration order. Preserve human edits; do not copy another worker's unfinished source into a validation snapshot.
2. Write a focused failing test, implement the behavior, and run the relevant JVM tests in the worker's worktree. Check user-visible behavior rather than source structure.
3. Integrate coherent changes. One integration owner freezes the combined source snapshot and owns its Gradle checks, APK staging, and emulator jobs. No source edits or competing builds in that checkout while validation runs.
4. Run `check`, then focused Android MCP tests for the block. After integration, run the full instrumented suite. Re-run broad checks only for new changes, a failure, or an unresolved concern.

The reusable Android worker is defined in [`.codex/agents/android-feature-worker.toml`](../.codex/agents/android-feature-worker.toml). It inherits the parent session's tools and MCP connection; repository configuration does not provision a host runner.

## Android runtime validation

Follow the [Android MCP runbook](../scripts/host-mcp/README.md) for setup, tool inputs, staging, logs, and attachment recovery. Build in the sandbox; execute APKs on the dedicated host emulator. Never replace this with a sandbox emulator, a phone, or host execution of checkout scripts.

After cheap tests pass for a coherent block, coordinate exclusive inbox access, call `health`, run `test-stage`, and pass its printed hashes to `start_test`. Do not change the source snapshot or restage while another job uses the inbox. Poll through terminal cleanup and consume all log chunks.

Use bounded diagnostics for unavailable tools or stale attachments. Make one diagnosis, follow the applicable recovery step, and retry when attachment or host state has changed. If another agent owns that issue, send it the evidence and continue independent work rather than repeatedly calling discovery or health. The installed host runner is a separate trusted snapshot; checkout edits do not refresh it.

Report the exact check or job, snapshot, result, and limitations. Instrumented evidence includes job ID, nonzero test count, and outcome. Distinguish APK compilation, focused runtime tests, and a full runtime-suite pass. If MCP remains unavailable, report runtime validation as blocked; do not describe the app as tested on Android.

## Commit checks

Install hooks with `mise run hooks-install`. Hooks check staged syntax/formatting and select validation for the staged changes. Android changes require relevant worktree inputs to match the index before running the full `check`; a pass against different unstaged code cannot validate the commit. Tooling changes run `tooling-test`. Mixed changes must satisfy both gates.

Resolve a failing hook or input mismatch instead of bypassing it. Stage a coherent change, preserving unrelated human work. Commit hooks do not substitute for instrumented runtime validation.

## Android test traps

- Navigation3 requires both the saveable-state and ViewModel-store entry decorators.
- Test Hilt modules replace production modules with a process-wide in-memory Room database. Clear tables and preferences used by each test; activity-starting rules may initialize state before `@Before`. Recreate or launch after arranging persisted inputs when necessary.
- Keep Preferences DataStore process-wide: rebuilding Hilt per test must not open competing stores for the same file. `HiltTestRunner.newApplication` takes `ClassLoader?` as its first parameter.
- Android contact stress tests assert Contacts Provider aggregation and name normalization, using aggregated contact IDs. Read the test's class documentation before changing sync expectations.
- Database changes require an explicit migration, committed exported schema, and populated migration tests. Release builds must never use destructive migration fallback.
- Long-lived processes must redirect stdout/stderr and close stdin. Keep runner timeouts and cleanup intact; an inherited output pipe can make a completed command appear hung.

## Lessons from the workflow audit

| Observed failure | Safeguard |
|---|---|
| Lint failure printed success; pre-launch checks omitted JVM tests and started Gradle three times | One blocking Gradle invocation includes all four gates |
| Workers built while other workers changed shared source | Separate worktrees, an integration owner, build lock, and input fingerprints |
| A working-tree fix could hide broken staged code; hook installer assumed `.git` was a directory | Parse index contents, require a coherent build snapshot, use Git's worktree-aware hook configuration |
| An adb exit of zero could report success without any tests running | Require an explicit instrumentation success with a nonzero test count |
| Repeated MCP attachment probes consumed time without changing the failing state | Assign one recovery owner; retry after a relevant state change |

## Exploratory QA and demos

Runner v4 adds batched ADB interactions and captures; see the MCP runbook for
interfaces. Use the normally launched debug app with fictional data, stable
selectors, and explicit assertions. Inspect keyboard-open layouts, Back/cancel,
empty results and long content. Return screenshots and a short recorded journey
with a qa-report pack for significant UX work. Existing Compose tests remain the
regression gate; exploratory evidence is additional validation.

The integration owner holds one session through terminal cleanup before staging
or running tests. Tooling checks include SDK-free host-runner tests. The real
stdio protocol test also runs when the host-mcp Python requirements are installed;
otherwise unittest reports its explicit skip. No host SDK/emulator is needed for
that fake-SDK protocol test.

When reading a report written by a host agent through the shared filesystem,
size/mtime may update while cached contents remain stale or appear NUL-padded.
If host and sandbox disagree, reopen after `os.posix_fadvise(fd, 0, 0,
os.POSIX_FADV_DONTNEED)` and verify the contents before assuming an action was
not performed. This recovered the completed host cleanup log during live QA.
Do not overwrite another agent's report based on stale cached contents.
