# Collecting development feedback

This workflow is for reviewing notes from the maintainer's debug build on a phone. It is separate from the app's data export and backup features.

Link feedback notes to a dedicated fictional Android contact. On the host, save any open note, connect the phone, and run:

```bash
mise run pull-dogfood
sbx run codex
```

Ask Codex to read the newest `ignored/dogfood-notes-*.md`. Each pull creates a UTC timestamped file and refuses to overwrite an existing one. The phone needs an installed debug build; the pull briefly stops the app and extracts only linked event notes.

Emulators are ignored when selecting a device. With multiple phones connected, specify the feedback contact and device serial:

```bash
./scripts/pull-dogfood-notes.sh 'Dogfooding Lname' SERIAL
```

Start the sandbox from the checkout containing the generated file. Classify actionable feedback as a fix, a deferred idea in `roadmap.md`, or a decision in `docs/decisions.md`. Reviewed feedback events may then be deleted from the phone; the pulled notes remain available.
