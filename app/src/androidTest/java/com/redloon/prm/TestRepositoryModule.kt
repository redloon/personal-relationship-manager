package com.redloon.prm

import com.redloon.prm.data.contacts.ContactsSyncManager
import com.redloon.prm.data.db.dao.ContactDao
import com.redloon.prm.data.db.dao.ContactFactDao
import com.redloon.prm.data.db.dao.EventDao
import com.redloon.prm.data.db.dao.TagDao
import com.redloon.prm.data.repository.ContactRepository
import com.redloon.prm.data.repository.EventRepository
import com.redloon.prm.data.repository.TagRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton

@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [com.redloon.prm.di.RepositoryModule::class]
)
object TestRepositoryModule {

    @Provides
    @Singleton
    fun provideContactRepository(
        contactDao: ContactDao,
        contactFactDao: ContactFactDao,
        eventDao: EventDao,
        contactsSyncManager: ContactsSyncManager,
        database: com.redloon.prm.data.db.AppDatabase
    ): ContactRepository =
        ContactRepository(contactDao, contactFactDao, eventDao, contactsSyncManager, database)

    @Provides
    @Singleton
    fun provideEventRepository(
        eventDao: EventDao,
        contactDao: ContactDao,
        tagDao: TagDao,
        database: com.redloon.prm.data.db.AppDatabase
    ): EventRepository = EventRepository(eventDao, contactDao, tagDao, database)

    @Provides
    @Singleton
    fun provideTagRepository(
        tagDao: TagDao,
        database: com.redloon.prm.data.db.AppDatabase
    ): TagRepository = TagRepository(tagDao, database)
}
