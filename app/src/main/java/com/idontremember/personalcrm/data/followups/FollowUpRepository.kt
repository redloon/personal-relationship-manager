package com.idontremember.personalcrm.data.followups

import androidx.room.withTransaction
import com.idontremember.personalcrm.data.db.AppDatabase
import com.idontremember.personalcrm.data.db.entity.FollowUp
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.combine

data class FollowUpWithPerson(val item: FollowUp, val name: String)

@Singleton
class FollowUpRepository @Inject constructor(private val database: AppDatabase) {
    val items =
        combine(database.followUpDao().getAll(), database.contactDao().getAllIncludingHidden()) {
                items,
                contacts
            ->
            val names = contacts.associate { it.id to it.displayName }
            items.map { FollowUpWithPerson(it, names[it.contactId].orEmpty()) }
        }
    suspend fun get(id: Long): FollowUp? = database.followUpDao().getById(id)?.takeIf {
        it.deletedAt ==
            null
    }
    suspend fun save(id: Long?, contactId: Long, text: String, date: String?): Long =
        database.withTransaction {
            require(text.isNotBlank())
            require(database.contactDao().getById(contactId) != null)
            date?.let { LocalDate.parse(it) }
            val old = id?.let { database.followUpDao().getById(it) }
            check(id == null || (old != null && old.deletedAt == null)) { "Follow-up unavailable" }
            if (old ==
                null
            ) {
                database.followUpDao().insert(
                    FollowUp(contactId = contactId, text = text.trim(), dueDate = date)
                )
            } else {
                database.followUpDao().update(
                    old.copy(
                        contactId = contactId,
                        text = text.trim(),
                        dueDate = date,
                        notifiedAt = if (old.dueDate !=
                            date
                        ) {
                            null
                        } else {
                            old.notifiedAt
                        },
                        updatedAt = System.currentTimeMillis()
                    )
                )
                old.id
            }
        }
    suspend fun saveAndComplete(
        id: Long,
        contactId: Long,
        text: String,
        date: String?,
        done: Boolean
    ) = database.withTransaction {
        save(id, contactId, text, date)
        complete(id, done)
    }
    suspend fun complete(id: Long, complete: Boolean) = database.withTransaction {
        val item = get(id) ?: return@withTransaction
        database.followUpDao().update(
            item.copy(
                completedAt = if (complete) System.currentTimeMillis() else null,
                updatedAt = System.currentTimeMillis()
            )
        )
    }
}
