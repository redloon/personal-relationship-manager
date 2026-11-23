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
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class ContactsSyncManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val contactDao: ContactDao
) {

    data class SyncResult(val added: Int, val updated: Int, val total: Int)

    fun hasPermission(): Boolean = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.READ_CONTACTS
    ) == PackageManager.PERMISSION_GRANTED

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
        val attachedByLookup = contactDao.getAllIncludingHiddenOnce().filter {
            it.lookupKey != null
        }.associateBy { it.lookupKey }
        var added = 0
        var updated = 0

        for (sysContact in systemContacts) {
            val attached =
                attachedByLookup[sysContact.lookupKey]
                    ?: contactDao.getByAndroidContactId(sysContact.androidContactId!!)
            // A provider ID can be reused for a different person. Preserve the old profile.
            val resolvedOldId = attached?.lookupKey?.takeIf {
                it != sysContact.lookupKey
            }?.let { key ->
                val uri = ContactsContract.Contacts.getLookupUri(
                    attached.androidContactId ?: 0L,
                    key
                )
                ContactsContract.Contacts.lookupContact(
                    context.contentResolver,
                    uri
                )?.lastPathSegment?.toLongOrNull()
            }
            val identityChanged = attached != null && (
                (attached.lookupKey == null && !legacyAttachmentMatches(attached, sysContact)) ||
                    (
                        attached.lookupKey != null && attached.lookupKey != sysContact.lookupKey &&
                            resolvedOldId != sysContact.androidContactId
                        )
                )
            val existing = if (identityChanged) {
                contactDao.detachAndroidContact(attached!!.id)
                null
            } else {
                attached
            }

            val occupyingId = contactDao.getByAndroidContactId(sysContact.androidContactId!!)
            if (occupyingId != null && occupyingId.id != existing?.id) {
                contactDao.detachAndroidContact(occupyingId.id)
            }
            if (existing == null) {
                // New contact — insert
                contactDao.insert(sysContact)
                added++
            } else {
                // Existing — update basic fields if changed, preserve local data
                val needsUpdate =
                    existing.deletionAllowed || existing.displayName != sysContact.displayName ||
                        existing.phoneNumber != sysContact.phoneNumber ||
                        existing.email != sysContact.email ||
                        existing.androidContactId != sysContact.androidContactId ||
                        existing.photoUri != sysContact.photoUri ||
                        existing.lookupKey != sysContact.lookupKey

                if (needsUpdate) {
                    contactDao.updateProviderFields(
                        existing.id,
                        sysContact.displayName,
                        sysContact.androidContactId,
                        sysContact.phoneNumber,
                        sysContact.email,
                        sysContact.photoUri,
                        sysContact.lookupKey,
                        System.currentTimeMillis()
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

    suspend fun getSystemContact(androidId: Long): Contact? = withContext(Dispatchers.IO) {
        if (!hasPermission()) throw SecurityException("READ_CONTACTS permission not granted")
        readSystemContacts().find { it.androidContactId == androidId }
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
                ContactsContract.Contacts.PHOTO_URI,
                ContactsContract.Contacts.LOOKUP_KEY
            ),
            null,
            null,
            "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} ASC"
        )

        cursor?.use { c ->
            val idIdx = c.getColumnIndex(ContactsContract.Contacts._ID)
            val nameIdx = c.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
            val photoIdx = c.getColumnIndex(ContactsContract.Contacts.PHOTO_URI)
            val lookupIdx = c.getColumnIndex(ContactsContract.Contacts.LOOKUP_KEY)

            while (c.moveToNext()) {
                val id = c.getLong(idIdx)
                val name = c.getString(nameIdx) ?: continue
                val photoUri = c.getString(photoIdx)

                contacts[id] = Contact(
                    androidContactId = id,
                    displayName = name,
                    photoUri = photoUri,
                    lookupKey = c.getString(lookupIdx)
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
