package com.idontremember.personalcrm.ui.contacts

import org.junit.Assert.*
import org.junit.Test

class PeopleSelectionTest {
    @Test fun longPressSelectsFirstPersonAndTapsToggleUntilLastIsRemoved() {
        val first = PeopleSelection().toggle(1)
        assertTrue(first.active)
        assertEquals(setOf(1L), first.ids)
        val next = first.toggle(2).toggle(1)
        assertEquals(setOf(2L), next.ids)
        assertEquals(PeopleSelection(), next.toggle(2))
    }

    @Test fun selectingSearchResultsKeepsSelectionsOutsideTheSearch() {
        val selection = PeopleSelection().toggle(1).selectAll(setOf(2, 3))
        assertEquals(setOf(1L, 2L, 3L), selection.ids)
        assertEquals(selection, selection.selectAll(setOf(2, 3)))
    }

    @Test fun emptyMenuSelectionRemainsActiveAndMissingRecordsArePruned() {
        assertTrue(PeopleSelection(active = true).reconcile(setOf(1)).active)
        val selected = PeopleSelection().toggle(1).toggle(2)
        assertEquals(setOf(2L), selected.reconcile(setOf(2, 3)).ids)
        assertEquals(PeopleSelection(), selected.reconcile(emptySet()))
    }
}
