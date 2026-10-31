package com.idontremember.personalcrm

import com.idontremember.personalcrm.data.contacts.ContactsSyncManager
import com.idontremember.personalcrm.data.db.dao.ContactDao
import com.idontremember.personalcrm.data.db.dao.ContactFactDao
import com.idontremember.personalcrm.data.db.dao.EventDao
import com.idontremember.personalcrm.data.db.dao.TagDao
import com.idontremember.personalcrm.data.repository.ContactRepository
import com.idontremember.personalcrm.data.repository.EventRepository
import com.idontremember.personalcrm.data.repository.TagRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton

@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [com.idontremember.personalcrm.di.RepositoryModule::class]
)
object TestRepositoryModule {

    @Provides
    @Singleton
    fun provideContactRepository(
        contactDao: ContactDao,
        contactFactDao: ContactFactDao,
        eventDao: EventDao,
        contactsSyncManager: ContactsSyncManager
    ): ContactRepository = ContactRepository(contactDao, contactFactDao, eventDao, contactsSyncManager)

    @Provides
    @Singleton
    fun provideEventRepository(
        eventDao: EventDao,
        contactDao: ContactDao,
        tagDao: TagDao,
        database: com.idontremember.personalcrm.data.db.AppDatabase
    ): EventRepository = EventRepository(eventDao, contactDao, tagDao, database)

    @Provides
    @Singleton
    fun provideTagRepository(
        tagDao: TagDao
    ): TagRepository = TagRepository(tagDao)
}
