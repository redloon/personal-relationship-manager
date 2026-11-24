package com.redloon.prm.data.repository

import com.redloon.prm.data.db.AppDatabase
import com.redloon.prm.data.db.entity.ContactTag
import com.redloon.prm.data.db.entity.Relationship
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProfileLinksRepository @Inject constructor(database: AppDatabase) {
    private val dao = database.profileLinksDao()
    fun tagsForPerson(id: Long) = dao.tagsForContact(id)
    fun relationshipsFor(id: Long) = dao.relationshipsFor(id)
    suspend fun assign(person: Long, tag: Long) = dao.addTag(ContactTag(person, tag))
    suspend fun unassign(person: Long, tag: Long) = dao.removeTag(ContactTag(person, tag))
    suspend fun link(a: Long, b: Long, label: String) {
        require(a != b && label.isNotBlank())
        dao.addRelationship(Relationship(minOf(a, b), maxOf(a, b), label.trim()))
    }
    suspend fun unlink(link: Relationship) = dao.removeRelationship(link)
}
