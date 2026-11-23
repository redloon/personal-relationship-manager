package com.idontremember.personalcrm.testdata

import android.content.ContentProviderOperation
import android.content.ContentResolver
import android.provider.ContactsContract
import com.idontremember.personalcrm.data.db.entity.Contact

/**
 * Generates randomized contact datasets for stress-testing the sync engine.
 *
 * Creates 400+ contacts with varying data quality:
 * - Full contacts (name + phone + email + photo URI)
 * - Minimal contacts (name only)
 * - Contacts with missing fields (null phone, null email)
 * - Contacts with special characters in names (accents, CJK, emoji)
 * - Contacts with very long names
 * - Duplicate-ish names (e.g. "John Smith" vs "John Smith Jr.")
 */
object RandomContactGenerator {

    private val FIRST_NAMES = listOf(
        "Alice", "Bob", "Charlie", "Diana", "Eve", "Frank", "Grace", "Hank",
        "Ivy", "Jack", "Karen", "Leo", "Mia", "Noah", "Olivia", "Paul",
        "Quinn", "Rachel", "Sam", "Tina", "Uma", "Victor", "Wendy", "Xavier",
        "Yara", "Zach", "Liam", "Emma", "Ava", "Ethan"
    )

    private val LAST_NAMES = listOf(
        "Smith", "Johnson", "Williams", "Brown", "Jones", "Garcia", "Miller",
        "Davis", "Rodriguez", "Martinez", "Hernandez", "Lopez", "Gonzalez",
        "Wilson", "Anderson", "Thomas", "Taylor", "Moore", "Jackson", "Martin"
    )

    private val SPECIAL_NAMES = listOf(
        "José García-López", // accented chars + hyphen
        "François Müller", // French + German
        "André O'Connor", // apostrophe
        "Björk Guðmundsdóttir", // Icelandic
        "中田 花子", // CJK characters
        "田中 太郎", // CJK characters
        "김민수", // Korean
        "محمد بن سلمان", // Arabic
        "Николай Петров", // Cyrillic
        "Em 😀 Johnson", // emoji in name
        "Mary-Jane O'Brien-Smith", // multiple hyphens + apostrophe
        "A".repeat(200), // very long name
        "John Smith", // will create duplicates
        "John Smith Jr.", // duplicate-ish
        "John Smith III", // duplicate-ish
        "Jane Doe",
        "Jane Doe Jr."
    )

    private val PHONE_PREFIXES = listOf(
        "+1", "+44", "+33", "+49", "+81", "+86", "+91", "+55", "+61", "+82"
    )

    private val EMAIL_DOMAINS = listOf(
        "gmail.com", "yahoo.com", "outlook.com", "proton.me", "icloud.com",
        "hotmail.com", "fastmail.com", "hey.com", "example.com", "test.org"
    )

    data class GeneratedContacts(
        val contacts: List<Contact>,
        val systemContacts: List<SystemContact>
    )

    data class SystemContact(
        val androidId: Long,
        val displayName: String,
        val phoneNumber: String?,
        val email: String?,
        val photoUri: String? = null
    )

    /**
     * Generate a dataset of [totalCount] contacts with mixed data quality.
     * The [seed] ensures reproducible results.
     */
    fun generate(totalCount: Int = 420, seed: Long = 42L): GeneratedContacts {
        val random = java.util.Random(seed)
        val contacts = mutableListOf<Contact>()
        val systemContacts = mutableListOf<SystemContact>()
        var androidIdCounter = 1000L

        // Phase 1: Special names (~30 contacts)
        for (name in SPECIAL_NAMES) {
            val androidId = androidIdCounter++
            val phone = if (random.nextBoolean()) generatePhone(random) else null
            val email = if (random.nextBoolean()) generateEmail(name, random) else null

            contacts.add(
                Contact(
                    androidContactId = androidId,
                    displayName = name,
                    phoneNumber = phone,
                    email = email
                )
            )
            systemContacts.add(
                SystemContact(
                    androidId = androidId,
                    displayName = name,
                    phoneNumber = phone,
                    email = email
                )
            )
        }

        // Phase 2: Duplicate-ish names (~20 contacts)
        val baseNames = listOf("John Smith", "Maria Garcia", "Li Wei", "Anna Kowalski")
        for (baseName in baseNames) {
            for (suffix in listOf("", " Jr.", " III", " IV")) {
                val name = baseName + suffix
                val androidId = androidIdCounter++
                contacts.add(
                    Contact(
                        androidContactId = androidId,
                        displayName = name,
                        phoneNumber = generatePhone(random),
                        email = generateEmail(name, random)
                    )
                )
                systemContacts.add(
                    SystemContact(
                        androidId = androidId,
                        displayName = name,
                        phoneNumber = generatePhone(random),
                        email = generateEmail(name, random)
                    )
                )
            }
        }

        // Phase 3: Fill remaining with random names
        val remaining = totalCount - contacts.size
        for (i in 0 until remaining) {
            val firstName = FIRST_NAMES[random.nextInt(FIRST_NAMES.size)]
            val lastName = LAST_NAMES[random.nextInt(LAST_NAMES.size)]
            val name = "$firstName $lastName"
            val androidId = androidIdCounter++

            // Vary data quality
            val hasPhone = random.nextFloat() > 0.15f // 85% have phone
            val hasEmail = random.nextFloat() > 0.25f // 75% have email
            val hasPhoto = random.nextFloat() > 0.7f // 30% have photo

            val phone = if (hasPhone) generatePhone(random) else null
            val email = if (hasEmail) generateEmail(name, random) else null
            val photoUri = if (hasPhoto) {
                "content://com.android.contacts/contacts/$androidId/photo"
            } else {
                null
            }

            contacts.add(
                Contact(
                    androidContactId = androidId,
                    displayName = name,
                    phoneNumber = phone,
                    email = email,
                    photoUri = photoUri
                )
            )
            systemContacts.add(
                SystemContact(
                    androidId = androidId,
                    displayName = name,
                    phoneNumber = phone,
                    email = email,
                    photoUri = photoUri
                )
            )
        }

        return GeneratedContacts(contacts = contacts, systemContacts = systemContacts)
    }

    /**
     * Seed the emulator's Contacts Provider with the given system contacts.
     * Returns the list of raw_contact_ids assigned by the provider.
     */
    fun seedContactsProvider(
        contentResolver: ContentResolver,
        systemContacts: List<SystemContact>
    ): List<Long> {
        val insertedIds = mutableListOf<Long>()

        // Insert in batches of 50 to avoid transaction size limits
        for (batch in systemContacts.chunked(50)) {
            val ops = ArrayList<ContentProviderOperation>()

            for (contact in batch) {
                val rawContactIndex = ops.size

                // Insert raw contact
                ops.add(
                    ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                        .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
                        .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null)
                        .build()
                )

                // Insert display name
                ops.add(
                    ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValueBackReference(
                            ContactsContract.Data.RAW_CONTACT_ID,
                            rawContactIndex
                        )
                        .withValue(
                            ContactsContract.Data.MIMETYPE,
                            ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE
                        )
                        .withValue(
                            ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME,
                            contact.displayName
                        )
                        .build()
                )

                // Insert phone number if present
                if (contact.phoneNumber != null) {
                    ops.add(
                        ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                            .withValueBackReference(
                                ContactsContract.Data.RAW_CONTACT_ID,
                                rawContactIndex
                            )
                            .withValue(
                                ContactsContract.Data.MIMETYPE,
                                ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE
                            )
                            .withValue(
                                ContactsContract.CommonDataKinds.Phone.NUMBER,
                                contact.phoneNumber
                            )
                            .withValue(
                                ContactsContract.CommonDataKinds.Phone.TYPE,
                                ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE
                            )
                            .build()
                    )
                }

                // Insert email if present
                if (contact.email != null) {
                    ops.add(
                        ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                            .withValueBackReference(
                                ContactsContract.Data.RAW_CONTACT_ID,
                                rawContactIndex
                            )
                            .withValue(
                                ContactsContract.Data.MIMETYPE,
                                ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE
                            )
                            .withValue(
                                ContactsContract.CommonDataKinds.Email.ADDRESS,
                                contact.email
                            )
                            .withValue(
                                ContactsContract.CommonDataKinds.Email.TYPE,
                                ContactsContract.CommonDataKinds.Email.TYPE_HOME
                            )
                            .build()
                    )
                }
            }

            val results = contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)

            // Extract the raw_contact_id from each result
            for (result in results) {
                val uri = result.uri
                if (uri != null) {
                    val id = uri.lastPathSegment?.toLongOrNull()
                    if (id != null) {
                        insertedIds.add(id)
                    }
                }
            }
        }

        return insertedIds
    }

    /**
     * Delete all contacts from the emulator's Contacts Provider.
     */
    fun clearContactsProvider(contentResolver: ContentResolver) {
        contentResolver.delete(ContactsContract.RawContacts.CONTENT_URI, null, null)
    }

    private fun generatePhone(random: java.util.Random): String {
        val prefix = PHONE_PREFIXES[random.nextInt(PHONE_PREFIXES.size)]
        val number = buildString {
            for (i in 0 until 10) {
                append(random.nextInt(10))
            }
        }
        return "$prefix$number"
    }

    private fun generateEmail(name: String, random: java.util.Random): String {
        val cleanName = name
            .replace(Regex("[^a-zA-Z\\s]"), "")
            .trim()
            .lowercase()
            .replace(Regex("\\s+"), ".")
            .take(30)
        val domain = EMAIL_DOMAINS[random.nextInt(EMAIL_DOMAINS.size)]
        val suffix = if (random.nextFloat() > 0.5f) random.nextInt(999) else ""
        return "$cleanName$suffix@$domain"
    }
}
