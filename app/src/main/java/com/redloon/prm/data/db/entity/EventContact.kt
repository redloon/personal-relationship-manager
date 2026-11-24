package com.redloon.prm.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "event_contacts",
    primaryKeys = ["event_id", "contact_id"],
    foreignKeys = [
        ForeignKey(
            entity = Event::class,
            parentColumns = ["id"],
            childColumns = ["event_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Contact::class,
            parentColumns = ["id"],
            childColumns = ["contact_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["event_id"]),
        Index(value = ["contact_id"])
    ]
)
data class EventContact(
    @ColumnInfo(name = "event_id")
    val eventId: Long,

    @ColumnInfo(name = "contact_id")
    val contactId: Long
)
