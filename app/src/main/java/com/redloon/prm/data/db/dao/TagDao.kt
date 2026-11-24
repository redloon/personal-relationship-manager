package com.redloon.prm.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.redloon.prm.data.db.entity.Tag
import kotlinx.coroutines.flow.Flow

@Dao
interface TagDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(tag: Tag): Long

    @Query("SELECT * FROM tags WHERE name = :name")
    suspend fun getByName(name: String): Tag?

    @Query("SELECT * FROM tags ORDER BY name ASC")
    fun getAll(): Flow<List<Tag>>

    @Query("SELECT * FROM tags ORDER BY name ASC")
    suspend fun getAllOnce(): List<Tag>

    @Query("DELETE FROM tags")
    suspend fun deleteAll()

    @Query("SELECT * FROM tags WHERE name LIKE '%' || :query || '%' ORDER BY name ASC")
    fun search(query: String): Flow<List<Tag>>

    @Query("SELECT * FROM tags WHERE name LIKE '%' || :query || '%' ORDER BY name ASC")
    suspend fun searchOnce(query: String): List<Tag>

    @Delete
    suspend fun delete(tag: Tag)

    // Get or create a tag by name (case-insensitive match)
    suspend fun getOrCreate(name: String): Tag {
        val normalized = name.trim().lowercase()
        return getByName(normalized) ?: let {
            val tag = Tag(name = normalized)
            val id = insert(tag)
            tag.copy(id = id)
        }
    }
}
