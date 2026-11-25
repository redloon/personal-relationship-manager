package com.redloon.prm.ui.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.net.toUri
import com.redloon.prm.R

internal data class BackupFolderDisplay(val label: String, val location: String)

internal fun resolveBackupFolderDisplay(context: Context, folder: String): BackupFolderDisplay =
    resolveBackupFolderDisplay(
        folder,
        context.getString(R.string.backup_internal_storage),
        context.getString(R.string.backup_downloads)
    ) { tree ->
        val document = DocumentsContract.buildDocumentUriUsingTree(
            tree,
            DocumentsContract.getTreeDocumentId(tree)
        )
        context.contentResolver.query(
            document,
            arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
    }

internal fun resolveBackupFolderDisplay(
    folder: String,
    internalStorage: String,
    downloads: String,
    readLabel: (Uri) -> String?
): BackupFolderDisplay {
    val tree = folder.toUri()
    val identifier = runCatching { DocumentsContract.getTreeDocumentId(tree) }.getOrNull()
    val metadata = runCatching { readLabel(tree) }.getOrNull()?.takeIf { it.isNotBlank() }
    val authority = tree.authority.orEmpty()
    val path = when (authority) {
        "com.android.externalstorage.documents" -> identifier?.let {
            val volume = it.substringBefore(':').let { name ->
                if (name == "primary") internalStorage else name
            }
            listOf(volume) + it.substringAfter(':', "").split('/').filter(String::isNotBlank)
        }
        "com.android.providers.downloads.documents" -> listOf(downloads)
        else -> null
    }
    val label = metadata ?: path?.lastOrNull()
        ?: identifier?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
        ?: tree.lastPathSegment?.takeIf { it.isNotBlank() } ?: folder
    val location = if (path != null) {
        (if (metadata != null && metadata != path.lastOrNull()) path + metadata else path)
            .joinToString(" / ")
    } else {
        listOf(authority, label).filter(String::isNotBlank).distinct().joinToString(" / ")
    }
    return BackupFolderDisplay(label, location)
}

internal fun backupFolderIntent(folder: String): Intent {
    val tree = folder.toUri()
    require(tree.scheme == "content" && DocumentsContract.isTreeUri(tree))
    val document = DocumentsContract.buildDocumentUriUsingTree(
        tree,
        DocumentsContract.getTreeDocumentId(tree)
    )
    return Intent(
        Intent.ACTION_VIEW
    ).setDataAndType(document, DocumentsContract.Document.MIME_TYPE_DIR)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
}

/** Viewing is best effort and never changes the persisted backup configuration. */
internal fun openBackupFolder(folder: String, launch: (Intent) -> Unit): Boolean = try {
    launch(backupFolderIntent(folder))
    true
} catch (_: ActivityNotFoundException) {
    false
} catch (_: SecurityException) {
    false
} catch (_: IllegalArgumentException) {
    false
}
