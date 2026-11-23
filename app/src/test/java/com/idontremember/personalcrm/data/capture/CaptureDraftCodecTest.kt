package com.idontremember.personalcrm.data.capture

import org.junit.Assert.*
import org.junit.Test

class CaptureDraftCodecTest {
    @Test fun existingCapturePayloadKeepsKeysAndTokenIdentity() {
        val original = """{"note":"Met @Maya","contacts":[7],"tags":["tea"],"date":123,"fact":false,"tokens":"[{\"start\":4,\"endExclusive\":9,\"target\":{\"type\":\"person\",\"id\":7}}]"}"""
        val draft = DraftCodec.decodeCapture(original)!!
        assertEquals("Met @Maya", draft.text)
        assertEquals(setOf(7L), draft.links.contactIds)
        assertEquals(setOf("tea"), draft.links.tagNames)
        assertEquals(listOf(InlineToken(4, 9, TokenTarget.Person(7))), draft.links.tokens)
        assertEquals(draft, DraftCodec.decodeCapture(DraftCodec.encode(draft)))
        assertTrue(DraftCodec.encode(draft).contains("\"note\""))
    }
    @Test fun invalidTokensDoNotDiscardOtherwiseRecoverableDraft() {
        val draft = DraftCodec.decodeCapture("""{"note":"Keep this","contacts":[7],"tokens":"broken"}""")!!
        assertEquals("Keep this", draft.text)
        assertEquals(setOf(7L), draft.links.contactIds)
        assertTrue(draft.links.tokens.isEmpty())
        assertNull(DraftCodec.decodeCapture("broken"))
    }
    @Test fun followUpPayloadKeepsExistingShapeAndNullDateSemantics() {
        val draft = DraftCodec.decodeFollowUp("""{"text":"Ask about trip","person":7,"date":""}""")!!
        assertEquals(FollowUpDraft("Ask about trip", 7, null), draft)
        assertEquals(draft, DraftCodec.decodeFollowUp(DraftCodec.encode(draft)))
        assertEquals("followup_person_7", followUpDraftKey(null, 7))
        assertEquals("followup_9", followUpDraftKey(9, 7))
    }
}
