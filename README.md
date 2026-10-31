# Personal CRM

An Android app for tracking contacts, interactions, and personal facts. Built with Jetpack Compose, Hilt, Room, and Navigation3.

## Quick Start

```bash
# Common commands (see mise.toml)
mise run build
mise run install
mise run unit
mise run test
mise run test-class -- com.idontremember.personalcrm.AppLaunchTest
mise run check
```

The test runner reads `sdk.dir` from the ignored `local.properties` and adds the SDK's platform tools, emulator, and command-line tools to `PATH`. If it needs to create an emulator, it installs the Android 34 Google APIs x86_64 image and creates `pixel_6_api_34`. If provisioning fails, check SDK licenses (`sdkmanager --licenses`) and make sure the SDK command-line tools are installed.

## Phase 1 dogfood loop

Create people in Android Contacts, grant this app Contacts access, then tap the refresh icon in the Contacts tab. Add freeform facts on a person's detail page. Use the Quick Settings **Add Event** tile to jot a note immediately; select a person with `@` if convenient, or open the saved event later and attach them in Edit. Look up the person's facts and event history before or after a conversation.

Use **Settings → Data → Export data** weekly and before installing a development build that changes storage. Keep the plain JSON file outside the app and treat it as private. **Restore data** replaces all current app records after confirmation; it does not restore preferences or merge records. Confirm the export success message and that the file exists before relying on it.

After three weeks, check the success signals and stop conditions in [product-intent.md](docs/product-intent.md). Keep new feature ideas parked until the capture and recall loop proves useful.

For sandbox build and emulator setup, see [android-sandbox.md](docs/android-sandbox.md).

## Architecture

```
app/src/main/java/com/idontremember/personalcrm/
├── data/
│   ├── contacts/ContactsSyncManager.kt   # Syncs Android Contacts Provider → Room
│   ├── backup/DataExporter.kt             # Manual JSON export and transactional restore
│   ├── db/                               # Room database, DAOs, entities
│   ├── repository/                       # Data access layer
│   └── settings/SettingsRepository.kt    # Typed DataStore preferences (theme, dates, defaults)
├── di/                                   # Hilt modules (DatabaseModule, RepositoryModule, SettingsModule)
├── ui/
│   ├── MainActivity.kt                   # Single activity, Hilt entry point
│   ├── navigation/AppNavigation.kt       # Navigation3 routes
│   ├── contacts/                         # ContactsListScreen, ContactDetailScreen
│   ├── events/                           # EventsListScreen, EventDetailScreen
│   ├── addevent/                         # AddEventSheet (modal bottom sheet)
│   ├── components/                       # Shared EmptyState/LoadingState/ErrorState, autocomplete, chips, date formatting
│   ├── settings/                         # SettingsScreen (grouped sections), SettingsViewModel
│   └── theme/                            # Material3 theme (wired to SettingsRepository)
└── tile/                                 # Quick-add event tile service
```

## Key Patterns

- **Navigation3**: Uses `NavDisplay` with `SaveableStateHolderNavEntryDecorator` + `ViewModelStoreNavEntryDecorator`. Both decorators are required — omitting SaveableStateHolder causes a runtime crash.
- **Hilt DI**: Test modules (`TestDatabaseModule`, `TestRepositoryModule`) replace production modules with in-memory Room DB.
- **Preferences**: one process-wide Preferences DataStore (`di/SettingsModule.kt`, created via the `preferencesDataStore` delegate — required because Hilt rebuilds its component per instrumented test and DataStore refuses two live instances over one file). `data/settings/SettingsRepository` exposes typed Flows + suspend setters; keys are named constants in `SettingsKeys`. `MainActivity` collects theme prefs into `PersonalCRMTheme` — theme changes apply without restart.
- **Strings & a11y (P2)**: every user-visible string lives in `res/values/strings.xml` (including icon `contentDescription`s, SimpleDateFormat patterns like `date_pattern_event`, and relative-date copy resolved via `RelativeDateStrings` in `ui/components/DateFormatting.kt`). `scripts/pre-launch-check.sh` fails on `Text("` or `contentDescription = "` literals under `ui/` — always extract there. All empty/loading/error states render through the shared `EmptyState` / `LoadingState` / `ErrorState` composables in `ui/components/` (each takes a snake_case `testTag`).
- **Contacts Sync**: `ContactsSyncManager` reads from Android's `ContactsContract` and upserts into Room. Local-only fields (facts, events) are preserved across syncs.
- **Room schema discipline (P4)**: every DB change ships as version bump + explicit `Migration` + committed schema JSON (`app/schemas/<db-class>/<version>.json`, exported via the `room.schemaLocation` KSP arg in `app/build.gradle.kts`) + a `MigrationTestHelper` migration test — all four, always. The policy comment at the top of `data/db/AppDatabase.kt` has the checklist; `androidTest/data/db/MigrationTestBase.kt`'s KDoc has the step-by-step recipe for adding a column. Migration tests read the committed schemas back from the test APK's assets (`androidTest.assets.srcDir("$projectDir/schemas")`). Never `fallbackToDestructiveMigration()` in release.

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
│   ├── SettingsScreenTest.kt     # Settings: gear entry (both lists), theme flip + persistence
│   ├── AccessibilitySmokeTest.kt # TalkBack order (semantics traversal), shared empty states, max font scale + 48dp targets
│   ├── AddEventSheetTest.kt      # Add event sheet: open via FAB, save event end-to-end
│   ├── NavigationTest.kt         # Start route + bottom-nav tab switching
│   └── TestNavHelpers.kt         # Shared bottom-nav navigation helpers for UI tests
└── data/db/
    ├── AppDatabaseTest.kt          # Room DAO unit tests
    ├── MigrationTestBase.kt        # Migration harness: helper rule, seed/assert helpers, add-a-column recipe
    └── AppDatabaseMigrationTest.kt # v1 schema smoke + worked v1→v2→v3 scratch-column round trip (P4)
```

JVM unit tests (`./gradlew testDebugUnitTest`, no emulator):
- `app/src/test/.../data/settings/SettingsRepositoryTest.kt` — prefs defaults, round-trip, persistence across a DataStore restart
- `app/src/test/.../ui/components/DateFormattingTest.kt` — absolute/relative date rendering (P1 date-display preference)

### Test Tags (for Compose UI tests)

All key composables have `Modifier.testTag(...)`:
- `contacts_list_screen`, `events_list_screen`, `contact_detail_screen`, `event_detail_screen`
- `sync_button`, `search_field`, `save_event_button`, `fact_add_button`, `fact_text_field`
- `note_text_field`, `contacts_list`, `events_list`, `add_event_fab`, `add_event_sheet`
- `events_search_field`, `events_empty_state`
- `bottom_navigation`, `nav_tab_contacts`, `nav_tab_events`
- `settings_button`, `settings_screen`, `settings_back_button`
- `contact_list_item`, `contacts_empty_state`, `contacts_loading_state`, `contacts_error_state`, `contacts_error_retry_button`
- `events_loading_state`, `events_error_state`, `events_error_retry_button`
- `facts_empty_state`, `contact_events_empty_state`, `contact_detail_loading_state`, `contact_detail_error_state`
- `event_links_empty_state`, `event_detail_loading_state`, `event_detail_error_state`, `event_detail_error_retry_button`
- shared state composables take a `testTag` param (defaults: `empty_state`, `loading_state`, `error_state`, `error_retry_button`)
- `settings_section_data`, `export_json_button`, `import_json_button`, `restore_confirmation_dialog`, `confirm_restore_button`
- `event_person_search`, `event_person_option_<id>`, `edit_event_button`, `save_event_edit_button`, `event_save_error`
- `settings_section_appearance`, `settings_section_date_time`
- `theme_mode_selector`, `theme_mode_system`, `theme_mode_light`, `theme_mode_dark`
- `dynamic_color_toggle`
- `date_display_selector`, `date_display_absolute`, `date_display_relative`
- `default_event_time_selector`, `default_event_time_now`, `default_event_time_last_used`

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
