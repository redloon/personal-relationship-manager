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

    @Test fun oldBackupsAndNewLinksRoundTrip() {
        val oldSnapshot = snapshot.copy(schemaVersion = 1)
        val old = BackupJson.encode(oldSnapshot).replace(Regex(",\\s*\"contactTags\"\\s*:\\s*\\[\\s*\\]"), "").replace(Regex(",\\s*\"relationships\"\\s*:\\s*\\[\\s*\\]"), "")
        assertEquals(oldSnapshot, BackupJson.decode(old))
        val newer = snapshot.copy(contacts = snapshot.contacts + ContactRecord(5, null, "Bob", null, null, null, 1, 1), contactTags = listOf(ContactTagRecord(1, 4)), relationships = listOf(RelationshipRecord(1, 5, "Friend")))
        assertEquals(newer, BackupJson.decode(BackupJson.encode(newer)))
        assertFailure { BackupJson.decode(BackupJson.encode(newer.copy(relationships = listOf(RelationshipRecord(5, 1, "Friend"))))) }
    }

    @Test fun rejectsMissingLinksAndUnknownVersion() {
        assertFailure { BackupJson.decode(BackupJson.encode(snapshot.copy(eventContacts = listOf(EventContactRecord(3, 99))))) }
        assertFailure { BackupJson.decode(BackupJson.encode(snapshot.copy(schemaVersion = 99))) }
        assertFailure { BackupJson.decode("{}") }
    }

    @Test fun versionTwoKeepsDeletedRecordsAndFollowUps() {
        val expanded = snapshot.copy(schemaVersion = 2,
            facts = snapshot.facts.map { it.copy(deletedAt = 100) },
            events = snapshot.events.map { it.copy(deletedAt = 101) },
            followUps = listOf(FollowUpRecord(9, 1, "Ask about the move", "2026-11-03", null, 80, 10, 20, null)))
        assertEquals(expanded, BackupJson.decode(BackupJson.encode(expanded)))
    }

    @Test fun rejectsOrphanFollowUpBeforeWriting() {
        assertFailure { BackupJson.decode(BackupJson.encode(snapshot.copy(
            followUps = listOf(FollowUpRecord(9, 999, "Ask", null, null, null, 1, 1, null))))) }
    }

    @Test fun rejectsInvalidFollowUpDate() {
        assertFailure { BackupJson.decode(BackupJson.encode(snapshot.copy(
            followUps = listOf(FollowUpRecord(9, 1, "Ask", "2026-02-31", null, null, 1, 1, null))))) }
    }

    @Test fun inlineTokensRoundTripAndRequireRealAssociations() {
        val tokens = com.idontremember.personalcrm.data.capture.InlineTokens.encode(listOf(
            com.idontremember.personalcrm.data.capture.InlineToken(0, 5, com.idontremember.personalcrm.data.capture.TokenTarget.Person(1))))
        val linked = snapshot.copy(events = listOf(EventRecord(3, "@Maya at coffee", 30, 11, 12, inlineTokens = tokens)))
        assertEquals(linked, BackupJson.decode(BackupJson.encode(linked)))
        assertFailure { BackupJson.decode(BackupJson.encode(linked.copy(eventContacts = emptyList()))) }
        val old = BackupJson.encode(snapshot.copy(schemaVersion = 2)).replace(",\n            \"inlineTokens\": \"[]\"", "")
        org.junit.Assert.assertFalse(old.contains("inlineTokens"))
        assertEquals("[]", BackupJson.decode(old).events.single().inlineTokens)
    }

    private fun assertFailure(block: () -> Unit) {
        try { block(); throw AssertionError("Expected invalid backup") }
        catch (_: IllegalArgumentException) { }
        catch (_: kotlinx.serialization.SerializationException) { }
    }
}
