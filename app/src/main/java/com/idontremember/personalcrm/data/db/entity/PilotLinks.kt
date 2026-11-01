package com.idontremember.personalcrm.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(tableName = "contact_tags", primaryKeys = ["contact_id", "tag_id"],
    foreignKeys = [ForeignKey(entity = Contact::class, parentColumns = ["id"], childColumns = ["contact_id"], onDelete = ForeignKey.CASCADE), ForeignKey(entity = Tag::class, parentColumns = ["id"], childColumns = ["tag_id"], onDelete = ForeignKey.CASCADE)], indices = [Index("contact_id"), Index("tag_id")])
data class ContactTag(@ColumnInfo(name = "contact_id") val contactId: Long, @ColumnInfo(name = "tag_id") val tagId: Long)

@Entity(tableName = "relationships", primaryKeys = ["contact_a", "contact_b", "label"],
    foreignKeys = [ForeignKey(entity = Contact::class, parentColumns = ["id"], childColumns = ["contact_a"], onDelete = ForeignKey.CASCADE), ForeignKey(entity = Contact::class, parentColumns = ["id"], childColumns = ["contact_b"], onDelete = ForeignKey.CASCADE)], indices = [Index("contact_a"), Index("contact_b")])
data class Relationship(@ColumnInfo(name = "contact_a") val contactA: Long, @ColumnInfo(name = "contact_b") val contactB: Long, val label: String)
