package com.idontremember.personalcrm.data.db

import com.idontremember.personalcrm.data.backup.BackupJson
import com.idontremember.personalcrm.data.backup.DataExporter
import com.idontremember.personalcrm.data.db.entity.*
import com.idontremember.personalcrm.data.repository.EventRepository
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

@HiltAndroidTest
class BackupRestoreTest {
    @get:Rule val hiltRule = HiltAndroidRule(this)
    @Inject lateinit var database: AppDatabase
    @Inject lateinit var exporter: DataExporter
    @Inject lateinit var eventRepository: EventRepository

    @Before fun setup() {
        hiltRule.inject()
        database.clearAllTables()
    }

    @Test fun exportClearAndRestorePreservesRelationships() = runBlocking {
        val contactId = database.contactDao().insert(Contact(displayName = "李 Alice"))
        database.contactFactDao().insert(ContactFact(contactId = contactId, text = "Likes 🍕"))
        val eventId = database.eventDao().insert(Event(note = "Coffee 🎉"))
        val tagId = database.tagDao().insert(Tag(name = "coffee"))
        database.eventDao().insertEventContact(EventContact(eventId, contactId))
        database.eventDao().insertEventTag(EventTag(eventId, tagId))

        val json = exporter.exportToJson()
        val snapshot = BackupJson.decode(json)
        database.clearAllTables()
        exporter.restore(snapshot)

        assertEquals("李 Alice", database.contactDao().getById(contactId)?.displayName)
        assertEquals("Likes 🍕", database.contactFactDao().getByContactIdOnce(contactId).single().text)
        assertEquals("Coffee 🎉", database.eventDao().getById(eventId)?.note)
        assertEquals(listOf(contactId), database.eventDao().getContactIdsForEvent(eventId))
        assertEquals(listOf(tagId), database.eventDao().getTagIdsForEvent(eventId))

        // A rejected backup must not delete the restored records.
        val invalid = snapshot.copy(eventContacts = listOf(com.idontremember.personalcrm.data.backup.EventContactRecord(eventId, 9999)))
        try { exporter.restore(invalid); throw AssertionError("Expected failure") }
        catch (_: IllegalArgumentException) { }
        assertTrue(database.contactDao().getById(contactId) != null)
    }

    @Test fun failedEventLinkDoesNotLeaveOrphanEvent() = runBlocking {
        try { eventRepository.createEvent("Will fail", listOf(9999), emptyList())
            throw AssertionError("Expected foreign key failure")
        } catch (_: Exception) { }
        assertTrue(database.eventDao().getAllOnce().isEmpty())
    }
}
