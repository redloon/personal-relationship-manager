# Personal CRM

An Android app for tracking contacts, interactions, and personal facts. Built with Jetpack Compose, Hilt, Room, and Navigation3.

## Quick Start

```bash
# Build
./gradlew assembleDebug

# Install on connected device
./gradlew installDebug

# Run all tests (starts emulator if needed)
./scripts/run-tests.sh

# Run specific test class
./scripts/run-tests.sh com.idontremember.personalcrm.AppLaunchTest

# Pre-commit checks (build + lint + static analysis)
./scripts/pre-launch-check.sh
```

## Architecture

```
app/src/main/java/com/idontremember/personalcrm/
├── data/
│   ├── contacts/ContactsSyncManager.kt   # Syncs Android Contacts Provider → Room
│   ├── db/                               # Room database, DAOs, entities
│   └── repository/                       # Data access layer
├── di/                                   # Hilt modules (DatabaseModule, RepositoryModule)
├── ui/
│   ├── MainActivity.kt                   # Single activity, Hilt entry point
│   ├── navigation/AppNavigation.kt       # Navigation3 routes
│   ├── contacts/                         # ContactsListScreen, ContactDetailScreen
│   ├── events/                           # EventsListScreen, EventDetailScreen
│   ├── addevent/                         # AddEventSheet (modal bottom sheet)
│   ├── components/                       # AutocompleteTextField, chips
│   └── theme/                            # Material3 theme
└── tile/                                 # Quick-add event tile service
```

## Key Patterns

- **Navigation3**: Uses `NavDisplay` with `SaveableStateHolderNavEntryDecorator` + `ViewModelStoreNavEntryDecorator`. Both decorators are required — omitting SaveableStateHolder causes a runtime crash.
- **Hilt DI**: Test modules (`TestDatabaseModule`, `TestRepositoryModule`) replace production modules with in-memory Room DB.
- **Contacts Sync**: `ContactsSyncManager` reads from Android's `ContactsContract` and upserts into Room. Local-only fields (facts, events) are preserved across syncs.

## Testing

```
app/src/androidTest/
├── AppLaunchTest.kt              # Smoke: does the app start?
├── ContactsSyncStressTest.kt     # 400+ contacts, idempotent sync, edge cases
├── testdata/RandomContactGenerator.kt  # Generates test datasets
├── ui/
│   ├── ContactsListScreenTest.kt # Contacts list renders, search + sync work
│   ├── EventsListScreenTest.kt   # Events screen via bottom nav (search, FAB, empty state)
│   ├── EventsSearchTest.kt       # Events search: nav via bottom bar, filter by note
│   ├── AddEventSheetTest.kt      # Add event sheet: open via FAB, save event end-to-end
│   ├── NavigationTest.kt         # Start route + bottom-nav tab switching
│   └── TestNavHelpers.kt         # Shared bottom-nav navigation helpers for UI tests
└── data/db/AppDatabaseTest.kt    # Room DAO unit tests
```

### Test Tags (for Compose UI tests)

All key composables have `Modifier.testTag(...)`:
- `contacts_list_screen`, `events_list_screen`, `contact_detail_screen`, `event_detail_screen`
- `sync_button`, `search_field`, `save_event_button`, `fact_add_button`, `fact_text_field`
- `note_text_field`, `contacts_list`, `events_list`, `add_event_fab`, `add_event_sheet`
- `events_search_field`, `events_empty_state`
- `bottom_navigation`, `nav_tab_contacts`, `nav_tab_events`

### Running on Emulator

The test runner (`scripts/run-tests.sh`) handles emulator lifecycle:
1. Checks for running emulator, starts headless one if needed
2. Builds and installs debug + test APKs
3. Runs instrumented tests
4. Captures logcat for crash diagnosis

## Scripts

| Script | Purpose |
|--------|---------|
| `scripts/run-tests.sh` | Full test cycle (build → install → test → logs) |
| `scripts/ensure-emulator.sh` | Start headless emulator if none running |
| `scripts/capture-crash-logs.sh` | Extract crash logs from logcat |
| `scripts/pre-launch-check.sh` | Static checks (build, lint, pattern scan) |
| `scripts/install-hooks.sh` | Install git pre-commit hook |

## Known Limitations

- **Bottom navigation bar exists** — `AppNavigation` wraps `NavDisplay` in a `Scaffold` with a `NavigationBar` (`nav_tab_contacts` / `nav_tab_events`). Tests can navigate between top-level screens by clicking those tabs.
- **ContactsSyncStressTest encodes Contacts Provider semantics** — expected counts come from the provider *after* aggregation (same-name raw contacts seeded on purpose merge into one `ContactsContract.Contacts` row), names are compared against `DISPLAY_NAME_PRIMARY` (the provider's name splitter reorders CJK names on read-back), and system-side lookups always use the aggregated contact id (`Contact.androidContactId == ContactsContract.Contacts._ID` — never the generator's synthetic ids or display names). Read the class doc in `ContactsSyncStressTest.kt` before changing it.
- **ContactsSyncStressTest requires emulator** — it seeds the real Android Contacts Provider and can't run on a host JVM. It needs WRITE_CONTACTS too, which only the debug build declares (`app/src/debug/AndroidManifest.xml`).

## For Future Agents

- Run `./scripts/pre-launch-check.sh` before committing to catch runtime crashes statically.
- **Never launch the emulator (or any long-lived background process) from a script with inherited stdout/stderr** — the background process keeps the caller's output pipe open and the run appears hung long after the work finished (this actually happened: tests finished in 65s while the tool call sat for 30+ minutes). `ensure-emulator.sh` redirects emulator output to `test-results/emulator-*.log` and closes stdin; keep it that way.
- `run-tests.sh` bounds every blocking step with `timeout` (override via `TEST_TIMEOUT` / `BUILD_TIMEOUT` env vars) and prints total wall time, so a wedged gradle/adb/emulator fails loudly instead of stalling.
- The Navigation3 decorator pattern is a known footgun — always include both `rememberSaveableStateHolderNavEntryDecorator()` and `rememberViewModelStoreNavEntryDecorator()`.
- Test DI modules are in `app/src/androidTest/.../TestDatabaseModule.kt` and `TestRepositoryModule.kt`. They use `@TestInstallIn` to replace production modules.
- The `HiltTestRunner` in `app/src/androidTest/.../HiltTestRunner.kt` must use `ClassLoader?` (not `Context?`) for the first parameter of `newApplication`.
- **The in-memory test Room DB is a process-wide Hilt singleton** — it is shared by every test class in one instrumentation run. The `-e clearPackageData true` flag in `run-tests.sh` is a **no-op without the Android Test Orchestrator** (not installed here); `run-tests.sh` compensates with an explicit `adb shell pm clear` before each run, but tests within a run still share state. UI/data tests must clear the tables they assert on in `@Before` (all current UI tests do), or cross-test data will leak (e.g. `ContactsListScreenTest`'s "No contacts yet." assertion breaks if `AppDatabaseTest` ran first and inserted contacts).
- Events screen in tests: click `onNodeWithTag("nav_tab_events")` (or use `navigateToEventsTab()` from `ui/TestNavHelpers.kt`), then assert on `events_list` / `events_empty_state`.