# Remembering context rework

Approved direction: forgotten-context recall first, voluntary follow-ups second. Home + People, shared composer, deterministic local search, editable facts, name-only people, portable daily backups, recoverable deletion, explicit morning reminders. No automatically generated obligations, business-style prep, introductions or guilt counters.

Implementation is divided by file ownership in one shared checkout: capture, recall/UI, reliability, and an integration owner for schema/follow-ups and verification. Reusable agent instructions: `.codex/agents/android-feature-worker.toml`; custom agents inherit the parent model, permissions and MCP connections. Three workers can run beside the integration owner. Coordinate exclusive APK staging and host emulator jobs.

Validation: write behavioral tests first; focused JVM tests and build checks precede focused Android MCP tests. Then run the full Android suite, read every log chunk, and report job IDs/counts/results. A build is not an instrumented test pass. Never start a sandbox emulator or silently substitute a phone.

## Implementation checkpoint

Implemented by three workers plus integration: shared create/edit capture and entry points; context search and Home/People/profiles; name-only profiles and explicit Android linking; 30-day recovery; verified daily portable backups; explicit follow-ups and opt-in single-summary reminders. Navigation intentionally retains Home/People on secondary screens; reselecting the active tab returns to its root while preserving search. New strings remain localized in Android values resources.

Validation completed: 43 JVM tests passed; debug and instrumentation APKs built; lint completed with zero errors (warnings remain). Populated SQLite v2→v3 migration matched exported schema columns and preserved context with no follow-ups created. Actual recall SQL returned 4,900 rows from 400 people, 3,000 notes and 1,500 facts in 28.2 ms on the sandbox SQLite probe; this is not an Android UI latency measurement. Contact consolidation, provider-ID swaps and reminder-claim rollback also passed targeted SQLite probes.

Android runtime validation completed through the repaired host MCP on the dedicated `android_tests` emulator. The initial full run exposed stale fixed-control selectors, an ambiguous person selector, a profile capture button covering relationship actions, and a save error hidden below the editor while the keyboard was open. Fixed the selectors, placed profile capture in reserved Scaffold bottom-bar space, pinned the shared error state beneath Save, and made person-picker tests scroll and wait for the dialog.

Validation evidence (2026-11-01):

- Launch: `9919051378d847f599162780c289f722`, 3 tests passed.
- Relationship regression: `bcc971c7d1454274a32d70258c22be16`, 1 test passed.
- Composer: `0c3aba554e3141d59abb5ab8832f3877`, 13 tests passed.
- Note-linking/picker regression: `7a826b9d4c7e4d4e858579d9418920f8`, 1 test passed.
- Final full suite: `69a9c63a9b4b45c8883601988a6f2fa4`, **90 tests passed**, 126.984 seconds. Terminal cleanup completed; every log chunk consumed.

Final app APK SHA-256: `04db07e1f8febf50b457744c0763d58fae574a14ff7358f3ab2310948c98484b`; test APK: `e16efc67bb2e1d6d992c736cc1b61be03d833a22725d209be4d9dbefa93d38d2`. The final JVM/build/lint/static check passed (43 JVM tests). These APKs include the uncommitted app rework and runtime fixes; workflow tooling was separately committed as `d12bc91`, with 29 tooling tests passing inside its pre-commit hook.
