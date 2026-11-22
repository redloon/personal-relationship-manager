# Usability exploration — 3 November 2026

I found **15 issues: 4 high, 10 medium, 1 low**. The biggest opportunities are making saved links trustworthy, making recall lead naturally to the person/context, and keeping useful Home actions visible as content grows.

I explored the normally launched Android app on the dedicated emulator, starting with empty app data and adding fictional people, facts, notes, tags and follow-ups. Findings below describe observed behavior; severity and proposed solutions are my judgments. **High** means a core task fails or leaves costly confusion to repair; **medium** means significant friction with a workaround; **low** means a local clarity problem. None establishes permanent data loss.

**1. Mention syntax can look meaningful without creating any links — High**

I entered and saved “Met @Oliver at #climbing. He is moving to Berlin next month.” The note saved with **no linked people or tags**. Typing/pasting the syntax is insufficient; a suggestion or picker selection must explicitly establish each link. Someone capturing quickly can believe they have associated a person and discover otherwise weeks later. The text remains searchable.

- **Option A:** Before saving unresolved mentions, offer an inline “Link Oliver / Create Oliver / Keep as text” choice. Resolve names only with user confirmation when ambiguous.
- **Option B:** Make linked mentions visually distinct tokens and ordinary text visibly ordinary; show a compact “No people linked” status with an Add person action. Explain that @ opens a picker.

[Evidence: saved note with no links](../ignored/mcp-artifacts/fdbb81686aa9415b970b448c0c8ddc01/b393a97c5d374e38b8bd3f7b7bbdaca7.png).

**2. Duplicate people are easy to create and hard to repair — High**

Adding “Maya Chen” a second time succeeded without a warning. Both People rows and follow-up picker options then looked identical. I found no delete-person or general merge-person action; Android-contact linking offers a separate, narrower merge path. This can split history between indistinguishable profiles.

- **Option A:** When creating a matching name, offer “Use existing Maya” first, alongside an explicit “Different person with this name.” Show a distinguishing saved detail.
- **Option B:** Add a small profile-management flow to merge duplicates or remove an accidental empty person, with a preview of affected information and recovery for removal.

[Evidence: duplicate People rows](../ignored/mcp-artifacts/fdbb81686aa9415b970b448c0c8ddc01/5fce1ba839f34256ae064c86342c1acf.png), [indistinguishable picker options](../ignored/mcp-artifacts/fdbb81686aa9415b970b448c0c8ddc01/14ebc623f3f640f98a939431a5e00f9c.png).

**3. Recalling a fact opens an editor — High**

Tapping a fact on Home opened “Edit fact,” focused the field and raised the keyboard. The person chip there is for removing an association, rather than visiting the person. A successful recall search therefore ends in a mutation surface instead of a comfortable reading/context view.

- **Option A:** Open the person's profile at the matching fact, highlighted, with editing available separately.
- **Option B:** Open a compact fact reading sheet containing the full text, recorded date, a tappable person link and an explicit Edit action.

[Evidence: fact opened from Home](../ignored/mcp-artifacts/fdbb81686aa9415b970b448c0c8ddc01/afc538814449469ba737de8a009e6faa.png).

**4. Tag browsing cannot retrieve the tagged notes — High**

After explicitly tagging notes, Tags → friends showed only “Maya Chen,” without the note explaining the match. Tags → climbing was completely blank when its tagged note had no linked person. Thus a note can have a valid tag but be unreachable through that tag's browsing screen. Combining direct and event-derived person membership is an intentional decision; the missing note retrieval is the usability gap.

- **Option A:** Show People and Notes sections on each tag page, including notes without people and the source of indirect person membership.
- **Option B:** Have a tag open existing universal-search results scoped to that tag, retaining the current person directory as a secondary view.

[Evidence: friends shows only a name](../ignored/mcp-artifacts/fdbb81686aa9415b970b448c0c8ddc01/ba6bbcd62709429696ad77e97038f4a6.png), [climbing is blank](../ignored/mcp-artifacts/fdbb81686aa9415b970b448c0c8ddc01/d6127ca8afec4c348eaee655f122c5aa.png).

**5. Home buries voluntary follow-ups beneath the recent feed — Medium**

With just one fact and one note, the follow-up heading reached the bottom of the initial viewport and its actual item was below it. The Add a note button also covered part of that area. The layout permits up to 20 recent entries before follow-ups. This makes a feature the user explicitly chose harder to rediscover as capture succeeds.

- **Option A:** Put a compact follow-up summary above recent context, with a count and an expandable preview; keep the language neutral.
- **Option B:** Give Follow-ups a persistent shortcut beside All notes and Tags, and shorten the recent preview to a few entries plus View all.

[Evidence: Home with only two recent entries](../ignored/mcp-artifacts/fdbb81686aa9415b970b448c0c8ddc01/7dff7577167340509c431dd572542ed6.png).

**6. People search finds the person but drops the remembered context — Medium**

Searching People for “Priya” correctly found Maya through a note. The result showed only Maya's name. Opening it reset to an unfiltered profile; Priya was outside the visible note preview. I had to search again to recover the detail that produced the match.

- **Option A:** Show a matching excerpt under each name and carry the query into the person's search view.
- **Option B:** Show the matching notes/facts as expandable results beneath each person, opening the selected item directly.

[Evidence: name-only result](../ignored/mcp-artifacts/fdbb81686aa9415b970b448c0c8ddc01/99ad9d02aee44a80ae0c0ebcf2f25602.png), [unfiltered destination](../ignored/mcp-artifacts/fdbb81686aa9415b970b448c0c8ddc01/40dff44ec02a4d629e2c49f1e5b5504e.png).

**7. Follow-up search results hide the useful date and completion state — Medium**

A follow-up scheduled for November 10 appeared in search with an unlabeled November 3 creation timestamp. After I marked it done, the result still looked the same; only reopening it revealed “Reopen.” Search can therefore make a finished action look outstanding and its date look wrong.

- **Option A:** Give follow-up results dedicated metadata: “Planned Nov 10” and “Completed,” using the app's date formatting.
- **Option B:** Separate open and completed follow-up results, show the scheduled date on open items, and put creation time in the detail view.

[Evidence: completed search result](../ignored/mcp-artifacts/fdbb81686aa9415b970b448c0c8ddc01/376b170633694f649f6530b8e4005a86.png), [same item showing its scheduled date and Reopen](../ignored/mcp-artifacts/fdbb81686aa9415b970b448c0c8ddc01/d235445667214967b53bfd19c2b396fe.png).

**8. Draft preservation is invisible outside the editor — Medium**

I wrote an unfinished note and closed it. Home showed no draft indicator, and the button still said “Add a note.” Tapping it silently resumed the old text under the same “Add a note” title. Preservation works, but I cannot tell whether I saved something, have unfinished work, or am starting fresh.

- **Option A:** Show a small “Unfinished note” card on Home with Resume and Discard actions; label the resumed editor accordingly.
- **Option B:** Change the capture button to “Resume draft” when appropriate, with a separate Start new action that safely preserves or explicitly replaces the draft.

[Evidence: Home after closing](../ignored/mcp-artifacts/fdbb81686aa9415b970b448c0c8ddc01/c0e7ad6e1d9247a7bbbd30ab9e9e9f8a.png), [silently resumed draft](../ignored/mcp-artifacts/fdbb81686aa9415b970b448c0c8ddc01/37102b52885c44c59e522731e14d0bbc.png).

**9. Longer capture text pushes linking controls below the keyboard — Medium**

A roughly 260-character note grew the editor until People, Tags and date controls were no longer visible above the keyboard. I had to dismiss the keyboard before attaching Maya. This interrupts the natural “write, associate, save” sequence and makes an unlinked save easier.

- **Option A:** Keep People and Tags in a fixed compact toolbar beside the fixed Save controls; let the text area scroll.
- **Option B:** Select people before text entry, with the selected people and an Add person control remaining visible above the field.

[Evidence: long capture with hidden linking controls](../ignored/mcp-artifacts/fdbb81686aa9415b970b448c0c8ddc01/c839074e0e704092804d2c418aeaab09.png).

**10. Fact actions consume much of the space needed to read facts — Medium**

On the tested narrow screen, Edit and Delete occupy two permanent columns beside each fact. A short fact wrapped across four lines, followed by a two-line recorded timestamp, while substantial space remained beside it. This reduces how much useful context fits on a profile.

- **Option A:** Replace the two icons with one overflow menu, giving text most of the width.
- **Option B:** Make the fact a full-width readable row and expose editing/deletion after opening it or through a contextual action menu.

[Evidence: narrow fact text beside actions](../ignored/mcp-artifacts/fdbb81686aa9415b970b448c0c8ddc01/575606c477484fa2adbf1d089a6f38f3.png).

**11. Profile note previews silently cut off text — Medium**

The long lunch note stopped at “running routes around the city,” then immediately displayed its timestamp. There was no ellipsis or Read more cue. The important detail about Priya's surgery was further down the saved note. A preview can look like the whole note and undermine quick recall.

- **Option A:** Add an ellipsis and a clear Read more affordance when text exceeds the preview.
- **Option B:** Allow inline expansion on the profile so reading the rest preserves the surrounding person context.

[Evidence: complete-looking truncated preview](../ignored/mcp-artifacts/fdbb81686aa9415b970b448c0c8ddc01/c83b8dbc88b7495eb508ce28ef741a68.png).

**12. “All notes” has a narrower scope than Home's “Notes” — Medium**

Home's search groups facts and follow-ups under “Notes,” but All notes opens an event-note-only list. My saved fact was absent there. The vocabulary makes it hard to predict where something was saved or whether a search is comprehensive. Keeping capture types separate can be useful; their scopes need to be explicit.

- **Option A:** Make All notes a complete capture history with optional Note, Fact and Follow-up filters.
- **Option B:** Keep the separate list, give it a distinct label such as “Interaction notes,” and use explicit result group labels on Home.

[Evidence: event-only Notes list](../ignored/mcp-artifacts/fdbb81686aa9415b970b448c0c8ddc01/600b4ea0b47b44ac9eeb891727daa569.png).

**13. Editing a person's name is hidden behind an unrelated label — Medium**

To rename a local person, I had to scroll past their context and follow-ups, expand “People and tags,” then scroll further to “Edit name.” The profile header offers no management action, and the expander does not indicate that it contains naming and Android-contact linking.

- **Option A:** Put Edit person in the profile header, opening a compact sheet for name and Android-contact association.
- **Option B:** Keep the lower section but label it “Person details,” add an expansion indicator, and place basic identity controls first.

[Evidence: management controls below People and tags](../ignored/mcp-artifacts/fdbb81686aa9415b970b448c0c8ddc01/4e54fd49a074431e92def8ed2abce13d.png).

**14. A follow-up cannot be captured for a new person in its own flow — Medium**

The follow-up picker requires an existing person. Searching for Oliver produced an empty dialog with only Close: no explanation or creation action. Save stays unavailable without a person. Unlike note capture, this flow makes a new encounter require leaving capture to create someone elsewhere.

- **Option A:** Reuse the note-capture picker, including inline person creation and immediate selection.
- **Option B:** Allow an explicitly created follow-up to be saved with a provisional typed name, then offer optional attachment to a profile later.

[Evidence: empty follow-up picker for a new person](../ignored/mcp-artifacts/fdbb81686aa9415b970b448c0c8ddc01/0d4375e503e64981a2faf26c4d055798.png).

**15. Fact capture gives contradictory instructions — Low**

Tapping Add fact on Maya's profile opened a form titled “Add a note,” prompted “What happened?”, and said “Select exactly one person using @” even though Maya was already selected. The separate People button also works, so the instruction overstates the required interaction.

- **Option A:** Use fact-specific title and prompt text, and show the person requirement only when it is unmet.
- **Option B:** Introduce a minimal person-bound Add fact sheet with a “What would you like to remember?” field and no person-selection step unless explicitly changed.

[Evidence: fact form with Maya already selected](../ignored/mcp-artifacts/fdbb81686aa9415b970b448c0c8ddc01/77694fcf1aa041548c25a51651e52a3a.png).

**Suggested order:** address 1–3 first to strengthen trust in capture and recall; then 5–6 to make Home and forgotten-context search more useful. Fix 4 before encouraging tag-based recall. Issues 7–15 can be grouped into small follow-up, capture and profile usability passes. These alternatives preserve explicit, voluntary follow-ups; none requires inactivity nudges, relationship scores or automatic obligations.

**What worked:** basic note saving, searching by a remembered detail, multi-term person search, person-scoped capture, converting note text into a follow-up, and restoring a deleted fact through Recently deleted. The Home search entry is a useful foundation.

**Evidence and limits:** checkout `b7051a4`; interactive session `fdbb81686aa9415b970b448c0c8ddc01`, dedicated `android_tests` emulator, 320 × 640 screenshot viewport. Session ended through terminal cleanup. `bash .mise/tasks/check` passed; this exploration is not an instrumented regression-suite pass. Layout findings are strongest for the tested narrow viewport. Large contact collections, TalkBack, other screen sizes, external sharing entry points, reminder delivery and complete backup/restore were not evaluated. Manual export reached completion and deleted-fact recovery was verified. No app source was changed.

[Short follow-up search/completion recording](../ignored/mcp-artifacts/fdbb81686aa9415b970b448c0c8ddc01/4a7e93aeb30f44bf8b092d8c9d88b0a6.mp4) · [Complete capture manifest](../ignored/mcp-artifacts/fdbb81686aa9415b970b448c0c8ddc01/manifest.json). Screenshots and video are local review artifacts under `ignored/`.
