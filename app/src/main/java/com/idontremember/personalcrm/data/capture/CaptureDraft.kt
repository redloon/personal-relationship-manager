package com.idontremember.personalcrm.data.capture

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class CaptureDraft(
    val text: String = "",
    val links: CaptureLinks = CaptureLinks(),
    val eventDate: Long = System.currentTimeMillis()
) {
    val hasContent: Boolean get() = text.isNotBlank() || links.contactIds.isNotEmpty() ||
        links.tagNames.isNotEmpty()
}
data class FollowUpDraft(
    val text: String = "",
    val personId: Long? = null,
    val dueDate: String? = null
)

fun followUpDraftKey(existingId: Long?, contactId: Long?): String =
    "followup_${existingId ?: contactId?.let { "person_$it" } ?: "new"}"

/** Wire names and token-string encoding preserve existing on-device drafts. */
object DraftCodec {
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            explicitNulls = false
        }

    @Serializable private data class CaptureRecord(
        val note: String = "",
        val contacts: Set<Long> = emptySet(),
        val tags: Set<String> = emptySet(),
        val date: Long = System.currentTimeMillis(),
        val fact: Boolean = false,
        val tokens: String = "[]"
    )

    @Serializable private data class FollowUpRecord(
        val text: String = "",
        val person: Long? = null,
        val date: String = ""
    )
    fun encode(draft: CaptureDraft): String = json.encodeToString(
        CaptureRecord(
            draft.text,
            draft.links.contactIds,
            draft.links.tagNames,
            draft.eventDate,
            draft.links.isFact,
            InlineTokens.encode(draft.links.tokens)
        )
    )
    fun decodeCapture(source: String): CaptureDraft? = runCatching {
        val value = json.decodeFromString<CaptureRecord>(source)
        val people = value.contacts.filter { it > 0 }.toSet()
        val tokens = runCatching {
            InlineTokens.decode(value.tokens).also {
                InlineTokens.validate(value.note, it, people, value.tags)
            }
        }.getOrDefault(emptyList())
        CaptureDraft(value.note, CaptureLinks(value.fact, people, value.tags, tokens), value.date)
    }.getOrNull()
    fun encode(draft: FollowUpDraft): String =
        json.encodeToString(FollowUpRecord(draft.text, draft.personId, draft.dueDate.orEmpty()))
    fun decodeFollowUp(source: String): FollowUpDraft? = runCatching {
        json.decodeFromString<FollowUpRecord>(source).let {
            FollowUpDraft(
                it.text,
                it.person?.takeIf { id ->
                    id >
                        0
                },
                it.date.ifBlank { null }
            )
        }
    }.getOrNull()
}
