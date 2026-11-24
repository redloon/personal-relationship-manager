package com.redloon.prm

import android.content.Context
import androidx.room.Room
import com.redloon.prm.data.contacts.ContactsSyncManager
import com.redloon.prm.data.db.AppDatabase
import com.redloon.prm.data.db.dao.ContactDao
import com.redloon.prm.data.db.dao.ContactFactDao
import com.redloon.prm.data.db.dao.EventDao
import com.redloon.prm.data.db.dao.ProfileLinksDao
import com.redloon.prm.data.db.dao.TagDao
import com.redloon.prm.data.repository.ContactRepository
import com.redloon.prm.data.repository.EventRepository
import com.redloon.prm.data.repository.TagRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton

@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [com.redloon.prm.di.DatabaseModule::class]
)
object TestDatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.inMemoryDatabaseBuilder(
            context,
            AppDatabase::class.java
        ).allowMainThreadQueries().build()

    @Provides
    fun provideProfileLinksDao(db: AppDatabase): ProfileLinksDao = db.profileLinksDao()

    @Provides
    fun provideContactDao(db: AppDatabase): ContactDao = db.contactDao()

    @Provides
    fun provideContactFactDao(db: AppDatabase): ContactFactDao = db.contactFactDao()

    @Provides
    fun provideEventDao(db: AppDatabase): EventDao = db.eventDao()

    @Provides
    fun provideTagDao(db: AppDatabase): TagDao = db.tagDao()
}
