package com.idontremember.personalcrm.data.search

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RecallMappingTest {
    @Test fun rawIdsAreParsedOnceWithoutConfusingOneAndEleven() {
        val entry = RecallRow("event", 9, null, "", "Text", 1, ",1,11,1,", "Text").toEntry()
        assertEquals(setOf(1L, 11L), entry.personIds)
        assertTrue(entry.belongsTo(1))
        assertFalse(entry.belongsTo(111))
        assertEquals("event:9", entry.key)
        assertEquals(RecallTarget.Event(9), entry.target)
    }

    @Test fun everyKindHasAnExplicitTargetAndStableWireName() {
        val targets =
            listOf(
                RecallTarget.Event(1),
                RecallTarget.Fact(1),
                RecallTarget.FollowUp(1),
                RecallTarget.Person(1),
                RecallTarget.Tag(1)
            )
        RecallKind.entries.zip(targets).forEach { (kind, target) ->
            assertEquals(
                target,
                RecallRow(kind.wireValue, 1, null, "", "", 1, "", "").toEntry().target
            )
            assertEquals(kind, RecallKind.fromWire(kind.wireValue))
        }
        assertThrows(IllegalArgumentException::class.java) { RecallKind.fromWire("unexpected") }
    }
}
