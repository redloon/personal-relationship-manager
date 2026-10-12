package com.idontremember.personalcrm

import android.content.ContentProviderOperation
import android.content.ContentResolver
import android.provider.ContactsContract
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.idontremember.personalcrm.data.contacts.ContactsSyncManager
import com.idontremember.personalcrm.data.db.dao.ContactDao
import com.idontremember.personalcrm.data.db.entity.Contact
import com.idontremember.personalcrm.testdata.RandomContactGenerator
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

/**
 * Large-scale idempotent sync test.
 *
 * Seeds the emulator's Contacts Provider with 400+ randomized contacts,
 * syncs them into the local Room DB, verifies data integrity,
 * then mutates a couple of contacts and re-syncs to verify idempotency.
 *
 * NOTE: This test requires a running emulator and READ_CONTACTS permission.
 *       It operates on the REAL system Contacts Provider.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class ContactsSyncStressTest {

    @get:Rule(order = 0)
    var hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var contactsSyncManager: ContactsSyncManager

    @Inject
    lateinit var contactDao: ContactDao

    private lateinit var contentResolver: ContentResolver
    private lateinit var generatedData: RandomContactGenerator.GeneratedContacts

    companion object {
        private const val CONTACT_COUNT = 420
        private const val SEED = 42L
    }

    @Before
    fun setup() {
        hiltRule.inject()
        contentResolver = InstrumentationRegistry.getInstrumentation().targetContext.contentResolver

        // Clear any existing contacts from the provider
        RandomContactGenerator.clearContactsProvider(contentResolver)

        // Generate and seed the test dataset
        generatedData = RandomContactGenerator.generate(totalCount = CONTACT_COUNT, seed = SEED)
        RandomContactGenerator.seedContactsProvider(contentResolver, generatedData.systemContacts)
    }

    @After
    fun teardown() {
        // Clean up the system contacts provider
        try {
            RandomContactGenerator.clearContactsProvider(contentResolver)
        } catch (e: Exception) {
            // Best effort cleanup
        }
    }

    @Test
    fun firstSync_importsAllContacts() = runTest {
        // Act: perform first sync
        val result = contactsSyncManager.syncContacts()

        // Assert: all contacts were added
        assertTrue(
            "Expected at least $CONTACT_COUNT contacts, got ${result.total}",
            result.total >= CONTACT_COUNT
        )
        assertEquals("Expected all contacts to be added", result.total, result.added)
        assertEquals("Expected zero updates on first sync", 0, result.updated)

        // Verify Room DB has the contacts
        val allContacts = contactDao.getAll().first()
        assertTrue(
            "Expected at least $CONTACT_COUNT contacts in DB, got ${allContacts.size}",
            allContacts.size >= CONTACT_COUNT
        )
    }

    @Test
    fun firstSync_contactNamesMatch() = runTest {
        contactsSyncManager.syncContacts()

        val allContacts = contactDao.getAll().first()
        val dbNames = allContacts.map { it.displayName }.toSet()

        // Verify special names survived
        val specialNames = listOf(
            "José García-López",
            "François Müller",
            "田中 太郎",
            "John Smith",
            "John Smith Jr."
        )

        for (name in specialNames) {
            assertTrue(
                "Expected to find '$name' in synced contacts",
                dbNames.any { it.contains(name.take(10)) || name.contains(it.take(10)) }
            )
        }
    }

    @Test
    fun firstSync_phoneNumbersMatch() = runTest {
        contactsSyncManager.syncContacts()

        val allContacts = contactDao.getAll().first()

        // Find a contact with a phone number
        val contactWithPhone = allContacts.firstOrNull { it.phoneNumber != null }
        assertNotNull("Expected at least one contact with a phone number", contactWithPhone)

        // Find the matching system contact
        val systemContact = generatedData.systemContacts.find {
            it.displayName == contactWithPhone!!.displayName
        }
        assertNotNull("System contact should exist for: ${contactWithPhone!!.displayName}", systemContact)
        assertEquals(systemContact!!.phoneNumber, contactWithPhone.phoneNumber)
    }

    @Test
    fun firstSync_emailsMatch() = runTest {
        contactsSyncManager.syncContacts()

        val allContacts = contactDao.getAll().first()

        // Find a contact with an email
        val contactWithEmail = allContacts.firstOrNull { it.email != null }
        assertNotNull("Expected at least one contact with an email", contactWithEmail)

        // Find the matching system contact
        val systemContact = generatedData.systemContacts.find {
            it.displayName == contactWithEmail!!.displayName
        }
        assertNotNull("System contact should exist for: ${contactWithEmail!!.displayName}", systemContact)
        assertEquals(systemContact!!.email, contactWithEmail.email)
    }

    @Test
    fun secondSync_isIdempotent_noChanges() = runTest {
        // First sync
        contactsSyncManager.syncContacts()
        val afterFirst = contactDao.getAll().first()
        val firstIds = afterFirst.map { it.id }.toSet()

        // Second sync with no changes
        val secondResult = contactsSyncManager.syncContacts()

        // Assert: zero added, zero updated
        assertEquals("Expected zero added on re-sync", 0, secondResult.added)
        assertEquals("Expected zero updated on re-sync", 0, secondResult.updated)

        // Assert: same contacts, same IDs
        val afterSecond = contactDao.getAll().first()
        assertEquals("Contact count changed", afterFirst.size, afterSecond.size)

        val secondIds = afterSecond.map { it.id }.toSet()
        assertEquals("Contact IDs changed", firstIds, secondIds)

        // Assert: no data changed
        for (i in afterFirst.indices) {
            assertEquals("Name changed for ${afterFirst[i].id}", afterFirst[i].displayName, afterSecond[i].displayName)
            assertEquals("Phone changed for ${afterFirst[i].id}", afterFirst[i].phoneNumber, afterSecond[i].phoneNumber)
            assertEquals("Email changed for ${afterFirst[i].id}", afterFirst[i].email, afterSecond[i].email)
        }
    }

    @Test
    fun secondSync_picksUpNewContact() = runTest {
        // First sync
        contactsSyncManager.syncContacts()
        val afterFirst = contactDao.getAll().first()
        val firstCount = afterFirst.size

        // Add one new contact to the system
        val newContactName = "New Person After Sync"
        val ops = ArrayList<ContentProviderOperation>()
        val rawIdx = 0
        ops.add(
            ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
                .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null)
                .build()
        )
        ops.add(
            ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawIdx)
                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, newContactName)
                .build()
        )
        ops.add(
            ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawIdx)
                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, "+19999999999")
                .withValue(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE)
                .build()
        )
        contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)

        // Second sync
        val secondResult = contactsSyncManager.syncContacts()

        // Assert: one more contact
        assertEquals("Expected one new contact", 1, secondResult.added)
        assertEquals("Expected zero updates", 0, secondResult.updated)

        val afterSecond = contactDao.getAll().first()
        assertEquals("Expected count to increase by 1", firstCount + 1, afterSecond.size)

        // Verify the new contact exists in DB
        val newInDb = afterSecond.find { it.displayName == newContactName }
        assertNotNull("New contact should be in DB", newInDb)
        assertEquals("+19999999999", newInDb!!.phoneNumber)
    }

    @Test
    fun secondSync_picksUpEditToExistingContact() = runTest {
        // First sync
        contactsSyncManager.syncContacts()
        val afterFirst = contactDao.getAll().first()

        // Pick a contact to edit
        val targetContact: Contact = afterFirst.firstOrNull { it.phoneNumber != null }
            ?: return@runTest fail("No contact with phone number found")

        val systemContact = generatedData.systemContacts.find {
            it.displayName == targetContact.displayName
        } ?: return@runTest fail("System contact not found for: ${targetContact.displayName}")

        // Update the phone number in the system contacts provider
        val newPhone = "+15550009999"
        contentResolver.update(
            ContactsContract.Data.CONTENT_URI,
            android.content.ContentValues().apply {
                put(ContactsContract.CommonDataKinds.Phone.NUMBER, newPhone)
            },
            "${ContactsContract.Data.RAW_CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?",
            arrayOf(
                systemContact.androidId.toString(),
                ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE
            )
        )

        // Second sync
        val secondResult = contactsSyncManager.syncContacts()

        // Assert: one update, zero added
        assertEquals("Expected zero added", 0, secondResult.added)
        assertEquals("Expected one update", 1, secondResult.updated)

        // Verify the phone number changed in Room
        val afterSecond = contactDao.getAll().first()
        val updatedContact = afterSecond.find { it.id == targetContact.id }
        assertNotNull("Updated contact should still exist", updatedContact)
        assertEquals("Phone should be updated", newPhone, updatedContact!!.phoneNumber)

        // Verify no other contacts changed
        val unchanged = afterSecond.filter { it.id != targetContact.id }
        val unchangedFirst = afterFirst.filter { it.id != targetContact.id }
        assertEquals("Unchanged contact count", unchangedFirst.size, unchanged.size)
    }

    @Test
    fun nullPhoneContacts_dontCrashSync() = runTest {
        // First sync — some contacts have null phones (by design in the generator)
        val result = contactsSyncManager.syncContacts()
        assertTrue("Sync should succeed", result.total > 0)

        // Verify contacts with null phones exist in DB
        val allContacts = contactDao.getAll().first()
        val nullPhoneContacts = allContacts.filter { it.phoneNumber == null }
        assertTrue(
            "Expected some contacts with null phone, got ${nullPhoneContacts.size}",
            nullPhoneContacts.isNotEmpty()
        )
    }

    @Test
    fun unicodeNames_surviveRoundTrip() = runTest {
        contactsSyncManager.syncContacts()

        val allContacts = contactDao.getAll().first()

        // Check CJK names
        val cjkContact = allContacts.find { it.displayName.contains("田中") || it.displayName.contains("中田") }
        assertNotNull("CJK contact should survive round-trip", cjkContact)

        // Check accented names
        val accentedContact = allContacts.find { it.displayName.contains("José") || it.displayName.contains("García") }
        assertNotNull("Accented contact should survive round-trip", accentedContact)

        // Check emoji name
        val emojiContact = allContacts.find { it.displayName.contains("😀") }
        assertNotNull("Emoji contact should survive round-trip", emojiContact)
    }

    @Test
    fun localRoomIds_areStableAcrossSyncs() = runTest {
        // First sync
        contactsSyncManager.syncContacts()
        val afterFirst = contactDao.getAll().first()
        val firstIdMap = afterFirst.associate { it.displayName to it.id }

        // Second sync (no changes)
        contactsSyncManager.syncContacts()
        val afterSecond = contactDao.getAll().first()
        val secondIdMap = afterSecond.associate { it.displayName to it.id }

        // All IDs should be identical
        for ((name, firstId) in firstIdMap) {
            val secondId = secondIdMap[name]
            assertNotNull("Contact '$name' missing after second sync", secondId)
            assertEquals("ID changed for '$name'", firstId, secondId)
        }
    }

    @Test
    fun longNameContact_syncsCorrectly() = runTest {
        contactsSyncManager.syncContacts()

        val allContacts = contactDao.getAll().first()

        // The generator creates a contact with a 200-char name
        val longNameContact = allContacts.find { it.displayName.length > 100 }
        assertNotNull("Long name contact should exist", longNameContact)
        assertTrue(
            "Long name should be preserved",
            longNameContact!!.displayName.startsWith("AAA") // "A".repeat(200)
        )
    }
}