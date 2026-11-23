package com.idontremember.personalcrm.data.contacts

import com.idontremember.personalcrm.data.db.entity.Contact
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ContactAttachmentTest {
    @Test fun reusedProviderIdCannotReplaceLegacyPersonsIdentity() {
        val saved =
            Contact(id = 1, androidContactId = 42, displayName = "Alice", phoneNumber = "123")
        assertFalse(
            legacyAttachmentMatches(
                saved,
                saved.copy(id = 0, displayName = "Bob", phoneNumber = "456")
            )
        )
        assertFalse(legacyAttachmentMatches(saved, saved.copy(phoneNumber = "456")))
    }

    @Test fun unchangedLegacyAttachmentCanAcquireLookupKey() {
        val saved =
            Contact(id = 1, androidContactId = 42, displayName = "Alice", phoneNumber = "123")
        assertTrue(
            legacyAttachmentMatches(saved, saved.copy(id = 0, lookupKey = "stable-provider-key"))
        )
    }
}
