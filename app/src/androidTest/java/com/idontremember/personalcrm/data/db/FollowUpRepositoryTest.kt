package com.idontremember.personalcrm.data.db

import com.idontremember.personalcrm.data.db.entity.Contact
import com.idontremember.personalcrm.data.followups.FollowUpRepository
import com.idontremember.personalcrm.data.recovery.RecoveryRepository
import com.idontremember.personalcrm.data.recovery.TrashKind
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

@HiltAndroidTest
class FollowUpRepositoryTest {
    @get:Rule val hilt = HiltAndroidRule(this)
    @Inject lateinit var db: AppDatabase
    @Inject lateinit var repository: FollowUpRepository
    @Inject lateinit var recovery: RecoveryRepository
    @Before fun setup() { hilt.inject(); db.clearAllTables() }

    @Test fun hundredsOfPeopleCreateNoObligations() = runBlocking {
        repeat(400) { db.contactDao().insert(Contact(displayName = "Person $it")) }
        assertTrue(repository.items.first().isEmpty())
        assertTrue(db.followUpDao().getAllOnce().isEmpty())
    }

    @Test fun explicitFollowUpCanBeCompletedReopenedRescheduledAndRecovered() = runBlocking {
        val person = db.contactDao().insert(Contact(displayName = "Morgan"))
        val id = repository.save(null, person, "Ask about the trip", "2026-11-02")
        val item = db.followUpDao().getById(id)!!
        db.followUpDao().update(item.copy(notifiedAt = 123L))
        repository.complete(id, true)
        assertNotNull(repository.get(id)?.completedAt)
        repository.complete(id, false)
        assertNull(repository.get(id)?.completedAt)
        assertEquals(123L, repository.get(id)?.notifiedAt) // Reopening must not nag again.
        repository.save(id, person, "Ask about the trip and photos", "2026-11-02")
        assertEquals(123L, repository.get(id)?.notifiedAt)
        repository.save(id, person, "Ask about the photos", "2026-11-03")
        assertNull(repository.get(id)?.notifiedAt)
        recovery.delete(TrashKind.FOLLOW_UP, id)
        assertTrue(repository.items.first().isEmpty())
        assertNull(repository.get(id))
        recovery.restore(TrashKind.FOLLOW_UP, id)
        assertEquals("Ask about the photos", repository.items.first().single().item.text)
    }

    @Test fun completingFromEditorKeepsCurrentTextPersonAndDate() = runBlocking {
        val first = db.contactDao().insert(Contact(displayName = "Morgan"))
        val second = db.contactDao().insert(Contact(displayName = "Sam"))
        val id = repository.save(null, first, "Old text", null)
        repository.saveAndComplete(id, second, "Updated text", "2026-12-01", true)
        val saved = repository.get(id)!!
        assertEquals("Updated text", saved.text)
        assertEquals(second, saved.contactId)
        assertEquals("2026-12-01", saved.dueDate)
        assertNotNull(saved.completedAt)
    }

    @Test fun invalidFollowUpDoesNotCreateData() = runBlocking {
        val person = db.contactDao().insert(Contact(displayName = "Morgan"))
        for (date in listOf("invalid", "2026-02-30")) {
            try { repository.save(null, person, "Trip", date); fail("Invalid date accepted") }
            catch (_: java.time.format.DateTimeParseException) { }
        }
        try { repository.save(null, person, "  ", null); fail("Empty follow-up accepted") }
        catch (_: IllegalArgumentException) { }
        assertTrue(db.followUpDao().getAllOnce().isEmpty())
    }
}
