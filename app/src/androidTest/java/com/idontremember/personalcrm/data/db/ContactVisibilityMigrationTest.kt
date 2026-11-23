package com.idontremember.personalcrm.data.db

import org.junit.Assert.assertEquals
import org.junit.Test

class ContactVisibilityMigrationTest : MigrationTestBase() {
    @Test fun existingProfilesStartVisibleAndAllContextSurvives() {
        helper.createDatabase("contact-visibility-migration", 4).use { db ->
            db.seedSampleData()
            db.execSQL(
                "INSERT INTO follow_ups(id,contact_id,text,created_at,updated_at) VALUES(1,1,'Ask about tea',1000,2000)"
            )
            db.execSQL("INSERT INTO contact_tags(contact_id,tag_id) VALUES(1,1)")
        }
        helper.runMigrationsAndValidate(
            "contact-visibility-migration",
            5,
            true,
            MIGRATION_4_5
        ).use { db ->
            db.assertSampleDataPreserved()
            assertEquals(0L, db.longValue("contacts", "is_hidden"))
            assertEquals(1L, db.rowCount("visible_contacts"))
            db.execSQL("UPDATE contacts SET is_hidden = 1")
            assertEquals(0L, db.rowCount("visible_contacts"))
            assertEquals(1L, db.rowCount("contacts"))
            assertEquals(1L, db.rowCount("follow_ups"))
            assertEquals(1L, db.rowCount("contact_tags"))
        }
    }
}
