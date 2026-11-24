package com.redloon.prm.data.db

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class OldestSchemaMigrationTest : MigrationTestBase() {
    @Test
    fun oldestPopulatedSchemaUpgradesToCurrent() {
        val name = "oldest-to-current.db"
        helper.createDatabase(name, 1).use { it.seedSampleData() }
        helper.runMigrationsAndValidate(
            name,
            6,
            true,
            MIGRATION_1_2,
            MIGRATION_2_3,
            MIGRATION_3_4,
            MIGRATION_4_5,
            MIGRATION_5_6
        ).use { db ->
            db.assertSampleDataPreserved()
            assertEquals(6, db.version)
            assertEquals("[]", db.textValue("events", "inline_tokens"))
            assertEquals(0L, db.longValue("contacts", "is_hidden"))
            assertEquals(0L, db.longValue("contacts", "deletion_allowed"))
            assertNull(db.textValue("contacts", "lookup_key"))
            assertNull(db.textValue("events", "deleted_at"))
            assertNull(db.textValue("contact_facts", "deleted_at"))
            assertEquals(1L, db.rowCount("visible_contacts"))
            for (table in listOf("contact_tags", "relationships", "follow_ups")) {
                assertEquals(0L, db.rowCount(table))
            }
            db.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
        }
    }
}
