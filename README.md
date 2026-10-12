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
│   ├── ContactsListScreenTest.kt # Contacts list renders, sync button works
│   ├── EventsListScreenTest.kt   # Events screen baseline
│   ├── AddEventSheetTest.kt      # Add event sheet baseline
│   └── NavigationTest.kt         # App starts on correct route
└── data/db/AppDatabaseTest.kt    # Room DAO unit tests
```

### Test Tags (for Compose UI tests)

All key composables have `Modifier.testTag(...)`:
- `contacts_list_screen`, `events_list_screen`, `contact_detail_screen`, `event_detail_screen`
- `sync_button`, `search_field`, `save_event_button`, `fact_add_button`, `fact_text_field`
- `note_text_field`, `contacts_list`, `events_list`, `add_event_fab`, `add_event_sheet`

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

- **Events/AddEvent UI tests are baseline only** — they verify app launch but don't navigate to events screen. A future agent should add a test-only nav entry point or a test activity that hosts individual screens.
- **No bottom navigation bar** — the current nav uses `NavDisplay` without a visible bottom bar. Navigation between contacts/events is handled programmatically.
- **ContactsSyncStressTest requires emulator** — it seeds the real Android Contacts Provider and can't run on a host JVM.

## For Future Agents

- Run `./scripts/pre-launch-check.sh` before committing to catch runtime crashes statically.
- The Navigation3 decorator pattern is a known footgun — always include both `rememberSaveableStateHolderNavEntryDecorator()` and `rememberViewModelStoreNavEntryDecorator()`.
- Test DI modules are in `app/src/androidTest/.../TestDatabaseModule.kt` and `TestRepositoryModule.kt`. They use `@TestInstallIn` to replace production modules.
- The `HiltTestRunner` in `app/src/androidTest/.../HiltTestRunner.kt` must use `ClassLoader?` (not `Context?`) for the first parameter of `newApplication`.