# Dogfooding polish validation — 2026-11-02

Implemented the four priorities from `usability-pass-2026-11-02.md` in
`55cb694`, with keyboard synchronization in Android tests in `3260c16`.

- People explains manual creation first; visible copy uses People and Notes.
- Capture modes wrap as whole controls, and Home uses “Search anything”.
- Existing follow-ups open without the keyboard; new ones focus after expansion.
  Date, completion and footer actions wrap when needed.
- Restore has compact counts and one replacement warning, preserving the required
  safety export and transactional replacement.

No schema, backup-format or API changes. No additional product features.

## Automated validation

Pre-commit passed the Android check: 43 JVM tests, both APK builds, lint and source
guards. An initial lint cache/FIR crash was resolved by rerunning without Gradle's
daemon/configuration cache and with one worker; no gates were disabled.

The dedicated `android_tests` emulator passed all **100 instrumented tests** in
144.118 seconds, job `fc3c1f251fc44548a1cc1e55de3f40a3`; cleanup completed and
all logs were read. Focused runs also passed follow-ups (4), restore (3), large-text
capture (1, exercising 1×/1.3×/2×), and Fact selection (1).

The first full run passed 99/100: Fact selection raced the opening keyboard.
A focused reproduction confirmed the race; the test now waits for the actual IME
window and asserts mode selection. The final full run above includes that fix.

## Exploratory validation

Runner v4, fingerprint `3c439642…`, dedicated emulator `emulator-5580`, 320×640.
Fictional data only. Session `37e768e42fc1473b804278b36f341a3d`.
Ended passed with cleanup complete and runner idle; 140 actions across 20 passing
batches, with three retained failed exploratory batches.

- Capture a linked note, search its detail, reach the person, and complete an
  existing follow-up without the keyboard. A verified 20.75-second demo records it.
- Export backup A; create a newer note B; restore A and verify B is absent while
  A remains; restore the safety copy and verify B returns.
- At the largest system font setting, capture keeps the whole Follow-up label and
  Save reachable. Create a follow-up, select its person, save, and find it in search.
  With the keyboard open the editor body scrolls; dismissing it exposes person/date
  controls. Original system font setting restored afterward.
- Deny Contacts access in Android settings, relaunch, and manually create
  `Nora Permission QA`; the person's profile opens successfully.

Some exploratory batches stopped on incorrect or ambiguous selectors or a strict
visibility check on a partially scrolled field. Retained failures are described in
the local review; screenshot captions alone are not assertions of completed flows.

## Handoff

- APK: `ignored/dogfood-validation/personal-crm-dogfood.apk`
- App SHA-256: `1554facdb3880181fe0a870d74c71cd90e7d2afd416b89675cca5b159977333c`
- Test SHA-256: `855aeb5b1d03b335abfb12d1a7f659a937caf684e6acb4049af8fc57313b73da`
- Local review: `ignored/dogfood-validation/review.md`; verified gallery alongside it.
- Manual testing guide: [Dogfooding week](dogfooding-week.md).

No physical-phone installation was performed. Remaining limits: other screen sizes,
TalkBack, real scheduled reminder delivery, seven-day backup retention, and revoked
backup-folder access. Use the week to collect actual capture/recall friction before
adding features.
