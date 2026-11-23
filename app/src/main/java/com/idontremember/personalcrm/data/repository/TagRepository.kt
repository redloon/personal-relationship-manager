package com.idontremember.personalcrm.data.repository

import androidx.room.withTransaction
import com.idontremember.personalcrm.data.db.AppDatabase
import com.idontremember.personalcrm.data.db.rewriteEventTokens
import com.idontremember.personalcrm.data.capture.InlineTokens
import com.idontremember.personalcrm.data.capture.TokenTarget
import com.idontremember.personalcrm.data.db.dao.TagDao
import com.idontremember.personalcrm.data.db.entity.Tag
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TagRepository @Inject constructor(
    private val tagDao: TagDao,
    private val database: AppDatabase
) {

    fun peopleForTag(id: Long) = database.profileLinksDao().peopleForTag(id)
    fun noteIdsForTag(id: Long) = database.profileLinksDao().noteIdsForTag(id)

    fun getAllTags(): Flow<List<Tag>> = tagDao.getAll()

    suspend fun getAllTagsOnce(): List<Tag> = tagDao.getAllOnce()

    fun searchTags(query: String): Flow<List<Tag>> = tagDao.search(query)

    suspend fun searchTagsOnce(query: String): List<Tag> = tagDao.searchOnce(query)

    suspend fun getOrCreateTag(name: String): Tag = tagDao.getOrCreate(name)

    suspend fun deleteTag(tag: Tag) = database.withTransaction {
        database.rewriteEventTokens { tokens -> tokens.filterNot { it.target == TokenTarget.Tag(tag.name) } }
        tagDao.delete(tag)
    }
}