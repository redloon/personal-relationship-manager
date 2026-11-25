package com.redloon.prm.data.contacts

import com.redloon.prm.data.db.entity.Contact
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ContactReconciliationTest {
    @Test fun indexedCandidatesMatchPairwisePolicyAtScaleAndKeepStableOrdering() {
        val profiles = (1L..240).flatMap { index ->
            listOf(
                protected(
                    index,
                    " Person ${index % 80} ",
                    "555${index % 90}",
                    "a${index % 100}@test"
                ),
                linked(
                    1000 + index,
                    "person ${index % 85}",
                    "555${index % 95}",
                    "A${index % 105}@TEST"
                )
            )
        } + listOf(protected(900, "Blank"), linked(1900, "Other blank"))
        val dismissed = setOf(ProfilePair(1, 1001), ProfilePair(2, 1002))
        val expected = profiles.filter { it.androidContactId == null }.flatMap { saved ->
            profiles.filter { it.androidContactId != null }.mapNotNull { android ->
                ReconnectMatching.reviewCandidate(saved, android)
            }
        }.filterNot { it.pair in dismissed }.sortedWith(
            compareBy<ReconnectCandidate> {
                ReconnectMatching.normalizeName(it.savedProfile.displayName)
            }
                .thenBy { it.savedProfile.id }.thenBy { it.androidProfile.id }
        )
        assertEquals(expected, ReconnectMatching.reviewCandidates(profiles.reversed(), dismissed))
        assertEquals(expected.size, expected.map { it.pair }.distinct().size)
    }

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
