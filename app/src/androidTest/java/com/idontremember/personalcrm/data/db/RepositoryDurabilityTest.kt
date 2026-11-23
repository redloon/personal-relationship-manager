package com.idontremember.personalcrm.data.db

import android.content.Context
import android.database.sqlite.SQLiteException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.idontremember.personalcrm.data.db.entity.Contact
import com.idontremember.personalcrm.data.followups.FollowUpRepository
import com.idontremember.personalcrm.data.recovery.RecoveryRepository
import com.idontremember.personalcrm.data.recovery.TrashKind
import com.idontremember.personalcrm.data.repository.EventRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Real files and SQLite failures, rather than a mock DAO or in-memory database. */
@RunWith(AndroidJUnit4::class)
class RepositoryDurabilityTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "repository-durability.db"
    private lateinit var db: AppDatabase
    private lateinit var events: EventRepository

    private fun reopen() {
        if (::db.isInitialized) db.close()
        db = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
        events = EventRepository(db.eventDao(), db.contactDao(), db.tagDao(), db)
    }

    @Before fun setup() {
        context.deleteDatabase(name)
        reopen()
    }

    @After fun cleanup() {
        db.close()
        context.deleteDatabase(name)
    }

    private fun failTagLink() {
        db.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER injected_failure BEFORE INSERT ON event_tags " +
                "BEGIN SELECT RAISE(ABORT, 'injected failure'); END"
        )
    }

    private suspend fun expectInjectedFailure(operation: suspend () -> Unit) {
        try {
            operation()
            fail("Injected SQLite failure was not propagated")
        } catch (error: SQLiteException) {
            assertTrue(error.message.orEmpty().contains("injected failure"))
        }
    }

    @Test fun failedCreateRollsBackRecordPersonLinksAndNewTagAfterReopening(): Unit = runBlocking {
        val person = db.contactDao().insert(Contact(displayName = "Mira Rollback Trial"))
        failTagLink()
        expectInjectedFailure {
            events.createEvent("Lantern trial", listOf(person), listOf("partialtag"))
        }
        reopen()
        assertTrue(db.eventDao().getAllOnce().isEmpty())
        assertTrue(db.eventDao().getAllEventContacts().isEmpty())
        assertTrue(db.eventDao().getAllEventTags().isEmpty())
        assertTrue(db.tagDao().getAllOnce().isEmpty())
        assertNotNull(db.contactDao().getById(person))
    }

    @Test
    fun failedEditRestoresOriginalTextDateAndAssociationsAfterReopening(): Unit = runBlocking {
        val first = db.contactDao().insert(Contact(displayName = "Mira Trial"))
        val second = db.contactDao().insert(Contact(displayName = "Sam Trial"))
        val id = events.createEvent("Original", listOf(first), listOf("originaltag"), 123L)
        val original = events.getEventWithMeta(id)
        failTagLink()
        expectInjectedFailure {
            events.updateEvent(id, "Changed", listOf(second), listOf("partialtag"), 456L)
        }
        reopen()
        assertEquals(original, events.getEventWithMeta(id))
        assertEquals(listOf("originaltag"), db.tagDao().getAllOnce().map { it.name })
        assertEquals(1, db.eventDao().getAllOnce().size)
    }

    @Test fun completionFailureRollsBackEditorChangesAfterReopening(): Unit = runBlocking {
        val first = db.contactDao().insert(Contact(displayName = "Mira Trial"))
        val second = db.contactDao().insert(Contact(displayName = "Sam Trial"))
        val followUps = FollowUpRepository(db)
        val id = followUps.save(null, first, "Original intention", "2026-12-01")
        val original = followUps.get(id)
        db.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER injected_failure BEFORE UPDATE ON follow_ups " +
                "WHEN NEW.completed_at IS NOT NULL " +
                "BEGIN SELECT RAISE(ABORT, 'injected failure'); END"
        )
        expectInjectedFailure {
            followUps.saveAndComplete(id, second, "Changed intention", "2026-12-02", true)
        }
        reopen()
        assertEquals(original, FollowUpRepository(db).get(id))
        assertEquals(1, db.followUpDao().getAllOnce().size)
    }

    @Test fun failedDeletionRemainsActiveAfterReopening(): Unit = runBlocking {
        val id = events.createEvent("Original", emptyList(), emptyList())
        val original = events.getEventWithMeta(id)
        db.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER injected_failure AFTER UPDATE ON events " +
                "WHEN NEW.deleted_at IS NOT NULL " +
                "BEGIN SELECT RAISE(ABORT, 'injected failure'); END"
        )
        expectInjectedFailure { RecoveryRepository(db).delete(TrashKind.EVENT, id) }
        reopen()
        assertEquals(original, events.getEventWithMeta(id))
    }

    @Test fun editingAnUnavailableRecordCannotReportASuccessfulSave(): Unit = runBlocking {
        val id = events.createEvent("Original", emptyList(), emptyList())
        RecoveryRepository(db).delete(TrashKind.EVENT, id)
        reopen()
        try {
            events.updateEvent(id, "Must not save", emptyList(), emptyList(), 123L)
            fail("Unavailable record was reported as successfully saved")
        } catch (_: IllegalStateException) {
            assertEquals("Original", db.eventDao().getAllOnce().single().note)
        }
    }
}
