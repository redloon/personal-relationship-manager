package com.redloon.prm

import android.content.ContentResolver
import androidx.test.platform.app.InstrumentationRegistry
import com.redloon.prm.data.backup.BackupJson
import com.redloon.prm.data.backup.DataExporter
import com.redloon.prm.data.contacts.ContactsSyncManager
import com.redloon.prm.data.db.AppDatabase
import com.redloon.prm.data.db.entity.Contact
import com.redloon.prm.data.db.entity.ContactFact
import com.redloon.prm.data.repository.ContactRepository
import com.redloon.prm.data.settings.SettingsRepository
import com.redloon.prm.testdata.RandomContactGenerator
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@HiltAndroidTest
class ContactReconciliationSyncTest {
    @get:Rule val hilt = HiltAndroidRule(this)

    @Inject lateinit var db: AppDatabase

    @Inject lateinit var sync: ContactsSyncManager

    @Inject lateinit var repository: ContactRepository

    @Inject lateinit var settings: SettingsRepository

    @Inject lateinit var exporter: DataExporter

    private lateinit var resolver: ContentResolver

    @Before fun setup() = runBlocking {
        hilt.inject()
        resolver = InstrumentationRegistry.getInstrumentation().targetContext.contentResolver
        db.clearAllTables()
        settings.clearDismissedReconnectPairs()
        RandomContactGenerator.clearContactsProvider(resolver)
    }

    @After fun teardown() {
        RandomContactGenerator.clearContactsProvider(resolver)
    }

    @Test fun freshRestoreReconnectsHiddenProfileAndRepeatedSyncIsIdempotent(): Unit = runBlocking {
        seed("Maya Chen", "+1 (415) 555-0123", "maya@example.com")
        val saved = db.contactDao().insert(
            Contact(
                displayName = "  MAYA   chen ",
                phoneNumber = "+14155550123",
                createdAt = 10,
                isHidden = true
            )
        )

        val first = sync.syncContacts()
        val survivor = db.contactDao().getById(saved)!!
        assertEquals(1, first.reconnected)
        assertEquals(0, first.added)
        assertEquals(0, first.reviewable)
        assertNotNull(survivor.androidContactId)
        assertEquals(10L, survivor.createdAt)
        assertTrue(survivor.isHidden)
        assertNull(db.contactDao().getAllIncludingHiddenOnce().singleOrNull { it.id != saved })

        val second = sync.syncContacts()
        assertEquals(0, second.added)
        assertEquals(0, second.updated)
        assertEquals(0, second.reconnected)
        assertEquals(1, db.contactDao().getAllIncludingHiddenOnce().size)
    }

    @Test fun portableRestoreReconcilesFourHundredContactsLeavingOnlyUncertainPairs(): Unit =
        runBlocking {
            val provider = (1..400).map { index ->
                RandomContactGenerator.SystemContact(
                    index.toLong(),
                    "Scale Person $index",
                    "+1202555${index.toString().padStart(4, '0')}",
                    null
                )
            } + listOf(
                RandomContactGenerator.SystemContact(401, "Ambiguous Person", "3035550101", null),
                RandomContactGenerator.SystemContact(402, "Name Only", null, null),
                RandomContactGenerator.SystemContact(403, "Renamed Person", "3035550103", null),
                RandomContactGenerator.SystemContact(
                    404,
                    "Dismissed Person New",
                    "3035550104",
                    null
                )
            )
            RandomContactGenerator.seedContactsProvider(resolver, provider)
            val saved = (1..200).map { index ->
                db.contactDao().insert(
                    Contact(
                        androidContactId = 9000L + index,
                        displayName = "  SCALE   person $index ",
                        phoneNumber = provider[index - 1].phoneNumber,
                        createdAt = index.toLong(),
                        isHidden = index % 10 == 0
                    )
                )
            }
            db.contactFactDao().insert(
                ContactFact(contactId = saved.first(), text = "Keep restored context")
            )
            repeat(2) {
                db.contactDao().insert(
                    Contact(displayName = "Ambiguous Person", phoneNumber = "3035550101")
                )
            }
            db.contactDao().insert(Contact(displayName = "Name Only"))
            db.contactDao().insert(Contact(displayName = "Old Person", phoneNumber = "3035550103"))
            db.contactDao().insert(
                Contact(displayName = "Dismissed Person", phoneNumber = "3035550104")
            )
            val snapshot = BackupJson.decode(exporter.exportToJson())
            db.clearAllTables()
            exporter.restore(snapshot)
            assertTrue(
                db.contactDao().getAllIncludingHiddenOnce().all {
                    it.androidContactId == null
                }
            )

            val first = sync.syncContacts()
            assertEquals(200, first.reconnected)
            assertEquals(204, first.added)
            assertEquals(5, first.reviewable)
            assertEquals(409, db.contactDao().getAllIncludingHiddenOnce().size)
            saved.forEachIndexed { index, savedId ->
                val survivor = db.contactDao().getById(savedId)!!
                assertNotNull(survivor.androidContactId)
                assertEquals(index + 1L, survivor.createdAt)
                assertEquals((index + 1) % 10 == 0, survivor.isHidden)
            }
            assertEquals(
                "Keep restored context",
                db.contactFactDao().getByContactIdOnce(saved.first()).single().text
            )
            val dismissed = repository.observeReconnectCandidates().first().single {
                it.savedProfile.displayName == "Dismissed Person"
            }
            repository.dismissReconnectCandidate(dismissed.pair)
            val second = sync.syncContacts()
            assertEquals(0, second.reconnected)
            assertEquals(0, second.added)
            assertEquals(0, second.updated)
            assertEquals(4, second.reviewable)
            assertEquals(409, db.contactDao().getAllIncludingHiddenOnce().size)
        }

    @Test fun renamedDetailMatchIsReviewableAndDismissalSurvivesSync(): Unit = runBlocking {
        seed("Maya Alvarez", "415-555-0123", null)
        val saved = db.contactDao().insert(
            Contact(displayName = "Maya Chen", phoneNumber = "4155550123")
        )

        val first = sync.syncContacts()
        assertEquals(0, first.reconnected)
        assertEquals(1, first.added)
        assertEquals(1, first.reviewable)
        val candidate = repository.observeReconnectCandidates().first().single()
        assertEquals(saved, candidate.savedProfile.id)

        repository.dismissReconnectCandidate(candidate.pair)
        assertTrue(repository.observeReconnectCandidates().first().isEmpty())
        val second = sync.syncContacts()
        assertEquals(0, second.reconnected)
        assertEquals(0, second.reviewable)
        assertEquals(2, db.contactDao().getAllIncludingHiddenOnce().size)
    }

    @Test fun ambiguousAndIntentionalSeparateProfilesAreNeverAutomaticallyReconnected(): Unit =
        runBlocking {
            seed("Maya Chen", "4155550123", null)
            db.contactDao().insert(Contact(displayName = "Maya Chen", phoneNumber = "4155550123"))
            db.contactDao().insert(Contact(displayName = "Maya Chen", phoneNumber = "4155550123"))
            db.contactDao().insert(
                Contact(
                    displayName = "Maya Chen",
                    phoneNumber = "4155550123",
                    deletionAllowed = true
                )
            )

            val result = sync.syncContacts()
            assertEquals(0, result.reconnected)
            assertEquals(1, result.added)
            assertEquals(2, result.reviewable)
            assertEquals(4, db.contactDao().getAllIncludingHiddenOnce().size)
            assertFalse(
                db.contactDao().getAllIncludingHiddenOnce().any {
                    it.deletionAllowed && it.androidContactId != null
                }
            )
        }

    private fun seed(name: String, phone: String?, email: String?) {
        RandomContactGenerator.seedContactsProvider(
            resolver,
            listOf(RandomContactGenerator.SystemContact(1, name, phone, email))
        )
    }
}
