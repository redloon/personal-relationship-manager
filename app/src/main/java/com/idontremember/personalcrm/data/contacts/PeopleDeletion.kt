package com.idontremember.personalcrm.data.contacts

import androidx.room.withTransaction
import com.idontremember.personalcrm.data.capture.TokenTarget
import com.idontremember.personalcrm.data.db.AppDatabase
import com.idontremember.personalcrm.data.db.entity.canDelete
import com.idontremember.personalcrm.data.db.rewriteEventTokens
import com.idontremember.personalcrm.data.followups.ReminderDeliveryLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class DeletionPerson(val id: Long, val name: String)
data class PeopleDeletionPreview(
    val people: List<DeletionPerson>,
    val factCount: Int,
    val followUpCount: Int,
    val relationshipCount: Int,
    val eventCount: Int
) {
    val ids: Set<Long> get() = people.map { it.id }.toSet()
}

/** Both preview and execution use this same eligibility boundary. All counts include trash. */
private suspend fun AppDatabase.readPeopleDeletion(ids: Set<Long>): PeopleDeletionPreview {
    require(ids.isNotEmpty())
    val people = contactDao().getAllIncludingHiddenOnce().filter { it.id in ids }
    require(
        people.size == ids.size && people.all {
            it.canDelete
        }
    ) { "Selected people are protected or unavailable" }
    val eventIds = mutableSetOf<Long>()
    var facts = 0
    var followUps = 0
    for (chunk in ids.toList().chunked(900)) {
        val placeholders = chunk.joinToString(",") { "?" }
        val args = chunk.map { it as Any }.toTypedArray()
        for (table in listOf("contact_facts", "follow_ups")) {
            openHelper.readableDatabase.query(
                "SELECT COUNT(*) FROM $table WHERE contact_id IN ($placeholders)",
                args
            ).use { cursor ->
                cursor.moveToFirst()
                if (table ==
                    "contact_facts"
                ) {
                    facts += cursor.getInt(0)
                } else {
                    followUps += cursor.getInt(0)
                }
            }
        }
        openHelper.readableDatabase.query(
            "SELECT DISTINCT event_id FROM event_contacts WHERE contact_id IN ($placeholders)",
            args
        ).use { cursor ->
            while (cursor.moveToNext()) eventIds += cursor.getLong(0)
        }
    }
    val relationships = profileLinksDao().allRelationships().count {
        it.contactA in ids ||
            it.contactB in ids
    }
    return PeopleDeletionPreview(
        people.map {
            DeletionPerson(it.id, it.displayName)
        },
        facts,
        followUps,
        relationships,
        eventIds.size
    )
}

internal suspend fun AppDatabase.previewPeopleDeletion(ids: Set<Long>) =
    withContext(Dispatchers.IO) {
        withTransaction { readPeopleDeletion(ids) }
    }

/** False means the user needs to review changed counts/names; no mutation has happened. */
internal suspend fun AppDatabase.deletePeople(preview: PeopleDeletionPreview): Boolean =
    ReminderDeliveryLock.mutex.withLock {
        withContext(Dispatchers.IO) {
            withTransaction {
                if (readPeopleDeletion(preview.ids) != preview) return@withTransaction false
                rewriteEventTokens { tokens ->
                    tokens.filterNot {
                        it.target is TokenTarget.Person &&
                            it.target.id in preview.ids
                    }
                }
                for (chunk in preview.ids.toList().chunked(900)) contactDao().deleteIds(chunk)
                true
            }
        }
    }
