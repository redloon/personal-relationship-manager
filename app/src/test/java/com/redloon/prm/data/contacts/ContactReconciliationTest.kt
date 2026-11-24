package com.redloon.prm.data.contacts

import com.redloon.prm.data.db.entity.Contact
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ContactReconciliationTest {
    @Test fun normalizesWhitespaceCasePhonesAndEmails() {
        assertEquals("maya chen", ReconnectMatching.normalizeName("  Maya\t CHEN  "))
        assertEquals("+14155550123", ReconnectMatching.normalizePhone(" +1 (415) 555-0123 "))
        assertEquals("18003569377", ReconnectMatching.normalizePhone("1-800-FLOWERS"))
        assertEquals("maya@example.com", ReconnectMatching.normalizeEmail(" Maya@Example.COM "))
    }

    @Test fun autoReconnectRequiresNameAndDetailAndBidirectionalUniqueness() {
        val saved = protected(1, " Maya  Chen ", phone = "+1 415 555 0123")
        val android = linked(2, "maya chen", phone = "+1 (415) 555-0123", email = "new@example.com")

        assertEquals(
            listOf(ProfilePair(1, 2)),
            ReconnectMatching.autoReconnectPairs(listOf(saved, android), emptySet())
        )
    }

    @Test fun ambiguityOnEitherSidePreventsAutomaticReconnect() {
        val saved = protected(1, "Maya Chen", phone = "4155550123")
        val otherSaved = protected(3, "Maya Chen", phone = "4155550123")
        val android = linked(2, "Maya Chen", phone = "4155550123")

        assertTrue(
            ReconnectMatching.autoReconnectPairs(
                listOf(saved, otherSaved, android),
                emptySet()
            ).isEmpty()
        )

        val secondAndroid = linked(4, "Maya Chen", phone = "4155550123")
        assertTrue(
            ReconnectMatching.autoReconnectPairs(
                listOf(saved, android, secondAndroid),
                emptySet()
            ).isEmpty()
        )
    }

    @Test fun reviewIncludesNameOnlyAndRenamedDetailMatches() {
        val saved = protected(1, "Maya Chen", phone = "4155550123")
        val sameName = linked(2, "Maya Chen")
        val renamed = linked(3, "Maya Alvarez", phone = "(415) 555-0123")

        assertEquals(
            listOf(ProfilePair(1, 2), ProfilePair(1, 3)),
            ReconnectMatching.reviewCandidates(
                listOf(saved, sameName, renamed),
                emptySet()
            ).map { it.pair }
        )
    }

    @Test fun excludesDeletionEligiblePeopleAndDismissedPairs() {
        val protected = protected(1, "Maya Chen", email = "maya@example.com")
        val deletable = protected.copy(id = 4, deletionAllowed = true)
        val android = linked(2, "Maya Chen", email = "MAYA@example.com")
        val dismissed = setOf(ProfilePair(1, 2))

        assertTrue(
            ReconnectMatching.autoReconnectPairs(
                listOf(protected, deletable, android),
                dismissed
            ).isEmpty()
        )
        assertTrue(
            ReconnectMatching.reviewCandidates(
                listOf(protected, deletable, android),
                dismissed
            ).isEmpty()
        )
    }

    @Test fun dismissedAlternativeStillPreventsAnAmbiguousAutomaticReconnect() {
        val saved = protected(1, "Maya Chen", phone = "4155550123")
        val first = linked(2, "Maya Chen", phone = "4155550123")
        val second = linked(3, "Maya Chen", phone = "4155550123")

        assertTrue(
            ReconnectMatching.autoReconnectPairs(
                listOf(saved, first, second),
                setOf(ProfilePair(1, 2))
            ).isEmpty()
        )
    }

    private fun protected(id: Long, name: String, phone: String? = null, email: String? = null) =
        Contact(id = id, displayName = name, phoneNumber = phone, email = email)

    private fun linked(id: Long, name: String, phone: String? = null, email: String? = null) =
        Contact(
            id = id,
            androidContactId = 100 + id,
            displayName = name,
            phoneNumber = phone,
            email = email
        )
}
