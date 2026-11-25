# Usability principles

Use these preferences as a litmus test when designing, reviewing, or auditing the app. They supplement [product intent](product-intent.md) and [product decisions](decisions.md). Passing a checklist does not replace using the app.

The confirmed rules below come from human feedback on the 3 November 2026 [usability review](usability-review-2026-11-03.md) and subsequent clarifications, identified in each source. The original annotations are private; the relevant intent is preserved here. Add new preferences with a stable ID, source, example, and observable audit check. Revise an existing rule when feedback changes it; do not silently promote an agent's design choice into a human preference.

## Confirmed human preferences

| ID | Rule and reason | Example and audit check | Source |
| --- | --- | --- | --- |
| UX-01 | Keep typing and useful controls visible above the keyboard. Text should scroll inside its available area rather than push actions away. | With long text and the IME open, see the current line/caret and use Save, People, Tags, and date controls without dismissing the keyboard. Check narrow screens and enlarged fonts. | Issue 9 |
| UX-02 | Links require an explicit choice and must look different from prose. Ordinary `@` and `#` remain usable text. | An email or pasted `@Maya` creates no association. Selecting Maya produces a distinct token. Deleting the last token unlinks Maya. A no-people hint offers help but allows saving a bare note. | Issue 1; token-deletion clarification |
| UX-03 | Capture should take little effort and require no separate organizing session. Support real names, including spaces. | Create “Maya Chen” while capturing a note or follow-up, even when “Maya” already exists. Keep the text/date when cancelling the picker. Check app, quick tile, and share entry points. | Issues 2, 8, 14; full-name feedback |
| UX-04 | Open saved content for reading; make editing deliberate. Reveal more context progressively. | Tapping a fact opens its full text and Recorded date without a keyboard. Its person link opens the profile; an explicit pencil opens editing. | Issues 3, 10 |
| UX-05 | Preserve the context that made a search useful. | Search Home for a detail deep in a note and preserve that recall context on return. People filters names/phone/email, opens an unfiltered profile, and retains its directory query on return. Find a tag as an entity and open its page. | Issues 4, 6, 12; People behavior revised by 2026-11-22 dogfood #45 |
| UX-06 | Give readable content the width it needs. Put familiar contextual actions where phone users expect them. | Fact rows have no permanent Edit/Delete columns. Profile editing is available in the header. Use a labelled trash icon for deletion and accessible touch targets; do not require discovering a swipe. | Issues 10, 13; icon feedback |
| UX-07 | Distinguish intentions from known information and label dates truthfully. | Follow-ups look distinct from facts/notes, show Planned date and Completed state, and expose creation time in detail. A fact says Recorded, not when it became true. Status must not depend only on color. | Issue 7; follow-up visual feedback; existing date decision |
| UX-08 | Make incomplete previews visibly incomplete. | Truncated text has an ellipsis and Read more; opening reveals all saved text. Short text has no unnecessary expansion affordance. | Issue 11 |
| UX-09 | Make features and navigation discoverable, including when empty. Do not hide a contact's Tags and Relationships behind a disclosure button. | Tags and Relationships appear as sections without opening “People and tags”; the human had forgotten these hidden sections existed. Follow-ups remains reachable with many recent records or none. Empty copy neutrally offers creation. Tag rows visibly lead somewhere. History's label and filters accurately describe its contents. | Issues 4, 5, 12; tag-row feedback; human follow-up on 25 November 2026 |
| UX-10 | Reuse familiar interactions while respecting the meaning of each capture type. | Use the same person creation/picker in notes and follow-ups. Fact mode shares capture structure but selects a person outside prose and uses fact-specific prompts. | Issues 14, 15; fact-mode clarification |

Existing product constraints still apply: preserve original prose on rename, make follow-ups voluntary without guilt or generated obligations, and keep portable recovery reliable. Their source of truth is [decisions](decisions.md), rather than a duplicate policy here.

## Implementation defaults, open to revision

These choices implement the feedback; they are not additional human-confirmed preferences:

- Home shows three recent facts/notes and shortcuts to History, Tags, and Follow-ups.
- History contains captured notes, facts, and follow-ups. People and tags are searchable entities in universal search, not History records.
- A tag page has People and Notes sections. People combines direct and tagged-note membership; Notes contains explicitly tagged event notes, including those without people.
- Removing an association chip retains the original words as ordinary prose. Existing notes without token metadata do not acquire tokens by guessing from their text.
- Follow-up recall uses a reading sheet with explicit editing and completion actions.

## Using the library in an audit

For each finding, record the violated principle IDs, concrete reproduction, severity and user consequence, alternative fixes, and evidence. Exercise capture, recall, empty states, back/cancel, long content, narrow displays, enlarged fonts, keyboard, and accessibility. Report untested cases explicitly. Use automated behavioral regressions where meaningful; avoid source-pattern rules that mistake a particular layout for good usability.

## Deferred questions

- Surface unlinked notes deliberately: define whether this means no people, no tags, or both before building a dedicated view. Do not invent a virtual person to hold them.
- General merge remains deferred. Verified local profiles can be permanently deleted after an affected-data confirmation; imported, previously linked, and older unverified profiles remain protected. Preserve event prose and allow distinct people with the same name.

People recency and counts use past, non-deleted events only. Facts do not imply an interaction. This confirmed preference comes from the 2026-11-22 People management follow-up; audit both display and sorting, including missing/future events.
