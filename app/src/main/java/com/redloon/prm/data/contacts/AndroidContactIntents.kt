package com.redloon.prm.data.contacts

import android.app.Activity
import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract

fun createAndroidContactIntent(name: String): Intent = Intent(Intent.ACTION_INSERT).apply {
    type = ContactsContract.RawContacts.CONTENT_TYPE
    putExtra(ContactsContract.Intents.Insert.NAME, name.trim())
}

fun createdContactUri(resultCode: Int, data: Intent?): Uri? =
    data?.data?.takeIf { resultCode == Activity.RESULT_OK }

/** Resolves contact-editor and picker URIs to an aggregated Android contact ID. */
fun resolveAndroidContactId(resolver: ContentResolver, uri: Uri): Long? {
    if (uri.authority != ContactsContract.AUTHORITY) return null
    val firstPath = uri.pathSegments.firstOrNull()
    val idColumn = when (firstPath) {
        "raw_contacts" -> ContactsContract.RawContacts.CONTACT_ID
        "data" -> ContactsContract.Data.CONTACT_ID
        else -> ContactsContract.Contacts._ID
    }
    return runCatching {
        resolver.query(uri, arrayOf(idColumn), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getLong(0).takeIf { it > 0L } else null
        }
    }.getOrNull()
}
