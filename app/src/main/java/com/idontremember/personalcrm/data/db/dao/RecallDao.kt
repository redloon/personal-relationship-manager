package com.idontremember.personalcrm.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import com.idontremember.personalcrm.data.search.RecallEntry
import kotlinx.coroutines.flow.Flow

/** A single reactive snapshot; no per-row metadata lookups or stale person chips. */
@Dao
interface RecallDao {
    @Query("""
        SELECT 'person' AS kind, c.id AS id, c.id AS contactId,
            c.display_name AS title, '' AS text, c.updated_at AS date,
            ',' || c.id || ',' AS personIds,
            c.display_name || ' ' || COALESCE(c.phone_number, '') || ' ' || COALESCE(c.email, '') || ' ' ||
            COALESCE((SELECT GROUP_CONCAT(t.name, ' ') FROM tags t JOIN contact_tags ct ON ct.tag_id=t.id WHERE ct.contact_id=c.id), '') AS searchText
        FROM contacts c
        UNION ALL
        SELECT 'fact', f.id, c.id, c.display_name, f.text, f.created_at,
            ',' || c.id || ',', c.display_name || ' ' || f.text || ' ' ||
            COALESCE((SELECT GROUP_CONCAT(t.name, ' ') FROM tags t JOIN contact_tags ct ON ct.tag_id=t.id WHERE ct.contact_id=c.id), '')
        FROM contact_facts f JOIN contacts c ON c.id=f.contact_id WHERE f.deleted_at IS NULL
        UNION ALL
        SELECT 'event', e.id, NULL,
            COALESCE((SELECT GROUP_CONCAT(c.display_name, ', ') FROM contacts c JOIN event_contacts ec ON ec.contact_id=c.id WHERE ec.event_id=e.id), ''),
            e.note, e.event_date,
            ',' || COALESCE((SELECT GROUP_CONCAT(ec.contact_id, ',') FROM event_contacts ec WHERE ec.event_id=e.id), '') || ',',
            e.note || ' ' || COALESCE((SELECT GROUP_CONCAT(c.display_name, ' ') FROM contacts c JOIN event_contacts ec ON ec.contact_id=c.id WHERE ec.event_id=e.id), '') || ' ' ||
            COALESCE((SELECT GROUP_CONCAT(t.name, ' ') FROM tags t JOIN event_tags et ON et.tag_id=t.id WHERE et.event_id=e.id), '')
        FROM events e WHERE e.deleted_at IS NULL
        UNION ALL
        SELECT 'followup', f.id, c.id, c.display_name, f.text, f.created_at,
            ',' || c.id || ',', c.display_name || ' ' || f.text
        FROM follow_ups f JOIN contacts c ON c.id=f.contact_id WHERE f.deleted_at IS NULL
    """)
    fun entries(): Flow<List<RecallEntry>>
}
