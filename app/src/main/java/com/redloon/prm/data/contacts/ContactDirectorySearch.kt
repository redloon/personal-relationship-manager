package com.redloon.prm.data.contacts

import com.redloon.prm.data.db.entity.Contact

/** Directory matching stays separate from Home's remembered-context search. */
object ContactDirectorySearch {
    fun find(people: List<Contact>, query: String): List<Contact> {
        val terms = query.trim().split(Regex("\\s+")).filter(String::isNotBlank)
        return if (terms.isEmpty()) {
            people
        } else {
            people.filter { person ->
                val text = listOfNotNull(
                    person.displayName,
                    person.phoneNumber,
                    person.email
                ).joinToString(" ")
                terms.all { text.contains(it, ignoreCase = true) }
            }
        }
    }
}
