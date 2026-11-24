/*
 * ============================================================================
 * ROOM SCHEMA / MIGRATION POLICY (packet P4) — every DB change, always:
 *
 *   1. BUMP the `version` in the @Database annotation below.
 *   2. MIGRATION: write an explicit `androidx.room.migration.Migration` for every
 *      version step (live in `data/db/Migrations.kt`) and register it in the
 *      production builder via `DatabaseModule`'s `.addMigrations(...)`.
 *   3. SCHEMA JSON: commit the schema KSP exports to
 *      `app/schemas/com.redloon.prm.data.db.AppDatabase/<version>.json`
 *      (export wired via the `room.schemaLocation` KSP arg in app/build.gradle.kts).
 *   4. MIGRATION TEST: add a `MigrationTestHelper` test (androidTest) that migrates
 *      a *populated* database from the previous version and asserts data
 *      preservation. Extend `data/db/MigrationTestBase` and follow
 *      `AppDatabaseMigrationTest`'s worked example.
 *   All four, together, every time. Never ship 1-2 without 3-4, never 3-4 without 1-2.
 *
 * NEVER call `fallbackToDestructiveMigration()` on a release code path — it silently
 * destroys user data. If it is ever needed for debug iteration only, guard it behind
 * `BuildConfig.DEBUG` (note: `buildConfig = false` today; enabling it is part of that
 * decision) so a release build can never ship it.
 *
 * Production schema history:
 *   1 = initial schema: contacts, contact_facts, events, event_contacts, tags,
 *       event_tags (see app/schemas/.../1.json for the authoritative snapshot).
 *   2 = direct contact tags and shared relationships.
 *   3 = recoverable notes, provider lookup keys, explicit follow-ups.
 *   4 = explicit inline token metadata, preserving original prose.
 *   5 = non-destructive person hiding and shared visible_contacts view.
 *   6 = protected-by-default person deletion eligibility.
 * ============================================================================
 */
package com.redloon.prm.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.redloon.prm.data.db.dao.ContactDao
import com.redloon.prm.data.db.dao.ContactFactDao
import com.redloon.prm.data.db.dao.EventDao
import com.redloon.prm.data.db.dao.FollowUpDao
import com.redloon.prm.data.db.dao.ProfileLinksDao
import com.redloon.prm.data.db.dao.RecallDao
import com.redloon.prm.data.db.dao.TagDao
import com.redloon.prm.data.db.entity.Contact
import com.redloon.prm.data.db.entity.ContactFact
import com.redloon.prm.data.db.entity.ContactTag
import com.redloon.prm.data.db.entity.Event
import com.redloon.prm.data.db.entity.EventContact
import com.redloon.prm.data.db.entity.EventTag
import com.redloon.prm.data.db.entity.FollowUp
import com.redloon.prm.data.db.entity.Relationship
import com.redloon.prm.data.db.entity.Tag

@Database(
    entities = [
        FollowUp::class,
        Contact::class,
        ContactFact::class,
        Event::class,
        EventContact::class,
        Tag::class,
        EventTag::class,
        ContactTag::class,
        Relationship::class
    ],
    views = [com.redloon.prm.data.db.entity.VisibleContact::class],
    version = 6,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun followUpDao(): FollowUpDao
    abstract fun recallDao(): RecallDao
    abstract fun profileLinksDao(): ProfileLinksDao
    abstract fun contactDao(): ContactDao
    abstract fun contactFactDao(): ContactFactDao
    abstract fun eventDao(): EventDao
    abstract fun tagDao(): TagDao
}
