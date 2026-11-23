package com.idontremember.personalcrm.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "event_tags",
    primaryKeys = ["event_id", "tag_id"],
    foreignKeys = [
        ForeignKey(
            entity = Event::class,
            parentColumns = ["id"],
            childColumns = ["event_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Tag::class,
            parentColumns = ["id"],
            childColumns = ["tag_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["event_id"]),
        Index(value = ["tag_id"])
    ]
)
data class EventTag(
    @ColumnInfo(name = "event_id")
    val eventId: Long,

    @ColumnInfo(name = "tag_id")
    val tagId: Long
)
