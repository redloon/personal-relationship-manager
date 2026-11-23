package com.idontremember.personalcrm.data.db

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.idontremember.personalcrm.data.db.dao.ContactDao
import com.idontremember.personalcrm.data.db.dao.ContactFactDao
import com.idontremember.personalcrm.data.db.dao.EventDao
import com.idontremember.personalcrm.data.db.dao.TagDao
import com.idontremember.personalcrm.data.db.entity.Contact
import com.idontremember.personalcrm.data.db.entity.ContactFact
import com.idontremember.personalcrm.data.db.entity.Event
import com.idontremember.personalcrm.data.db.entity.EventContact
import com.idontremember.personalcrm.data.db.entity.EventTag
import com.idontremember.personalcrm.data.db.entity.Tag
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class AppDatabaseTest {

    @get:Rule
    var hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var database: AppDatabase

    @Inject
    lateinit var contactDao: ContactDao

    @Inject
    lateinit var contactFactDao: ContactFactDao

    @Inject
    lateinit var eventDao: EventDao

    @Inject
    lateinit var tagDao: TagDao

    @Before
    fun setup() {
        hiltRule.inject()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun insertAndRetrieveContact() = runTest {
        val contact = Contact(
            androidContactId = 12345L,
            displayName = "Alice Smith",
            phoneNumber = "+1234567890",
            email = "alice@example.com"
        )
        val id = contactDao.insert(contact)
        assertTrue(id > 0)

        val retrieved = contactDao.getById(id)
        assertNotNull(retrieved)
        assertEquals("Alice Smith", retrieved!!.displayName)
        assertEquals("+1234567890", retrieved.phoneNumber)
    }

    @Test
    fun upsertContactByAndroidId() = runTest {
        val contact1 = Contact(androidContactId = 100L, displayName = "Bob")
        val contact2 = Contact(androidContactId = 100L, displayName = "Robert")

        contactDao.insert(contact1)
        val id2 = contactDao.insert(contact2) // Should be ignored due to IGNORE strategy

        val all = contactDao.getAll().first()
        assertEquals(1, all.size)
        assertEquals("Bob", all[0].displayName) // Original preserved
    }

    @Test
    fun insertAndRetrieveFact() = runTest {
        val contact = Contact(displayName = "Test User")
        val contactId = contactDao.insert(contact)

        val fact = ContactFact(contactId = contactId, text = "gluten-free")
        val factId = contactFactDao.insert(fact)

        val facts = contactFactDao.getByContactId(contactId).first()
        assertEquals(1, facts.size)
        assertEquals("gluten-free", facts[0].text)
    }

    @Test
    fun insertEventWithContactsAndTags() = runTest {
        // Setup contacts
        val contact1 = Contact(displayName = "Alice")
        val contact2 = Contact(displayName = "Bob")
        val c1Id = contactDao.insert(contact1)
        val c2Id = contactDao.insert(contact2)

        // Create event
        val event = Event(note = "Had dinner with Alice and Bob")
        val eventId = eventDao.insert(event)

        // Link contacts
        eventDao.insertEventContact(EventContact(eventId = eventId, contactId = c1Id))
        eventDao.insertEventContact(EventContact(eventId = eventId, contactId = c2Id))

        // Create and link tag
        val tag = tagDao.getOrCreate("dinner")
        eventDao.insertEventTag(EventTag(eventId = eventId, tagId = tag.id))

        // Verify
        val contactIds = eventDao.getContactIdsForEvent(eventId)
        assertEquals(2, contactIds.size)
        assertTrue(contactIds.contains(c1Id))
        assertTrue(contactIds.contains(c2Id))

        val tagIds = eventDao.getTagIdsForEvent(eventId)
        assertEquals(1, tagIds.size)
    }

    @Test
    fun tagGetOrCreate() = runTest {
        val tag1 = tagDao.getOrCreate("hiking")
        val tag2 = tagDao.getOrCreate("hiking") // Should return same tag

        assertEquals(tag1.id, tag2.id)

        val tag3 = tagDao.getOrCreate("Hiking") // Case insensitive
        assertEquals(tag1.id, tag3.id)
    }

    @Test
    fun searchContacts() = runTest {
        contactDao.insert(Contact(displayName = "Alice Smith"))
        contactDao.insert(Contact(displayName = "Bob Jones"))
        contactDao.insert(Contact(displayName = "Alice Johnson"))

        val results = contactDao.search("Alice").first()
        assertEquals(2, results.size)
        assertTrue(results.all { it.displayName.contains("Alice") })
    }

    @Test
    fun searchEventsByNote() = runTest {
        eventDao.insert(Event(note = "Project kickoff meeting with Alice"))
        eventDao.insert(Event(note = "Phone call with Bob"))

        // Unique token so results don't depend on what other tests inserted
        val results = eventDao.searchByNote("kickoff").first()
        assertEquals(1, results.size)
        assertEquals("Project kickoff meeting with Alice", results.first().note)

        assertTrue(eventDao.searchByNote("zzzznomatch").first().isEmpty())
    }
}
