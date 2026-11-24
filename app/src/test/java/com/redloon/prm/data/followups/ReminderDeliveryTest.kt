package com.redloon.prm.data.followups

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test

class ReminderDeliveryTest {
    @Test fun failedPostRestoresClaimsAndPreviousDay() = runBlocking {
        val state = State().apply { postError = IllegalStateException("Binder failed") }
        assertEquals(DeliveryOutcome.RETRY, state.deliver())
        assertNull(state.notifiedAt)
        assertEquals("yesterday", state.day)
    }

    @Test fun disabledDeliveryNeverConsumesRecords() = runBlocking {
        val state = State().apply { enabled = false }
        assertEquals(DeliveryOutcome.SKIPPED, state.deliver())
        assertFalse(state.reserved)
        assertNull(state.notifiedAt)
        assertEquals(0, state.posts)
    }

    @Test fun permissionRevokedAfterClaimRestoresClaimsWithoutLosingConcurrentEdit() = runBlocking {
        val state = State().apply {
            afterClaim =
                {
                    text = "Edited while scheduling"
                    enabled = false
                }
        }
        assertEquals(DeliveryOutcome.SKIPPED, state.deliver())
        assertNull(state.notifiedAt)
        assertEquals("Edited while scheduling", state.text)
        assertEquals("yesterday", state.day)
    }

    @Test fun securityFailureReleasesClaimAndDoesNotRetryBlockedPermission() = runBlocking {
        val state = State().apply { postError = SecurityException("Permission revoked") }
        assertEquals(DeliveryOutcome.SKIPPED, state.deliver())
        assertNull(state.notifiedAt)
        assertEquals("yesterday", state.day)
    }

    @Test fun successfulPostHappensOnce() = runBlocking {
        val state = State()
        assertEquals(DeliveryOutcome.SENT, state.deliver())
        assertEquals(DeliveryOutcome.SKIPPED, state.deliver())
        assertEquals(1, state.posts)
        assertEquals(123L, state.notifiedAt)
        assertEquals("today", state.day)
    }

    @Test fun cancellationRestoresClaimBeforePropagating() = runBlocking {
        val state = State().apply { postError = CancellationException("Stopped") }
        try {
            state.deliver()
            fail("Expected cancellation")
        } catch (_: CancellationException) { }
        assertNull(state.notifiedAt)
        assertEquals("yesterday", state.day)
    }

    private class State {
        var enabled = true
        var reserved = false
        var notifiedAt: Long? = null
        var day: String? = "yesterday"
        var previousDay: String? = null
        var text = "Ask about trip"
        var posts = 0
        var postError: Exception? = null
        var afterClaim: (State.() -> Unit)? = null
        suspend fun deliver() = deliverClaimedReminder(
            allowed = { enabled && (reserved || day != "today") },
            reserve = {
                previousDay = day
                day = "today"
                notifiedAt = 123L
                reserved = true
                afterClaim?.invoke(this)
                true
            },
            post = {
                postError?.let { throw it }
                posts++
                reserved = false
            },
            release = {
                notifiedAt = null
                day = previousDay
                reserved = false
            }
        )
    }
}
