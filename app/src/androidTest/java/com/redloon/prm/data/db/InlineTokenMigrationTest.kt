package com.redloon.prm.data.db

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InlineTokenMigrationTest : MigrationTestBase() {
    @Test fun versionFourPreservesProseLinksAndDeletedRecordsWithoutInferringTokens() {
        helper.createDatabase("inline-token-migration", 3).use { db ->
            db.seedSampleData()
            db.execSQL("UPDATE events SET note = '@Ada Lovelace likes #tea', deleted_at = 1234")
            db.execSQL(
                "INSERT INTO follow_ups(id,contact_id,text,created_at,updated_at) VALUES(1,1,'Ask about tea',1000,2000)"
            )
            db.execSQL("INSERT INTO contact_tags(contact_id,tag_id) VALUES(1,1)")
        }
        helper.runMigrationsAndValidate(
            "inline-token-migration",
            4,
            true,
            MIGRATION_3_4
        ).use { db ->
            assertEquals("@Ada Lovelace likes #tea", db.textValue("events", "note"))
            assertEquals("[]", db.textValue("events", "inline_tokens"))
            assertEquals(1234L, db.longValue("events", "deleted_at"))
            for (table in listOf(
                "contacts",
                "contact_facts",
                "events",
                "event_contacts",
                "tags",
                "event_tags",
                "contact_tags",
                "follow_ups"
            )) {
                assertEquals(table, 1L, db.rowCount(table))
            }
        }
    }
}
