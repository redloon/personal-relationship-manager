package com.idontremember.personalcrm.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.idontremember.personalcrm.data.db.entity.Contact
import kotlinx.coroutines.flow.Flow

@Dao
interface ContactDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(contact: Contact): Long

    @Update
    suspend fun update(contact: Contact)

    @Query("SELECT * FROM contacts ORDER BY display_name ASC")
    fun getAll(): Flow<List<Contact>>

    @Query("SELECT * FROM contacts ORDER BY id")
    suspend fun getAllOnce(): List<Contact>

    @Query("DELETE FROM contacts")
    suspend fun deleteAll()

    @Query("SELECT * FROM contacts WHERE id = :id")
    suspend fun getById(id: Long): Contact?

    @Query("SELECT * FROM contacts WHERE id = :id")
    fun getByIdFlow(id: Long): Flow<Contact?>

    @Query("SELECT * FROM contacts WHERE android_contact_id = :androidContactId")
    suspend fun getByAndroidContactId(androidContactId: Long): Contact?

    @Query("SELECT * FROM contacts WHERE display_name LIKE '%' || :query || '%' ORDER BY display_name ASC")
    fun search(query: String): Flow<List<Contact>>

    @Delete
    suspend fun delete(contact: Contact)

    @Query("SELECT COUNT(*) FROM contacts")
    suspend fun count(): Int
}
