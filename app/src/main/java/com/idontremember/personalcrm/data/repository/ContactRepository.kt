package com.idontremember.personalcrm.data.repository

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
    private val contactsSyncManager: ContactsSyncManager
) {

    // --- Contacts ---

    fun getAllContacts(): Flow<List<Contact>> = contactDao.getAll()

    fun searchContacts(query: String): Flow<List<Contact>> = contactDao.search(query)

    suspend fun getContactById(id: Long): Contact? = contactDao.getById(id)

    fun getContactByIdFlow(id: Long): Flow<Contact?> = contactDao.getByIdFlow(id)

    suspend fun syncContacts(): ContactsSyncManager.SyncResult = contactsSyncManager.syncContacts()

    suspend fun insertManually(contact: Contact): Long = contactDao.insert(contact)

    suspend fun deleteContact(contact: Contact) = contactDao.delete(contact)

    // --- Facts ---

    fun getFactsForContact(contactId: Long): Flow<List<ContactFact>> =
        contactFactDao.getByContactId(contactId)

    suspend fun addFact(contactId: Long, text: String): Long {
        return contactFactDao.insert(
            ContactFact(contactId = contactId, text = text)
        )
    }

    suspend fun deleteFact(fact: ContactFact) = contactFactDao.delete(fact)

    suspend fun deleteFactById(id: Long) = contactFactDao.deleteById(id)

    // --- Events for a contact ---

    fun getEventsForContact(contactId: Long): Flow<List<Event>> =
        eventDao.getByContactId(contactId)
}