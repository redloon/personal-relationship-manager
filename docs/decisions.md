# Decisions

Record important, non-obvious product decisions here, briefly.

## 2026-11-22 — Unlinked people, permanent deletion and interaction sorting

Unlinked people is the user-facing name for profiles without a current Android attachment; hidden profiles remain in Hidden people. Newly created local profiles may be permanently deleted after an affected-data confirmation from any People list. Imported or ever-linked profiles are protected permanently. Earlier profiles with unverifiable history are protected too; a missing attachment is not proof of local creation. This adds bounded local-profile deletion to the earlier management decision; general merge remains deferred.

Person deletion removes their facts, follow-ups (including trash), relationships and direct tag links. Event notes remain, even without any people; their prose, dates, shared tags and other people are preserved. Removed-person inline metadata becomes ordinary original prose. Person deletion has no Undo or 30-day retention. Mixed protected selections are rejected together and eligibility/counts are rechecked transactionally.

Last interaction and interaction count derive solely from non-deleted events whose event date/time has occurred. Facts and future events never affect these metrics. People subtitles intentionally show relative recency; profile dates continue honoring the date-display preference. No manually maintained last-seen field or relationship score.

People, Unlinked people and Hidden people independently remember name, last interaction, date-added or interaction-count sorting in either direction. Default is most-recent interaction. Missing interaction dates stay last in either direction; metric ties use case-insensitive name then person ID. Date added retains the original profile creation time through restore.

A protected-by-default deletion capability is backed up from portable format 5 onward. Older backups restore as protected; imported attachments are protected before portable restore detaches device identities. New local profiles retain eligibility across backup/restore only if never linked.

## 2026-11-22 — App-only people and directory search

App-only people are temporary profiles without an Android attachment, including restored or detached profiles awaiting relinking. People and Settings offer the same management page, excluding hidden profiles. Correct names and remembered context through the normal profile; no separate inline editing, app phone/email editor, Android contact creation, or general merge flow. Bounded deletion is governed by the newer Unlinked people decision. Explicit linking adopts the Android name and keeps app identity, context and visibility. Android Contacts remain authoritative for attached names and contact details.

People searches only names, phone numbers and email, using case-insensitive terms that must all match. It opens an unfiltered profile and retains its directory query on return. Home remains universal recall, including saved context. This supersedes the earlier People deep-context search example in UX-05 (dogfood #45).

Mention suggestions must survive keyboard touches and incremental full-name typing. Choosing Keep as text dismisses suggestions deliberately; ordinary keyboard input does not. Suggestions fit above or below the typing field within the space above the keyboard; long menus scroll and leave the header actions unobstructed. Last interaction uses the past-event-only policy recorded above.

Opening a configured backup folder is a best-effort viewing action with read access. An unavailable viewer or revoked access reports failure without changing backup configuration or reopening the folder picker.

Invitation/decline tracking (#42) remains an exploration: saved notes can record this context today. Do not add counters, scores or obligations without a clear decision they would support. Keep Home and People as the primary bottom destinations; Settings remains reachable through the gear (#48), since configuration is occasional rather than a primary capture/recall task.

## 2026-11-22 — Clear identity for Hidden people

People keeps its toolbar sync action without a duplicate import button beneath search. Hidden people uses a persistent eye-off icon, page label and distinct theme-based header color, including during bulk selection. The selected count appears beneath the page label, with room for enlarged text. Keep the explanation brief; color supplements the label and icon. Selection and visibility behavior remain governed by the non-destructive hiding decision.

## 2026-11-21 — Non-destructive person hiding and bulk selection

Long-press on People starts multi-selection; a visible Select people menu offers the same action. Hiding changes only a profile's visibility, never its data, timestamps, Android attachment or associations. Unhiding restores discovery of that same profile. Sync updates hidden profiles normally and preserves visibility. No automatic classification or import triage.

Person discovery uses the shared `visible_contacts` view: People, universal person results, recent people, tag People sections and all in-app person selectors exclude hidden profiles. New discovery queries must use that view or the repository's default filtered APIs. Explicit all-record access is reserved for saved associations, hidden management, sync, backup and identity operations. Saved notes, facts, follow-ups, existing relationships and their person links remain readable/searchable; reminders and drafts are unchanged. Search can therefore still find saved content about a hidden person.

Manual exports and automatic backups include hidden profiles, their hidden state and saved context. Older backups without visibility metadata restore profiles as visible. Portable restore preserves the hidden state on restored profiles but detaches Android identities. A later sync can import the same Android contacts as new visible profiles until explicitly relinked; hiding belongs to an app profile and does not provide a portable address-book exclusion list.

Selection persists across searches, and Select all results adds only current results. Bulk actions consume the selected IDs independently of the selection controls; hiding is the first action, not a special selection mode. Undo affects only profiles changed by its operation.

## 2026-11-22 — Free software without project monetization

Personal CRM is licensed under GPL-3.0-or-later and will remain free to use, without monetization. Distributed derivatives must comply with the GPL's source-sharing requirements. Commercial use remains permitted by the license; the no-monetization commitment is this project's policy. Preserve existing third-party license notices.

## 2026-11-04 — Explicit links and shared capture

Typing or pasting `@`/`#` is ordinary prose until a person/tag is explicitly selected. Selected inline mentions have distinct token styling; deleting the last token for a target removes its association. Picker-only links remain independently removable. Removing a link chip preserves the words as plain text. Legacy prose is never parsed to invent token identity. Facts use the shared capture sheet with a separate person selector, without inserting that person's name into the fact text. Unlinked event notes are valid.

## 2026-11-04 — Read first and preserve recall context

Open facts and follow-ups in reading views with explicit edit actions. Preserve search context when entering a profile. Universal search exposes people, tags, notes, facts, and follow-ups as distinct types. History contains captured notes, facts, and follow-ups; tag pages show both their people and explicitly tagged event notes, including notes without people. Keep follow-ups visibly distinct as intentions.

## 2026-10-31 — Preserve event text when a contact is renamed

An event note keeps the words originally written, including an old `@Name`. The event's contact link stays attached, and its person chip shows the contact's current name. Rewriting old prose could change its meaning. Use the link to reach that person's history.

## 2026-11-01 — Use the fact's recorded date

Show when a fact was entered as “Recorded”. Do not imply that it is the date the fact became true; revisit editable dates if the pilot needs them.

## 2026-11-01 — Pilot discovery and links

Recent people are ordered by their latest event. Tag membership combines direct person tags with tagged events, without duplicates; deleting a tag keeps event prose. Relationship labels are shared in both directions, and multiple labels may connect a pair. Defer commonality suggestions, extra relationship metadata, editable fact dates, and advanced tag management until pilot use shows a need.

## 2026-11-01 — Expand around remembering context

Capture and recall have enough value to justify a substantial usability rework. Home and People are the primary destinations; history and tags are secondary. Universal search includes facts, event text, linked people, tags and voluntary follow-ups. Recent context includes facts and events; last interaction remains derived only from events. People can exist without Android contacts. Preserve original prose and facts' recorded dates.

## 2026-11-01 — Follow-ups must never create obligations automatically

Only explicitly created follow-ups exist. Importing contacts creates none. Optional dated follow-ups receive at most one discreet morning summary a day, and each item is notified once; unfinished items do not repeat. No overdue warnings, relationship scores, inactivity nudges or automatic outreach tasks. Reminder delivery is approximate and permission is contextual.

## 2026-11-01 — Portable recovery

Use optional daily versioned JSON backups to a selected folder, retaining seven verified automatic copies. Deleted events, facts and follow-ups are recoverable for 30 days. Restore is transactional replacement after a safety export. Restore detaches Android contact IDs and photo URIs; relinking is explicit, and reminders require re-enabling. Exclude implicit platform backup paths so they cannot bypass these safeguards.
