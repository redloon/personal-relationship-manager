package com.redloon.prm.data.contacts

import android.telephony.PhoneNumberUtils
import com.redloon.prm.data.db.entity.Contact
import java.util.Locale

data class ProfilePair(val savedProfileId: Long, val androidProfileId: Long)

enum class ReconnectMatch { NAME, PHONE, EMAIL }

data class ReconnectCandidate(
    val savedProfile: Contact,
    val androidProfile: Contact,
    val matches: Set<ReconnectMatch>
) {
    val pair = ProfilePair(savedProfile.id, androidProfile.id)
}

/** Pure matching policy shared by directory sync, review, and transactional revalidation. */
object ReconnectMatching {
    fun normalizeName(value: String): String =
        value.trim().replace(Regex("\\s+"), " ").lowercase(Locale.ROOT)

    /** Uses Android's number normalization on-device, with an equivalent JVM-test fallback. */
    fun normalizePhone(value: String?): String? {
        if (value.isNullOrBlank()) return null
        try {
            return PhoneNumberUtils.normalizeNumber(value).takeIf { it.isNotEmpty() }
        } catch (_: RuntimeException) {
            // The local-unit-test Android stub throws; keep the matching policy JVM-testable.
        }
        val converted = buildString(value.length) {
            value.forEachIndexed { index, character ->
                val digit = Character.digit(character, 10)
                when {
                    digit >= 0 -> append(digit)
                    character == '+' && index == value.indexOfFirst { !it.isWhitespace() } ->
                        append(character)
                    character.isLetter() -> append(keypadDigit(character))
                }
            }
        }
        return converted.takeIf { it.isNotEmpty() && it != "+" }
    }

    fun normalizeEmail(value: String?): String? =
        value?.trim()?.lowercase(Locale.ROOT)?.takeIf { it.isNotEmpty() }

    fun autoReconnectPairs(
        profiles: List<Contact>,
        dismissed: Set<ProfilePair>
    ): List<ProfilePair> {
        val candidates = candidatePairs(profiles, emptySet()).filter { candidate ->
            ReconnectMatch.NAME in candidate.matches &&
                candidate.matches.any { it == ReconnectMatch.PHONE || it == ReconnectMatch.EMAIL }
        }
        val bySaved = candidates.groupingBy { it.savedProfile.id }.eachCount()
        val byAndroid = candidates.groupingBy { it.androidProfile.id }.eachCount()
        return candidates.filter {
            bySaved[it.savedProfile.id] == 1 && byAndroid[it.androidProfile.id] == 1
        }.map { it.pair }.filterNot { it in dismissed }
            .sortedWith(compareBy(ProfilePair::savedProfileId, ProfilePair::androidProfileId))
    }

    fun reviewCandidates(
        profiles: List<Contact>,
        dismissed: Set<ProfilePair>
    ): List<ReconnectCandidate> = candidatePairs(profiles, dismissed).sortedWith(
        compareBy<ReconnectCandidate> { normalizeName(it.savedProfile.displayName) }
            .thenBy { it.savedProfile.id }
            .thenBy { it.androidProfile.id }
    )

    fun reviewCandidate(saved: Contact, android: Contact): ReconnectCandidate? =
        matches(saved, android).takeIf { it.isNotEmpty() }?.let {
            ReconnectCandidate(saved, android, it)
        }

    private fun candidatePairs(
        profiles: List<Contact>,
        dismissed: Set<ProfilePair>
    ): List<ReconnectCandidate> {
        val saved = profiles.filter { it.androidContactId == null && !it.deletionAllowed }
        val android = profiles.filter { it.androidContactId != null }.map { NormalizedProfile(it) }
        val byName = android.groupBy { it.name }
        val byPhone = android.filter { it.phone != null }.groupBy { it.phone }
        val byEmail = android.filter { it.email != null }.groupBy { it.email }
        return buildList {
            for (savedProfile in saved) {
                val normalized = NormalizedProfile(savedProfile)
                val possible = buildSet {
                    addAll(byName[normalized.name].orEmpty())
                    normalized.phone?.let { addAll(byPhone[it].orEmpty()) }
                    normalized.email?.let { addAll(byEmail[it].orEmpty()) }
                }
                for (androidProfile in possible) {
                    val pair = ProfilePair(savedProfile.id, androidProfile.contact.id)
                    if (pair !in dismissed) {
                        add(
                            ReconnectCandidate(
                                savedProfile,
                                androidProfile.contact,
                                matches(normalized, androidProfile)
                            )
                        )
                    }
                }
            }
        }
    }

    private class NormalizedProfile(val contact: Contact) {
        val name = normalizeName(contact.displayName)
        val phone = normalizePhone(contact.phoneNumber)
        val email = normalizeEmail(contact.email)
    }

    private fun matches(saved: Contact, android: Contact): Set<ReconnectMatch> =
        matches(NormalizedProfile(saved), NormalizedProfile(android))

    private fun matches(saved: NormalizedProfile, android: NormalizedProfile): Set<ReconnectMatch> =
        buildSet {
            if (saved.name == android.name) {
                add(ReconnectMatch.NAME)
            }
            if (saved.phone != null && saved.phone == android.phone) {
                add(ReconnectMatch.PHONE)
            }
            if (saved.email != null && saved.email == android.email) {
                add(ReconnectMatch.EMAIL)
            }
        }

    private fun keypadDigit(character: Char): Char = when (character.uppercaseChar()) {
        in 'A'..'C' -> '2'
        in 'D'..'F' -> '3'
        in 'G'..'I' -> '4'
        in 'J'..'L' -> '5'
        in 'M'..'O' -> '6'
        in 'P'..'S' -> '7'
        in 'T'..'V' -> '8'
        in 'W'..'Z' -> '9'
        else -> character
    }
}
