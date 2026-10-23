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
import kotlinx.coroutines.runBlocking
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
 * NOTE: This test requires a running emulator plus READ_CONTACTS and
 *       WRITE_CONTACTS (the latter only in debug builds — see
 *       app/src/debug/AndroidManifest.xml). It operates on the REAL system
 *       Contacts Provider.
 *
 * Contacts Provider semantics this suite must respect (learned the hard way):
 * - Seeded RAW contacts are aggregated into ContactsContract.Contacts rows:
 *   same-name raw contacts (the generator seeds duplicate "John Smith"
 *   entries on purpose) merge into ONE aggregated contact. The app syncs
 *   aggregated contacts, so expected counts come from the provider, not from
 *   the number of seeded rows.
 * - Aggregated contacts can carry several phone/email rows (one per merged
 *   raw contact); the sync keeps one of them.
 * - DISPLAY_NAME_PRIMARY may differ from the seeded string (the provider's
 *   name splitter reorders CJK names on read-back).
 * - Identity: Room's Contact.androidContactId is ContactsContract.Contacts._ID
 *   (the AGGREGATED id) — it is NOT the generator's synthetic SystemContact
 *   androidId, and display names are not unique. Always match the system side
 *   by contact id.
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

        // Start clean in Room too: the in-memory test DB is a process-wide Hilt
        // singleton shared by every test class in one instrumentation run, and
        // the sync counters ("added" vs "updated") only make sense against an
        // empty local table. Contact deletion cascades to facts/event links.
        runBlocking {
            contactDao.getAll().first().forEach { contactDao.delete(it) }
        }

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

    // ------------------------------------------------------------------
    // Provider query helpers — always match the system side by contact id
    // (ContactsContract.Contacts._ID == Room's androidContactId).
    // ------------------------------------------------------------------

    /** Display names exactly as the sync reads them (null names are skipped). */
    private fun providerDisplayNames(): List<String> {
        val names = mutableListOf<String>()
        contentResolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            arrayOf(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY),
            null,
            null,
            null
        )?.use { c ->
            val idx = c.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
            while (c.moveToNext()) {
                c.getString(idx)?.let { names.add(it) }
            }
        }
        return names
    }

    private fun providerPhoneNumbers(contactId: Long): Set<String> {
        val numbers = mutableSetOf<String>()
        contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
            arrayOf(contactId.toString()),
            null
        )?.use { c ->
            val idx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (c.moveToNext()) {
                c.getString(idx)?.let { numbers.add(it) }
            }
        }
        return numbers
    }

    private fun providerEmails(contactId: Long): Set<String> {
        val addresses = mutableSetOf<String>()
        contentResolver.query(
            ContactsContract.CommonDataKinds.Email.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Email.ADDRESS),
            "${ContactsContract.CommonDataKinds.Email.CONTACT_ID} = ?",
            arrayOf(contactId.toString()),
            null
        )?.use { c ->
            val idx = c.getColumnIndex(ContactsContract.CommonDataKinds.Email.ADDRESS)
            while (c.moveToNext()) {
                c.getString(idx)?.let { addresses.add(it) }
            }
        }
        return addresses
    }

    /**
     * Name fingerprint tolerant of the provider's name-splitter normalization:
     * "John Smith Jr." reads back as "John Smith, Jr." (suffix rejoined with a
     * comma) and "田中 太郎" reads back as "田中太郎" (space dropped) or
     * "太郎 田中" (CJK tokens reordered). Comparing the sorted set of
     * letters/digits tolerates punctuation, whitespace and token-order changes.
     */
    private fun nameFingerprint(name: String): String =
        name.filter { it.isLetterOrDigit() }.toSortedSet().joinToString("")

    @Test
    fun firstSync_importsAllContacts() = runTest {
        // The provider aggregates same-name raw contacts into one aggregated
        // contact (seeding includes duplicate "John Smith" entries on purpose)
        // and the app syncs aggregated contacts — so the expected count is
        // what the provider reports after seeding, not the seeded row count.
        val providerNames = providerDisplayNames()
        val providerCount = providerNames.size
        assertTrue("Seed should produce 400+ aggregated contacts, got $providerCount", providerCount > 400)

        // Act: perform first sync
        val result = contactsSyncManager.syncContacts()

        // Assert: every provider contact was added, nothing updated
        assertEquals("Sync should report one result per provider contact", providerCount, result.total)
        assertEquals("Expected all contacts to be added", result.total, result.added)
        assertEquals("Expected zero updates on first sync", 0, result.updated)

        // Verify Room mirrors the provider exactly
        val allContacts = contactDao.getAll().first()
        assertEquals("Room should mirror the provider contact count", providerCount, allContacts.size)
    }

    @Test
    fun firstSync_contactNamesMatch() = runTest {
        contactsSyncManager.syncContacts()

        val allContacts = contactDao.getAll().first()
        val dbNames = allContacts.map { it.displayName }.toSet()

        // The synced names must mirror the provider exactly — the app copies
        // DISPLAY_NAME_PRIMARY verbatim. The provider may normalize seeded
        // names (CJK name splitter reorders on read-back), so compare against
        // the provider, not the seeded strings.
        val providerNames = providerDisplayNames().toSet()
        assertEquals("Synced names should mirror the provider", providerNames, dbNames)

        // Verify special names survived (whitespace/token-order tolerant —
        // see sameNameIgnoringTokenOrder)
        val specialNames = listOf(
            "José García-López",
            "François Müller",
            "田中 太郎",
            "John Smith",
            "John Smith Jr."
        )

        for (name in specialNames) {
            assertTrue(
                "Expected to find '$name' in synced contacts, got: ${dbNames.sorted()}",
                dbNames.any { nameFingerprint(it) == nameFingerprint(name) }
            )
        }
    }

    @Test
    fun firstSync_phoneNumbersMatch() = runTest {
        contactsSyncManager.syncContacts()

        val allContacts = contactDao.getAll().first()

        // Match the system side by contact id (display names are not unique —
        // the generator seeds duplicates on purpose). Pick a contact whose
        // aggregated provider row has exactly one phone row so the expected
        // value is unambiguous regardless of which row the sync kept.
        val target = allContacts.firstOrNull { contact ->
            contact.phoneNumber != null &&
                contact.androidContactId != null &&
                providerPhoneNumbers(contact.androidContactId!!).size == 1
        }
        assertNotNull("Expected at least one contact with a single provider phone row", target)

        val providerPhones = providerPhoneNumbers(target!!.androidContactId!!)
        assertEquals("Synced phone should match the provider", providerPhones.first(), target.phoneNumber)
    }

    @Test
    fun firstSync_emailsMatch() = runTest {
        contactsSyncManager.syncContacts()

        val allContacts = contactDao.getAll().first()

        // Same identity rules as firstSync_phoneNumbersMatch: match by contact
        // id, and use a contact with a single provider email row.
        val target = allContacts.firstOrNull { contact ->
            contact.email != null &&
                contact.androidContactId != null &&
                providerEmails(contact.androidContactId!!).size == 1
        }
        assertNotNull("Expected at least one contact with a single provider email row", target)

        val expectedEmails = providerEmails(target!!.androidContactId!!)
        assertEquals("Synced email should match the provider", expectedEmails.first(), target.email)
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

        // Pick a contact to edit. The system side must be located by contact id
        // (Room's androidContactId == ContactsContract.Contacts._ID), NOT by
        // display name (non-unique) and NOT by the generator's synthetic
        // SystemContact.androidId (that is not a provider row id).
        val targetContact: Contact = afterFirst.firstOrNull {
            it.phoneNumber != null &&
                it.androidContactId != null &&
                providerPhoneNumbers(it.androidContactId!!).isNotEmpty()
        } ?: return@runTest fail("No contact with phone number found")

        // Update the phone number in the system contacts provider. Target the
        // aggregated contact's Data rows and update every one of them, so the
        // sync's read-back is deterministic regardless of row order.
        val newPhone = "+15550009999"
        val rowsUpdated = contentResolver.update(
            ContactsContract.Data.CONTENT_URI,
            android.content.ContentValues().apply {
                put(ContactsContract.CommonDataKinds.Phone.NUMBER, newPhone)
            },
            "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ? AND ${ContactsContract.CommonDataKinds.Phone.MIMETYPE} = ?",
            arrayOf(
                targetContact.androidContactId.toString(),
                ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE
            )
        )
        assertTrue("Provider should have updated at least one phone row", rowsUpdated > 0)

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