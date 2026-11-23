package com.idontremember.personalcrm.data.capture

/** Lightweight choices shared by capture and management, independent of Compose. */
data class PersonChoice(val id: Long, val displayName: String, val phoneNumber: String? = null)
data class TagChoice(val id: Long, val name: String)
