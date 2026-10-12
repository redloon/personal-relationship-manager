package com.idontremember.personalcrm.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.idontremember.personalcrm.data.db.entity.ContactFact
import kotlinx.coroutines.flow.Flow

@Dao
interface ContactFactDao {

    @Insert
    suspend fun insert(fact: ContactFact): Long

    @Query("SELECT * FROM contact_facts WHERE contact_id = :contactId ORDER BY created_at DESC")
    fun getByContactId(contactId: Long): Flow<List<ContactFact>>

    @Query("SELECT * FROM contact_facts WHERE contact_id = :contactId ORDER BY created_at DESC")
    suspend fun getByContactIdOnce(contactId: Long): List<ContactFact>

    @Delete
    suspend fun delete(fact: ContactFact)

    @Query("DELETE FROM contact_facts WHERE id = :id")
    suspend fun deleteById(id: Long)
}