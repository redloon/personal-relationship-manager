package com.idontremember.personalcrm.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupJsonTest {
    private val snapshot = BackupSnapshot(
        exportedAt = "2026-10-30T12:00:00Z",
        contacts = listOf(ContactRecord(1, null, "李 Alice", null, null, null, 10, 20)),
        facts = listOf(FactRecord(2, 1, "Likes 🍕", 15)),
        events = listOf(EventRecord(3, "Coffee 🎉", 30, 11, 12)),
        tags = listOf(TagRecord(4, "coffee", 13)),
        eventContacts = listOf(EventContactRecord(3, 1)),
        eventTags = listOf(EventTagRecord(3, 4))
    )

    @Test fun roundTripKeepsAllRecordsAndUnicode() {
        val text = BackupJson.encode(snapshot)
        assertTrue(text.contains("schemaVersion"))
        assertEquals(snapshot, BackupJson.decode(text))
    }

    @Test fun rejectsMissingLinksAndUnknownVersion() {
        assertFailure { BackupJson.decode(BackupJson.encode(snapshot.copy(eventContacts = listOf(EventContactRecord(3, 99))))) }
        assertFailure { BackupJson.decode(BackupJson.encode(snapshot.copy(schemaVersion = 2))) }
        assertFailure { BackupJson.decode("{}") }
    }

    private fun assertFailure(block: () -> Unit) {
        try { block(); throw AssertionError("Expected invalid backup") }
        catch (_: IllegalArgumentException) { }
        catch (_: kotlinx.serialization.SerializationException) { }
    }
}
