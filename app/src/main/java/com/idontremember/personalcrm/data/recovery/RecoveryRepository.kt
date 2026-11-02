package com.idontremember.personalcrm.data.recovery

import androidx.room.withTransaction
import com.idontremember.personalcrm.data.db.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

enum class TrashKind(val table: String) { EVENT("events"), FACT("contact_facts"), FOLLOW_UP("follow_ups") }
data class DeletedItem(val kind: TrashKind, val id: Long, val text: String, val deletedAt: Long)

@Singleton
class RecoveryRepository @Inject constructor(private val database: AppDatabase) {
    fun observeDeleted(): Flow<List<DeletedItem>> = database.invalidationTracker
        .createFlow("events", "contact_facts", "follow_ups")
        .map { withContext(Dispatchers.IO) { readDeleted() } }

    private fun readDeleted(): List<DeletedItem> = buildList {
        val sql = database.openHelper.readableDatabase
        for (kind in TrashKind.entries) {
            val textColumn = if(kind == TrashKind.EVENT) "note" else "text"
            sql.query("SELECT id, $textColumn, deleted_at FROM ${kind.table} WHERE deleted_at IS NOT NULL AND deleted_at >= ? ORDER BY deleted_at DESC", arrayOf(System.currentTimeMillis() - RETENTION_MILLIS)).use { c ->
                while(c.moveToNext()) add(DeletedItem(kind, c.getLong(0), c.getString(1), c.getLong(2)))
            }
        }
    }.sortedByDescending { it.deletedAt }

    suspend fun delete(kind: TrashKind, id: Long) = withContext(Dispatchers.IO) {
        database.withTransaction {
            database.openHelper.writableDatabase.execSQL("UPDATE ${kind.table} SET deleted_at = ? WHERE id = ? AND deleted_at IS NULL", arrayOf(System.currentTimeMillis(), id))
        }
    }

    suspend fun restore(kind: TrashKind, id: Long) = withContext(Dispatchers.IO) {
        database.withTransaction {
            database.openHelper.writableDatabase.execSQL("UPDATE ${kind.table} SET deleted_at = NULL WHERE id = ? AND deleted_at >= ?", arrayOf(id, System.currentTimeMillis() - RETENTION_MILLIS))
        }
    }

    suspend fun purgeExpired(now: Long = System.currentTimeMillis()) = withContext(Dispatchers.IO) {
        database.withTransaction {
            for(kind in TrashKind.entries) database.openHelper.writableDatabase.execSQL(
                "DELETE FROM ${kind.table} WHERE deleted_at IS NOT NULL AND deleted_at < ?", arrayOf(now - RETENTION_MILLIS))
        }
    }

    companion object { const val RETENTION_MILLIS = 30L * 24 * 60 * 60 * 1000 }
}
