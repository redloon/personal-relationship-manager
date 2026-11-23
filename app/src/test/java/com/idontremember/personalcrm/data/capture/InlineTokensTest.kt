package com.idontremember.personalcrm.data.capture

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class InlineTokensTest {
    private val person = TokenTarget.Person(7)
    private val token = InlineToken(4, 14, person)

    @Test fun selectionIsExplicitAndKeepsUnicodeOffsets() {
        val inserted = InlineTokens.insert("🍵 @Maya C", 3, 10, "@Maya Chen", person, emptyList())
        assertEquals("🍵 @Maya Chen ", inserted.text)
        assertEquals(listOf(InlineToken(3, 13, person)), inserted.tokens)
        assertEquals(14, inserted.cursor)
        assertEquals(
            emptyList<InlineToken>(),
            InlineTokens.edited("", "@Maya Chen #friend", emptyList())
        )
    }

    @Test fun ordinaryEditsShiftSpansAndOverlappingEditsUnlink() {
        val text = "Met @Maya Chen today"
        assertEquals(
            listOf(token.copy(start = 5, endExclusive = 15)),
            InlineTokens.edited(text, "!$text", listOf(token))
        )
        assertEquals(listOf(token), InlineTokens.edited(text, "$text!", listOf(token)))
        assertTrue(InlineTokens.edited(text, "Met @Maya Chenish today", listOf(token)).isNotEmpty())
        assertTrue(InlineTokens.edited(text, "Met @Maya Che today", listOf(token)).isEmpty())
    }

    @Test fun lastTokenRemovalDeterminesWhichAssociationToRemove() {
        val first = InlineToken(0, 5, person)
        val second = InlineToken(6, 11, person)
        assertTrue(InlineTokens.removedTargets(listOf(first, second), listOf(first)).isEmpty())
        assertEquals(setOf(person), InlineTokens.removedTargets(listOf(first), emptyList()))
    }

    @Test fun codecRejectsOutOfBoundsOverlapsAndUnlinkedTargets() {
        val valid = listOf(token)
        assertEquals(valid, InlineTokens.decode(InlineTokens.encode(valid)))
        InlineTokens.validate("Met @Maya Chen today", valid, setOf(7), emptySet())
        assertThrows(IllegalArgumentException::class.java) {
            InlineTokens.validate("short", valid, setOf(7), emptySet())
        }
        assertThrows(IllegalArgumentException::class.java) {
            InlineTokens.validate(
                "Met @Maya Chen today",
                valid + token,
                setOf(7),
                emptySet()
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            InlineTokens.validate("Met @Maya Chen today", valid, emptySet(), emptySet())
        }
    }

    @Test fun deletingOneOfIdenticalLabelsPreservesTheOtherPersonsIdentity() {
        val first = InlineToken(0, 5, TokenTarget.Person(1))
        val second = InlineToken(6, 11, TokenTarget.Person(2))
        val expected = listOf(second.copy(start = 0, endExclusive = 5))
        assertEquals(
            expected,
            InlineTokens.edited("@Maya @Maya ", "@Maya ", listOf(first, second), 0, 6, 0)
        )
        assertEquals(
            expected,
            InlineTokens.edited("@Maya @Maya ", "@Maya ", listOf(first, second), 6, 6, 0)
        )
        assertEquals(
            expected,
            InlineTokens.edited("@Maya @Maya ", "@Maya ", listOf(first, second), 0, 0, 0)
        )
        assertEquals(
            listOf(first),
            InlineTokens.edited("@Maya @Maya ", "@Maya ", listOf(first, second), 6, 12, 6)
        )
    }

    @Test fun normalizationFindsDuplicateFullNamesWithoutChangingDisplayText() {
        assertEquals(ContactNames.key("  Maya   Chen  "), ContactNames.key("maya chen"))
        assertNotEquals(ContactNames.key("Maya"), ContactNames.key("Maya Chen"))
    }
}
