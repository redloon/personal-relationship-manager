package com.redloon.prm.data.contacts

import androidx.room.withTransaction
import com.redloon.prm.data.backup.BackupJson
import com.redloon.prm.data.backup.DataExporter
import com.redloon.prm.data.capture.InlineToken
import com.redloon.prm.data.capture.InlineTokens
import com.redloon.prm.data.capture.TokenTarget
import com.redloon.prm.data.db.AppDatabase
import com.redloon.prm.data.db.entity.Contact
import com.redloon.prm.data.db.entity.ContactFact
import com.redloon.prm.data.db.entity.ContactTag
import com.redloon.prm.data.db.entity.Event
import com.redloon.prm.data.db.entity.EventContact
import com.redloon.prm.data.db.entity.EventTag
import com.redloon.prm.data.db.entity.FollowUp
import com.redloon.prm.data.db.entity.Relationship
import com.redloon.prm.data.db.entity.Tag
import com.redloon.prm.data.db.entity.canDelete
import com.redloon.prm.data.repository.ContactRepository
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@HiltAndroidTest
class PeopleManagementTest {
    @get:Rule val hilt = HiltAndroidRule(this)

    @Inject lateinit var db: AppDatabase

    @Inject lateinit var repository: ContactRepository

    @Inject lateinit var exporter: DataExporter

    @Inject lateinit var interactions: InteractionRepository

    @Before fun setup() {
        hilt.inject()
        db.clearAllTables()
    }

    @Test
    fun deletionPreservesProseDatesTagsAndOtherPeopleIncludingTrashedTokens(): Unit = runBlocking {
        val person = repository.createLocalContact("Wrong Person")
        val other = repository.createLocalContact("Other")
        val text = "Met @Wrong Person"
        val event = db.eventDao().insert(
            Event(
                note = text,
                eventDate = 7,
                createdAt = 8,
                updatedAt = 9,
                inlineTokens = InlineTokens.encode(
                    listOf(InlineToken(4, text.length, TokenTarget.Person(person)))
                )
            )
        )
        val trash = db.eventDao().insert(
            Event(
                note = text,
                deletedAt = 1,
                inlineTokens = InlineTokens.encode(
                    listOf(InlineToken(4, text.length, TokenTarget.Person(person)))
                )
            )
        )
        for (id in listOf(event, trash)) db.eventDao().insertEventContact(EventContact(id, person))
        db.eventDao().insertEventContact(EventContact(event, other))
        val tag = db.tagDao().insert(Tag(name = "kept"))
        db.eventDao().insertEventTag(EventTag(event, tag))
        db.contactFactDao().insert(ContactFact(contactId = person, text = "Owned fact"))
        db.followUpDao().insert(FollowUp(contactId = person, text = "Owned intention"))
        db.profileLinksDao().addRelationship(
            Relationship(minOf(person, other), maxOf(person, other), "Friend")
        )
        val preview = repository.previewPeopleDeletion(setOf(person))
        assertEquals(2, preview.eventCount)
        assertEquals(1, preview.factCount)
        assertEquals(1, preview.followUpCount)
        assertEquals(1, preview.relationshipCount)
        assertTrue(repository.deletePeople(preview))
        assertNull(repository.getContactById(person))
        val saved = db.eventDao().getById(event)!!
        assertEquals(text, saved.note)
        assertEquals(7, saved.eventDate)
        assertEquals(9, saved.updatedAt)
        assertEquals("[]", saved.inlineTokens)
        assertEquals(listOf(other), db.eventDao().getContactIdsForEvent(event))
        assertEquals(listOf(tag), db.eventDao().getTagIdsForEvent(event))
        assertEquals("[]", db.eventDao().getAllOnce().single { it.id == trash }.inlineTokens)
        assertTrue(db.profileLinksDao().allRelationships().isEmpty())
        BackupJson.decode(exporter.exportToJson())
    }

    @Test
    fun reconnectKeepsRestoredIdentityAndConsolidatesAllAndroidProfileContext(): Unit =
        runBlocking {
            val saved = db.contactDao().insert(
                Contact(
                    displayName = "Old Maya",
                    phoneNumber = "4155550123",
                    createdAt = 10,
                    updatedAt = 11,
                    isHidden = true
                )
            )
            val android = db.contactDao().insert(
                Contact(
                    androidContactId = 42,
                    displayName = "Maya Chen",
                    phoneNumber = "(415) 555-0123",
                    email = "maya@example.com",
                    photoUri = "content://photo",
                    lookupKey = "maya-key",
                    createdAt = 20
                )
            )
            val other = db.contactDao().insert(Contact(displayName = "Other"))
            db.contactFactDao().insert(ContactFact(contactId = saved, text = "Saved fact"))
            db.contactFactDao().insert(ContactFact(contactId = android, text = "Android fact"))
            db.followUpDao().insert(FollowUp(contactId = saved, text = "Saved follow-up"))
            db.followUpDao().insert(FollowUp(contactId = android, text = "Android follow-up"))
            val note = "@Old Maya met @Maya Chen"
            val event = db.eventDao().insert(
                Event(
                    note = note,
                    inlineTokens = InlineTokens.encode(
                        listOf(
                            InlineToken(0, 9, TokenTarget.Person(saved)),
                            InlineToken(14, note.length, TokenTarget.Person(android))
                        )
                    )
                )
            )
            db.eventDao().insertEventContact(EventContact(event, saved))
            db.eventDao().insertEventContact(EventContact(event, android))
            val tag = db.tagDao().insert(Tag(name = "friend"))
            db.profileLinksDao().addTag(ContactTag(saved, tag))
            db.profileLinksDao().addTag(ContactTag(android, tag))
            db.profileLinksDao().addRelationship(
                Relationship(minOf(saved, other), maxOf(saved, other), "Friend")
            )
            db.profileLinksDao().addRelationship(
                Relationship(minOf(android, other), maxOf(android, other), "Friend")
            )
            db.profileLinksDao().addRelationship(
                Relationship(minOf(saved, android), maxOf(saved, android), "Duplicate")
            )

            assertTrue(repository.reconnectProfiles(saved, android))

            val survivor = repository.getContactById(saved)!!
            assertEquals("Maya Chen", survivor.displayName)
            assertEquals(42L, survivor.androidContactId)
            assertEquals("(415) 555-0123", survivor.phoneNumber)
            assertEquals("maya@example.com", survivor.email)
            assertEquals("content://photo", survivor.photoUri)
            assertEquals("maya-key", survivor.lookupKey)
            assertEquals(10L, survivor.createdAt)
            assertTrue(survivor.isHidden)
            assertFalse(survivor.deletionAllowed)
            assertNull(repository.getContactById(android))
            assertEquals(2, db.contactFactDao().getByContactId(saved).first().size)
            assertEquals(2, db.followUpDao().getAllOnce().count { it.contactId == saved })
            assertEquals(listOf(saved), db.eventDao().getContactIdsForEvent(event))
            assertEquals(listOf(ContactTag(saved, tag)), db.profileLinksDao().allContactTags())
            assertEquals(
                listOf(Relationship(minOf(saved, other), maxOf(saved, other), "Friend")),
                db.profileLinksDao().allRelationships()
            )
            assertEquals(
                listOf(TokenTarget.Person(saved), TokenTarget.Person(saved)),
                InlineTokens.decode(db.eventDao().getById(event)!!.inlineTokens).map { it.target }
            )
        }

    @Test fun protectedAndMixedSelectionsAreRejectedAndChangedPreviewsAreNotExecuted(): Unit =
        runBlocking {
            val local = repository.createLocalContact("Local")
            val legacy = db.contactDao().insert(Contact(displayName = "Legacy"))
            try {
                repository.previewPeopleDeletion(setOf(local, legacy))
                fail("Protected selection")
            } catch (
                _: IllegalArgumentException
            ) { }
            assertNotNull(repository.getContactById(local))
            val preview = repository.previewPeopleDeletion(setOf(local))
            db.contactFactDao().insert(
                ContactFact(contactId = local, text = "Added while confirming")
            )
            assertFalse(repository.deletePeople(preview))
            assertNotNull(repository.getContactById(local))
            val current = repository.previewPeopleDeletion(setOf(local))
            db.contactDao().update(
                repository.getContactById(
                    local
                )!!.copy(androidContactId = 42, deletionAllowed = false)
            )
            try {
                repository.deletePeople(current)
                fail("Became protected")
            } catch (
                _: IllegalArgumentException
            ) { }
            assertNotNull(repository.getContactById(local))
        }

    @Test fun failedDeletionRollsBackTokenCleanupAndEverySelectedPerson(): Unit = runBlocking {
        val a = repository.createLocalContact("First")
        val b = repository.createLocalContact("Second")
        val text = "@First"
        val event = db.eventDao().insert(
            Event(
                note = text,
                inlineTokens = InlineTokens.encode(
                    listOf(InlineToken(0, text.length, TokenTarget.Person(a)))
                )
            )
        )
        db.eventDao().insertEventContact(EventContact(event, a))
        val preview = repository.previewPeopleDeletion(setOf(a, b))
        db.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER reject_person_delete BEFORE DELETE ON contacts BEGIN SELECT RAISE(ABORT, 'forced deletion failure'); END"
        )
        try {
            try {
                repository.deletePeople(preview)
                fail("Expected rollback")
            } catch (
                _: android.database.sqlite.SQLiteException
            ) { }
            assertNotNull(repository.getContactById(a))
            assertNotNull(repository.getContactById(b))
            assertEquals(listOf(a), db.eventDao().getContactIdsForEvent(event))
            assertEquals(
                listOf(TokenTarget.Person(a)),
                InlineTokens.decode(db.eventDao().getById(event)!!.inlineTokens).map {
                    it.target
                }
            )
        } finally {
            db.openHelper.writableDatabase.execSQL("DROP TRIGGER IF EXISTS reject_person_delete")
        }
    }

    @Test fun largeSelectionsUseOneAtomicOperationBeyondSqlParameterLimits(): Unit = runBlocking {
        val ids = db.withTransaction {
            (1..1005).map {
                db.contactDao().insert(Contact(displayName = "Created $it", deletionAllowed = true))
            }.toSet()
        }
        assertTrue(repository.deletePeople(repository.previewPeopleDeletion(ids)))
        assertTrue(db.contactDao().getAllIncludingHiddenOnce().isEmpty())
    }

    @Test
    fun statisticsIgnoreFactsFutureEventsAndDeletedEventsAndAdvanceWithTime(): Unit = runBlocking {
        val person = repository.createLocalContact("Interactions")
        for ((date, deleted) in listOf(10L to null, 20L to null, 30L to null, 24L to 1L)) {
            val event = db.eventDao().insert(
                Event(note = "Context", eventDate = date, deletedAt = deleted)
            )
            db.eventDao().insertEventContact(EventContact(event, person))
        }
        db.contactFactDao().insert(
            ContactFact(contactId = person, text = "New fact", createdAt = 99)
        )
        assertEquals(
            InteractionStats(person, 20, 2),
            db.contactDao().interactionStats(25).first().single()
        )
        assertEquals(
            InteractionStats(person, 30, 3),
            db.contactDao().interactionStats(35).first().single()
        )
    }

    @Test fun newlySavedEventsUpdateRecencyImmediatelyBetweenClockTicks(): Unit = runBlocking {
        val person = repository.createLocalContact("Live interaction")
        val updates = Channel<InteractionSnapshot>(Channel.BUFFERED)
        val collecting = launch { interactions.snapshots.collect { updates.send(it) } }
        try {
            val initial = withTimeout(5_000) { updates.receive() }
            val date = maxOf(initial.asOf + 1, System.currentTimeMillis())
            val event = db.eventDao().insert(Event(note = "Just happened", eventDate = date))
            db.eventDao().insertEventContact(EventContact(event, person))
            val updated = withTimeout(5_000) {
                var next = updates.receive()
                while (next.forPerson(person).interactionCount != 1) next = updates.receive()
                next
            }
            assertEquals(date, updated.forPerson(person).lastInteractionAt)
            assertTrue(updated.asOf >= date)
        } finally {
            collecting.cancelAndJoin()
            updates.close()
        }
    }

    @Test fun exportRestorePreservesEligibilityAndHiddenStateButOldBackupsRemainProtected(): Unit =
        runBlocking {
            val local = repository.createLocalContact("Local")
            repository.setPeopleHidden(setOf(local), true)
            val phone = db.contactDao().insert(
                Contact(displayName = "Phone", androidContactId = 42)
            )
            val backup = BackupJson.decode(exporter.exportToJson())
            exporter.restore(backup)
            assertTrue(repository.getContactById(local)!!.canDelete)
            assertTrue(repository.getContactById(local)!!.isHidden)
            assertFalse(repository.getContactById(phone)!!.canDelete)
            exporter.restore(backup.copy(schemaVersion = 4))
            assertFalse(repository.getContactById(local)!!.canDelete)
        }
}
