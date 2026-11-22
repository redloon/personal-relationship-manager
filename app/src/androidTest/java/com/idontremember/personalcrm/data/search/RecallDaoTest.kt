package com.idontremember.personalcrm.data.search

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.idontremember.personalcrm.data.db.AppDatabase
import com.idontremember.personalcrm.data.db.entity.*
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class RecallDaoTest {
    @get:Rule val hilt = HiltAndroidRule(this)
    @Inject lateinit var db: AppDatabase
    @Before fun setup() { hilt.inject(); db.clearAllTables() }

    @Test fun searchFindsFactsLinkedPeopleTagsAndFollowUps() = runBlocking {
        val alice = db.contactDao().insert(Contact(displayName = "Alice"))
        val fact = db.contactFactDao().insert(ContactFact(contactId = alice, text = "Enjoys climbing"))
        val event = db.eventDao().insert(Event(note = "Met over coffee"))
        db.eventDao().insertEventContact(EventContact(event, alice))
        val tag = db.tagDao().insert(Tag(name = "Outdoors"))
        db.eventDao().insertEventTag(EventTag(event, tag))
        val follow = db.followUpDao().insert(FollowUp(contactId = alice, text = "Ask about the trail"))
        val rows = db.recallDao().entries().first()
        assertEquals(listOf(fact), RecallSearch.find(rows, "Alice climbing").map { it.id })
        assertTrue(RecallSearch.find(rows, "Alice outdoors").any { it.kind == "event" && it.id == event })
        assertTrue(RecallSearch.find(rows, "trail").any { it.kind == "followup" && it.id == follow })
    }

    @Test fun deletedContextIsExcludedAndRenameDoesNotRewriteEvent() = runBlocking {
        val alice = db.contactDao().insert(Contact(displayName = "Old Alice"))
        val fact = db.contactFactDao().insert(ContactFact(contactId = alice, text = "Deleted climbing", deletedAt = 1))
        val event = db.eventDao().insert(Event(note = "Met @Old Alice"))
        db.eventDao().insertEventContact(EventContact(event, alice))
        val before = db.contactDao().getById(alice)!!
        db.contactDao().update(before.copy(displayName = "New Alice"))
        val rows = db.recallDao().entries().first()
        assertFalse(rows.any { it.kind == "fact" && it.id == fact })
        assertTrue(RecallSearch.find(rows, "New Alice").any { it.kind == "event" && it.id == event })
        assertEquals("Met @Old Alice", db.eventDao().getById(event)!!.note)
    }
    @Test fun tagEntitiesAndFollowUpStateAreAvailableWithoutPerRowQueries() = runBlocking {
        val person = db.contactDao().insert(Contact(displayName = "Maya"))
        val tag = db.tagDao().insert(Tag(name = "dog-owner"))
        val follow = db.followUpDao().insert(FollowUp(contactId = person, text = "Ask about dog", dueDate = "2026-11-10", completedAt = 7, createdAt = 3))
        val rows = db.recallDao().entries().first()
        assertEquals(listOf(tag), RecallSearch.find(rows, "dog-owner", kind = "tag").map { it.id })
        val result = rows.single { it.kind == "followup" && it.id == follow }
        assertEquals("2026-11-10", result.dueDate)
        assertEquals(7L, result.completedAt)
        assertEquals(3L, result.createdAt)
    }

    @Test fun exactTagBrowsingIncludesPersonlessNotesAndDeduplicatesMembership() = runBlocking {
        val person = db.contactDao().insert(Contact(displayName = "Maya"))
        val dog = db.tagDao().insert(Tag(name = "dog"))
        val dogOwner = db.tagDao().insert(Tag(name = "dog-owner"))
        val linked = db.eventDao().insert(Event(note = "Linked note"))
        val bare = db.eventDao().insert(Event(note = "Personless note"))
        val other = db.eventDao().insert(Event(note = "Other tag"))
        db.eventDao().insertEventContact(EventContact(linked, person))
        db.eventDao().insertEventTag(EventTag(linked, dogOwner))
        db.eventDao().insertEventTag(EventTag(bare, dogOwner))
        db.eventDao().insertEventTag(EventTag(other, dog))
        db.pilotDao().addTag(ContactTag(person, dogOwner))
        assertEquals(setOf(linked, bare), db.pilotDao().noteIdsForTag(dogOwner).first().toSet())
        val members = db.pilotDao().peopleForTag(dogOwner).first()
        assertEquals(1, members.size)
        assertTrue(members.single().direct)
        assertTrue(members.single().viaNote)
        val event = db.eventDao().getById(linked)!!
        db.eventDao().update(event.copy(deletedAt = 100))
        val after = db.pilotDao().peopleForTag(dogOwner).first().single()
        assertTrue(after.direct)
        assertFalse(after.viaNote)
        assertEquals(listOf(bare), db.pilotDao().noteIdsForTag(dogOwner).first())
    }
}
