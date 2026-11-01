package com.idontremember.personalcrm.data.db

import com.idontremember.personalcrm.data.backup.BackupJson
import com.idontremember.personalcrm.data.backup.DataExporter
import com.idontremember.personalcrm.data.db.entity.*
import com.idontremember.personalcrm.data.repository.EventRepository
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
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
        val otherId = database.contactDao().insert(Contact(displayName = "Bob"))
        database.contactFactDao().insert(ContactFact(contactId = contactId, text = "Likes 🍕"))
        val eventId = database.eventDao().insert(Event(note = "Coffee 🎉"))
        val tagId = database.tagDao().insert(Tag(name = "coffee"))
        database.eventDao().insertEventContact(EventContact(eventId, contactId))
        database.eventDao().insertEventTag(EventTag(eventId, tagId))
        database.pilotDao().addTag(ContactTag(contactId, tagId))
        database.pilotDao().addRelationship(Relationship(contactId, otherId, "Friend"))

        val json = exporter.exportToJson()
        val snapshot = BackupJson.decode(json)
        database.clearAllTables()
        exporter.restore(snapshot)

        assertEquals("李 Alice", database.contactDao().getById(contactId)?.displayName)
        assertEquals("Likes 🍕", database.contactFactDao().getByContactIdOnce(contactId).single().text)
        assertEquals("Coffee 🎉", database.eventDao().getById(eventId)?.note)
        assertEquals(listOf(contactId), database.eventDao().getContactIdsForEvent(eventId))
        assertEquals(listOf(tagId), database.eventDao().getTagIdsForEvent(eventId))
        assertEquals(listOf(ContactTag(contactId, tagId)), database.pilotDao().allContactTags())
        assertEquals(listOf(Relationship(contactId, otherId, "Friend")), database.pilotDao().allRelationships())
        assertEquals(listOf(contactId), database.pilotDao().contactsForTag(tagId).first().map { it.id })
        database.tagDao().delete(Tag(id = tagId, name = "coffee"))
        assertEquals("Coffee 🎉", database.eventDao().getById(eventId)?.note)
        assertTrue(database.pilotDao().allContactTags().isEmpty())

        // A rejected backup must not delete the restored records.
        val invalid = snapshot.copy(eventContacts = listOf(com.idontremember.personalcrm.data.backup.EventContactRecord(eventId, 9999)))
        try { exporter.restore(invalid); throw AssertionError("Expected failure") }
        catch (_: IllegalArgumentException) { }
        assertTrue(database.contactDao().getById(contactId) != null)
    }

    @Test fun tagMembershipAndRelationshipsAreReciprocal() = runBlocking {
        val a = database.contactDao().insert(Contact(displayName = "Alice"))
        val b = database.contactDao().insert(Contact(displayName = "Bob"))
        val tag = database.tagDao().insert(Tag(name = "music"))
        val event = database.eventDao().insert(Event(note = "#music with Alice"))
        database.eventDao().insertEventContact(EventContact(event, a))
        database.eventDao().insertEventTag(EventTag(event, tag))
        database.pilotDao().addTag(ContactTag(a, tag))
        database.pilotDao().addTag(ContactTag(b, tag))
        assertEquals(listOf(a, b), database.pilotDao().contactsForTag(tag).first().map { it.id })
        val link = Relationship(a, b, "Friend")
        database.pilotDao().addRelationship(link)
        assertEquals(listOf(link), database.pilotDao().relationshipsFor(a).first())
        assertEquals(listOf(link), database.pilotDao().relationshipsFor(b).first())
        database.pilotDao().removeRelationship(link)
        assertTrue(database.pilotDao().relationshipsFor(b).first().isEmpty())
        database.tagDao().delete(Tag(id = tag, name = "music"))
        assertEquals("#music with Alice", database.eventDao().getById(event)?.note)
        assertTrue(database.pilotDao().contactsForTag(tag).first().isEmpty())
    }

    @Test fun failedEventLinkDoesNotLeaveOrphanEvent() = runBlocking {
        try { eventRepository.createEvent("Will fail", listOf(9999), emptyList())
            throw AssertionError("Expected foreign key failure")
        } catch (_: Exception) { }
        assertTrue(database.eventDao().getAllOnce().isEmpty())
    }
}
