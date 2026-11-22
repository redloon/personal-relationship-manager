package com.idontremember.personalcrm.data.db.dao

import androidx.room.*
import com.idontremember.personalcrm.data.db.entity.*
import kotlinx.coroutines.flow.Flow
import com.idontremember.personalcrm.data.search.TagPerson

@Dao
interface PilotDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun addTag(link: ContactTag)
    @Delete suspend fun removeTag(link: ContactTag)
    @Query("SELECT * FROM contact_tags") suspend fun allContactTags(): List<ContactTag>
    @Query("SELECT t.* FROM tags t JOIN contact_tags ct ON ct.tag_id = t.id WHERE ct.contact_id = :id ORDER BY t.name") fun tagsForContact(id: Long): Flow<List<Tag>>
    @Query("SELECT DISTINCT c.* FROM visible_contacts c WHERE c.id IN (SELECT contact_id FROM contact_tags WHERE tag_id = :id UNION SELECT ec.contact_id FROM event_contacts ec JOIN event_tags et ON ec.event_id = et.event_id JOIN events e ON e.id = ec.event_id WHERE et.tag_id = :id AND e.deleted_at IS NULL) ORDER BY c.display_name") fun contactsForTag(id: Long): Flow<List<Contact>>
    @Query("""
        SELECT c.id, c.display_name AS displayName,
            EXISTS(SELECT 1 FROM contact_tags ct WHERE ct.contact_id=c.id AND ct.tag_id=:id) AS direct,
            EXISTS(SELECT 1 FROM event_contacts ec JOIN event_tags et ON ec.event_id=et.event_id
                JOIN events e ON e.id=ec.event_id WHERE ec.contact_id=c.id AND et.tag_id=:id AND e.deleted_at IS NULL) AS viaNote
        FROM visible_contacts c
        WHERE c.id IN (SELECT contact_id FROM contact_tags WHERE tag_id=:id
            UNION SELECT ec.contact_id FROM event_contacts ec JOIN event_tags et ON ec.event_id=et.event_id
                JOIN events e ON e.id=ec.event_id WHERE et.tag_id=:id AND e.deleted_at IS NULL)
        ORDER BY c.display_name COLLATE NOCASE
    """) fun peopleForTag(id: Long): Flow<List<TagPerson>>
    @Query("SELECT e.id FROM events e JOIN event_tags et ON e.id=et.event_id WHERE et.tag_id=:id AND e.deleted_at IS NULL")
    fun noteIdsForTag(id: Long): Flow<List<Long>>
    @Query("SELECT c.* FROM visible_contacts c JOIN event_contacts ec ON c.id = ec.contact_id JOIN events e ON e.id = ec.event_id WHERE e.deleted_at IS NULL GROUP BY c.id ORDER BY MAX(e.event_date) DESC, c.display_name") fun recentContacts(): Flow<List<Contact>>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun addRelationship(link: Relationship)
    @Delete suspend fun removeRelationship(link: Relationship)
    @Query("SELECT * FROM relationships") suspend fun allRelationships(): List<Relationship>
    @Query("SELECT * FROM relationships WHERE contact_a = :id OR contact_b = :id ORDER BY label") fun relationshipsFor(id: Long): Flow<List<Relationship>>
}
