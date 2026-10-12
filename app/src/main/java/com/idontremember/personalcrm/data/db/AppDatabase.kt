package com.idontremember.personalcrm.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.idontremember.personalcrm.data.db.dao.ContactDao
import com.idontremember.personalcrm.data.db.dao.ContactFactDao
import com.idontremember.personalcrm.data.db.dao.EventDao
import com.idontremember.personalcrm.data.db.dao.TagDao
import com.idontremember.personalcrm.data.db.entity.Contact
import com.idontremember.personalcrm.data.db.entity.ContactFact
import com.idontremember.personalcrm.data.db.entity.Event
import com.idontremember.personalcrm.data.db.entity.EventContact
import com.idontremember.personalcrm.data.db.entity.EventTag
import com.idontremember.personalcrm.data.db.entity.Tag

@Database(
    entities = [
        Contact::class,
        ContactFact::class,
        Event::class,
        EventContact::class,
        Tag::class,
        EventTag::class
    ],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun contactDao(): ContactDao
    abstract fun contactFactDao(): ContactFactDao
    abstract fun eventDao(): EventDao
    abstract fun tagDao(): TagDao
}