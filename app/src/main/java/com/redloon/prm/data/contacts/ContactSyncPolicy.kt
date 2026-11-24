package com.redloon.prm.data.contacts

object ContactSyncPolicy {
    const val REFRESH_INTERVAL_MILLIS = 24 * 60 * 60 * 1000L

    fun showOnboarding(dismissed: Boolean): Boolean = !dismissed

    fun shouldRefresh(
        enabled: Boolean,
        hasPermission: Boolean,
        lastSuccessfulSyncAt: Long,
        now: Long
    ): Boolean = enabled && hasPermission && (
        lastSuccessfulSyncAt <= 0L || now - lastSuccessfulSyncAt >= REFRESH_INTERVAL_MILLIS
        )
}
