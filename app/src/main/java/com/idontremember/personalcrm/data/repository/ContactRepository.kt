package com.idontremember.personalcrm.data.repository

import com.idontremember.personalcrm.data.capture.ContactNames
import com.idontremember.personalcrm.data.capture.InlineTokens
import com.idontremember.personalcrm.data.capture.TokenTarget
import androidx.room.withTransaction
import com.idontremember.personalcrm.data.db.AppDatabase
import com.idontremember.personalcrm.data.contacts.ContactsSyncManager
import com.idontremember.personalcrm.data.db.dao.ContactDao
import com.idontremember.personalcrm.data.db.dao.ContactFactDao
import com.idontremember.personalcrm.data.db.dao.EventDao
import com.idontremember.personalcrm.data.db.entity.Contact
import com.idontremember.personalcrm.data.db.entity.ContactFact
import com.idontremember.personalcrm.data.db.entity.Event
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContactRepository @Inject constructor(
    private val contactDao: ContactDao,
    private val contactFactDao: ContactFactDao,
    private val eventDao: EventDao,
    private val contactsSyncManager: ContactsSyncManager,
    private val database: AppDatabase
) {

    // --- Contacts ---

    fun getAllContacts(): Flow<List<Contact>> = contactDao.getAll()

    fun searchContacts(query: String): Flow<List<Contact>> = contactDao.search(query)

    fun getAppOnlyContacts(): Flow<List<Contact>> = contactDao.getAppOnly()

    fun getHiddenContacts(): Flow<List<Contact>> = contactDao.getHidden()

    /** Only for resolving saved associations; never use for person discovery. */
    fun getContactsIncludingHidden(): Flow<List<Contact>> = contactDao.getAllIncludingHidden()

    /** Returns only changed IDs, so Undo cannot affect unrelated profiles. */
    suspend fun setPeopleHidden(ids: Set<Long>, hidden: Boolean): Set<Long> = database.withTransaction {
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

    suspend fun insertManually(contact: Contact): Long = contactDao.insert(contact)

    suspend fun createLocalContact(name: String, allowDuplicate: Boolean = false): Long = database.withTransaction {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "A name is required" }
        if (!allowDuplicate) contactDao.getAllIncludingHiddenOnce().firstOrNull { !it.isHidden && ContactNames.key(it.displayName) == ContactNames.key(trimmed) }?.let { return@withTransaction it.id }
        contactDao.insert(Contact(displayName = trimmed))
    }

    suspend fun renameLocalContact(id: Long, name: String) {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "A name is required" }
        database.withTransaction {
            val person = contactDao.getById(id) ?: error("Person unavailable")
            require(person.androidContactId == null) { "Edit linked names in Android Contacts" }
            contactDao.update(person.copy(displayName = trimmed, updatedAt = System.currentTimeMillis()))
        }
    }

    /** Caller confirms consolidation before invoking when another app profile is attached. */
    suspend fun linkAndroidContact(appContactId: Long, androidId: Long) {
        val provider = contactsSyncManager.getSystemContact(androidId)
            ?: error("Android contact unavailable")
        database.withTransaction {
            val keeper = contactDao.getById(appContactId) ?: error("Person unavailable")
            val imported = contactDao.getByAndroidContactId(androidId)
            if (imported != null && imported.id != appContactId) consolidate(appContactId, imported.id)
            contactDao.update(keeper.copy(displayName = provider.displayName, androidContactId = androidId, lookupKey = provider.lookupKey,
                phoneNumber = provider.phoneNumber, email = provider.email, photoUri = provider.photoUri,
                updatedAt = System.currentTimeMillis()))
        }
    }

    private fun consolidate(keep: Long, remove: Long) {
        val sql = database.openHelper.writableDatabase
        sql.execSQL("UPDATE contact_facts SET contact_id = ? WHERE contact_id = ?", arrayOf(keep, remove))
        sql.execSQL("UPDATE follow_ups SET contact_id = ? WHERE contact_id = ?", arrayOf(keep, remove))
        sql.execSQL("INSERT OR IGNORE INTO event_contacts(event_id, contact_id) SELECT event_id, ? FROM event_contacts WHERE contact_id = ?", arrayOf(keep, remove))
        sql.execSQL("INSERT OR IGNORE INTO contact_tags(contact_id, tag_id) SELECT ?, tag_id FROM contact_tags WHERE contact_id = ?", arrayOf(keep, remove))
        val links = mutableListOf<Triple<Long, Long, String>>()
        sql.query("SELECT contact_a, contact_b, label FROM relationships WHERE contact_a = ? OR contact_b = ?", arrayOf(remove, remove)).use { c ->
            while(c.moveToNext()) {
                val a = if(c.getLong(0) == remove) keep else c.getLong(0)
                val b = if(c.getLong(1) == remove) keep else c.getLong(1)
                if(a != b) links += Triple(minOf(a,b), maxOf(a,b), c.getString(2))
            }
        }
        for((a,b,label) in links) sql.execSQL("INSERT OR IGNORE INTO relationships(contact_a, contact_b, label) VALUES (?, ?, ?)", arrayOf<Any>(a,b,label))
        val tokenUpdates = mutableListOf<Pair<Long, String>>()
        sql.query("SELECT id, inline_tokens FROM events").use { cursor ->
            while (cursor.moveToNext()) {
                val tokens = InlineTokens.decode(cursor.getString(1))
                if (tokens.any { it.target == TokenTarget.Person(remove) }) {
                    tokenUpdates += cursor.getLong(0) to InlineTokens.encode(tokens.map { if (it.target == TokenTarget.Person(remove)) it.copy(target = TokenTarget.Person(keep)) else it })
                }
            }
        }
        for ((id, tokens) in tokenUpdates) sql.execSQL("UPDATE events SET inline_tokens = ? WHERE id = ?", arrayOf<Any>(tokens, id))
        sql.execSQL("DELETE FROM contacts WHERE id = ?", arrayOf(remove))
    }

    suspend fun deleteContact(contact: Contact) = contactDao.delete(contact)

    // --- Facts ---

    fun getFactsForContact(contactId: Long): Flow<List<ContactFact>> =
        contactFactDao.getByContactId(contactId)

    suspend fun addFact(contactId: Long, text: String): Long {
        return contactFactDao.insert(
            ContactFact(contactId = contactId, text = text)
        )
    }

    suspend fun deleteFact(fact: ContactFact) = contactFactDao.update(fact.copy(deletedAt = System.currentTimeMillis()))

    suspend fun deleteFactById(id: Long) {
        database.withTransaction {
            database.openHelper.writableDatabase.execSQL("UPDATE contact_facts SET deleted_at = ? WHERE id = ?", arrayOf(System.currentTimeMillis(), id))
        }
    }

    suspend fun updateFact(id: Long, text: String) {
        require(text.isNotBlank())
        database.withTransaction {
            database.openHelper.writableDatabase.execSQL("UPDATE contact_facts SET text = ? WHERE id = ? AND deleted_at IS NULL", arrayOf<Any>(text, id))
        }
    }

    // --- Events for a contact ---

    fun getEventsForContact(contactId: Long): Flow<List<Event>> =
        eventDao.getByContactId(contactId)
}