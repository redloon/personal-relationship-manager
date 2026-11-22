# Human-feedback usability implementation

Implements the accepted responses to the [3 November review](../usability-review-2026-11-03.md), including full-name creation. Durable preferences live in [usability principles](../usability-principles.md); this document records delivery and validation.

## Ownership and contracts

Three Android workers use isolated worktrees based on `4d9b8bb`, the behavior-preserving extraction of shared record rows and profile controls. The coordinator owns documentation, navigation, integration and exclusive Android staging/testing. Workers preserve others' edits, run focused red-green JVM checks and provide Compose regressions.

- Capture owns picker/create, explicit tokens, draft state, composer, and all schema/backup changes.
- Search owns recall data, Home, History, tag browsing and People search.
- Reading owns record presentation, profile controls, fact/follow-up readers and follow-up editing.
- Shared interfaces: person selection returns an identified contact; Home receives draft availability; recall exposes follow-up dates/status and tag entities; profile navigation accepts an initial query; readers have explicit Edit callbacks.

## Feedback coverage

| Issue | Intended behavior | Owner |
| --- | --- | --- |
| 1 | Explicit styled tokens; deleting the last token unlinks; ordinary prose and personless notes remain valid | Capture |
| 2 | Suggest existing matching people; allow explicitly distinct same-name people | Capture |
| 3 | Fact reader before editing, with person navigation | Reading + coordinator |
| 4 | Searchable tag entities and exact-ID tag pages with People and Notes | Search + coordinator |
| 5 | Discoverable Home Follow-ups shortcut, neutral empty creation and short recent preview | Search + Reading |
| 6 | Matching People excerpt and query retained in profile | Search + Reading + coordinator |
| 7 | Planned date/completion in results, creation date in detail | Search + Reading |
| 8 | Resume draft label using existing draft storage | Capture + Search + coordinator |
| 9 | Internally scrolling editor and keyboard-safe actions in capture/follow-ups | Capture + Reading |
| 10 | Full-width readable rows and contextual actions | Reading |
| 11 | Ellipsis and Read more for truncated previews | Reading |
| 12 | Complete History with captured-record filters; universal entity-type filters | Search + coordinator |
| 13 | Header person editing with name/Android association controls | Reading |
| 14 | Shared person picker/create inside follow-up capture | Capture + Reading |
| 15 | Shared fact mode, separate person selector and appropriate copy | Capture |
| Additional | Multiword names despite shorter existing prefixes; navigable tag rows; familiar icons; visually distinct intentions | All |

General duplicate repair and a dedicated unlinked-notes view are deferred. No fake person, automatic relationship obligation or speculative preference-scoring infrastructure is introduced.

## Delivery

All accepted items in the coverage table are implemented. The shared person picker supports full names, reuse of existing people, and explicit creation of a different person with the same name. Notes use explicit token identities; ordinary pasted text remains prose. Token deletion, repeated equal labels, chip removal, rename, backup/restore, and reversible Note/Fact toggles have regressions.

Fact/follow-up readers, readable previews, profile header editing, search context, tag pages, complete History, Home shortcuts, draft visibility and keyboard-safe capture are integrated. Room version 4 includes an explicit populated 3→4 migration; backup version 3 reads previous version 2 exports.

The preference library defines ten stable UX rules, records their human sources, separates implementation choices from confirmed preferences, and provides observable audit checks. AGENTS.md and product intent direct future work to it.

## Validation record

- Red-green baselines covered token ownership, full-name creation, recall filtering and excerpts, record reading, and navigation. Navigation job `0037dbf750ac4a1dae6dc9f5fe125156` initially failed on the missing tag filter and fact in History.
- Final `bash .mise/tasks/check --max-workers=1` passed: **59 JVM tests**, both APKs, lint and static checks. `git diff --check` passed.
- **119 Android tests passed across all 26 test classes**, listed below. Every job reached terminal cleanup and all integer-offset log chunks were read.
- The two unfiltered attempts (`185a4fe3782e4479967124fb4681e8b1`, `b5d665f1234647458239ffb2f1417933`) exceeded the trusted runner's 180-second instrumentation limit. These are not full-suite passes. Class-by-class jobs provide complete test coverage; a single-process, unfiltered pass remains unverified.
- Runtime validation exposed stale empty-state assumptions, an invalid non-Unit Kotlin test declaration, and test taps on prefetched, unplaced lazy rows. These were corrected. Relationship controls now scroll through their lazy list before physical taps, and removal checks persistence before row disappearance. An unsuccessful speculative dialog-hoisting change was reverted.
- The final app hash for every passing class job is `8c31010c790a84e70f48ca5b72e37df7da5b9e0e5206a0ac440be60fb54f01bf`. Test-only fixes produced three test APK revisions; the final test hash is `a4d3e40cea4aa82546bc5abb3be2006e9bdb86b562b67c34e23fb15f7b345728`. No production changes occurred between those jobs. Full provenance, including failed attempts, is retained in `ignored/usability-review-2026-11-03/android-class-runs.json`.
- Keyboard regressions use the actual IME and accessible bounds at 320×640 and 480×960. Accessibility checks include 2× text; Unicode full-name capture also passes through the quick tile.

| Test class | Tests | Passing job |
| --- | ---: | --- |
| AppLaunchTest | 3 | `13f79aa97fa14b5fa97edce73bd6a4f5` |
| ContactsSyncStressTest | 11 | `becbbb544297458bac26363c20d5bf03` |
| data.db.AppDatabaseMigrationTest | 4 | `cc78374127264331974dc8d9ce324b03` |
| data.db.AppDatabaseTest | 7 | `c1896f7e14cc456c86fec787115de861` |
| data.db.BackupRestoreTest | 7 | `2b1cc59022014f2d93cf57bba0215ef6` |
| data.db.FollowUpRepositoryTest | 4 | `6aa1d3190a4d411eb9e97f738489f97c` |
| data.db.InlineTokenMigrationTest | 1 | `b5379eacfe3d4fdd834a7a4295446a20` |
| data.db.RecallMigrationTest | 1 | `56a6876108d64cd3a4ddb18b754d7ee3` |
| data.search.RecallDaoTest | 4 | `fd5a7c9623e345f68508eff1eb312c4d` |
| ui.AccessibilityResourceIdsTest | 4 | `af30c0a97c474a41b18d1b76cc63de56` |
| ui.AccessibilitySmokeTest | 4 | `c3cdaa26ce7a454b9090989f12d96754` |
| ui.AddEventSheetTest | 17 | `1886633e7f3d4cfaa98d654fbc348537` |
| ui.CaptureViewportTest | 1 | `82b6715902fd46fe985f8bddd769d27c` |
| ui.ContactsListScreenTest | 7 | `dd9469824c0947218b01617f5eb738e3` |
| ui.DashboardTest | 6 | `cb3a698b0a334cbb961188a986e44917` |
| ui.EventContactNavigationTest | 3 | `806d227828f0484cbaaffc898c529448` |
| ui.EventsListScreenTest | 3 | `e5ff8cad5a4e4c978e0f32818fe3ca30` |
| ui.EventsSearchTest | 4 | `0df0acce79b8416784f579bbdbab4703` |
| ui.FollowUpsTest | 5 | `9769836f516c472690bf0020beee9160` |
| ui.NavigationTest | 4 | `5373f068874943079e2b7481cf905fa1` |
| ui.QuickAddActivityTest | 2 | `31380af7d5344f5ba6dd223ff1c372d7` |
| ui.ReadingUsabilityTest | 4 | `a40d364ccadd4aeb97f44e86685d2d16` |
| ui.RestoreFlowTest | 3 | `affdb04342854df7af1280d5a6116380` |
| ui.SettingsScreenTest | 4 | `83bc3f05c263424aad640c8728c61035` |
| ui.ShareCaptureTest | 3 | `6075669091ac4dadbe57bdb4d239be27` |
| ui.UsabilityNavigationTest | 3 | `326a5093c2884adcaceaa909955a1cff` |

## Exploratory evidence

The normal debug app was exercised with fictional data through Android MCP session `e0d4e3c4f92e4ab1969b77230cf80385`. Verified journeys created “Maya Chen” from an inline mention, saved the linked note, created a fact with its person outside the prose, opened its reader without editing, created/read a follow-up, and opened a tag page containing a note without people. Assertions checked navigation and saved content.

The [screenshot/video review pack](../../ignored/mcp-artifacts/e0d4e3c4f92e4ab1969b77230cf80385/review.md) contains the recorded fact capture/recall journey and inspected screenshots. The first exploratory batch used an incorrect text selector for the mention popup; its retained failure was followed by a successful creation using one screenshot-backed coordinate tap. Later journeys used stable selectors.

This pack predates the final token-toggle fix; that delta is covered by focused JVM and Android regressions. The follow-up screenshot labelled “keyboard-safe editor” was taken after person selection with the IME closed; it is presentation evidence only. Actual keyboard safety is supported by the instrumented tests. The video artifact was finalized and hash-verified by the report tool; independent video decoding was unavailable in this sandbox.

## Remaining scope

General duplicate merge/delete and a dedicated unlinked-notes view remain intentionally deferred. Existing contact-consolidation behavior can leave a persisted draft referring to a removed person; draft reconciliation belongs with that deferred repair work. No physical-phone or exhaustive locale/device-matrix validation is claimed.

## Wrap-up validation — 22 November 2026

Reviewed the pending changes against the product decisions and recovered the prior implementation handoff. No unfinished feature work was found. The staged app/test APKs exactly match the final hashes above.

- `bash .mise/tasks/check --max-workers=1` passed; both APKs, lint (zero errors), JVM and static checks succeeded. A fresh `bash .mise/tasks/unit --max-workers=1 --rerun` ran all **59 tests**, with zero failures/errors.
- Fresh navigation job `95191e9a5309493f874c8c3e437250ff` passed **3 tests** on `android_tests`. Full job `07a5cdb6b7db49489ec88b08b318da94` reached the runner's 180-second limit; it is not a full-suite pass. Both jobs reached terminal cleanup and all log chunks were consumed.
- The three previous worker worktrees were fully archived under `ignored/cleanup-2026-11-22/`, including ignored files, their Git indexes, staged/working patches and status. Every archived file and symlink was verified against its source. Their differing revisions predate the final integration fixes; none contained a changed file missing from the main checkout.
