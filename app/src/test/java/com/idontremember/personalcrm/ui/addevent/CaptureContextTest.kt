package com.idontremember.personalcrm.ui.addevent

import com.idontremember.personalcrm.ui.components.TriggerType
import com.idontremember.personalcrm.ui.components.detectTrigger
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

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

    @Test fun completedMentionDoesNotReopenAsYouContinueWriting() {
        val text = "Met @Alice Smith yesterday"
        assertNull(detectTrigger(text, text.length, listOf("Alice", "Alice Smith")))
        val typing = "Met @Alice Sm"
        assertEquals("Alice Sm", detectTrigger(typing, typing.length, listOf("Alice", "Alice Smith"))?.query)
    }

    @Test fun datePickerKeepsLocalTimeInsteadOfSwitchingToUtcMidnight() {
        val zone = TimeZone.getTimeZone("America/Los_Angeles")
        val original = Calendar.getInstance(zone).apply { set(2026, Calendar.NOVEMBER, 1, 17, 35, 0); set(Calendar.MILLISECOND, 0) }
        val pickedUtc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { clear(); set(2026, Calendar.NOVEMBER, 5) }
        val result = Calendar.getInstance(zone).apply { timeInMillis = withPickedDate(original.timeInMillis, pickedUtc.timeInMillis, zone) }
        assertEquals(5, result.get(Calendar.DAY_OF_MONTH))
        assertEquals(17, result.get(Calendar.HOUR_OF_DAY))
        assertEquals(35, result.get(Calendar.MINUTE))
    }
}
