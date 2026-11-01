import shutil
import sqlite3
import tempfile
import unittest
from pathlib import Path

from extract_dogfood_notes import extract


class ExtractDogfoodNotesTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        root = Path(self.directory.name)
        self.source = root / "source.db"
        self.copy = root / "copy.db"
        self.output = root / "dogfood-notes.md"
        self.db = sqlite3.connect(self.source)
        self.addCleanup(self.db.close)
        self.db.execute("PRAGMA journal_mode=WAL")
        self.db.executescript("""
            CREATE TABLE contacts (id INTEGER PRIMARY KEY, display_name TEXT);
            CREATE TABLE events (id INTEGER PRIMARY KEY, event_date INTEGER, note TEXT);
            CREATE TABLE event_contacts (event_id INTEGER, contact_id INTEGER);
        """)
        self.db.executemany("INSERT INTO contacts VALUES (?, ?)",
                            [(1, "Dogfooding"), (2, "Other")])
        self.db.executemany("INSERT INTO events VALUES (?, ?, ?)", [
            (10, 2000, "Later\nsecond line"),
            (11, 1000, "Earlier"),
            (12, 500, "Private unrelated note"),
        ])
        self.db.executemany("INSERT INTO event_contacts VALUES (?, ?)",
                            [(10, 1), (11, 1), (12, 2)])
        self.db.commit()

    def snapshot(self):
        for suffix in ("", "-wal", "-shm"):
            Path(str(self.copy) + suffix).unlink(missing_ok=True)
        shutil.copyfile(self.source, self.copy)
        wal = Path(str(self.source) + "-wal")
        if wal.exists():
            shutil.copyfile(wal, Path(str(self.copy) + "-wal"))

    def test_filters_orders_and_preserves_multiline_notes_from_wal(self):
        self.snapshot()
        self.assertEqual(extract(self.copy, "Dogfooding", self.output), 2)
        text = self.output.read_text()
        self.assertLess(text.index("Earlier"), text.index("Later\nsecond line"))
        self.assertNotIn("Private unrelated note", text)
        self.assertIn("event 10", text)

    def test_missing_or_duplicate_contact_keeps_previous_output(self):
        self.output.write_text("previous notes")
        self.snapshot()
        with self.assertRaisesRegex(ValueError, "found 0"):
            extract(self.copy, "Missing", self.output)
        self.db.execute("INSERT INTO contacts VALUES (3, 'Dogfooding')")
        self.db.commit()
        self.snapshot()
        with self.assertRaisesRegex(ValueError, "found 2"):
            extract(self.copy, "Dogfooding", self.output)
        self.assertEqual(self.output.read_text(), "previous notes")

    def test_empty_result_and_bad_copy(self):
        self.db.execute("DELETE FROM event_contacts WHERE contact_id = 1")
        self.db.commit()
        self.snapshot()
        self.assertEqual(extract(self.copy, "Dogfooding", self.output), 0)
        self.assertIn("No linked events found", self.output.read_text())
        self.copy.write_text("adb error")
        with self.assertRaisesRegex(ValueError, "SQLite database"):
            extract(self.copy, "Dogfooding", self.output)
        self.assertIn("No linked events found", self.output.read_text())

    def test_existing_output_is_never_overwritten(self):
        self.snapshot()
        self.output.write_text("earlier pull")
        with self.assertRaises(FileExistsError):
            extract(self.copy, "Dogfooding", self.output)
        self.assertEqual(self.output.read_text(), "earlier pull")


if __name__ == "__main__":
    unittest.main()
