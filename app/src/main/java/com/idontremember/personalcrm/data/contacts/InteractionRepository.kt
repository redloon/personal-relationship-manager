package com.idontremember.personalcrm.data.contacts

import com.idontremember.personalcrm.data.db.AppDatabase
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.merge

/** Cold ticks stop with their screen's subscription; the clock is injectable for boundary tests. */
fun minuteTicks(clock: Clock) = flow {
    while (true) {
        emit(clock.millis())
        delay(60_000)
    }
}

@Singleton
class InteractionRepository @Inject constructor(database: AppDatabase, clock: Clock) {
    /** Read the clock on every invalidation too: a newly saved event is newer than the last timer tick. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val snapshots = merge(
        minuteTicks(clock).map { Unit },
        database.invalidationTracker.createFlow("events", "event_contacts").map { Unit }
    ).mapLatest {
        val now = clock.millis()
        val rows = database.contactDao().interactionStats(now).first()
        InteractionSnapshot(now, rows.associateBy(InteractionStats::contactId))
    }
}
