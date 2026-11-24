package com.redloon.prm.data.db

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecallMigrationTest : MigrationTestBase() {
    @Test fun versionThreePreservesContextAndStartsWithNoObligations() {
        helper.createDatabase("recall-migration", 2).use { db ->
            db.seedSampleData()
            db.execSQL("INSERT INTO contact_tags(contact_id,tag_id) VALUES(1,1)")
        }
        helper.runMigrationsAndValidate("recall-migration", 3, true, MIGRATION_2_3).use { db ->
            db.assertSampleDataPreserved()
            assertEquals(1L, db.rowCount("contact_tags"))
            assertTrue(db.columnExists("contacts", "lookup_key"))
            assertNull(db.textValue("events", "deleted_at"))
            assertNull(db.textValue("contact_facts", "deleted_at"))
            assertEquals(0L, db.rowCount("follow_ups"))
        }
    }
}
