package com.redloon.prm.data.contacts

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ContactSyncPolicyTest {
    private val day = 24 * 60 * 60 * 1000L

    @Test fun onboardingAppearsOnlyUntilAChoiceIsPersisted() {
        assertTrue(ContactSyncPolicy.showOnboarding(dismissed = false))
        assertFalse(ContactSyncPolicy.showOnboarding(dismissed = true))
    }

    @Test fun automaticRefreshRequiresExplicitOptInAndPermission() {
        assertFalse(
            ContactSyncPolicy.shouldRefresh(
                enabled = false,
                hasPermission = true,
                lastSuccessfulSyncAt = 0,
                now = day
            )
        )
        assertFalse(
            ContactSyncPolicy.shouldRefresh(
                enabled = true,
                hasPermission = false,
                lastSuccessfulSyncAt = 0,
                now = day
            )
        )
    }

    @Test fun automaticRefreshUsesAnInclusiveTwentyFourHourBoundary() {
        val last = 10_000L
        assertFalse(
            ContactSyncPolicy.shouldRefresh(true, true, last, last + day - 1)
        )
        assertTrue(
            ContactSyncPolicy.shouldRefresh(true, true, last, last + day)
        )
    }

    @Test fun neverSyncedOptedInDirectoryRefreshesImmediately() {
        assertTrue(ContactSyncPolicy.shouldRefresh(true, true, 0, now = 1))
    }
}
