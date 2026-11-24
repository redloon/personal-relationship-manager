package com.redloon.prm.data.contacts

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidContactIntentsTest {
    @Test fun creationIntentUsesNativeEditorAndTrimmedName() {
        val intent = createAndroidContactIntent("  Maya Chen  ")

        assertEquals(Intent.ACTION_INSERT, intent.action)
        assertEquals(ContactsContract.RawContacts.CONTENT_TYPE, intent.type)
        assertEquals("Maya Chen", intent.getStringExtra(ContactsContract.Intents.Insert.NAME))
    }

    @Test fun creationResultAcceptsOnlySuccessfulUris() {
        val uri = Uri.parse("content://com.android.contacts/contacts/42")
        val data = Intent().setData(uri)

        assertEquals(uri, createdContactUri(Activity.RESULT_OK, data))
        assertNull(createdContactUri(Activity.RESULT_CANCELED, data))
        assertNull(createdContactUri(Activity.RESULT_OK, Intent()))
        assertNull(createdContactUri(Activity.RESULT_OK, null))
    }
}
