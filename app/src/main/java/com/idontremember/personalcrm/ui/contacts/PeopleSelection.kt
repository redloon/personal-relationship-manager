package com.idontremember.personalcrm.ui.contacts

/** Selection is independent of filtering and of the action performed on its IDs. */
data class PeopleSelection(val active: Boolean = false, val ids: Set<Long> = emptySet()) {
    fun toggle(id: Long): PeopleSelection {
        val next = if (id in ids) ids - id else ids + id
        return PeopleSelection(next.isNotEmpty(), next)
    }

    fun selectAll(results: Set<Long>) = PeopleSelection(true, ids + results)

    fun reconcile(available: Set<Long>): PeopleSelection {
        if (ids.isEmpty()) return this
        val next = ids intersect available
        return PeopleSelection(next.isNotEmpty(), next)
    }
}
