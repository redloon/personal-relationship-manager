package com.redloon.prm

import android.content.ContentResolver
import androidx.test.platform.app.InstrumentationRegistry
import com.redloon.prm.data.contacts.ContactsSyncManager
import com.redloon.prm.data.db.AppDatabase
import com.redloon.prm.data.db.entity.Contact
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
