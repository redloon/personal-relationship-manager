package com.idontremember.personalcrm.data.capture

/** Link state shared by mode changes and text edits; fact ownership lives outside its prose. */
data class CaptureLinks(
    val isFact: Boolean = false,
    val contactIds: Set<Long> = emptySet(),
    val tagNames: Set<String> = emptySet(),
    val tokens: List<InlineToken> = emptyList()
) {
    fun withFactMode(value: Boolean): CaptureLinks = copy(isFact = value)

    fun edit(
        before: String,
        after: String,
        selectionStart: Int,
        selectionEnd: Int,
        cursorAfter: Int
    ): CaptureLinks {
        val next = InlineTokens.edited(
            before,
            after,
            tokens,
            selectionStart,
            selectionEnd,
            cursorAfter
        )
        val removed = if (isFact) emptySet() else InlineTokens.removedTargets(tokens, next)
        return copy(
            tokens = next,
            contactIds =
            contactIds - removed.filterIsInstance<TokenTarget.Person>().map { it.id }.toSet(),
            tagNames =
            tagNames - removed.filterIsInstance<TokenTarget.Tag>().map { it.name }.toSet()
        )
    }

    fun insert(
        text: String,
        start: Int,
        end: Int,
        label: String,
        target: TokenTarget
    ): CaptureInsertion {
        val inserted = InlineTokens.insert(text, start, end, label, target, tokens)
        val removed = InlineTokens.removedTargets(tokens, inserted.tokens)
        var next = copy(
            tokens = inserted.tokens,
            contactIds =
            contactIds - removed.filterIsInstance<TokenTarget.Person>().map { it.id }.toSet(),
            tagNames =
            tagNames - removed.filterIsInstance<TokenTarget.Tag>().map { it.name }.toSet()
        )
        next = when (target) {
            is TokenTarget.Person -> next.copy(contactIds = next.contactIds + target.id)
            is TokenTarget.Tag -> next.copy(tagNames = next.tagNames + target.name)
        }
        return CaptureInsertion(inserted.text, inserted.cursor, next)
    }

    fun selectTag(name: String): CaptureLinks {
        val normalized = name.trim().removePrefix("#").lowercase(java.util.Locale.ROOT)
        return if (normalized.isBlank()) this else copy(tagNames = tagNames + normalized)
    }
    fun removePerson(id: Long) = copy(
        contactIds = contactIds - id,
        tokens = tokens.filterNot {
            it.target ==
                TokenTarget.Person(id)
        }
    )
    fun removeTag(name: String) = copy(
        tagNames = tagNames - name,
        tokens = tokens.filterNot {
            it.target ==
                TokenTarget.Tag(name)
        }
    )
    fun reconcilePeople(existingIds: Set<Long>) = copy(
        contactIds = contactIds intersect existingIds,
        tokens = tokens.filter { it.target !is TokenTarget.Person || it.target.id in existingIds }
    )

    fun selectPerson(id: Long): CaptureLinks {
        val selected = if (isFact) setOf(id) else contactIds + id
        return copy(
            contactIds = selected,
            tokens = tokens.filter { token ->
                val person = token.target as? TokenTarget.Person
                person == null || person.id in selected
            }
        )
    }
}

data class CaptureInsertion(val text: String, val cursor: Int, val links: CaptureLinks)
