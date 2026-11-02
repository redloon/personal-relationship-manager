# Exploratory usability pass — 2026-11-02

Baseline: `c6dbd9d`. Dedicated `android_tests` emulator, 320×640 screen,
runner v4 fingerprint `3c439642…`. Session
`532e8e2e93b04e76a0b2c04887afe601` ended **passed**, cleanup complete, runner idle.
Fictional data only. This pass changed no application code.

## Confirmed behavior

- A roughly 570-character note remained editable with the keyboard open and Save reachable.
- Closing and reopening capture preserved its draft. Saving made it searchable.
- Searching `bridge` centered the result excerpt around the matching words.
  The initial suspicion that search only showed the beginning was wrong;
  screenshot review and `RecallSearch.excerpt` confirmed existing behavior.
- Closing an unsaved edit preserved the original note. The final accessibility
  text did not contain the inserted `CANCELLED EDIT` marker.
- Android's document picker saved a JSON export; reopening it showed the expected
  one-note preview. Cancel returned safely. Confirming required a separate safety
  export; saving it returned to Settings and the note remained searchable.
  Because the backup and current data were identical, this is picker/flow evidence,
  not independent proof of replacement semantics; automated restore tests cover those.
- At the system font slider's largest setting, capture and Save remained usable.
  Restored the original font tick afterward; display size was unchanged.
- Revoked Contacts access in Android settings, denied the app's subsequent request,
  and successfully created `Mira Local QA` manually.
- Created an undated voluntary follow-up, reopened it, marked it done, and verified
  it disappeared from the pending list.

## Recommended next changes

| Priority | Observed friction | Small improvement |
| --- | --- | --- |
| 1 | Empty People says “Tap refresh to sync from Android contacts,” while manual creation is available. Profile and backup copy still call notes “events.” | Explain Add person first, keep import optional, use Notes consistently. |
| 2 | Largest-font capture forces Follow-up into “Follo / w-up”; the title also wraps. Home's search field takes substantial space. | Let capture choices wrap as whole controls; check header/search density with large text and keyboard open. |
| 3 | Opening an existing follow-up launches the keyboard even when the only action is Mark done. | Focus the editor on deliberate editing; keep completion immediately available. |
| 4 | Restore preview repeats replacement warnings and uses dense technical counts. | One concise replacement warning, familiar record names, retain the explicit safety-copy action. |

No search-snippet rework is justified by this pass. Settings scroll loss was not
confirmed: broad automation swipes skipped the target. No blocking app failure was
observed in these journeys; this is a bounded pass, not exhaustive QA.

## Evidence and limits

Local evidence is deliberately ignored by Git:

- `ignored/usability-pass/review.md`: curated screenshots and findings.
- `ignored/usability-pass/gallery.md`: full manifest-verified image gallery.
- `ignored/usability-pass/evidence.json`: terminal statuses, actions and consumed logs.

There were 122 actions in 23 passing batches. Seven additional exploratory batches
stopped on missing selectors/visibility waits: broad swipes, app recreation after
font changes, appended search input, and platform permission-label differences.
They remain in the evidence; they are not counted as passing journeys.
Screenshots can precede animation/keyboard settling, so captions alone are not proof.
Concurrent gateway log collection stalled; sequential collection completed.

Pre-commit ran the Android check and tooling gates successfully for the baseline.
Previous full instrumented validation remains 94 passed, job
`3223034f07c44987b2733693ee3dd02b`; this interactive pass is separate evidence.
No application inputs changed, so the full suite was not repeated.

Not covered here: actual scheduled reminder delivery, seven-day backup retention,
folder permission revocation, restore with differing datasets, other screen sizes,
TalkBack, and physical-device behavior. The emulator retains fictional QA data and
denied Contacts access; the next runner session clears data and grants test permissions.
