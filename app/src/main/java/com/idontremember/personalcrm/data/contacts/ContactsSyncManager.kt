package com.idontremember.personalcrm.data.contacts

import android.Manifest
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import com.idontremember.personalcrm.data.db.dao.ContactDao
import com.idontremember.personalcrm.data.db.entity.Contact
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContactsSyncManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val contactDao: ContactDao
) {

    data class SyncResult(
        val added: Int,
        val updated: Int,
        val total: Int
    )

    fun hasPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Sync contacts from Android Contacts Provider.
     * - New contacts are inserted
     * - Existing contacts have their basic fields (name, phone, email, photo) updated
     * - Local-only fields (facts, events) are never touched
     *
     * @throws SecurityException if READ_CONTACTS permission is not granted
     */
    suspend fun syncContacts(): SyncResult = withContext(Dispatchers.IO) {
        if (!hasPermission()) {
            throw SecurityException("READ_CONTACTS permission not granted")
        }

        val systemContacts = readSystemContacts()
        var added = 0
        var updated = 0

        for (sysContact in systemContacts) {
            val existing = contactDao.getByAndroidContactId(sysContact.androidContactId!!)

            if (existing == null) {
                // New contact — insert
                contactDao.insert(sysContact)
                added++
            } else {
                // Existing — update basic fields if changed, preserve local data
                val needsUpdate = existing.displayName != sysContact.displayName ||
                    existing.phoneNumber != sysContact.phoneNumber ||
                    existing.email != sysContact.email ||
                    existing.photoUri != sysContact.photoUri

                if (needsUpdate) {
                    contactDao.update(
                        existing.copy(
                            displayName = sysContact.displayName,
                            phoneNumber = sysContact.phoneNumber,
                            email = sysContact.email,
                            photoUri = sysContact.photoUri,
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                    updated++
                }
            }
        }

        SyncResult(
            added = added,
            updated = updated,
            total = systemContacts.size
        )
    }

    private fun readSystemContacts(): List<Contact> {
        val contacts = mutableMapOf<Long, Contact>()
        val contentResolver: ContentResolver = context.contentResolver

        // Query contacts
        val cursor = contentResolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            arrayOf(
                ContactsContract.Contacts._ID,
                ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
                ContactsContract.Contacts.PHOTO_URI
            ),
            null,
            null,
            "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} ASC"
        )

        cursor?.use { c ->
            val idIdx = c.getColumnIndex(ContactsContract.Contacts._ID)
            val nameIdx = c.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
            val photoIdx = c.getColumnIndex(ContactsContract.Contacts.PHOTO_URI)

            while (c.moveToNext()) {
                val id = c.getLong(idIdx)
                val name = c.getString(nameIdx) ?: continue
                val photoUri = c.getString(photoIdx)

                contacts[id] = Contact(
                    androidContactId = id,
                    displayName = name,
                    photoUri = photoUri
                )
            }
        }

        // Query phone numbers
        val phoneCursor = contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            ),
            null,
            null,
            null
        )

        phoneCursor?.use { c ->
            val contactIdIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
            val numberIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

            while (c.moveToNext()) {
                val contactId = c.getLong(contactIdIdx)
                val number = c.getString(numberIdx)
                if (number != null && contacts.containsKey(contactId)) {
                    contacts[contactId] = contacts[contactId]!!.copy(phoneNumber = number)
                }
            }
        }

        // Query emails
        val emailCursor = contentResolver.query(
            ContactsContract.CommonDataKinds.Email.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Email.CONTACT_ID,
                ContactsContract.CommonDataKinds.Email.ADDRESS
            ),
            null,
            null,
            null
        )

        emailCursor?.use { c ->
            val contactIdIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Email.CONTACT_ID)
            val emailIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Email.ADDRESS)

            while (c.moveToNext()) {
                val contactId = c.getLong(contactIdIdx)
                val email = c.getString(emailIdx)
                if (email != null && contacts.containsKey(contactId)) {
                    contacts[contactId] = contacts[contactId]!!.copy(email = email)
                }
            }
        }

        return contacts.values.toList()
    }
}