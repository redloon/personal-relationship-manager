package com.idontremember.personalcrm.data.search

/** Literal matching is deliberate: a user's punctuation is never SQL syntax. */
object RecallSearch {
    fun find(entries: List<RecallEntry>, query: String, personId: Long? = null, kind: RecallKind? = null): List<RecallEntry> {
        val terms = query.trim().split(Regex("\\s+")).filter(String::isNotBlank)
        val phrase = query.trim()
        val matchingPeople = if (kind == RecallKind.PERSON && terms.isNotEmpty()) entries.asSequence()
            .filter { personId == null || it.belongsTo(personId) }
            .filter { row -> terms.all { row.searchText.contains(it, ignoreCase = true) } }
            .flatMap { it.personIds.asSequence() }.toSet()
        else null
        return entries.asSequence()
            .filter { personId == null || it.belongsTo(personId) }
            .filter { kind == null || it.kind == kind }
            .filter {
                when {
                    matchingPeople != null -> it.id in matchingPeople
                    terms.isEmpty() -> kind != null || it.kind == RecallKind.FACT || it.kind == RecallKind.EVENT
                    else -> terms.all { term -> it.searchText.contains(term, ignoreCase = true) }
                }
            }
            .distinctBy { it.key }
            .sortedWith(compareBy<RecallEntry> {
                when {
                    it.kind == RecallKind.PERSON && it.title.equals(phrase, true) -> 0
                    it.kind == RecallKind.PERSON && it.title.startsWith(phrase, true) -> 1
                    it.kind == RecallKind.PERSON -> 2
                    else -> 3
                }
            }.thenByDescending { it.date }.thenBy { it.kind.wireValue }.thenBy { it.id })
            .toList()
    }

    /** History is captures, including completed intentions, ordered by when they were recorded. */
    fun history(entries: List<RecallEntry>, query: String, kind: RecallKind? = null): List<RecallEntry> {
        val captures = entries.filter { it.kind.isCapture }
        val terms = query.trim().split(Regex("\\s+")).filter(String::isNotBlank)
        return captures.asSequence()
            .filter { kind == null || it.kind == kind }
            .filter { row -> terms.all { row.searchText.contains(it, ignoreCase = true) } }
            .distinctBy { it.key }
            .sortedWith(compareByDescending<RecallEntry> { it.createdAt }.thenBy { it.kind.wireValue }.thenBy { it.id })
            .toList()
    }

    /** Prefer matching saved context over repeating the person's own name. */
    fun personExcerpts(entries: List<RecallEntry>, query: String): Map<Long, String> {
        if (query.isBlank()) return emptyMap()
        val result = linkedMapOf<Long, String>()
        val terms = query.trim().split(Regex("\\s+")).filter(String::isNotBlank)
        find(entries, query).sortedBy { if (it.kind == RecallKind.PERSON) 1 else 0 }.forEach { row ->
            val context = if (terms.any { row.text.contains(it, ignoreCase = true) }) row.text else row.searchText
            row.personIds.forEach { id ->
                result.putIfAbsent(id, excerpt(context, query))
            }
        }
        return result
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
