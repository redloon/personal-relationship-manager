package com.redloon.prm.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.redloon.prm.data.db.entity.FollowUp
import kotlinx.coroutines.flow.Flow

@Dao
interface FollowUpDao {
    @Query(
        "SELECT * FROM follow_ups WHERE deleted_at IS NULL ORDER BY completed_at IS NOT NULL, due_date IS NULL, due_date, created_at DESC"
    )
    fun getAll(): Flow<List<FollowUp>>

    @Query("SELECT * FROM follow_ups ORDER BY id")
    suspend fun getAllOnce(): List<FollowUp>

    @Query("SELECT * FROM follow_ups WHERE id = :id")
    suspend fun getById(id: Long): FollowUp?

    @Insert suspend fun insert(item: FollowUp): Long

    @Update suspend fun update(item: FollowUp)

    @Query("DELETE FROM follow_ups")
    suspend fun deleteAll()
}
