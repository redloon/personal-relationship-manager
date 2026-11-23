package com.idontremember.personalcrm.data.contacts

import com.idontremember.personalcrm.data.db.entity.Contact
import com.idontremember.personalcrm.data.db.entity.canDelete
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PeopleSortTest {
    private val people = listOf(
        PeopleListItem(
            Contact(id = 3, displayName = "Never", createdAt = 30),
            InteractionStats(3, null, 0),
            100
        ),
        PeopleListItem(
            Contact(id = 2, displayName = "Beta", createdAt = 20),
            InteractionStats(2, 90, 3),
            100
        ),
        PeopleListItem(
            Contact(id = 1, displayName = "alpha", createdAt = 10),
            InteractionStats(1, 50, 1),
            100
        )
    )

    @Test fun allEightOrdersAreExplicitAndUnknownRecencyAlwaysStaysLast() {
        val expected = mapOf(
            PeopleSort.NAME_ASC to listOf(1L, 2L, 3L),
            PeopleSort.NAME_DESC to listOf(3L, 2L, 1L),
            PeopleSort.RECENT to listOf(2L, 1L, 3L),
            PeopleSort.LONGEST_AGO to listOf(1L, 2L, 3L),
            PeopleSort.ADDED_NEWEST to listOf(3L, 2L, 1L),
            PeopleSort.ADDED_OLDEST to listOf(1L, 2L, 3L),
            PeopleSort.MOST_INTERACTIONS to listOf(2L, 1L, 3L),
            PeopleSort.FEWEST_INTERACTIONS to listOf(3L, 1L, 2L)
        )
        assertEquals(PeopleSort.entries.toSet(), expected.keys)
        expected.forEach { (sort, ids) -> assertEquals(ids, sort.apply(people).map { it.id }) }
    }

    @Test fun metricTiesUseNamesThenIdsAndCannotBecomeRelationshipScores() {
        val tied = people.map { it.copy(stats = InteractionStats(it.id, 50, 2)) }
        assertEquals(listOf(1L, 2L, 3L), PeopleSort.RECENT.apply(tied).map { it.id })
        val same =
            listOf(
                people[1].copy(contact = people[1].contact.copy(displayName = "ALPHA")),
                people[2]
            )
        assertEquals(listOf(1L, 2L), PeopleSort.NAME_ASC.apply(same).map { it.id })
    }

    @Test fun deletionRequiresBothAnExplicitCapabilityAndNoAttachment() {
        assertFalse(Contact(displayName = "Legacy").canDelete)
        assertFalse(
            Contact(displayName = "Phone", androidContactId = 1, deletionAllowed = true).canDelete
        )
        assertTrue(Contact(displayName = "Created here", deletionAllowed = true).canDelete)
    }
}
