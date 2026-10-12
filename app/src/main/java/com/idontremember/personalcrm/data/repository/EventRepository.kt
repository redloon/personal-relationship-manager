package com.idontremember.personalcrm.data.repository

import com.idontremember.personalcrm.data.db.dao.ContactDao
import com.idontremember.personalcrm.data.db.dao.EventDao
import com.idontremember.personalcrm.data.db.dao.TagDao
import com.idontremember.personalcrm.data.db.entity.Contact
import com.idontremember.personalcrm.data.db.entity.Event
import com.idontremember.personalcrm.data.db.entity.EventContact
import com.idontremember.personalcrm.data.db.entity.EventTag
import com.idontremember.personalcrm.data.db.entity.Tag
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

data class EventWithMeta(
    val event: Event,
    val contacts: List<Contact>,
    val tags: List<Tag>
)

@Singleton
class EventRepository @Inject constructor(
    private val eventDao: EventDao,
    private val contactDao: ContactDao,
    private val tagDao: TagDao
) {

    fun getAllEvents(): Flow<List<Event>> = eventDao.getAll()

    fun getRecentEvents(limit: Int = 50): Flow<List<Event>> = eventDao.getRecent(limit)

    suspend fun getEventById(id: Long): Event? = eventDao.getById(id)

    fun getEventByIdFlow(id: Long): Flow<Event?> = eventDao.getByIdFlow(id)

    fun getEventsForContact(contactId: Long): Flow<List<Event>> =
        eventDao.getByContactId(contactId)

    fun getEventsForTag(tagId: Long): Flow<List<Event>> =
        eventDao.getByTagId(tagId)

    /**
     * Create an event with linked contacts and tags.
     * @param note The raw note text (may contain @mentions and #tags inline)
     * @param contactIds List of contact IDs to link
     * @param tagNames List of tag names to link (will be created if they don't exist)
     * @param eventDate Epoch millis for the event date
     */
    suspend fun createEvent(
        note: String,
        contactIds: List<Long>,
        tagNames: List<String>,
        eventDate: Long = System.currentTimeMillis()
    ): Long {
        val event = Event(note = note, eventDate = eventDate)
        val eventId = eventDao.insert(event)

        // Link contacts
        for (contactId in contactIds) {
            eventDao.insertEventContact(EventContact(eventId = eventId, contactId = contactId))
        }

        // Link tags (get-or-create)
        for (tagName in tagNames) {
            val tag = tagDao.getOrCreate(tagName)
            eventDao.insertEventTag(EventTag(eventId = eventId, tagId = tag.id))
        }

        return eventId
    }

    /**
     * Update an event, replacing its contact and tag links.
     */
    suspend fun updateEvent(
        eventId: Long,
        note: String,
        contactIds: List<Long>,
        tagNames: List<String>,
        eventDate: Long
    ) {
        val existing = eventDao.getById(eventId) ?: return
        eventDao.update(existing.copy(note = note, eventDate = eventDate, updatedAt = System.currentTimeMillis()))

        // Replace links
        eventDao.deleteEventContacts(eventId)
        eventDao.deleteEventTags(eventId)

        for (contactId in contactIds) {
            eventDao.insertEventContact(EventContact(eventId = eventId, contactId = contactId))
        }

        for (tagName in tagNames) {
            val tag = tagDao.getOrCreate(tagName)
            eventDao.insertEventTag(EventTag(eventId = eventId, tagId = tag.id))
        }
    }

    suspend fun deleteEvent(event: Event) = eventDao.delete(event)

    suspend fun getEventWithMeta(eventId: Long): EventWithMeta? {
        val event = eventDao.getById(eventId) ?: return null
        val contactIds = eventDao.getContactIdsForEvent(eventId)
        val tagIds = eventDao.getTagIdsForEvent(eventId)

        val contacts = contactIds.mapNotNull { contactDao.getById(it) }
        val tags = tagIds.mapNotNull {
            // Need to get tag by ID — using getAllOnce and filtering for now
            // A proper getById on TagDao would be cleaner but this works
            tagDao.getAllOnce().find { t -> t.id == it }
        }

        return EventWithMeta(event = event, contacts = contacts, tags = tags)
    }
}