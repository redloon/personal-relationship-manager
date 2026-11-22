package com.idontremember.personalcrm.data.capture

/** Link state shared by mode changes and text edits; fact ownership lives outside its prose. */
data class CaptureLinks(
    val isFact: Boolean = false,
    val contactIds: Set<Long> = emptySet(),
    val tagNames: Set<String> = emptySet(),
    val tokens: List<InlineToken> = emptyList()
) {
    fun withFactMode(value: Boolean): CaptureLinks = copy(isFact = value)

    fun edit(before: String, after: String, selectionStart: Int, selectionEnd: Int, cursorAfter: Int): CaptureLinks {
        val next = InlineTokens.edited(before, after, tokens, selectionStart, selectionEnd, cursorAfter)
        val removed = if (isFact) emptySet() else InlineTokens.removedTargets(tokens, next)
        return copy(tokens = next,
            contactIds = contactIds - removed.filterIsInstance<TokenTarget.Person>().map { it.id }.toSet(),
            tagNames = tagNames - removed.filterIsInstance<TokenTarget.Tag>().map { it.name }.toSet())
    }

    fun selectPerson(id: Long): CaptureLinks {
        val selected = if (isFact) setOf(id) else contactIds + id
        return copy(contactIds = selected, tokens = tokens.filter { token ->
            val person = token.target as? TokenTarget.Person
            person == null || person.id in selected
        })
    }
}
