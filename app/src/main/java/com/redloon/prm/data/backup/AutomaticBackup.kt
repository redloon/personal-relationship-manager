package com.redloon.prm.data.backup

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.redloon.prm.data.recovery.RecoveryRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private val Context.backupPreferences by preferencesDataStore("backup_settings")
data class BackupStatus(val folder: String?, val lastSuccess: Long?, val failed: Boolean)

@Singleton
class AutomaticBackup @Inject constructor(
    @ApplicationContext private val context: Context,
    private val exporter: DataExporter,
    private val recovery: RecoveryRepository
) {
    private val backupLock = Mutex()
    private val store = context.backupPreferences
    val status = store.data.map { BackupStatus(it[FOLDER], it[LAST_SUCCESS], it[FAILED] ?: false) }

    suspend fun setFolder(uri: Uri?) {
        if (uri == null) {
            store.edit {
                it.remove(FOLDER)
                it[FAILED] = false
            }
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        } else {
            context.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            store.edit {
                if (it[FOLDER] != uri.toString()) it.remove(LAST_SUCCESS)
                it[FOLDER] = uri.toString()
                it[FAILED] = false
            }
            val request = PeriodicWorkRequestBuilder<AutomaticBackupWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(1, TimeUnit.DAYS)
                .setConstraints(
                    Constraints.Builder().setRequiresStorageNotLow(true).build()
                ).build()
            WorkManager.getInstance(
                context
            ).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        }
    }

    suspend fun backUpNow(): Boolean = backupLock.withLock {
        withContext(Dispatchers.IO) {
            val folder = store.data.first()[FOLDER] ?: return@withContext false
            try {
                recovery.purgeExpired()
                val source = exporter.exportToJson()
                val stamp = SimpleDateFormat("yyyy-MM-dd-HHmmss-SSS", Locale.US).apply {
                    timeZone =
                        TimeZone.getTimeZone("UTC")
                }.format(Date())
                VerifiedBackupWriter(
                    DocumentsBackupDestination(context.contentResolver, folder.toUri())
                )
                    .write(source, "prm-auto-$stamp.json")
                store.edit {
                    it[LAST_SUCCESS] = System.currentTimeMillis()
                    it[FAILED] = false
                }
                true
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                store.edit { it[FAILED] = true }
                false
            }
        }
    }

    companion object {
        private const val WORK_NAME = "daily-prm-backup"
        private val FOLDER = stringPreferencesKey("folder")
        private val LAST_SUCCESS = longPreferencesKey("last_success")
        private val FAILED = booleanPreferencesKey("failed")
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface BackupWorkerDependencies {
    fun automaticBackup(): AutomaticBackup
}

class AutomaticBackupWorker(context: Context, parameters: WorkerParameters) :
    CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val backup = EntryPointAccessors.fromApplication(
            applicationContext,
            BackupWorkerDependencies::class.java
        ).automaticBackup()
        return if (backup.backUpNow()) Result.success() else Result.retry()
    }
}
