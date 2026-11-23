package com.idontremember.personalcrm.ui.addevent

import com.idontremember.personalcrm.ui.components.TriggerType
import com.idontremember.personalcrm.ui.components.detectTrigger
import java.util.Calendar
import java.util.TimeZone
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class CaptureContextTest {
    @Test fun editingNeverUsesTheNewCaptureDraft() {
        assertEquals("draft", captureDraftKey())
        assertEquals("event_12", captureDraftKey(eventId = 12))
        assertEquals("fact_12", captureDraftKey(factId = 12))
        assertNotEquals(captureDraftKey(eventId = 12), captureDraftKey(factId = 12))
    }

    @Test fun fullNameMentionCanBeSelected() {
        val text = "Met @Alice Smith"
        val trigger = detectTrigger(text, text.length)
        assertEquals(TriggerType.CONTACT, trigger?.type)
        assertEquals("Alice Smith", trigger?.query)
        assertNull(detectTrigger("email@example.com", 17))
        assertNull(detectTrigger("#two words", 10))
    }

    @Test fun unresolvedFullNamesAreNotTerminatedByExistingShorterNames() {
        val text = "Met @Alice Smith yesterday"
        assertEquals("Alice Smith yesterday", detectTrigger(text, text.length)?.query)
        assertEquals("Maya C", detectTrigger("@Maya C", 7)?.query)
        assertEquals("Maya ", detectTrigger("@Maya ", 6)?.query)
        val typing = "Met @Alice Sm"
        assertEquals("Alice Sm", detectTrigger(typing, typing.length)?.query)
    }

    @Test fun incrementalMentionTypingKeepsSpacesAndUsesTheLatestTrigger() {
        val text = "@Maya Chen"
        for (end in 1..text.length) {
            assertEquals(text.substring(1, end), detectTrigger(text.substring(0, end), end)?.query)
        }
        val consecutive = "@Maya Chen and @Alex"
        val trigger = detectTrigger(consecutive, consecutive.length)!!
        assertEquals("Alex", trigger.query)
        assertEquals(consecutive.lastIndexOf('@'), trigger.startIndex)
    }

    @Test fun tagFilteringEndsAtWhitespaceAndMixedTriggersUseTheirOwnQuery() {
        for (text in listOf("#", "#t", "#te", "#tea")) {
            assertEquals(TriggerType.TAG, detectTrigger(text, text.length)?.type)
            assertEquals(text.drop(1), detectTrigger(text, text.length)?.query)
        }
        assertNull(detectTrigger("#tea ", 5))
        val mixed = "@Maya Chen #tea @Alex"
        assertEquals("Alex", detectTrigger(mixed, mixed.length)?.query)
        assertEquals(TriggerType.CONTACT, detectTrigger(mixed, mixed.length)?.type)
        assertNull(detectTrigger("mail@example.com", 16))
    }

    @Test fun datePickerKeepsLocalTimeInsteadOfSwitchingToUtcMidnight() {
        val zone = TimeZone.getTimeZone("America/Los_Angeles")
        val original = Calendar.getInstance(zone).apply {
            set(2026, Calendar.NOVEMBER, 1, 17, 35, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val pickedUtc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(2026, Calendar.NOVEMBER, 5)
        }
        val result = Calendar.getInstance(zone).apply {
            timeInMillis =
                withPickedDate(original.timeInMillis, pickedUtc.timeInMillis, zone)
        }
        assertEquals(5, result.get(Calendar.DAY_OF_MONTH))
        assertEquals(17, result.get(Calendar.HOUR_OF_DAY))
        assertEquals(35, result.get(Calendar.MINUTE))
    }
}
