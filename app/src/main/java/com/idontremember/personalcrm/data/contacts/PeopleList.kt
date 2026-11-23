package com.idontremember.personalcrm.data.contacts

import com.idontremember.personalcrm.data.db.entity.Contact

/** APP_ONLY is a stable saved-state identifier; its user-facing name is Unlinked people. */
enum class PeopleListScope { ALL, HIDDEN, APP_ONLY }
data class InteractionStats(
    val contactId: Long,
    val lastInteractionAt: Long?,
    val interactionCount: Int
)
data class InteractionSnapshot(val asOf: Long, val people: Map<Long, InteractionStats>) {
    fun forPerson(id: Long) = people[id] ?: InteractionStats(id, null, 0)
}
data class PeopleListItem(val contact: Contact, val stats: InteractionStats, val asOf: Long) {
    val id: Long get() = contact.id
}
enum class PeopleBulkAction { HIDE, UNHIDE, DELETE }

enum class PeopleSort {
    NAME_ASC,
    NAME_DESC,
    RECENT,
    LONGEST_AGO,
    ADDED_NEWEST,
    ADDED_OLDEST,
    MOST_INTERACTIONS,
    FEWEST_INTERACTIONS;

    fun apply(people: List<PeopleListItem>): List<PeopleListItem> {
        val names =
            Comparator<PeopleListItem> { a, b ->
                a.contact.displayName.compareTo(b.contact.displayName, ignoreCase = true)
            }
        val primary = when (this) {
            NAME_ASC -> names
            NAME_DESC -> names.reversed()
            RECENT -> compareBy<PeopleListItem> {
                it.stats.lastInteractionAt == null
            }.thenByDescending { it.stats.lastInteractionAt }
            LONGEST_AGO -> compareBy<PeopleListItem> {
                it.stats.lastInteractionAt == null
            }.thenBy { it.stats.lastInteractionAt }
            ADDED_NEWEST -> compareByDescending { it.contact.createdAt }
            ADDED_OLDEST -> compareBy { it.contact.createdAt }
            MOST_INTERACTIONS -> compareByDescending { it.stats.interactionCount }
            FEWEST_INTERACTIONS -> compareBy { it.stats.interactionCount }
        }
        return people.sortedWith(primary.then(names).thenBy { it.id })
    }
}
