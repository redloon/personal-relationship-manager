package com.idontremember.personalcrm.data.db

import com.idontremember.personalcrm.data.backup.BackupJson
import com.idontremember.personalcrm.data.backup.DataExporter
import com.idontremember.personalcrm.data.capture.InlineToken
import com.idontremember.personalcrm.data.capture.TokenTarget
import com.idontremember.personalcrm.data.db.entity.*
import com.idontremember.personalcrm.data.repository.ContactRepository
import com.idontremember.personalcrm.data.repository.EventRepository
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

@HiltAndroidTest
class ContactVisibilityTest {
    @get:Rule val hilt = HiltAndroidRule(this)
    @Inject lateinit var db: AppDatabase
    @Inject lateinit var contacts: ContactRepository
    @Inject lateinit var events: EventRepository
    @Inject lateinit var exporter: DataExporter
    @Inject lateinit var followUps: com.idontremember.personalcrm.data.followups.FollowUpRepository
    @Before fun setup() { hilt.inject(); db.clearAllTables() }
    @After fun cleanup() { db.clearAllTables() }

    @Test fun hideAndUnhidePreserveEveryRecordAndExistingLink(): Unit = runBlocking {
        val person = db.contactDao().insert(Contact(displayName = "Maya", androidContactId = 42,
            lookupKey = "maya", phoneNumber = "+15551234", email = "maya@example.com",
            photoUri = "content://contacts/42/photo", createdAt = 10, updatedAt = 20))
        val other = contacts.createLocalContact("Sam")
        contacts.addFact(person, "Likes tea")
        val followUp = db.followUpDao().insert(FollowUp(contactId = person, text = "Ask about tea", notifiedAt = 30))
        val followUpBefore = db.followUpDao().getById(followUp)
        val event = events.createEvent("@Maya likes tea", listOf(person), listOf("tea"),
            inlineTokens = listOf(InlineToken(0, 5, TokenTarget.Person(person))))
        val tag = db.tagDao().getByName("tea")!!.id
        db.pilotDao().addTag(ContactTag(person, tag))
        db.pilotDao().addRelationship(Relationship(minOf(person, other), maxOf(person, other), "Friend"))
        val before = BackupJson.decode(exporter.exportToJson())
        val profile = contacts.getContactById(person)!!
        assertEquals(setOf(person), contacts.setPeopleHidden(setOf(person), true))
        assertEquals(profile.copy(isHidden = true), contacts.getContactById(person))
        assertFalse(contacts.getAllContacts().first().any { it.id == person })
        assertTrue(contacts.getHiddenContacts().first().any { it.id == person })
        assertTrue(contacts.searchContacts("Maya").first().isEmpty())
        assertTrue(db.pilotDao().recentContacts().first().isEmpty())
        assertTrue(db.pilotDao().contactsForTag(tag).first().isEmpty())
        assertTrue(db.pilotDao().peopleForTag(tag).first().isEmpty())
        assertEquals("Maya", followUps.items.first().single().name)
        val entries = db.recallDao().entries().first()
        assertFalse(entries.any { it.kind == "person" && it.id == person })
        assertTrue(entries.any { it.kind == "fact" && it.contactId == person })
        assertTrue(entries.any { it.kind == "followup" && it.contactId == person })
        assertEquals("Maya", events.getEventWithMeta(event)!!.contacts.single().displayName)
        assertEquals("Maya", events.observeEventsWithMeta().first().single().contacts.single().displayName)
        assertEquals(setOf(person), contacts.setPeopleHidden(setOf(person), false))
        val after = BackupJson.decode(exporter.exportToJson()).copy(exportedAt = before.exportedAt)
        assertEquals(before, after)
        assertEquals(followUpBefore, db.followUpDao().getById(followUp))
        assertEquals(profile, contacts.getContactById(person))
        assertTrue(db.pilotDao().recentContacts().first().any { it.id == person })
        assertEquals(listOf(person), db.pilotDao().peopleForTag(tag).first().map { it.id })
    }

    @Test fun largeBulkChangesAreIdempotentAndDoNotChangeTimestamps(): Unit = runBlocking {
        val ids = (1..1200).map { db.contactDao().insert(Contact(displayName = "Person $it", updatedAt = 20)) }.toSet()
        assertEquals(ids, contacts.setPeopleHidden(ids, true))
        assertTrue(contacts.setPeopleHidden(ids, true).isEmpty())
        assertTrue(contacts.getAllContacts().first().isEmpty())
        assertEquals(1200, contacts.getHiddenContacts().first().size)
        assertEquals(ids, contacts.setPeopleHidden(ids, false))
        assertTrue(db.contactDao().getAllIncludingHiddenOnce().all { it.updatedAt == 20L && !it.isHidden })
    }

    @Test fun failedBulkChangeRollsBackEarlierChunks(): Unit = runBlocking {
        val ids = (1..901).map { db.contactDao().insert(Contact(displayName = "Person $it")) }.toSet()
        val sql = db.openHelper.writableDatabase
        sql.execSQL("CREATE TRIGGER fail_visibility BEFORE UPDATE OF is_hidden ON contacts WHEN OLD.id = ${ids.last()} BEGIN SELECT RAISE(ABORT, 'Test failure'); END")
        try {
            try {
                contacts.setPeopleHidden(ids, true)
                fail("Expected the injected write failure")
            } catch (_: android.database.sqlite.SQLiteException) { }
            assertTrue(db.contactDao().getAllIncludingHiddenOnce().none { it.isHidden })
        } finally { sql.execSQL("DROP TRIGGER fail_visibility") }
        assertEquals(ids, contacts.setPeopleHidden(ids, true))
    }

    @Test fun providerUpdatesAndDetachDoNotOverwriteVisibility(): Unit = runBlocking {
        val person = db.contactDao().insert(Contact(displayName = "Maya", androidContactId = 42, lookupKey = "maya"))
        val stale = db.contactDao().getById(person)!!
        contacts.setPeopleHidden(setOf(person), true)
        db.contactDao().updateProviderFields(person, "Maya Chen", 42, "+1234", null, null, "maya", 40)
        assertTrue(db.contactDao().getById(person)!!.isHidden)
        db.contactDao().detachAndroidContact(stale.id)
        assertTrue(db.contactDao().getById(person)!!.isHidden)
        contacts.setPeopleHidden(setOf(person), false)
        assertEquals("Maya Chen", db.contactDao().getById(person)!!.displayName)
    }

    @Test fun hiddenNamesakeIsNotSilentlyReturnedAndBackupKeepsHiding(): Unit = runBlocking {
        val hidden = contacts.createLocalContact("Maya")
        contacts.setPeopleHidden(setOf(hidden), true)
        val visible = contacts.createLocalContact("Maya")
        assertNotEquals(hidden, visible)
        assertTrue(contacts.getContactById(hidden)!!.isHidden)
        val snapshot = BackupJson.decode(exporter.exportToJson())
        exporter.restore(snapshot)
        assertTrue(contacts.getContactById(hidden)!!.isHidden)
        assertEquals(listOf(visible), contacts.getAllContacts().first().map { it.id })
    }
}
