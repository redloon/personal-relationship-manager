package com.redloon.prm.data.contacts

import androidx.room.withTransaction
import com.redloon.prm.data.capture.TokenTarget
import com.redloon.prm.data.db.AppDatabase
import com.redloon.prm.data.db.dao.ContactDao
import com.redloon.prm.data.db.entity.Contact
import com.redloon.prm.data.db.rewriteEventTokens
import javax.inject.Inject
import javax.inject.Singleton

/** Owns the one supported profile-consolidation path and always runs it transactionally. */
@Singleton
class ProfileConsolidator @Inject constructor(
    private val database: AppDatabase,
    private val contactDao: ContactDao
) {
    suspend fun linkToAndroid(appProfileId: Long, provider: Contact) {
        database.withTransaction {
            val keeper = contactDao.getById(appProfileId) ?: error("Person unavailable")
            val imported = provider.androidContactId?.let { contactDao.getByAndroidContactId(it) }
            if (imported != null && imported.id != appProfileId) {
                consolidateInsideTransaction(appProfileId, imported.id)
            }
            updateFromProvider(keeper, provider)
        }
    }

    /** Returns false if either profile changed and is no longer a safe review candidate. */
    suspend fun reconnectProtected(savedProfileId: Long, androidProfileId: Long): Boolean =
        database.withTransaction {
            val saved = contactDao.getById(savedProfileId) ?: return@withTransaction false
            val android = contactDao.getById(androidProfileId) ?: return@withTransaction false
            if (
                saved.androidContactId != null || saved.deletionAllowed ||
                android.androidContactId == null ||
                ReconnectMatching.reviewCandidate(saved, android) == null
            ) {
                return@withTransaction false
            }
            consolidateInsideTransaction(saved.id, android.id)
            updateFromProvider(saved, android)
            true
        }

    /** Rechecks bidirectional uniqueness in the same transaction that performs automatic repair. */
    suspend fun autoReconnectProtected(pair: ProfilePair, dismissed: Set<ProfilePair>): Boolean =
        database.withTransaction {
            if (
                pair !in ReconnectMatching.autoReconnectPairs(
                    contactDao.getAllIncludingHiddenOnce(),
                    dismissed
                )
            ) {
                return@withTransaction false
            }
            val saved = contactDao.getById(pair.savedProfileId) ?: return@withTransaction false
            val android = contactDao.getById(pair.androidProfileId) ?: return@withTransaction false
            consolidateInsideTransaction(saved.id, android.id)
            updateFromProvider(saved, android)
            true
        }

    private suspend fun updateFromProvider(keeper: Contact, provider: Contact) {
        contactDao.updateProviderFields(
            id = keeper.id,
            name = provider.displayName,
            androidId = provider.androidContactId ?: error("Android contact unavailable"),
            phone = provider.phoneNumber,
            email = provider.email,
            photo = provider.photoUri,
            lookup = provider.lookupKey,
            updatedAt = System.currentTimeMillis()
        )
    }

    private fun consolidateInsideTransaction(keep: Long, remove: Long) {
        check(database.inTransaction())
        val sql = database.openHelper.writableDatabase
        sql.execSQL(
            "UPDATE contact_facts SET contact_id = ? WHERE contact_id = ?",
            arrayOf(keep, remove)
        )
        sql.execSQL(
            "UPDATE follow_ups SET contact_id = ? WHERE contact_id = ?",
            arrayOf(keep, remove)
        )
        sql.execSQL(
            "INSERT OR IGNORE INTO event_contacts(event_id, contact_id) SELECT event_id, ? FROM event_contacts WHERE contact_id = ?",
            arrayOf(keep, remove)
        )
        sql.execSQL(
            "INSERT OR IGNORE INTO contact_tags(contact_id, tag_id) SELECT ?, tag_id FROM contact_tags WHERE contact_id = ?",
            arrayOf(keep, remove)
        )
        val links = mutableListOf<Triple<Long, Long, String>>()
        sql.query(
            "SELECT contact_a, contact_b, label FROM relationships WHERE contact_a = ? OR contact_b = ?",
            arrayOf(remove, remove)
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val a = if (cursor.getLong(0) == remove) keep else cursor.getLong(0)
                val b = if (cursor.getLong(1) == remove) keep else cursor.getLong(1)
                if (a != b) links += Triple(minOf(a, b), maxOf(a, b), cursor.getString(2))
            }
        }
        for ((a, b, label) in links) {
            sql.execSQL(
                "INSERT OR IGNORE INTO relationships(contact_a, contact_b, label) VALUES (?, ?, ?)",
                arrayOf<Any>(a, b, label)
            )
        }
        database.rewriteEventTokens { tokens ->
            tokens.map { token ->
                if (token.target == TokenTarget.Person(remove)) {
                    token.copy(target = TokenTarget.Person(keep))
                } else {
                    token
                }
            }
        }
        sql.execSQL("DELETE FROM contacts WHERE id = ?", arrayOf(remove))
    }
}
