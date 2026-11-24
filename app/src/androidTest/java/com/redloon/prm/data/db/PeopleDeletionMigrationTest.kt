package com.redloon.prm.data.db

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PeopleDeletionMigrationTest : MigrationTestBase() {
    private val testDb = "people-deletion-migration"

    @Test fun existingVisibleHiddenAttachedAndDetachedPeopleAreProtected() {
        helper.createDatabase(testDb, 5).apply {
            seedSampleData()
            execSQL(
                "INSERT INTO contacts(id,display_name,created_at,updated_at,is_hidden) VALUES(2,'Unlinked legacy',1,1,1)"
            )
            close()
        }
        helper.runMigrationsAndValidate(testDb, 6, true, MIGRATION_5_6).apply {
            assertEquals("Ada Lovelace", textValue("contacts", "display_name"))
            query("SELECT android_contact_id FROM contacts WHERE id=1").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(42L, cursor.getLong(0))
            }
            query("SELECT is_hidden, android_contact_id FROM contacts WHERE id=2").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(1, cursor.getInt(0))
                assertTrue(cursor.isNull(1))
            }
            for (table in listOf(
                "contact_facts",
                "events",
                "event_contacts",
                "tags",
                "event_tags"
            )) {
                assertEquals(1L, rowCount(table))
            }
            query("SELECT deletion_allowed FROM contacts").use { cursor ->
                while (cursor.moveToNext()) assertEquals(0, cursor.getInt(0))
            }
            assertEquals(1L, rowCount("visible_contacts"))
            assertEquals(2L, rowCount("contacts"))
            close()
        }
    }
}
