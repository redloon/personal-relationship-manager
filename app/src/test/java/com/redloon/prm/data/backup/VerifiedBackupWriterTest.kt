package com.redloon.prm.data.backup

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test

class VerifiedBackupWriterTest {
    private val source = BackupJson.encode(
        BackupSnapshot(
            exportedAt = "2026-11-01T09:00:00Z",
            contacts = emptyList(),
            facts = emptyList(),
            events = emptyList(),
            tags = emptyList(),
            eventContacts = emptyList(),
            eventTags = emptyList()
        )
    )

    @Test fun failedWritePreservesEveryPreviousBackup() {
        val folder = Folder().apply {
            seed(7, source)
            failWrite = true
        }
        try {
            VerifiedBackupWriter(folder).write(source, "prm-auto-08.json")
            fail()
        } catch (
            _: IllegalStateException
        ) { }
        assertEquals((1..7).map { "prm-auto-0$it.json" }.toSet(), folder.files.keys)
    }

    @Test fun unreadableNewBackupDoesNotTriggerRetention() {
        val folder = Folder().apply {
            seed(7, source)
            corruptNewRead = true
        }
        try {
            VerifiedBackupWriter(folder).write(source, "prm-auto-08.json")
            fail()
        } catch (
            _: IllegalArgumentException
        ) { }
        assertEquals(7, folder.files.size)
        assertTrue(folder.files.containsKey("prm-auto-01.json"))
    }

    @Test fun verifiedNewBackupKeepsLatestSevenAndLeavesOtherFilesAlone() {
        val folder = Folder().apply {
            seed(7, source)
            files["my-notes.json"] = "private notes"
        }
        VerifiedBackupWriter(folder).write(source, "prm-auto-08.json")
        assertFalse(folder.files.containsKey("prm-auto-01.json"))
        assertEquals(source, folder.files["prm-auto-08.json"])
        assertEquals("private notes", folder.files["my-notes.json"])
        assertEquals(8, folder.files.size)
    }

    @Test fun failedRetentionStillLeavesNewVerifiedBackup() {
        val folder = Folder().apply {
            seed(7, source)
            failDeletionOfOld = true
        }
        VerifiedBackupWriter(folder).write(source, "prm-auto-08.json")
        assertEquals(source, folder.files["prm-auto-08.json"])
        assertEquals(8, folder.files.size)
    }

    private class Folder : BackupDestination {
        val files = linkedMapOf<String, String>()
        var failWrite = false
        var corruptNewRead = false
        var failDeletionOfOld = false
        fun seed(count: Int, source: String) {
            (1..count).forEach {
                files["prm-auto-0$it.json"] =
                    source
            }
        }
        override fun create(name: String): String {
            files[name] = ""
            return name
        }
        override fun write(id: String, source: String) {
            if (failWrite) error("Disk full")
            files[id] =
                source
        }
        override fun read(id: String): String = if (corruptNewRead &&
            id.endsWith("08.json")
        ) {
            "{}"
        } else {
            files.getValue(id)
        }
        override fun list(): List<BackupFile> = files.keys.map { BackupFile(it, it) }
        override fun delete(id: String) {
            if (failDeletionOfOld &&
                id.endsWith("01.json")
            ) {
                error("Read-only")
            }
            files.remove(id)
        }
    }
}
