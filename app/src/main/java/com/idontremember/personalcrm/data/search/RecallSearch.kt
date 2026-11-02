package com.idontremember.personalcrm.data.search

/** Literal matching is deliberate: a user's punctuation is never SQL syntax. */
object RecallSearch {
    fun find(entries: List<RecallEntry>, query: String, personId: Long? = null): List<RecallEntry> {
        val terms = query.trim().split(Regex("\\s+")).filter(String::isNotBlank)
        val phrase = query.trim()
        return entries.asSequence()
            .filter { personId == null || it.belongsTo(personId) }
            .filter { if (terms.isEmpty()) it.kind == "fact" || it.kind == "event" else terms.all { term -> it.searchText.contains(term, ignoreCase = true) } }
            .distinctBy { it.key }
            .sortedWith(compareBy<RecallEntry> {
                when {
                    it.kind == "person" && it.title.equals(phrase, true) -> 0
                    it.kind == "person" && it.title.startsWith(phrase, true) -> 1
                    it.kind == "person" -> 2
                    else -> 3
                }
            }.thenByDescending { it.date }.thenBy { it.kind }.thenBy { it.id })
            .toList()
    }

    fun excerpt(text: String, query: String, maxLength: Int = 140): String {
        val flattened = text.replace(Regex("\\s+"), " ").trim()
        if (flattened.length <= maxLength) return flattened
        val hit = query.trim().split(Regex("\\s+")).filter(String::isNotBlank)
            .map { flattened.indexOf(it, ignoreCase = true) }.filter { it >= 0 }.minOrNull() ?: 0
        val start = maxOf(0, minOf(hit - maxLength / 3, flattened.length - maxLength))
        return (if (start > 0) "…" else "") + flattened.substring(start, start + maxLength) +
            (if (start + maxLength < flattened.length) "…" else "")
    }
}
