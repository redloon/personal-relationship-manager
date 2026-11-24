package com.redloon.prm.data.followups

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

internal enum class DeliveryOutcome { SENT, SKIPPED, RETRY }

/** Compensate a reserved delivery when posting is known to have failed or been aborted. */
internal suspend fun deliverClaimedReminder(
    allowed: suspend () -> Boolean,
    reserve: suspend () -> Boolean,
    post: suspend () -> Unit,
    release: suspend () -> Unit
): DeliveryOutcome {
    var attemptedReservation = false
    suspend fun releaseReservation() {
        if (attemptedReservation) withContext(NonCancellable) { release() }
    }
    return try {
        if (!allowed()) return DeliveryOutcome.SKIPPED
        attemptedReservation = true
        if (!reserve() || !allowed()) {
            releaseReservation()
            DeliveryOutcome.SKIPPED
        } else {
            post()
            DeliveryOutcome.SENT
        }
    } catch (error: CancellationException) {
        releaseReservation()
        throw error
    } catch (_: SecurityException) {
        releaseReservation()
        DeliveryOutcome.SKIPPED
    } catch (_: Exception) {
        releaseReservation()
        DeliveryOutcome.RETRY
    }
}
