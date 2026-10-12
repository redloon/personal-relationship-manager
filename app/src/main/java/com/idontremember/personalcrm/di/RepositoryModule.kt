package com.idontremember.personalcrm.di

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
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

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
        tagDao: TagDao
    ): EventRepository = EventRepository(eventDao, contactDao, tagDao)

    @Provides
    @Singleton
    fun provideTagRepository(
        tagDao: TagDao
    ): TagRepository = TagRepository(tagDao)
}