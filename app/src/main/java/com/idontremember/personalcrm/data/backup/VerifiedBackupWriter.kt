package com.idontremember.personalcrm.data.backup

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract

internal data class BackupFile(val id: String, val name: String)
internal interface BackupDestination {
    fun create(name: String): String
    fun write(id: String, source: String)
    fun read(id: String): String
    fun list(): List<BackupFile>
    fun delete(id: String)
}

/** No prior backup is removed until the new document has been read and verified. */
internal class VerifiedBackupWriter(private val destination: BackupDestination) {
    fun write(source: String, name: String) {
        val expected = BackupJson.decode(source)
        val created = destination.create(name)
        try {
            destination.write(created, source)
            require(BackupJson.decode(destination.read(created)) == expected) { "Backup verification failed" }
        } catch(error: Exception) {
            runCatching { destination.delete(created) }
            throw error
        }
        // Provider failure during housekeeping must not turn a valid backup into data loss.
        runCatching {
            destination.list().filter { it.name.startsWith("personal-crm-auto-") && it.name.endsWith(".json") }
                .filter { runCatching { BackupJson.decode(destination.read(it.id)) }.isSuccess }
                .sortedByDescending { it.name }.drop(7).forEach { destination.delete(it.id) }
        }
    }
}

internal class DocumentsBackupDestination(private val resolver: ContentResolver, private val tree: Uri) : BackupDestination {
    override fun create(name: String): String {
        val root = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        return DocumentsContract.createDocument(resolver, root, "application/json", name)?.toString()
            ?: error("Cannot create backup")
    }
    override fun write(id: String, source: String) {
        val output = resolver.openOutputStream(Uri.parse(id), "wt") ?: error("Cannot write backup")
        output.use { it.write(source.toByteArray(Charsets.UTF_8)); it.flush() }
    }
    override fun read(id: String): String = resolver.openInputStream(Uri.parse(id))
        ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: error("Cannot read backup")
    override fun list(): List<BackupFile> = buildList {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        resolver.query(children, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { c ->
            while(c.moveToNext()) add(BackupFile(DocumentsContract.buildDocumentUriUsingTree(tree, c.getString(0)).toString(), c.getString(1)))
        }
    }
    override fun delete(id: String) {
        check(DocumentsContract.deleteDocument(resolver, Uri.parse(id))) { "Cannot delete backup" }
    }
}
