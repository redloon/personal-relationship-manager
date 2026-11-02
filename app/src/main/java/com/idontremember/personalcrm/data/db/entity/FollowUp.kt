package com.idontremember.personalcrm.data.db.entity

import androidx.room.*

@Entity(tableName = "follow_ups", foreignKeys = [ForeignKey(entity = Contact::class, parentColumns = ["id"], childColumns = ["contact_id"], onDelete = ForeignKey.CASCADE)], indices = [Index("contact_id")])
data class FollowUp(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "contact_id") val contactId: Long,
    val text: String,
    @ColumnInfo(name = "due_date") val dueDate: String? = null,
    @ColumnInfo(name = "completed_at") val completedAt: Long? = null,
    @ColumnInfo(name = "notified_at") val notifiedAt: Long? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "deleted_at") val deletedAt: Long? = null
)
