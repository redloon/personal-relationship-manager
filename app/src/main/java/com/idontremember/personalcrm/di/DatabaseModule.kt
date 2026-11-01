package com.idontremember.personalcrm.di

import android.content.Context
import androidx.room.Room
import com.idontremember.personalcrm.data.db.AppDatabase
import com.idontremember.personalcrm.data.db.MIGRATION_1_2
import com.idontremember.personalcrm.data.db.dao.PilotDao
import com.idontremember.personalcrm.data.db.dao.ContactDao
import com.idontremember.personalcrm.data.db.dao.ContactFactDao
import com.idontremember.personalcrm.data.db.dao.EventDao
import com.idontremember.personalcrm.data.db.dao.TagDao
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
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "personal_crm.db"
        ).addMigrations(MIGRATION_1_2).build()
    }

    @Provides
    fun providePilotDao(db: AppDatabase): PilotDao = db.pilotDao()

    @Provides
    fun provideContactDao(db: AppDatabase): ContactDao = db.contactDao()

    @Provides
    fun provideContactFactDao(db: AppDatabase): ContactFactDao = db.contactFactDao()

    @Provides
    fun provideEventDao(db: AppDatabase): EventDao = db.eventDao()

    @Provides
    fun provideTagDao(db: AppDatabase): TagDao = db.tagDao()
}