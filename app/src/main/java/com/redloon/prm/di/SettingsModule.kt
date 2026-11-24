package com.redloon.prm.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.redloon.prm.data.settings.SettingsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Process-wide Preferences DataStore ("settings.preferences_pb") backing
 * [SettingsRepository]. P10+ hang additional typed preferences off the same
 * DataStore via SettingsKeys.
 *
 * NOTE: the top-level `preferencesDataStore` delegate is deliberate — it caches a
 * single DataStore instance per process. Hilt's SingletonComponent is recreated
 * for every instrumented test, and DataStore refuses two live instances over one
 * file ("multiple DataStores active"), so the instance must outlive the component.
 */
private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "settings"
)

@Module
@InstallIn(SingletonComponent::class)
object SettingsModule {

    @Provides
    @Singleton
    fun provideSettingsDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        context.settingsDataStore

    @Provides
    @Singleton
    fun provideSettingsRepository(dataStore: DataStore<Preferences>): SettingsRepository =
        SettingsRepository(dataStore)
}
