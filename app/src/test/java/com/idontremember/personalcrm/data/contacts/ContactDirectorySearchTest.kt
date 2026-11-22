package com.idontremember.personalcrm.data.contacts

import com.idontremember.personalcrm.data.db.entity.Contact
import org.junit.Assert.*
import org.junit.Test

class ContactDirectorySearchTest {
    private val maya = Contact(id = 1, displayName = "Maya Chen", phoneNumber = "+1 555 0100", email = "maya@example.com")
    private val sam = Contact(id = 2, displayName = "Sam Lee")

    @Test fun matchesOnlyContactFieldsAndSupportsMultipleTerms() {
        assertEquals(listOf(maya), ContactDirectorySearch.find(listOf(maya, sam), "CHEN example"))
        assertEquals(listOf(maya), ContactDirectorySearch.find(listOf(maya, sam), "555 0100"))
        assertTrue(ContactDirectorySearch.find(listOf(maya, sam), "Lantern bakery").isEmpty())
    }
    @Test fun blankQueriesPreserveOrderingAndPunctuationIsLiteral() {
        assertEquals(listOf(sam, maya), ContactDirectorySearch.find(listOf(sam, maya), "  "))
        assertTrue(ContactDirectorySearch.find(listOf(maya), "%").isEmpty())
    }
}
