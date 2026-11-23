package com.idontremember.personalcrm.data.search

/** Explains both ways a person can belong to an exact tag. */
data class TagPerson(
    val id: Long,
    val displayName: String,
    val direct: Boolean,
    val viaNote: Boolean
)
