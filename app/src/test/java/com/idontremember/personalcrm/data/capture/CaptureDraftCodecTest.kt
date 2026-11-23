package com.idontremember.personalcrm.data.capture

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CaptureDraftCodecTest {
    @Test fun existingCapturePayloadKeepsKeysAndTokenIdentity() {
        val original =
            """{"note":"Met @Maya","contacts":[7],"tags":["tea"]""" +
                ""","date":123,"fact":false,"tokens":"[{\"start\":4,\"endExclusive\":9,""" +
                """\"target\":{\"type\":\"person\",\"id\":7}}]"}"""
        val draft = DraftCodec.decodeCapture(original)!!
        assertEquals("Met @Maya", draft.text)
        assertEquals(setOf(7L), draft.links.contactIds)
        assertEquals(setOf("tea"), draft.links.tagNames)
        assertEquals(listOf(InlineToken(4, 9, TokenTarget.Person(7))), draft.links.tokens)
        assertEquals(draft, DraftCodec.decodeCapture(DraftCodec.encode(draft)))
        assertTrue(DraftCodec.encode(draft).contains("\"note\""))
    }

    @Test fun invalidTokensDoNotDiscardOtherwiseRecoverableDraft() {
        val draft = DraftCodec.decodeCapture(
            """{"note":"Keep this","contacts":[7],"tokens":"broken"}"""
        )!!
        assertEquals("Keep this", draft.text)
        assertEquals(setOf(7L), draft.links.contactIds)
        assertTrue(draft.links.tokens.isEmpty())
        assertNull(DraftCodec.decodeCapture("broken"))
    }

    @Test fun followUpPayloadKeepsExistingShapeAndNullDateSemantics() {
        val draft = DraftCodec.decodeFollowUp(
            """{"text":"Ask about trip","person":7,"date":""}"""
        )!!
        assertEquals(FollowUpDraft("Ask about trip", 7, null), draft)
        assertEquals(draft, DraftCodec.decodeFollowUp(DraftCodec.encode(draft)))
        assertEquals("followup_person_7", followUpDraftKey(null, 7))
        assertEquals("followup_9", followUpDraftKey(9, 7))
    }

    @Test fun seededUnicodeTokensSurviveDraftEncodingAndEdits() {
        val seed = System.getProperty("junit.jupiter.execution.order.random.seed")!!.toLong()
        val random = kotlin.random.Random(seed)
        val pieces = listOf("🍵", "👩🏽‍💻", "e\u0301", "東京", "مرحبا", "\n", "\"", "\\", "@", "#")
        repeat(200) { case ->
            val prefix = (0 until random.nextInt(1, 12)).joinToString("") { pieces.random(random) }
            val personId = random.nextLong(1, 10_000)
            val person = TokenTarget.Person(personId)
            val label = "@${pieces.random(random)} Maya"
            val inserted = CaptureLinks().insert(
                prefix,
                prefix.length,
                prefix.length,
                label,
                person
            )
            val draft = CaptureDraft(inserted.text, inserted.links, random.nextLong())
            assertEquals(
                draft,
                DraftCodec.decodeCapture(DraftCodec.encode(draft)),
                "seed=$seed case=$case"
            )
            assertEquals(
                label,
                draft.text.substring(
                    draft.links.tokens.single().start,
                    draft.links.tokens.single().endExclusive
                )
            )
            val shifted = draft.links.edit(draft.text, "🍵" + draft.text, 0, 0, 2)
            assertEquals(
                draft.links.tokens.map {
                    it.copy(
                        start = it.start + 2,
                        endExclusive =
                        it.endExclusive + 2
                    )
                },
                shifted.tokens
            )
            val removed = shifted.edit(
                "🍵" + draft.text,
                "🍵" + prefix,
                2 + prefix.length,
                2 + draft.text.length,
                2 + prefix.length
            )
            assertTrue(removed.tokens.isEmpty(), "seed=$seed case=$case")
            assertTrue(removed.contactIds.isEmpty())
        }
    }
}
