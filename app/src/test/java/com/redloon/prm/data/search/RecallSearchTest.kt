package com.redloon.prm.data.search

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RecallSearchTest {
    private fun row(
        kind: RecallKind = RecallKind.EVENT,
        id: Long = 1,
        title: String = "Alice",
        text: String = "Likes climbing",
        date: Long = 1,
        people: String = ",1,",
        search: String = "$title $text"
    ) = RecallRow(kind.wireValue, id, 1, title, text, date, people, search).toEntry()

    @Test fun forgottenFactFindsPersonAndEveryTermMustMatch() {
        val entries = listOf(row(kind = RecallKind.FACT, search = "Alice Likes climbing outdoors"))
        assertEquals(1, RecallSearch.find(entries, "ALICE climbing").size)
        assertTrue(RecallSearch.find(entries, "climbing swimming").isEmpty())
    }

    @Test fun wildcardCharactersAreLiteral() {
        val entries =
            listOf(
                row(text = "100% certain"),
                row(id = 2, text = "under_score"),
                row(id = 3, text = "ordinary")
            )
        assertEquals(listOf(1L), RecallSearch.find(entries, "%").map { it.id })
        assertEquals(listOf(2L), RecallSearch.find(entries, "_").map { it.id })
    }

    @Test fun exactAndPrefixNamesOutrankContextAndTiesAreStable() {
        val entries =
            listOf(
                row(id = 8, date = 100),
                row(kind = RecallKind.PERSON, id = 3, title = "Alice Jones", text = "", date = 2),
                row(kind = RecallKind.PERSON, id = 2, title = "Alice", text = "", date = 1)
            )
        assertEquals(listOf(2L, 3L, 8L), RecallSearch.find(entries, "Alice").map { it.id })
    }

    @Test fun personScopeDoesNotConfuseIdsOneAndEleven() {
        val entries = listOf(row(id = 1, people = ",1,"), row(id = 2, people = ",11,"))
        assertEquals(listOf(1L), RecallSearch.find(entries, "climbing", 1).map { it.id })
    }

    @Test fun blankQueryReturnsNewestContextAndNotAddressBook() {
        val entries =
            listOf(
                row(id = 1, date = 10),
                row(kind = RecallKind.PERSON, id = 2, date = 30),
                row(kind = RecallKind.FACT, id = 3, date = 20),
                row(kind = RecallKind.FOLLOW_UP, id = 4, date = 40)
            )
        assertEquals(listOf(3L, 1L), RecallSearch.find(entries, "  ").map { it.id })
    }

    @Test fun followUpsRemainSearchableButNeverAppearInBlankRecents() {
        val entries = listOf(row(kind = RecallKind.FOLLOW_UP, id = 4, text = "Ask about climbing"))
        assertTrue(RecallSearch.find(entries, "").isEmpty())
        assertEquals(listOf(4L), RecallSearch.find(entries, "climbing").map { it.id })
    }

    @Test fun excerptContainsMatchingWordDeepInLongNote() {
        val text = "irrelevant ".repeat(40) + "climbing outdoors together"
        assertTrue(RecallSearch.excerpt(text, "climbing", 80).contains("climbing"))
        assertTrue(RecallSearch.excerpt(text, "climbing", 80).length <= 82)
    }

    @Test fun explicitTypeScopeCanBrowseWithoutAQuery() {
        val entries =
            listOf(
                row(kind = RecallKind.EVENT),
                row(kind = RecallKind.TAG, id = 2, title = "dog-owner"),
                row(kind = RecallKind.PERSON, id = 3)
            )
        assertEquals(
            listOf(2L),
            RecallSearch.find(entries, "", kind = RecallKind.TAG).map {
                it.id
            }
        )
        assertEquals(
            listOf(3L),
            RecallSearch.find(entries, "", kind = RecallKind.PERSON).map {
                it.id
            }
        )
        assertTrue(RecallSearch.find(entries, "dog", kind = RecallKind.FACT).isEmpty())
    }

    @Test fun historyIncludesEveryCaptureTypeOrderedByCreationRatherThanPlannedOrEventDate() {
        val entries = listOf(
            row(kind = RecallKind.EVENT, id = 1, date = 900).copy(createdAt = 10),
            row(kind = RecallKind.FACT, id = 2, date = 20).copy(createdAt = 20),
            row(
                kind = RecallKind.FOLLOW_UP,
                id = 3,
                date = 1
            ).copy(createdAt = 30, dueDate = "2026-12-01", completedAt = 40),
            row(kind = RecallKind.PERSON, id = 4, date = 50),
            row(kind = RecallKind.TAG, id = 5, date = 60)
        )
        assertEquals(listOf(3L, 2L, 1L), RecallSearch.history(entries, "").map { it.id })
        assertEquals(
            listOf(2L),
            RecallSearch.history(entries, "climbing", RecallKind.FACT).map {
                it.id
            }
        )
    }

    @Test fun peopleExcerptsExplainMatchesFromDeepInsideSharedNotes() {
        val text = "irrelevant ".repeat(40) + "Priya surgery details"
        val entries = listOf(row(people = ",1,11,", text = text))
        val excerpts = RecallSearch.personExcerpts(entries, "Priya")
        assertTrue(excerpts.getValue(1).contains("Priya"))
        assertEquals(excerpts[1], excerpts[11])
        assertTrue(RecallSearch.personExcerpts(entries, "").isEmpty())
        assertTrue(
            RecallSearch.personExcerpts(entries, "Alice Priya").getValue(1).contains("Priya")
        )
    }

    @Test fun peopleScopeFindsEveryPersonLinkedToMatchingSavedContext() {
        val entries = listOf(
            row(kind = RecallKind.PERSON, id = 1, title = "Maya", text = "", search = "Maya"),
            row(
                kind = RecallKind.PERSON,
                id = 11,
                title = "Oliver",
                text = "",
                people = ",11,",
                search = "Oliver"
            ),
            row(
                kind = RecallKind.PERSON,
                id = 2,
                title = "Unrelated",
                text = "",
                people = ",2,",
                search = "Unrelated"
            ),
            row(kind = RecallKind.FACT, id = 10, text = "Priya surgery", people = ",1,11,")
        )
        assertEquals(
            setOf(1L, 11L),
            RecallSearch.find(entries, "Priya", kind = RecallKind.PERSON).map {
                it.id
            }.toSet()
        )
    }
}
