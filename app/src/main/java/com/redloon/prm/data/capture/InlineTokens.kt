package com.redloon.prm.data.capture

import java.util.Locale
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
sealed class TokenTarget {
    @Serializable
    @SerialName("person")
    data class Person(val id: Long) : TokenTarget()

    @Serializable
    @SerialName("tag")
    data class Tag(val name: String) : TokenTarget()
}

/** UTF-16 offsets into the original prose, never into a rewritten display string. */
@Serializable
data class InlineToken(val start: Int, val endExclusive: Int, val target: TokenTarget)
data class TokenInsertion(val text: String, val tokens: List<InlineToken>, val cursor: Int)

object InlineTokens {
    private val json = Json { encodeDefaults = true }
    fun encode(tokens: List<InlineToken>): String = json.encodeToString(tokens)
    fun decode(source: String): List<InlineToken> = json.decodeFromString(source)

    fun validate(text: String, tokens: List<InlineToken>, people: Set<Long>, tags: Set<String>) {
        var previousEnd = 0
        for (token in tokens.sortedBy { it.start }) {
            require(
                token.start >= previousEnd && token.endExclusive > token.start &&
                    token.endExclusive <= text.length
            ) { "Invalid inline token range" }
            when (val target = token.target) {
                is TokenTarget.Person -> require(target.id in people) { "Unlinked person token" }
                is TokenTarget.Tag -> require(
                    target.name in tags && target.name == target.name.trim().lowercase(
                        Locale.ROOT
                    ) &&
                        target.name.isNotBlank()
                ) { "Unlinked tag token" }
            }
            previousEnd = token.endExclusive
        }
    }

    /** Selection/cursor hints disambiguate edits to repeated identical token labels. */
    fun edited(
        before: String,
        after: String,
        tokens: List<InlineToken>,
        selectionStart: Int? = null,
        selectionEnd: Int? = selectionStart,
        cursorAfter: Int? = null
    ): List<InlineToken> {
        if (before == after) return tokens
        val delta = after.length - before.length
        fun matches(start: Int, end: Int): Boolean {
            val insertedLength = delta + end - start
            return start >= 0 && end in start..before.length && insertedLength >= 0 &&
                start + insertedLength <= after.length && before.take(start) == after.take(start) &&
                before.substring(end) == after.substring(start + insertedLength) &&
                (cursorAfter == null || cursorAfter == start + insertedLength)
        }
        val candidates = mutableListOf<Pair<Int, Int>>()
        if (selectionStart != null && selectionEnd != null) {
            candidates += minOf(selectionStart, selectionEnd) to maxOf(selectionStart, selectionEnd)
            if (selectionStart == selectionEnd && delta < 0) {
                if (cursorAfter != null) candidates += cursorAfter to selectionStart
                candidates += selectionStart to selectionStart - delta
            }
        }
        val hinted = candidates.firstOrNull { matches(it.first, it.second) }
        val prefix = hinted?.first ?: before.commonPrefixWith(after).length
        val oldEnd =
            hinted?.second
                ?: (
                    before.length -
                        before.substring(prefix).commonSuffixWith(after.substring(prefix)).length
                    )
        return tokens.mapNotNull { token ->
            when {
                token.endExclusive <= prefix -> token
                token.start >= oldEnd -> token.copy(
                    start = token.start + delta,
                    endExclusive =
                    token.endExclusive + delta
                )
                else -> null
            }
        }
    }

    fun removedTargets(before: List<InlineToken>, after: List<InlineToken>): Set<TokenTarget> =
        before.map { it.target }.toSet() - after.map { it.target }.toSet()

    fun insert(
        text: String,
        start: Int,
        end: Int,
        label: String,
        target: TokenTarget,
        tokens: List<InlineToken>
    ): TokenInsertion {
        require(start in 0..text.length && end in start..text.length)
        val result = text.replaceRange(start, end, "$label ")
        val retained = tokens.filterNot { it.start < end && it.endExclusive > start }
        val shifted = edited(text, result, retained, start, end, start + label.length + 1)
        return TokenInsertion(
            result,
            (shifted + InlineToken(start, start + label.length, target)).sortedBy {
                it.start
            },
            start + label.length + 1
        )
    }
}

object ContactNames {
    fun key(name: String): String = name.trim().replace(Regex("\\s+"), " ").lowercase(Locale.ROOT)
}
