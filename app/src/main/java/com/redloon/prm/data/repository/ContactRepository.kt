package com.redloon.prm.data.repository

import androidx.room.withTransaction
import com.redloon.prm.data.capture.ContactNames
import com.redloon.prm.data.contacts.ContactsSyncManager
import com.redloon.prm.data.contacts.PeopleDeletionPreview
import com.redloon.prm.data.contacts.ProfileConsolidator
import com.redloon.prm.data.contacts.ProfilePair
import com.redloon.prm.data.contacts.ReconnectCandidate
import com.redloon.prm.data.contacts.ReconnectMatching
import com.redloon.prm.data.contacts.deletePeople
import com.redloon.prm.data.contacts.previewPeopleDeletion
import com.redloon.prm.data.db.AppDatabase
import com.redloon.prm.data.db.dao.ContactDao
import com.redloon.prm.data.db.dao.ContactFactDao
import com.redloon.prm.data.db.dao.EventDao
import com.redloon.prm.data.db.entity.Contact
import com.redloon.prm.data.db.entity.ContactFact
import com.redloon.prm.data.db.entity.Event
import com.redloon.prm.data.db.entity.canDelete
import com.redloon.prm.data.settings.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

@Singleton
class ContactRepository @Inject constructor(
    private val contactDao: ContactDao,
    private val contactFactDao: ContactFactDao,
    private val eventDao: EventDao,
    private val contactsSyncManager: ContactsSyncManager,
    private val database: AppDatabase,
    private val profileConsolidator: ProfileConsolidator,
    private val settings: SettingsRepository
) {

    // --- Contacts ---

    fun getAllContacts(): Flow<List<Contact>> = contactDao.getAll()

    fun searchContacts(query: String): Flow<List<Contact>> = contactDao.search(query)

    fun getAppOnlyContacts(): Flow<List<Contact>> = contactDao.getAppOnly()

    fun getHiddenContacts(): Flow<List<Contact>> = contactDao.getHidden()

    /** Only for resolving saved associations; never use for person discovery. */
    fun getContactsIncludingHidden(): Flow<List<Contact>> = contactDao.getAllIncludingHidden()

    /** Returns only changed IDs, so Undo cannot affect unrelated profiles. */
    suspend fun setPeopleHidden(ids: Set<Long>, hidden: Boolean): Set<Long> =
        database.withTransaction {
            val changed = linkedSetOf<Long>()
            for (chunk in ids.toList().chunked(900)) {
                val changing = contactDao.idsChangingVisibility(chunk, hidden)
                if (changing.isNotEmpty()) contactDao.setHidden(changing, hidden)
                changed.addAll(changing)
            }
            changed
        }

    suspend fun getContactByAndroidId(id: Long): Contact? = contactDao.getByAndroidContactId(id)

    suspend fun getContactById(id: Long): Contact? = contactDao.getById(id)

    fun getContactByIdFlow(id: Long): Flow<Contact?> = contactDao.getByIdFlow(id)

    suspend fun syncContacts(): ContactsSyncManager.SyncResult = contactsSyncManager.syncContacts()

    fun observeReconnectCandidates(): Flow<List<ReconnectCandidate>> = combine(
        contactDao.getAllIncludingHidden(),
        settings.dismissedReconnectPairs
    ) { profiles, dismissed ->
        ReconnectMatching.reviewCandidates(profiles, dismissed)
    }

    suspend fun reconnectProfiles(savedProfileId: Long, androidProfileId: Long): Boolean =
        profileConsolidator.reconnectProtected(savedProfileId, androidProfileId)

    suspend fun dismissReconnectCandidate(pair: ProfilePair) {
        settings.dismissReconnectPair(pair)
    }

    suspend fun createLocalContact(name: String, allowDuplicate: Boolean = false): Long =
        database.withTransaction {
            val trimmed = name.trim()
            require(trimmed.isNotEmpty()) { "A name is required" }
            if (!allowDuplicate) {
                contactDao.getAllIncludingHiddenOnce().firstOrNull {
                    !it.isHidden &&
                        ContactNames.key(it.displayName) == ContactNames.key(trimmed)
                }?.let { return@withTransaction it.id }
            }
            contactDao.insert(Contact(displayName = trimmed, deletionAllowed = true))
        }

    suspend fun renameLocalContact(id: Long, name: String) {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "A name is required" }
        database.withTransaction {
            val person = contactDao.getById(id) ?: error("Person unavailable")
            require(person.androidContactId == null) { "Edit linked names in Android Contacts" }
            contactDao.update(
                person.copy(displayName = trimmed, updatedAt = System.currentTimeMillis())
            )
        }
    }

    /** Caller confirms consolidation before invoking when another app profile is attached. */
    suspend fun linkAndroidContact(appContactId: Long, androidId: Long) {
        val provider = contactsSyncManager.getSystemContact(androidId)
            ?: error("Android contact unavailable")
        profileConsolidator.linkToAndroid(appContactId, provider)
    }

    suspend fun previewPeopleDeletion(ids: Set<Long>) = database.previewPeopleDeletion(ids)
    suspend fun deletePeople(preview: PeopleDeletionPreview) = database.deletePeople(preview)

    // --- Facts ---

    fun getFactsForContact(contactId: Long): Flow<List<ContactFact>> =
        contactFactDao.getByContactId(contactId)

    suspend fun addFact(contactId: Long, text: String): Long = contactFactDao.insert(
        ContactFact(contactId = contactId, text = text)
    )

    suspend fun deleteFact(fact: ContactFact) =
        contactFactDao.update(fact.copy(deletedAt = System.currentTimeMillis()))

    suspend fun deleteFactById(id: Long) {
        database.withTransaction {
            database.openHelper.writableDatabase.execSQL(
                "UPDATE contact_facts SET deleted_at = ? WHERE id = ?",
                arrayOf(System.currentTimeMillis(), id)
            )
        }
    }

    suspend fun updateFact(id: Long, text: String) {
        require(text.isNotBlank())
        database.withTransaction {
            database.openHelper.writableDatabase.execSQL(
                "UPDATE contact_facts SET text = ? WHERE id = ? AND deleted_at IS NULL",
                arrayOf<Any>(text, id)
            )
        }
    }

    // --- Events for a contact ---

    fun getEventsForContact(contactId: Long): Flow<List<Event>> = eventDao.getByContactId(contactId)
}
