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

    @Query("SELECT * FROM visible_contacts ORDER BY display_name COLLATE NOCASE")
    fun getAll(): Flow<List<Contact>>

    @Query("SELECT * FROM contacts ORDER BY id")
    suspend fun getAllIncludingHiddenOnce(): List<Contact>

    /** Explicit opt-in for existing links, backup and identity management. */
    @Query("SELECT * FROM contacts ORDER BY display_name COLLATE NOCASE")
    fun getAllIncludingHidden(): Flow<List<Contact>>

    @Query("SELECT * FROM contacts WHERE is_hidden = 1 ORDER BY display_name COLLATE NOCASE")
    fun getHidden(): Flow<List<Contact>>

    @Query("SELECT id FROM contacts WHERE id IN (:ids) AND is_hidden != :hidden")
    suspend fun idsChangingVisibility(ids: List<Long>, hidden: Boolean): List<Long>

    @Query("UPDATE contacts SET is_hidden = :hidden WHERE id IN (:ids)")
    suspend fun setHidden(ids: List<Long>, hidden: Boolean)

    @Query("UPDATE contacts SET display_name = :name, android_contact_id = :androidId, phone_number = :phone, email = :email, photo_uri = :photo, lookup_key = :lookup, updated_at = :updatedAt WHERE id = :id")
    suspend fun updateProviderFields(id: Long, name: String, androidId: Long, phone: String?, email: String?, photo: String?, lookup: String?, updatedAt: Long)

    @Query("UPDATE contacts SET android_contact_id = NULL, lookup_key = NULL, photo_uri = NULL WHERE id = :id")
    suspend fun detachAndroidContact(id: Long)

    @Query("DELETE FROM contacts")
    suspend fun deleteAll()

    @Query("SELECT * FROM contacts WHERE id = :id")
    suspend fun getById(id: Long): Contact?

    @Query("SELECT * FROM contacts WHERE id = :id")
    fun getByIdFlow(id: Long): Flow<Contact?>

    @Query("SELECT * FROM contacts WHERE android_contact_id = :androidContactId")
    suspend fun getByAndroidContactId(androidContactId: Long): Contact?

    @Query("SELECT * FROM visible_contacts WHERE display_name LIKE '%' || :query || '%' ORDER BY display_name ASC")
    fun search(query: String): Flow<List<Contact>>

    @Delete
    suspend fun delete(contact: Contact)

    @Query("SELECT COUNT(*) FROM visible_contacts")
    suspend fun count(): Int
}
