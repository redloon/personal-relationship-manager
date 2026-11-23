# Testing confidence validation — 2026-11-23

Implementation and runtime acceptance are complete. The refreshed v5 runner passed the focused test and all 160 Android tests in one unfiltered job on the dedicated host emulator.

## Sandbox evidence

- Final `verify --console=plain` passed: 78 JVM tests, both APKs, Android/Slack Compose lint, ktlint and static guards. The final warm Gradle check took 2 seconds; the first complete build took 103 seconds, plus tooling checks (118 seconds total). These runs have different cache states and are not a speed comparison.
- Latest `tooling-test` passed 123 tests (43 task/hook/report/environment, 4 previously omitted extraction tests, 76 host-runner/protocol tests), with no skips. This includes the final parser hardening after `verify`.
- All 77 original JVM test identities are preserved across 18 JUnit 5 classes. Seed 101 replayed identical execution order; seed 202 changed class/method order. Fresh seeds 3471873929356396897 and -5935119671610017119 differed with configuration caching enabled. Filtering also passed for `*CaptureDraftCodecTest`.
- Added 200 seeded Unicode/token draft/edit round-trip cases. The test uses the invocation seed for replay.
- Real negative probes rejected a temporary formatting violation and a temporary Compose modifier violation. The latter was confirmed by issue ID and file location in the XML lint report; no incompatible registry was baselined. Probe files were removed.
- Subprocess probes verified live/partial output, timeout/cancellation, whole-job deadline precedence, cleanup/lock release, optional status waits and raw result parsing. The real stdio protocol test exercises default filtered/full deadlines, per-test identities/results/durations, timings and separate diagnostic logs against fake SDK commands.
- Staged syntax/whitespace passed using a disposable index containing the complete change set. Source/schema/baseline mutation, staging-lock and staged-input coherence regression tests passed. `environment-check` passed without changing hooks or configuration. Repeated `tooling-setup` succeeded.

Raw evidence is retained under `ignored/testing-validation/`: `verify-final.log`, `tooling-final.log`, `ordering.json`, seed invocation logs, both negative-probe logs, `environment.json`, `stage-final.log` and `migration.log`.

## Android evidence

`OldestSchemaMigrationTest` passed **1 test** on the dedicated `android_tests` AVD (`emulator-5580`), job **5286e4dc848e4e5196082896af3981c6**. Instrumentation reported 0.159 seconds. Terminal cleanup completed; all 94,053 log bytes were consumed using integer offsets, including the final empty chunk. This used installed runner v4 (PID 3204111), so it validates the populated v1→v6 migration, not the new v5 orchestration.

APK hashes (unchanged by the later local-helper raw-output fix):

| APK | SHA-256 |
| --- | --- |
| App | `a1a833fb2db31ed757814a1f90df3a19dde724748510b4b3614bfa10b1e37407` |
| Android tests | `f9839b4628271161359391ba42df11ebe69c0e6d64fee5772761bb38d172a5b2` |

The focused job used build-input fingerprint `d6adfc35d86c722dec9cf08a926450fb3ec90a1e025465065b8d214fd46e6b7f`. Final staging records `89ebca3bf70980d5490c30134b39f42b44a4ae788c8360d12366a81f37a3abb0`, Git revision `206a26351f96f7f204b26208345292f2dc4c09c7`, and the same APK hashes. No inbox was restaged while the job was active.

## Refresh shutdown regression

The first host refresh failed because an initialized idle MCP process stayed alive after SIGTERM while its gateway held stdin open. A real stdio subprocess reproduced the hang; closing stdin allowed exit. The original sleep-only shutdown test missed it.

Refresh now waits ten seconds for graceful exit, then sends SIGKILL only after rechecking the same installed process identity. The main refresh lock and persisted-running-job refusal remain in place. Regression tests cover initialized MCP with stdin open, active jobs, unrelated processes, and identity changes with/without pidfd. All 12 setup tests and the complete 123-test tooling gate pass; `ignored/testing-validation/refresh-tooling.log` retains the latter result. The host retry subsequently completed and its loaded runner fingerprint was verified before the runtime jobs below. A separate probe also exercised the default ten-second graceful timeout and full refresh flow in 10.126 seconds, with SDK/gateway commands mocked (`refresh-default-probe.log`).

## Refreshed host acceptance

The host successfully ran `bash scripts/host-mcp/install.sh --refresh-only --sandbox codex-personal-crm`. Health reported version **5**, PID **83324**, `avd=android_tests`, `serial=emulator-5580` and source hash `7661d8375ecdbb0786584a28954d980ea85a28f9d401b624af70a817a35c6496` matching the checkout. The sandbox staged the verified APK pair above while the runner was idle.

- Focused job **a15cf2657bc6497bab4c2bd62a49ff41** passed **1 test**, with its individual identity/outcome/duration and phase timings recorded. Total 3.816 seconds; test phase 1.405 seconds; cleanup 0.141 seconds. All 1,198 progress-log bytes were consumed.
- Unfiltered job **365de994b62e4c71a4260dffcb86d3bf** passed **160 tests**, all with identities, passed outcomes and durations. It used the default 900-second instrumentation limit. Live logs were read before completion. All 111,726 progress-log bytes were consumed through the final empty chunk after terminal cleanup. Final health confirmed `busy=false`.

| Full-job phase | Seconds |
| --- | ---: |
| APK verification/start | 1.355 |
| Emulator | 0.208 |
| Install/reset | 0.657 |
| Tests | 261.402 |
| Cleanup | 0.318 |
| **Total** | **263.941** |

Full logcat remains separate as the host job's diagnostic `logcat.log`. Raw progress logs, the complete terminal status (including all 160 test records), APK hashes, source provenance and baseline timing records are retained in `ignored/testing-validation/v5-full.log` and `runtime-comparison.json`. The focused progress log is `v5-focused.log`.

## Observed comparison

The previous committed-build evidence (`ignored/people-overhaul-committed-tests-2026-11-22.json`) contains 36 class jobs and 159 tests. Their original instrumentation summaries sum to **346.745 seconds**. The new single job reports **259.602 seconds** of device instrumentation, **25.1% less**, with one additional migration test. Submissions fell from **36 to 1** (**97.2% fewer**). The new full run used 1 submission, 5 bounded status calls and 11 progress-log reads: **17 orchestration calls**, excluding staging/health and the separate focused job.

This is an observed comparison across two APK snapshots, not a controlled benchmark. Baseline installation, cleanup, agent wait time and polling counts were not recorded, so no total wall-time speedup is claimed.
