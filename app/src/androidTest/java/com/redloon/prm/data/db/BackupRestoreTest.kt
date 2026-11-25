package com.redloon.prm.data.db

import com.redloon.prm.data.backup.BackupJson
import com.redloon.prm.data.backup.DataExporter
import com.redloon.prm.data.contacts.ProfilePair
import com.redloon.prm.data.db.entity.Contact
import com.redloon.prm.data.db.entity.ContactFact
import com.redloon.prm.data.db.entity.ContactTag
import com.redloon.prm.data.db.entity.Event
import com.redloon.prm.data.db.entity.EventContact
import com.redloon.prm.data.db.entity.EventTag
import com.redloon.prm.data.db.entity.FollowUp
import com.redloon.prm.data.db.entity.Relationship
import com.redloon.prm.data.db.entity.Tag
import com.redloon.prm.data.recovery.RecoveryRepository
import com.redloon.prm.data.recovery.TrashKind
import com.redloon.prm.data.repository.ContactRepository
import com.redloon.prm.data.repository.EventRepository
import com.redloon.prm.data.settings.SettingsRepository
import com.redloon.prm.testdata.PopulationFixtures
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@HiltAndroidTest
class BackupRestoreTest {
    @get:Rule val hiltRule = HiltAndroidRule(this)

    @Inject lateinit var database: AppDatabase

    @Inject lateinit var exporter: DataExporter

    @Inject lateinit var recovery: RecoveryRepository

    @Inject lateinit var contacts: ContactRepository

    @Inject lateinit var settings: SettingsRepository

    @Inject lateinit var eventRepository: EventRepository

    @Inject lateinit var tagRepository: com.redloon.prm.data.repository.TagRepository

    @Before fun setup() {
        hiltRule.inject()
        database.clearAllTables()
        runBlocking { settings.clearDismissedReconnectPairs() }
    }

    @Test fun exportClearAndRestorePreservesRelationships() = runBlocking {
        val background = PopulationFixtures.seedBackgroundPeople(database.contactDao())
        val contactId = database.contactDao().insert(Contact(displayName = "李 Alice"))
        val otherId = database.contactDao().insert(Contact(displayName = "Bob"))
        database.contactFactDao().insert(ContactFact(contactId = contactId, text = "Likes 🍕"))
        val eventId = database.eventDao().insert(Event(note = "Coffee 🎉"))
        val tagId = database.tagDao().insert(Tag(name = "coffee"))
        database.eventDao().insertEventContact(EventContact(eventId, contactId))
        database.eventDao().insertEventTag(EventTag(eventId, tagId))
        database.profileLinksDao().addTag(ContactTag(contactId, tagId))
        database.profileLinksDao().addRelationship(Relationship(contactId, otherId, "Friend"))

        val json = exporter.exportToJson()
        val snapshot = BackupJson.decode(json)
        database.clearAllTables()
        exporter.restore(snapshot)

        assertEquals(62, database.contactDao().getAllIncludingHiddenOnce().size)
        assertEquals(
            background,
            database.contactDao().getAllIncludingHiddenOnce().filter {
                it.displayName.startsWith("Zzz Background")
            }.map { it.id }
        )

        assertEquals("李 Alice", database.contactDao().getById(contactId)?.displayName)
        assertEquals(
            "Likes 🍕",
            database.contactFactDao().getByContactIdOnce(contactId).single().text
        )
        assertEquals("Coffee 🎉", database.eventDao().getById(eventId)?.note)
        assertEquals(listOf(contactId), database.eventDao().getContactIdsForEvent(eventId))
        assertEquals(listOf(tagId), database.eventDao().getTagIdsForEvent(eventId))
        assertEquals(
            listOf(ContactTag(contactId, tagId)),
            database.profileLinksDao().allContactTags()
        )
        assertEquals(
            listOf(Relationship(contactId, otherId, "Friend")),
            database.profileLinksDao().allRelationships()
        )
        assertEquals(
            listOf(contactId),
            database.profileLinksDao().contactsForTag(tagId).first().map {
                it.id
            }
        )
        database.tagDao().delete(Tag(id = tagId, name = "coffee"))
        assertEquals("Coffee 🎉", database.eventDao().getById(eventId)?.note)
        assertTrue(database.profileLinksDao().allContactTags().isEmpty())

        // A rejected backup must not delete the restored records.
        val invalid = snapshot.copy(
            eventContacts = listOf(
                com.redloon.prm.data.backup.EventContactRecord(eventId, 9999)
            )
        )
        try {
            exporter.restore(invalid)
            throw AssertionError("Expected failure")
        } catch (
            _: IllegalArgumentException
        ) { }
        assertTrue(database.contactDao().getById(contactId) != null)
    }

    @Test fun portableRestoreDetachesDeviceIdentityAndKeepsTrash() = runBlocking {
        val person = database.contactDao().insert(
            Contact(
                displayName = "Alice",
                androidContactId = 42,
                photoUri = "content://old-device/photo",
                lookupKey = "old-device-key"
            )
        )
        val now = System.currentTimeMillis()
        val fact = database.contactFactDao().insert(
            ContactFact(contactId = person, text = "Climbing", deletedAt = now)
        )
        val event = database.eventDao().insert(Event(note = "Coffee", deletedAt = now))
        val follow = database.followUpDao().insert(
            FollowUp(
                contactId = person,
                text = "Ask about trip",
                dueDate = "2026-11-03",
                notifiedAt = now
            )
        )
        settings.setFollowUpRemindersEnabled(true)
        settings.dismissReconnectPair(ProfilePair(person, 999))
        val snapshot = BackupJson.decode(exporter.exportToJson())
        assertEquals(now, snapshot.facts.single().deletedAt)
        exporter.restore(snapshot)
        val restored = database.contactDao().getById(person)!!
        assertNull(restored.androidContactId)
        assertNull(restored.lookupKey)
        assertNull(restored.photoUri)
        assertFalse(settings.followUpRemindersEnabled.first())
        assertTrue(settings.dismissedReconnectPairs.first().isEmpty())
        assertNull(database.followUpDao().getById(follow)?.notifiedAt)
        assertTrue(database.contactFactDao().getByContactIdOnce(person).isEmpty())
        recovery.restore(TrashKind.FACT, fact)
        recovery.restore(TrashKind.EVENT, event)
        assertEquals("Climbing", database.contactFactDao().getByContactIdOnce(person).single().text)
        assertNull(database.eventDao().getById(event)?.deletedAt)
    }

    @Test fun recoverableDeletionSurvivesQueriesAndExpiresAfterThirtyDays() = runBlocking {
        val person = contacts.createLocalContact("  Alice  ")
        val sameName = contacts.createLocalContact("Alice", allowDuplicate = true)
        assertTrue(person != sameName) // A name is not a portable identity.
        assertEquals("Alice", database.contactDao().getById(person)?.displayName)
        val fact = contacts.addFact(person, "Climbing")
        recovery.delete(TrashKind.FACT, fact)
        assertTrue(database.contactFactDao().getByContactIdOnce(person).isEmpty())
        assertEquals(fact, recovery.observeDeleted().first().single().id)
        recovery.restore(TrashKind.FACT, fact)
        assertEquals(fact, database.contactFactDao().getByContactIdOnce(person).single().id)
        recovery.delete(TrashKind.FACT, fact)
        recovery.purgeExpired(
            System.currentTimeMillis() + RecoveryRepository.RETENTION_MILLIS + 1_000
        )
        assertTrue(database.contactFactDao().getAllOnce().isEmpty())
    }

    @Test fun localRenamePreservesFactsEventProseAndLinks() = runBlocking {
        val person = contacts.createLocalContact("Alice")
        val fact = contacts.addFact(person, "Likes climbing")
        val event = eventRepository.createEvent("@Alice at the park", listOf(person), emptyList())
        contacts.renameLocalContact(person, "  Alicia  ")
        assertEquals("Alicia", database.contactDao().getById(person)?.displayName)
        assertEquals(fact, database.contactFactDao().getByContactIdOnce(person).single().id)
        assertEquals("@Alice at the park", database.eventDao().getById(event)?.note)
        assertEquals(listOf(person), database.eventDao().getContactIdsForEvent(event))
    }

    @Test
    fun explicitTokensSurviveBackupRenameAndTagRemovalWithoutChangingProse(): Unit = runBlocking {
        val person = contacts.createLocalContact("Maya Chen")
        val note = "@Maya Chen likes #tea"
        val tokens = listOf(
            com.redloon.prm.data.capture.InlineToken(
                0,
                10,
                com.redloon.prm.data.capture.TokenTarget.Person(person)
            ),
            com.redloon.prm.data.capture.InlineToken(
                17,
                21,
                com.redloon.prm.data.capture.TokenTarget.Tag("tea")
            )
        )
        val event = eventRepository.createEvent(
            note,
            listOf(person),
            listOf("tea"),
            inlineTokens = tokens
        )
        contacts.renameLocalContact(person, "Maya Li")
        val snapshot = BackupJson.decode(exporter.exportToJson())
        exporter.restore(snapshot)
        assertEquals(note, database.eventDao().getById(event)!!.note)
        assertEquals(
            tokens,
            com.redloon.prm.data.capture.InlineTokens.decode(
                database.eventDao().getById(event)!!.inlineTokens
            )
        )
        assertEquals("Maya Li", database.contactDao().getById(person)!!.displayName)
        tagRepository.deleteTag(database.tagDao().getByName("tea")!!)
        assertEquals(note, database.eventDao().getById(event)!!.note)
        assertEquals(
            tokens.take(1),
            com.redloon.prm.data.capture.InlineTokens.decode(
                database.eventDao().getById(event)!!.inlineTokens
            )
        )
        BackupJson.decode(exporter.exportToJson())
    }

    @Test fun tagMembershipAndRelationshipsAreReciprocal() = runBlocking {
        val a = database.contactDao().insert(Contact(displayName = "Alice"))
        val b = database.contactDao().insert(Contact(displayName = "Bob"))
        val tag = database.tagDao().insert(Tag(name = "music"))
        val event = database.eventDao().insert(Event(note = "#music with Alice"))
        database.eventDao().insertEventContact(EventContact(event, a))
        database.eventDao().insertEventTag(EventTag(event, tag))
        database.profileLinksDao().addTag(ContactTag(a, tag))
        database.profileLinksDao().addTag(ContactTag(b, tag))
        assertEquals(
            listOf(a, b),
            database.profileLinksDao().contactsForTag(tag).first().map {
                it.id
            }
        )
        val link = Relationship(a, b, "Friend")
        database.profileLinksDao().addRelationship(link)
        assertEquals(listOf(link), database.profileLinksDao().relationshipsFor(a).first())
        assertEquals(listOf(link), database.profileLinksDao().relationshipsFor(b).first())
        database.profileLinksDao().removeRelationship(link)
        assertTrue(database.profileLinksDao().relationshipsFor(b).first().isEmpty())
        database.tagDao().delete(Tag(id = tag, name = "music"))
        assertEquals("#music with Alice", database.eventDao().getById(event)?.note)
        assertTrue(database.profileLinksDao().contactsForTag(tag).first().isEmpty())
    }

    @Test fun failedEventLinkDoesNotLeaveOrphanEvent() = runBlocking {
        try {
            eventRepository.createEvent("Will fail", listOf(9999), emptyList())
            throw AssertionError("Expected foreign key failure")
        } catch (_: Exception) { }
        assertTrue(database.eventDao().getAllOnce().isEmpty())
    }
}
