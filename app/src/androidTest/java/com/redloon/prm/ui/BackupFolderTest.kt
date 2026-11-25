package com.redloon.prm.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.redloon.prm.ui.settings.backupFolderIntent
import com.redloon.prm.ui.settings.openBackupFolder
import com.redloon.prm.ui.settings.resolveBackupFolderDisplay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupFolderTest {
    @Test fun readableLocationsCoverStorageDownloadsCloudAndRevokedMetadata() {
        fun display(uri: String, label: String? = null) =
            resolveBackupFolderDisplay(uri, "Internal storage", "Downloads") { label }
        val local = display(tree.toString())
        assertEquals("Documents", local.label)
        assertEquals("Internal storage / Documents", local.location)
        assertEquals(
            "1234-ABCD / Backups / PRM",
            display(
                "content://com.android.externalstorage.documents/tree/1234-ABCD%3ABackups%2FPRM"
            ).location
        )
        assertEquals(
            "Downloads / PRM",
            display(
                "content://com.android.providers.downloads.documents/tree/downloads",
                "PRM"
            ).location
        )
        val cloud = display("content://com.google.android.apps.docs.storage/tree/opaque-id", "PRM")
        assertEquals("PRM", cloud.label)
        assertEquals("com.google.android.apps.docs.storage / PRM", cloud.location)
        val revoked = resolveBackupFolderDisplay(tree.toString(), "Internal storage", "Downloads") {
            throw SecurityException()
        }
        assertEquals(local, revoked)
        assertTrue(display("invalid").location.isNotBlank())
    }

    private val tree = Uri.parse(
        "content://com.android.externalstorage.documents/tree/primary%3ADocuments"
    )

    @Test fun opensTheSelectedDirectoryWithReadAccess() {
        val intent = backupFolderIntent(tree.toString())
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals(DocumentsContract.Document.MIME_TYPE_DIR, intent.type)
        assertEquals("primary:Documents", DocumentsContract.getDocumentId(intent.data!!))
        assertEquals(Intent.FLAG_GRANT_READ_URI_PERMISSION, intent.flags)
        var launched: Intent? = null
        assertTrue(openBackupFolder(tree.toString()) { launched = it })
        assertEquals(intent.data, launched!!.data)
    }

    @Test fun missingViewerRevokedAccessAndMalformedFolderReturnFailure() {
        assertFalse(openBackupFolder(tree.toString()) { throw ActivityNotFoundException() })
        assertFalse(openBackupFolder(tree.toString()) { throw SecurityException("revoked") })
        var called = false
        assertFalse(openBackupFolder("invalid") { called = true })
        assertFalse(called)
    }
}
