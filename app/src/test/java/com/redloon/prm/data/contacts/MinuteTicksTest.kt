package com.redloon.prm.data.contacts

import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MinuteTicksTest {
    @Test fun activeClockTicksCanAdvanceFutureEventEligibilityWithoutDatabaseWrites() = runTest {
        val clock = object : Clock() {
            var now = 100L
            override fun getZone(): ZoneId = ZoneId.of("UTC")
            override fun withZone(zone: ZoneId): Clock = this
            override fun instant(): Instant = Instant.ofEpochMilli(now)
        }
        val values = mutableListOf<Long>()
        val job = launch { minuteTicks(clock).take(2).toList(values) }
        runCurrent()
        clock.now = 60_100
        advanceTimeBy(60_000)
        runCurrent()
        job.join()
        assertEquals(listOf(100L, 60_100L), values)
    }
}
