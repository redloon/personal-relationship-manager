package com.idontremember.personalcrm.data.search

enum class RecallKind(val wireValue: String, val isCapture: Boolean) {
    EVENT("event", true), FACT("fact", true), FOLLOW_UP("followup", true), PERSON("person", false), TAG("tag", false);
    override fun toString(): String = wireValue
    companion object {
        fun fromWireOrNull(value: String?): RecallKind? = entries.firstOrNull { it.wireValue == value }
        fun fromWire(value: String): RecallKind = requireNotNull(fromWireOrNull(value)) { "Unknown recall kind: $value" }
    }
}

sealed interface RecallTarget {
    data class Event(val id: Long) : RecallTarget
    data class Fact(val id: Long) : RecallTarget
    data class FollowUp(val id: Long) : RecallTarget
    data class Person(val id: Long) : RecallTarget
    data class Tag(val id: Long) : RecallTarget
}

/** Transport details stay at the database boundary. */
data class RecallRow(
    val kind: String, val id: Long, val contactId: Long?, val title: String, val text: String,
    val date: Long, val personIds: String, val searchText: String,
    val dueDate: String? = null, val completedAt: Long? = null, val createdAt: Long = date
) {
    fun toEntry() = RecallEntry(RecallKind.fromWire(kind), id, contactId, title, text, date,
        personIds.split(',').mapNotNull { it.toLongOrNull()?.takeIf { id -> id > 0 } }.toSet(),
        searchText, dueDate, completedAt, createdAt)
}

/** Display context with typed identity; no SQL delimiters or fallback record kinds in UI. */
data class RecallEntry(
    val kind: RecallKind, val id: Long, val contactId: Long?, val title: String, val text: String,
    val date: Long, val personIds: Set<Long>, val searchText: String,
    val dueDate: String? = null, val completedAt: Long? = null, val createdAt: Long = date
) {
    val key: String get() = "${kind.wireValue}:$id"
    fun belongsTo(personId: Long): Boolean = personId in personIds
    val target: RecallTarget get() = when (kind) {
        RecallKind.EVENT -> RecallTarget.Event(id)
        RecallKind.FACT -> RecallTarget.Fact(id)
        RecallKind.FOLLOW_UP -> RecallTarget.FollowUp(id)
        RecallKind.PERSON -> RecallTarget.Person(id)
        RecallKind.TAG -> RecallTarget.Tag(id)
    }
}
