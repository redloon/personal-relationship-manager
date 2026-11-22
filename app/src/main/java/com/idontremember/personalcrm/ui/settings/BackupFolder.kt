package com.idontremember.personalcrm.ui.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract

internal fun backupFolderIntent(folder: String): Intent {
    val tree = Uri.parse(folder)
    require(tree.scheme == "content" && DocumentsContract.isTreeUri(tree))
    val document = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
    return Intent(Intent.ACTION_VIEW).setDataAndType(document, DocumentsContract.Document.MIME_TYPE_DIR)
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
