package com.idontremember.personalcrm.data.backup

import androidx.room.withTransaction
import com.idontremember.personalcrm.data.db.AppDatabase
import com.idontremember.personalcrm.data.db.entity.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class BackupSnapshot(
    val schemaVersion: Int = 1,
    val app: String = "personal-crm",
    val exportedAt: String,
    val contacts: List<ContactRecord>,
    val facts: List<FactRecord>,
    val events: List<EventRecord>,
    val tags: List<TagRecord>,
    val eventContacts: List<EventContactRecord>,
    val eventTags: List<EventTagRecord>
)

@Serializable data class ContactRecord(val id: Long, val androidContactId: Long?, val displayName: String,
    val phoneNumber: String?, val email: String?, val photoUri: String?, val createdAt: Long, val updatedAt: Long)
@Serializable data class FactRecord(val id: Long, val contactId: Long, val text: String, val createdAt: Long)
@Serializable data class EventRecord(val id: Long, val note: String, val eventDate: Long, val createdAt: Long, val updatedAt: Long)
@Serializable data class TagRecord(val id: Long, val name: String, val createdAt: Long)
@Serializable data class EventContactRecord(val eventId: Long, val contactId: Long)
@Serializable data class EventTagRecord(val eventId: Long, val tagId: Long)

/** The small Phase 1 format. Validation happens before any database write. */
object BackupJson {
    private val json = Json { prettyPrint = true; encodeDefaults = true }

    fun encode(snapshot: BackupSnapshot): String = json.encodeToString(BackupSnapshot.serializer(), snapshot)

    fun decode(source: String): BackupSnapshot {
        val fields = json.parseToJsonElement(source).jsonObject
        require("schemaVersion" in fields && "app" in fields) { "Missing backup header" }
        val snapshot = json.decodeFromString(BackupSnapshot.serializer(), source)
        require(snapshot.schemaVersion == 1 && snapshot.app == "personal-crm") { "Unsupported backup" }
        require(snapshot.exportedAt.isNotBlank()) { "Missing export time" }
        fun <T> ids(items: List<T>, id: (T) -> Long): Set<Long> {
            val values = items.map(id)
            require(values.all { it > 0 } && values.size == values.toSet().size) { "Invalid record IDs" }
            return values.toSet()
        }
        val contactIds = ids(snapshot.contacts) { it.id }
        ids(snapshot.facts) { it.id }
        val eventIds = ids(snapshot.events) { it.id }
        val tagIds = ids(snapshot.tags) { it.id }
        require(snapshot.contacts.mapNotNull { it.androidContactId }.distinct().size ==
            snapshot.contacts.count { it.androidContactId != null }) { "Duplicate Android contact" }
        require(snapshot.tags.map { it.name }.distinct().size == snapshot.tags.size) { "Duplicate tag" }
        require(snapshot.facts.all { it.contactId in contactIds }) { "Orphan fact" }
        require(snapshot.eventContacts.all { it.eventId in eventIds && it.contactId in contactIds } &&
            snapshot.eventContacts.distinct().size == snapshot.eventContacts.size) { "Invalid event contact link" }
        require(snapshot.eventTags.all { it.eventId in eventIds && it.tagId in tagIds } &&
            snapshot.eventTags.distinct().size == snapshot.eventTags.size) { "Invalid event tag link" }
        return snapshot
    }
}

@Singleton
class DataExporter @Inject constructor(private val database: AppDatabase) {
    suspend fun exportToJson(): String = withContext(Dispatchers.IO) {
        val snapshot = database.withTransaction {
            BackupSnapshot(
                exportedAt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }.format(Date()),
                contacts = database.contactDao().getAllOnce().map { ContactRecord(it.id, it.androidContactId, it.displayName,
                    it.phoneNumber, it.email, it.photoUri, it.createdAt, it.updatedAt) },
                facts = database.contactFactDao().getAllOnce().map { FactRecord(it.id, it.contactId, it.text, it.createdAt) },
                events = database.eventDao().getAllOnce().map { EventRecord(it.id, it.note, it.eventDate, it.createdAt, it.updatedAt) },
                tags = database.tagDao().getAllOnce().map { TagRecord(it.id, it.name, it.createdAt) },
                eventContacts = database.eventDao().getAllEventContacts().map { EventContactRecord(it.eventId, it.contactId) },
                eventTags = database.eventDao().getAllEventTags().map { EventTagRecord(it.eventId, it.tagId) }
            )
        }
        BackupJson.encode(snapshot)
    }

    suspend fun restore(snapshot: BackupSnapshot) = withContext(Dispatchers.IO) {
        BackupJson.decode(BackupJson.encode(snapshot))
        database.withTransaction {
            database.eventDao().deleteAll()
            database.contactDao().deleteAll()
            database.tagDao().deleteAll()
            snapshot.contacts.forEach { database.contactDao().insert(Contact(it.id, it.androidContactId, it.displayName,
                it.phoneNumber, it.email, it.photoUri, it.createdAt, it.updatedAt)) }
            snapshot.events.forEach { database.eventDao().insert(Event(it.id, it.note, it.eventDate, it.createdAt, it.updatedAt)) }
            snapshot.tags.forEach { database.tagDao().insert(Tag(it.id, it.name, it.createdAt)) }
            snapshot.facts.forEach { database.contactFactDao().insert(ContactFact(it.id, it.contactId, it.text, it.createdAt)) }
            snapshot.eventContacts.forEach { database.eventDao().insertEventContact(EventContact(it.eventId, it.contactId)) }
            snapshot.eventTags.forEach { database.eventDao().insertEventTag(EventTag(it.eventId, it.tagId)) }
        }
    }
}
