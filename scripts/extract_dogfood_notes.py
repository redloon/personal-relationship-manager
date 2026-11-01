#!/usr/bin/env python3
"""Render events linked to one exact contact from a copied Room database."""

import os
from pathlib import Path
import sqlite3
import sys
import tempfile
from datetime import datetime, timezone


def extract(db_path: Path, contact_name: str, output: Path) -> int:
    with db_path.open("rb") as source:
        header = source.read(16)
    if header != b"SQLite format 3\0":
        raise ValueError("adb did not return a SQLite database; check the debug build and device")

    with sqlite3.connect(f"{db_path.resolve().as_uri()}?mode=ro", uri=True) as db:
        contacts = db.execute(
            "SELECT id FROM contacts WHERE display_name = ?", (contact_name,)
        ).fetchall()
        if len(contacts) != 1:
            raise ValueError(
                f"Expected one contact named {contact_name!r}; found {len(contacts)}"
            )
        events = db.execute(
            """SELECT e.id, e.event_date, e.note
               FROM events e JOIN event_contacts ec ON ec.event_id = e.id
               WHERE ec.contact_id = ?
               ORDER BY e.event_date, e.id""",
            (contacts[0][0],),
        ).fetchall()

    lines = [f"# Dogfood notes for {contact_name}", ""]
    if not events:
        lines.extend(["No linked events found.", ""])
    for event_id, event_date, note in events:
        timestamp = datetime.fromtimestamp(event_date / 1000, timezone.utc)
        lines.extend([f"## {timestamp.isoformat()} · event {event_id}", "", note, ""])

    output.parent.mkdir(parents=True, exist_ok=True)
    temp_name = None
    try:
        with tempfile.NamedTemporaryFile(
            mode="w", encoding="utf-8", dir=output.parent,
            prefix=".dogfood-notes-", delete=False,
        ) as temp:
            temp_name = temp.name
            os.chmod(temp_name, 0o600)
            temp.write("\n".join(lines))
        # Publish atomically without ever replacing an earlier pull.
        os.link(temp_name, output)
    finally:
        if temp_name and os.path.exists(temp_name):
            os.unlink(temp_name)
    return len(events)


if __name__ == "__main__":
    if len(sys.argv) != 4:
        raise SystemExit("Usage: extract_dogfood_notes.py DB CONTACT_NAME OUTPUT")
    try:
        count = extract(Path(sys.argv[1]), sys.argv[2], Path(sys.argv[3]))
    except (OSError, sqlite3.Error, ValueError) as error:
        raise SystemExit(f"Dogfood pull failed: {error}") from error
    print(f"Wrote {count} event(s) to {sys.argv[3]}")
