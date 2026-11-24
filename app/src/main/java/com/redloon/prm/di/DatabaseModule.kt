package com.redloon.prm.di

import android.content.Context
import androidx.room.Room
import com.redloon.prm.data.db.AppDatabase
import com.redloon.prm.data.db.MIGRATION_1_2
import com.redloon.prm.data.db.MIGRATION_2_3
import com.redloon.prm.data.db.MIGRATION_3_4
import com.redloon.prm.data.db.MIGRATION_4_5
import com.redloon.prm.data.db.MIGRATION_5_6
import com.redloon.prm.data.db.dao.ContactDao
import com.redloon.prm.data.db.dao.ContactFactDao
import com.redloon.prm.data.db.dao.EventDao
import com.redloon.prm.data.db.dao.ProfileLinksDao
import com.redloon.prm.data.db.dao.TagDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase = Room.databaseBuilder(
        context,
        AppDatabase::class.java,
        "prm.db"
    ).addMigrations(
        MIGRATION_1_2,
        MIGRATION_2_3,
        MIGRATION_3_4,
        MIGRATION_4_5,
        MIGRATION_5_6
    ).build()

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
