package com.idontremember.personalcrm.data.followups

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.room.withTransaction
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.idontremember.personalcrm.R
import com.idontremember.personalcrm.data.db.AppDatabase
import com.idontremember.personalcrm.data.settings.SettingsRepository
import com.idontremember.personalcrm.ui.MainActivity
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ReminderDependencies {
    fun database(): AppDatabase
    fun settings(): SettingsRepository
}

object ReminderDeliveryLock {
    val mutex = Mutex()
}

class FollowUpWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = ReminderDeliveryLock.mutex.withLock {
        try {
            deliver()
        } catch (
            error: CancellationException
        ) {
            throw error
        } catch (
            _: SecurityException
        ) {
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    private suspend fun deliver(): Result {
        val deps = EntryPointAccessors.fromApplication(
            applicationContext,
            ReminderDependencies::class.java
        )
        val settings = deps.settings()
        val now = LocalDateTime.now()
        val today = now.toLocalDate().toString()
        if (!settings.followUpRemindersEnabled.first() ||
            !ReminderPolicy.canDeliver(
                now,
                settings.reminderHour.first(),
                settings.lastReminderDay.first()
            )
        ) {
            return Result.success()
        }
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >=
            26
        ) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL,
                    applicationContext.getString(R.string.followup_channel),
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }
        suspend fun allowed(): Boolean {
            if (!settings.followUpRemindersEnabled.first()) return false
            if (!ReminderPolicy.canDeliver(
                    LocalDateTime.now(),
                    settings.reminderHour.first(),
                    null
                )
            ) {
                return false
            }
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(
                    applicationContext,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return false
            }
            if (!NotificationManagerCompat.from(
                    applicationContext
                ).areNotificationsEnabled()
            ) {
                return false
            }
            return Build.VERSION.SDK_INT < 26 ||
                manager.getNotificationChannel(CHANNEL)?.importance !=
                NotificationManager.IMPORTANCE_NONE
        }
        val intent = Intent(
            applicationContext,
            MainActivity::class.java
        ).putExtra(OPEN_FOLLOW_UPS, true)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pending = PendingIntent.getActivity(
            applicationContext,
            803,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL)
            .setSmallIcon(R.drawable.ic_add_event)
            .setContentTitle(applicationContext.getString(R.string.followup_notification_title))
            .setContentText(applicationContext.getString(R.string.followup_notification_body))
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setPriority(NotificationCompat.PRIORITY_LOW).setOnlyAlertOnce(true)
            .setAutoCancel(true).setContentIntent(pending).build()
        val database = deps.database()
        val claimedIds = mutableListOf<Long>()
        var claimedAt = 0L
        var previousDay: String? = null
        var dayReservationAttempted = false
        val outcome = deliverClaimedReminder(
            allowed = ::allowed,
            reserve = {
                previousDay = settings.lastReminderDay.first()
                if (!ReminderPolicy.canDeliver(now, settings.reminderHour.first(), previousDay)) {
                    false
                } else {
                    database.withTransaction {
                        val due = database.followUpDao().getAllOnce().filter {
                            it.deletedAt == null &&
                                it.completedAt == null &&
                                it.notifiedAt == null &&
                                ReminderPolicy.isDue(it.dueDate, now.toLocalDate())
                        }
                        if (due.isEmpty()) {
                            false
                        } else {
                            claimedAt = System.currentTimeMillis()
                            val claim = database.openHelper.writableDatabase.compileStatement(
                                "UPDATE follow_ups SET notified_at = ? WHERE id = ? AND notified_at IS NULL AND completed_at IS NULL AND deleted_at IS NULL AND due_date = ?"
                            )
                            claim.use { statement ->
                                due.forEach { item ->
                                    statement.bindLong(1, claimedAt)
                                    statement.bindLong(2, item.id)
                                    statement.bindString(3, item.dueDate!!)
                                    if (statement.executeUpdateDelete() == 1) claimedIds += item.id
                                }
                            }
                            if (claimedIds.isEmpty()) {
                                false
                            } else {
                                // DataStore and Room are separate stores: compensate both on known failure.
                                dayReservationAttempted = true
                                settings.setLastReminderDay(today)
                                true
                            }
                        }
                    }
                }
            },
            post = { manager.notify(803, notification) },
            release = {
                database.withTransaction {
                    val release = database.openHelper.writableDatabase.compileStatement(
                        "UPDATE follow_ups SET notified_at = NULL WHERE id = ? AND notified_at = ?"
                    )
                    release.use { statement ->
                        claimedIds.forEach { id ->
                            statement.bindLong(1, id)
                            statement.bindLong(2, claimedAt)
                            statement.executeUpdateDelete()
                        }
                    }
                }
                if (dayReservationAttempted &&
                    settings.lastReminderDay.first() == today
                ) {
                    settings.setLastReminderDay(previousDay)
                }
                dayReservationAttempted = false
                claimedIds.clear()
            }
        )
        return if (outcome == DeliveryOutcome.RETRY) Result.retry() else Result.success()
    }
    companion object {
        const val OPEN_FOLLOW_UPS = "open_follow_ups"
        const val CHANNEL = "explicit_followups"
        fun ensureScheduled(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "explicit-followups",
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<FollowUpWorker>(1, TimeUnit.HOURS).build()
            )
        }
    }
}
