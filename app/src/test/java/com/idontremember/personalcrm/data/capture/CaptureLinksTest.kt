package com.idontremember.personalcrm.data.capture

import org.junit.Assert.*
import org.junit.Test

class CaptureLinksTest {
    private val note = "Met @Maya"
    private val token = InlineToken(4, 9, TokenTarget.Person(7))
    private val linked = CaptureLinks(contactIds = setOf(7), tokens = listOf(token))

    @Test fun noteFactNoteRoundTripKeepsTokenIdentityAndDeletingItStillUnlinks() {
        val backToNote = linked.withFactMode(true).withFactMode(false)
        assertEquals(linked, backToNote)
        val deleted = backToNote.edit(note, "Met ", 4, 9, 4)
        assertTrue(deleted.tokens.isEmpty())
        assertTrue(deleted.contactIds.isEmpty())
    }

    @Test fun editingFactProseMovesSpansButDoesNotRemoveTheSeparatelySelectedPerson() {
        val fact = linked.withFactMode(true)
        val prefixed = fact.edit(note, "!$note", 0, 0, 1)
        assertEquals(listOf(token.copy(start = 5, endExclusive = 10)), prefixed.tokens)
        assertEquals(setOf(7L), prefixed.contactIds)
        val deleted = fact.edit(note, "Met ", 4, 9, 4)
        assertTrue(deleted.tokens.isEmpty())
        assertEquals(setOf(7L), deleted.contactIds)
        assertEquals(setOf(7L), deleted.withFactMode(false).contactIds)
    }

    @Test fun insertedTagAndMissingPersonReconciliationPreserveProseAndOtherLinks() {
        val inserted = linked.insert("Met @Maya #t", 10, 12, "#tea", TokenTarget.Tag("tea"))
        assertEquals("Met @Maya #tea ", inserted.text)
        assertEquals(setOf("tea"), inserted.links.tagNames)
        val reconciled = inserted.links.reconcilePeople(emptySet())
        assertTrue(reconciled.contactIds.isEmpty())
        assertEquals(setOf("tea"), reconciled.tagNames)
        assertEquals(listOf(TokenTarget.Tag("tea")), reconciled.tokens.map { it.target })
        assertEquals(emptyList<InlineToken>(), reconciled.removeTag("tea").tokens)
    }

    @Test fun choosingADifferentFactPersonDropsOnlyFormerPersonTokens() {
        val tagged = linked.copy(tagNames = setOf("tea"), tokens = linked.tokens + InlineToken(10, 14, TokenTarget.Tag("tea")))
        val changed = tagged.withFactMode(true).selectPerson(8)
        assertEquals(setOf(8L), changed.contactIds)
        assertEquals(listOf(InlineToken(10, 14, TokenTarget.Tag("tea"))), changed.tokens)
        assertEquals(setOf("tea"), changed.tagNames)
        assertEquals(changed.copy(isFact = false), changed.withFactMode(false))
    }
}
