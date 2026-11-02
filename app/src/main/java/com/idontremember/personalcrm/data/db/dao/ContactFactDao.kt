package com.idontremember.personalcrm.data.db.dao

import androidx.room.Dao
import androidx.room.Update
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.idontremember.personalcrm.data.db.entity.ContactFact
import kotlinx.coroutines.flow.Flow

@Dao
interface ContactFactDao {

    @Query("SELECT * FROM contact_facts WHERE id = :id AND deleted_at IS NULL")
    suspend fun getById(id: Long): ContactFact?

    @Update suspend fun update(fact: ContactFact)

    @Query("SELECT * FROM contact_facts WHERE deleted_at IS NULL ORDER BY created_at DESC")
    fun getAll(): Flow<List<ContactFact>>

    @Insert
    suspend fun insert(fact: ContactFact): Long

    @Query("SELECT * FROM contact_facts WHERE contact_id = :contactId AND deleted_at IS NULL ORDER BY created_at DESC")
    fun getByContactId(contactId: Long): Flow<List<ContactFact>>

    @Query("SELECT * FROM contact_facts WHERE contact_id = :contactId AND deleted_at IS NULL ORDER BY created_at DESC")
    suspend fun getByContactIdOnce(contactId: Long): List<ContactFact>

    @Delete
    suspend fun delete(fact: ContactFact)

    @Query("DELETE FROM contact_facts WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM contact_facts ORDER BY contact_id, created_at DESC")
    suspend fun getAllOnce(): List<ContactFact>
}