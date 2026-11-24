package com.redloon.prm.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.redloon.prm.data.db.entity.Event
import com.redloon.prm.data.db.entity.EventContact
import com.redloon.prm.data.db.entity.EventTag
import kotlinx.coroutines.flow.Flow

@Dao
interface EventDao {
    @Query("SELECT * FROM event_contacts")
    fun observeEventContacts(): Flow<List<EventContact>>

    @Query("SELECT * FROM event_tags")
    fun observeEventTags(): Flow<List<EventTag>>

    @Insert
    suspend fun insert(event: Event): Long

    @Update
    suspend fun update(event: Event)

    @Delete
    suspend fun delete(event: Event)

    @Query("SELECT * FROM events WHERE id = :id AND deleted_at IS NULL")
    suspend fun getById(id: Long): Event?

    @Query("SELECT * FROM events WHERE id = :id AND deleted_at IS NULL")
    fun getByIdFlow(id: Long): Flow<Event?>

    @Query("SELECT * FROM events WHERE deleted_at IS NULL ORDER BY event_date DESC")
    fun getAll(): Flow<List<Event>>

    @Query(
        "SELECT * FROM events WHERE deleted_at IS NULL AND note LIKE '%' || :query || '%' ORDER BY event_date DESC"
    )
    fun searchByNote(query: String): Flow<List<Event>>

    @Query("SELECT * FROM events WHERE deleted_at IS NULL ORDER BY event_date DESC LIMIT :limit")
    fun getRecent(limit: Int = 50): Flow<List<Event>>

    // Events involving a specific contact
    @Query(
        """
        SELECT e.* FROM events e
        INNER JOIN event_contacts ec ON e.id = ec.event_id
        WHERE ec.contact_id = :contactId AND e.deleted_at IS NULL
        ORDER BY e.event_date DESC
    """
    )
    fun getByContactId(contactId: Long): Flow<List<Event>>

    // Events with a specific tag
    @Query(
        """
        SELECT e.* FROM events e
        INNER JOIN event_tags et ON e.id = et.event_id
        WHERE et.tag_id = :tagId AND e.deleted_at IS NULL
        ORDER BY e.event_date DESC
    """
    )
    fun getByTagId(tagId: Long): Flow<List<Event>>

    // Junction table operations
    @Insert
    suspend fun insertEventContact(eventContact: EventContact)

    @Insert
    suspend fun insertEventTag(eventTag: EventTag)

    @Query("DELETE FROM event_contacts WHERE event_id = :eventId")
    suspend fun deleteEventContacts(eventId: Long)

    @Query("DELETE FROM event_tags WHERE event_id = :eventId")
    suspend fun deleteEventTags(eventId: Long)

    // Get contact IDs for an event
    @Query("SELECT contact_id FROM event_contacts WHERE event_id = :eventId")
    suspend fun getContactIdsForEvent(eventId: Long): List<Long>

    // Get tag IDs for an event
    @Query("SELECT tag_id FROM event_tags WHERE event_id = :eventId")
    suspend fun getTagIdsForEvent(eventId: Long): List<Long>

    // Get events with contact and tag info for list display
    @Query(
        """
        SELECT e.* FROM events e WHERE e.deleted_at IS NULL
        ORDER BY e.event_date DESC
    """
    )
    fun getAllEventsWithMeta(): Flow<List<Event>>

    @Query("SELECT * FROM events ORDER BY event_date DESC")
    suspend fun getAllOnce(): List<Event>

    @Query("DELETE FROM events")
    suspend fun deleteAll()

    @Query("SELECT * FROM event_contacts")
    suspend fun getAllEventContacts(): List<EventContact>

    @Query("SELECT * FROM event_tags")
    suspend fun getAllEventTags(): List<EventTag>
}
