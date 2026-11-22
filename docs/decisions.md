# Decisions

Record important, non-obvious product decisions here, briefly.

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
