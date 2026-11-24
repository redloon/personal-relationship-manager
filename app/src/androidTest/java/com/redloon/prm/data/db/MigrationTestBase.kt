package com.redloon.prm.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule

/**
 * Base class for Room migration tests (packet P4 — schema discipline).
 *
 * Provides the production `MigrationTestHelper` rule (schema JSONs loaded from the
 * test APK's assets, which `app/build.gradle.kts` populates from `app/schemas/`) and
 * generic seeding/assertion helpers. Every DB version bump gets a test that extends
 * this class.
 *
 * ## Recipe: adding a column to table `X` (e.g. P5/P6/P11)
 *
 * 1. Add the field to the entity (`data/db/entity/…`) with `@ColumnInfo` name.
 * 2. Bump `version` in `AppDatabase`'s `@Database` (see the policy comment there).
 * 3. Write `MIGRATION_<old>_<new>` in `data/db/Migrations.kt`:
 *    `object : Migration(old, new) { override fun migrate(db: SupportSQLiteDatabase) { … } }`
 *    (nullable column: `ALTER TABLE X ADD COLUMN y T`; dropping/rebuilding columns:
 *    copy the table-rebuild pattern in `AppDatabaseMigrationTest.SCRATCH_MIGRATION_2_3`).
 * 4. Register it in `di/DatabaseModule`'s `Room.databaseBuilder(…).addMigrations(…)`.
 * 5. Build once; commit the exported `app/schemas/…/<new>.json`.
 * 6. Add a test here: `helper.createDatabase(name, old)` → `seedSampleData()` →
 *    `helper.runMigrationsAndValidate(name, new, true, MIGRATION_…)` → assert
 *    `assertSampleDataPreserved()` + the new column's default value.
 *
 * See `AppDatabaseMigrationTest` for a complete worked example (v1→v2→v3 round-trip
 * with a scratch column that only exists in test fixtures).
 */
abstract class MigrationTestBase {

    /**
     * Production-schema helper. The `databaseClass` constructor keys schema lookups
     * on `AppDatabase`'s canonical name — i.e. the committed `app/schemas/` JSONs.
     * Must be a `@Rule` (it is a TestWatcher that closes DB connections).
     */
    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    /** Inserts one fully-populated row in every production table (FK order respected). */
    protected fun SupportSQLiteDatabase.seedSampleData() {
        execSQL(
            "INSERT INTO `contacts` (`id`, `android_contact_id`, `display_name`, " +
                "`phone_number`, `email`, `photo_uri`, `created_at`, `updated_at`) " +
                "VALUES (1, 42, 'Ada Lovelace', '+15550100', 'ada@example.com', NULL, 1000, 2000)"
        )
        execSQL(
            "INSERT INTO `contact_facts` (`id`, `contact_id`, `text`, `created_at`) " +
                "VALUES (1, 1, 'Likes tea', 1500)"
        )
        execSQL(
            "INSERT INTO `events` (`id`, `note`, `event_date`, `created_at`, `updated_at`) " +
                "VALUES (1, 'Coffee chat', 5000, 5000, 6000)"
        )
        execSQL("INSERT INTO `event_contacts` (`event_id`, `contact_id`) VALUES (1, 1)")
        execSQL("INSERT INTO `tags` (`id`, `name`, `created_at`) VALUES (1, 'friend', 1000)")
        execSQL("INSERT INTO `event_tags` (`event_id`, `tag_id`) VALUES (1, 1)")
    }

    /** Asserts the rows inserted by [seedSampleData] survived a migration untouched. */
    protected fun SupportSQLiteDatabase.assertSampleDataPreserved() {
        for (table in listOf(
            "contacts",
            "contact_facts",
            "events",
            "event_contacts",
            "tags",
            "event_tags"
        )) {
            assertEquals("row count for `$table`", 1L, rowCount(table))
        }
        assertEquals("Ada Lovelace", textValue("contacts", "display_name"))
        assertEquals("+15550100", textValue("contacts", "phone_number"))
        assertEquals(42L, longValue("contacts", "android_contact_id"))
        assertEquals("Likes tea", textValue("contact_facts", "text"))
        assertEquals("Coffee chat", textValue("events", "note"))
        assertEquals("friend", textValue("tags", "name"))
    }

    protected fun SupportSQLiteDatabase.rowCount(table: String): Long = longValue(table, null) ?: 0L

    /** True when [column] exists on [table] (`PRAGMA table_info`). */
    protected fun SupportSQLiteDatabase.columnExists(table: String, column: String): Boolean {
        query("PRAGMA table_info(`$table`)").use { cursor ->
            val nameIndex = cursor.getColumnIndex("name")
            while (cursor.moveToNext()) {
                if (cursor.getString(nameIndex) == column) return true
            }
        }
        return false
    }

    /** True when an index named [indexName] exists (`sqlite_master`). */
    protected fun SupportSQLiteDatabase.indexExists(indexName: String): Boolean {
        query(
            "SELECT count(*) FROM sqlite_master WHERE type = 'index' AND name = ?",
            arrayOf(indexName)
        ).use { cursor ->
            cursor.moveToFirst()
            return cursor.getLong(0) > 0
        }
    }

    /** First value of [column] in [table], or null when the table is empty. */
    protected fun SupportSQLiteDatabase.textValue(table: String, column: String): String? =
        query("SELECT `$column` FROM `$table` LIMIT 1").use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }

    /**
     * Aggregates [column] over [table], or `count(*)` when [column] is null;
     * null when the table is empty and no aggregate is requested.
     */
    protected fun SupportSQLiteDatabase.longValue(table: String, column: String?): Long? {
        val sql = if (column ==
            null
        ) {
            "SELECT count(*) FROM `$table`"
        } else {
            "SELECT `$column` FROM `$table` LIMIT 1"
        }
        return query(sql).use { cursor ->
            if (cursor.moveToFirst()) cursor.getLong(0) else null
        }
    }
}
