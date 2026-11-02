# Personal CRM

An Android app for tracking contacts, interactions, and personal facts. Built with Jetpack Compose, Hilt, Room, and Navigation3.

## Quick Start

```bash
mise install                  # Java 21
mise run hooks-install
mise run unit
mise run check
mise run test-stage           # then submit APK hashes through Android MCP
```

Canonical commands live in `.mise/tasks/`; without mise, use `bash .mise/tasks/TASK`. See [development.md](docs/development.md) for focused tests, hooks, and parallel-agent workflow.

Provision the SDK packages listed in [android-sandbox.md](docs/android-sandbox.md). Build, lint, and run JVM tests in the sandbox; use the host `android-tests` MCP for instrumented tests. Follow the [MCP runbook](scripts/host-mcp/README.md) for setup and execution. The legacy local-emulator `test` / `test-class` helpers are host-only and reject Docker execution.

Tasks discover JDK 21 and the SDK per environment (`ANDROID_HOME` / `ANDROID_SDK_ROOT`, tools on PATH, `~/android-sdk`, or `~/Android/Sdk`). Keep `sdk.dir` out of shared `local.properties`; it overrides environment discovery. Use `mise run phone-install` only for an explicitly requested physical-phone install.

## Everyday use

Home searches across people, facts, notes, linked people, tags, and follow-ups. Try a detail you remember, such as “climbing,” rather than a name. Recent context includes facts and notes. People supports name-only profiles; importing Android Contacts is optional and creates no follow-ups.

Use **Add a note**, the Quick Settings tile, the launcher shortcut, or Android text sharing to capture. The shared editor also supports facts, follow-ups, and editing saved notes. Closing preserves unfinished work; Discard removes it. Profiles provide person-scoped search and capture.

Follow-ups are voluntary. Add a date if you want an optional quiet morning reminder. Each item is notified once; unfinished items do not repeat. Android may delay delivery. Reminders and their time are controlled in Settings.

Use **Settings → Data → Export data** for a portable JSON copy, or choose a folder for daily backups. Automatic backup retains seven verified copies. Restore replaces current records after a safety export and confirmation; it detaches Android contact links for safe use on another phone. Link profiles explicitly afterward and re-enable reminders if wanted. Preferences, drafts, and folder permissions are not restored. Deleted notes, facts, and follow-ups can be recovered for 30 days in Settings.

### Development feedback

To review dogfooding notes, link events to a fake Android contact. On the host, save any open note, plug in the phone, and run from this repo:

```bash
mise run pull-dogfood
sbx run codex
```

Ask Codex to read the newest `ignored/dogfood-notes-*.md` and plan changes from it. Each pull creates a new UTC timestamped file and refuses to overwrite one; earlier notes remain available. The pull requires an installed debug build, briefly stops the app, and writes only linked event notes. It ignores emulators when choosing a device; if multiple phones are connected, run `./scripts/pull-dogfood-notes.sh 'Dogfooding Lname' SERIAL` instead. Launch the sandbox from a checkout that contains the generated file. This development note pull does not replace the JSON backup.

After review, record each actionable item as a fix, deferred idea in `roadmap.md`, or decision in `docs/decisions.md`; then delete reviewed dogfood events from the phone if desired. The timestamped pull remains available after deletion.

The original pilot gates are historical. Current scope prioritizes forgotten-context recall and voluntary follow-ups; see [product-intent.md](docs/product-intent.md) and [decisions.md](docs/decisions.md).

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
- **Strings & a11y (P2)**: every user-visible string lives in `res/values/` XML (`strings.xml` or a feature-specific strings file) (including icon `contentDescription`s, SimpleDateFormat patterns like `date_pattern_event`, and relative-date copy resolved via `RelativeDateStrings` in `ui/components/DateFormatting.kt`). `mise run check` fails on `Text("` or `contentDescription = "` literals under `ui/` — always extract there. All empty/loading/error states render through the shared `EmptyState` / `LoadingState` / `ErrorState` composables in `ui/components/` (each takes a snake_case `testTag`).
- **Contacts Sync**: `ContactsSyncManager` reads from Android's `ContactsContract` and upserts into Room. Local-only fields (facts, events) are preserved across syncs.
- **Room schema discipline (P4)**: every DB change ships as version bump + explicit `Migration` + committed schema JSON (`app/schemas/<db-class>/<version>.json`, exported via the `room.schemaLocation` KSP arg in `app/build.gradle.kts`) + a `MigrationTestHelper` migration test — all four, always. The policy comment at the top of `data/db/AppDatabase.kt` has the checklist; `androidTest/data/db/MigrationTestBase.kt`'s KDoc has the step-by-step recipe for adding a column. Migration tests read the committed schemas back from the test APK's assets (`androidTest.assets.srcDir("$projectDir/schemas")`). Never `fallbackToDestructiveMigration()` in release.

## Development and testing

Follow [development.md](docs/development.md). Workers use isolated worktrees and focused red-green JVM tests; one integration owner validates a stable combined source snapshot. `mise run check` runs JVM tests, builds both APKs, lint, and static checks. Failures block a clean handoff.

Android runtime validation uses [Android MCP](scripts/host-mcp/README.md): focused tests after a coherent block, then the full suite after integration. Report job IDs, test counts, and outcomes. A build is not a runtime test pass. Avoid repeating broad checks without new changes or unresolved failures.

Tests live in `app/src/test` (JVM) and `app/src/androidTest` (Android). Shared navigation helpers in `ui/TestNavHelpers.kt` preserve current Home/People navigation semantics; use the test tags defined by the screens and their behavioral tests.

## Known Limitations

- **Navigation** — Home and People are the primary tabs (`nav_tab_home` / `nav_tab_contacts`), each preserving its own history. Event history is a secondary Home action (`nav_tab_events`); use `navigateToEventsTab()` in existing tests.
- **ContactsSyncStressTest encodes Contacts Provider semantics** — expected counts come from the provider *after* aggregation (same-name raw contacts seeded on purpose merge into one `ContactsContract.Contacts` row), names are compared against `DISPLAY_NAME_PRIMARY` (the provider's name splitter reorders CJK names on read-back), and system-side lookups always use the aggregated contact id (`Contact.androidContactId == ContactsContract.Contacts._ID` — never the generator's synthetic ids or display names). Read the class doc in `ContactsSyncStressTest.kt` before changing it.
- **ContactsSyncStressTest requires emulator** — it seeds the real Android Contacts Provider and can't run on a host JVM. It needs WRITE_CONTACTS too, which only the debug build declares (`app/src/debug/AndroidManifest.xml`).

## For Future Agents

Read [AGENTS.md](AGENTS.md), [decisions.md](docs/decisions.md), and [development.md](docs/development.md). Reuse shared controls, preserve data, and coordinate interfaces and file ownership before parallel edits. The reusable worker is [android-feature-worker.toml](.codex/agents/android-feature-worker.toml).

Use bounded MCP diagnostics and defer to the agent owning attachment/host issues. Keep independent work moving, and report blocked runtime validation honestly rather than repeatedly probing unchanged infrastructure.
