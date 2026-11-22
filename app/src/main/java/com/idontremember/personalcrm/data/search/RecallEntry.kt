package com.idontremember.personalcrm.data.search

/** Display-ready source context, retaining its original record for navigation. */
data class RecallEntry(
    val kind: String,
    val id: Long,
    val contactId: Long?,
    val title: String,
    val text: String,
    val date: Long,
    val personIds: String,
    val searchText: String,
    val dueDate: String? = null,
    val completedAt: Long? = null,
    val createdAt: Long = date
) {
    val key: String get() = "$kind:$id"
    fun belongsTo(personId: Long): Boolean = personIds.contains(",$personId,")
}
