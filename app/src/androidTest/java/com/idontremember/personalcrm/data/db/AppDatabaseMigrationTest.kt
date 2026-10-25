package com.idontremember.personalcrm.data.db

import androidx.room.migration.Migration
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Packet P4 worked example: migration scaffolding + schema discipline.
 *
 * Production `AppDatabase` **stays at version 1** — this test exercises the full
 * migration workflow against scratch versions 2 and 3 that exist only as test
 * fixtures (`app/src/androidTest/assets/scratch-schemas/`):
 *
 * - v1→v2 adds a scratch column `contact_facts.scratch_note`,
 * - v2→v3 removes it again via the table-rebuild pattern,
 * and both steps prove seeded data survives.
 *
 * The scratch schema JSONs live in `androidTest/assets/scratch-schemas/` (NOT in
 * `app/schemas/`) so future *production* version bumps can never collide with these
 * fixtures. `scratch-schemas/1.json` is a verbatim copy of the production
 * `app/schemas/…/1.json` export at the time of writing; `2.json`/`3.json` are derived
 * from it by exactly the column change of their step. This is why the scratch helper
 * below uses the assets-folder constructor instead of the `databaseClass` one.
 *
 * Downstream packets (P5/P6/P11) add *real* columns: follow the recipe in
 * [MigrationTestBase]'s KDoc and use the inherited `helper` (production schemas).
 */
@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest : MigrationTestBase() {

    /**
     * Scratch-schema helper, pointed at `assets/scratch-schemas/`. Kept separate from
     * the inherited production `helper` on purpose — see the class KDoc.
     */
    @get:Rule
    @Suppress("DEPRECATION") // assets-folder ctor: the only way to load non-production schema fixtures
    val scratchHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        SCRATCH_SCHEMA_ASSETS
    )

    /** v1→v2 worked example: add a nullable scratch column. */
    private val scratchMigration12 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `contact_facts` ADD COLUMN `scratch_note` TEXT")
        }
    }

    /**
     * v2→v3 worked example: remove the scratch column again. Uses the table-rebuild
     * pattern because `ALTER TABLE … DROP COLUMN` needs SQLite >= 3.35 (API 35+)
     * while minSdk is 24. Recreating the table must reproduce its FK clause and
     * re-create its index — both are validated below.
     */
    private val scratchMigration23 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // NOTE: Room runs migrations inside a transaction, where `PRAGMA foreign_keys`
            // is a no-op; kept as documentation of the safe pattern for table rebuilds.
            db.execSQL("PRAGMA foreign_keys = OFF")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `contact_facts_p4_scratch_tmp` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`contact_id` INTEGER NOT NULL, " +
                    "`text` TEXT NOT NULL, " +
                    "`created_at` INTEGER NOT NULL, " +
                    "FOREIGN KEY(`contact_id`) REFERENCES `contacts`(`id`) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE )"
            )
            db.execSQL(
                "INSERT INTO `contact_facts_p4_scratch_tmp` " +
                    "(`id`, `contact_id`, `text`, `created_at`) " +
                    "SELECT `id`, `contact_id`, `text`, `created_at` FROM `contact_facts`"
            )
            db.execSQL("DROP TABLE `contact_facts`")
            db.execSQL("ALTER TABLE `contact_facts_p4_scratch_tmp` RENAME TO `contact_facts`")
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_contact_facts_contact_id` " +
                    "ON `contact_facts` (`contact_id`)"
            )
            db.execSQL("PRAGMA foreign_keys = ON")
        }
    }

    /**
     * Smoke test for the production pipeline: the committed `app/schemas/…/1.json`
     * export is packaged into the test APK and creates a working v1 database.
     * This is the create-side of the recipe downstream packets follow.
     */
    @Test
    fun productionSchemaV1_createsUsableDatabaseFromCommittedJson() {
        val db = helper.createDatabase(PRODUCTION_DB, 1)
        try {
            db.seedSampleData()
            db.assertSampleDataPreserved()
        } finally {
            db.close()
        }
    }

    /** Step 1 of the round trip: v1→v2 adds the scratch column without losing rows. */
    @Test
    fun scratchColumn_v1ToV2_addsColumnAndPreservesData() {
        scratchHelper.createDatabase(SCRATCH_DB, 1).use { db ->
            db.seedSampleData()
        }
        val db = scratchHelper.runMigrationsAndValidate(SCRATCH_DB, 2, true, scratchMigration12)
        try {
            db.assertSampleDataPreserved()
            assertTrue(db.columnExists("contact_facts", "scratch_note"))
            assertNull(db.textValue("contact_facts", "scratch_note"))
        } finally {
            db.close()
        }
    }

    /** Full v1→v2→v3 round trip: column comes and goes, data never does. */
    @Test
    fun scratchColumn_v1ToV2ToV3_roundTripPreservesData() {
        scratchHelper.createDatabase(SCRATCH_DB, 1).use { db ->
            db.seedSampleData()
        }
        scratchHelper.runMigrationsAndValidate(SCRATCH_DB, 2, true, scratchMigration12).use { db ->
            assertTrue(db.columnExists("contact_facts", "scratch_note"))
        }
        val db = scratchHelper.runMigrationsAndValidate(SCRATCH_DB, 3, true, scratchMigration23)
        try {
            db.assertSampleDataPreserved()
            assertFalse(db.columnExists("contact_facts", "scratch_note"))
            // The rebuild must not lose the table's index (classic migration bug).
            assertTrue(db.indexExists("index_contact_facts_contact_id"))
        } finally {
            db.close()
        }
    }

    companion object {
        private const val PRODUCTION_DB = "p4-production-migration-test.db"
        private const val SCRATCH_DB = "p4-scratch-migration-test.db"
        private const val SCRATCH_SCHEMA_ASSETS = "scratch-schemas"
    }
}
