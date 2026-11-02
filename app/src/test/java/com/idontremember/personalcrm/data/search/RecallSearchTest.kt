package com.idontremember.personalcrm.data.search

import org.junit.Assert.*
import org.junit.Test

class RecallSearchTest {
    private fun row(kind: String = "event", id: Long = 1, title: String = "Alice", text: String = "Likes climbing", date: Long = 1, people: String = ",1,", search: String = "$title $text") =
        RecallEntry(kind, id, 1, title, text, date, people, search)

    @Test fun forgottenFactFindsPersonAndEveryTermMustMatch() {
        val entries = listOf(row(kind = "fact", search = "Alice Likes climbing outdoors"))
        assertEquals(1, RecallSearch.find(entries, "ALICE climbing").size)
        assertTrue(RecallSearch.find(entries, "climbing swimming").isEmpty())
    }
    @Test fun wildcardCharactersAreLiteral() {
        val entries = listOf(row(text = "100% certain"), row(id = 2, text = "under_score"), row(id = 3, text = "ordinary"))
        assertEquals(listOf(1L), RecallSearch.find(entries, "%").map { it.id })
        assertEquals(listOf(2L), RecallSearch.find(entries, "_").map { it.id })
    }
    @Test fun exactAndPrefixNamesOutrankContextAndTiesAreStable() {
        val entries = listOf(row(id = 8, date = 100), row(kind = "person", id = 3, title = "Alice Jones", text = "", date = 2), row(kind = "person", id = 2, title = "Alice", text = "", date = 1))
        assertEquals(listOf(2L, 3L, 8L), RecallSearch.find(entries, "Alice").map { it.id })
    }
    @Test fun personScopeDoesNotConfuseIdsOneAndEleven() {
        val entries = listOf(row(id = 1, people = ",1,"), row(id = 2, people = ",11,"))
        assertEquals(listOf(1L), RecallSearch.find(entries, "climbing", 1).map { it.id })
    }
    @Test fun blankQueryReturnsNewestContextAndNotAddressBook() {
        val entries = listOf(row(id = 1, date = 10), row(kind = "person", id = 2, date = 30), row(kind = "fact", id = 3, date = 20), row(kind = "followup", id = 4, date = 40))
        assertEquals(listOf(3L, 1L), RecallSearch.find(entries, "  ").map { it.id })
    }
    @Test fun followUpsRemainSearchableButNeverAppearInBlankRecents() {
        val entries = listOf(row(kind = "followup", id = 4, text = "Ask about climbing"))
        assertTrue(RecallSearch.find(entries, "").isEmpty())
        assertEquals(listOf(4L), RecallSearch.find(entries, "climbing").map { it.id })
    }
    @Test fun excerptContainsMatchingWordDeepInLongNote() {
        val text = "irrelevant ".repeat(40) + "climbing outdoors together"
        assertTrue(RecallSearch.excerpt(text, "climbing", 80).contains("climbing"))
        assertTrue(RecallSearch.excerpt(text, "climbing", 80).length <= 82)
    }
}
