package com.redloon.prm.data.backup

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.edit
import androidx.room.withTransaction
import com.redloon.prm.data.capture.InlineTokens
import com.redloon.prm.data.db.AppDatabase
import com.redloon.prm.data.db.entity.Contact
import com.redloon.prm.data.db.entity.ContactFact
import com.redloon.prm.data.db.entity.ContactTag
import com.redloon.prm.data.db.entity.Event
import com.redloon.prm.data.db.entity.EventContact
import com.redloon.prm.data.db.entity.EventTag
import com.redloon.prm.data.db.entity.FollowUp
import com.redloon.prm.data.db.entity.Relationship
import com.redloon.prm.data.db.entity.Tag
import com.redloon.prm.data.followups.ReminderDeliveryLock
import com.redloon.prm.data.settings.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

@Serializable
data class BackupSnapshot(
    val schemaVersion: Int = 5,
    val app: String = "personal-crm",
    val exportedAt: String,
    val contacts: List<ContactRecord>,
    val facts: List<FactRecord>,
    val events: List<EventRecord>,
    val tags: List<TagRecord>,
    val eventContacts: List<EventContactRecord>,
    val eventTags: List<EventTagRecord>,
    val contactTags: List<ContactTagRecord> = emptyList(),
    val relationships: List<RelationshipRecord> = emptyList(),
    val followUps: List<FollowUpRecord> = emptyList()
)

@Serializable data class ContactRecord(
    val id: Long,
    val androidContactId: Long?,
    val displayName: String,
    val phoneNumber: String?,
    val email: String?,
    val photoUri: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val isHidden: Boolean = false,
    val deletionAllowed: Boolean = false
)

@Serializable data class FactRecord(
    val id: Long,
    val contactId: Long,
    val text: String,
    val createdAt: Long,
    val deletedAt: Long? = null
)

@Serializable data class EventRecord(
    val id: Long,
    val note: String,
    val eventDate: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val inlineTokens: String = "[]"
)

@Serializable data class TagRecord(val id: Long, val name: String, val createdAt: Long)

@Serializable data class EventContactRecord(val eventId: Long, val contactId: Long)

@Serializable data class EventTagRecord(val eventId: Long, val tagId: Long)

@Serializable data class FollowUpRecord(
    val id: Long,
    val contactId: Long,
    val text: String,
    val dueDate: String?,
    val completedAt: Long?,
    val notifiedAt: Long?,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null
)

@Serializable data class ContactTagRecord(val contactId: Long, val tagId: Long)

@Serializable
data class RelationshipRecord(val contactA: Long, val contactB: Long, val label: String)

/** Portable versioned JSON. Validate all IDs and links before changing the database. */
object BackupJson {
    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
    }

    private fun validDate(value: String): Boolean = runCatching {
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }
        value.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) &&
            format.format(format.parse(value)!!) == value
    }.getOrDefault(false)

    fun encode(snapshot: BackupSnapshot): String =
        json.encodeToString(BackupSnapshot.serializer(), snapshot)

    fun decode(source: String): BackupSnapshot {
        val fields = json.parseToJsonElement(source).jsonObject
        require("schemaVersion" in fields && "app" in fields) { "Missing backup header" }
        val snapshot = json.decodeFromString(BackupSnapshot.serializer(), source)
        require(snapshot.schemaVersion in 1..5 && snapshot.app == "personal-crm") {
            "Unsupported backup"
        }
        require(snapshot.exportedAt.isNotBlank()) { "Missing export time" }
        fun <T> ids(items: List<T>, id: (T) -> Long): Set<Long> {
            val values = items.map(id)
            require(
                values.all {
                    it > 0
                } && values.size == values.toSet().size
            ) { "Invalid record IDs" }
            return values.toSet()
        }
        val contactIds = ids(snapshot.contacts) { it.id }
        ids(snapshot.facts) { it.id }
        ids(snapshot.followUps) { it.id }
        require(
            snapshot.followUps.all {
                it.contactId in contactIds && it.text.isNotBlank() &&
                    (it.dueDate == null || validDate(it.dueDate))
            }
        ) { "Invalid follow-up" }
        val eventIds = ids(snapshot.events) { it.id }
        val tagIds = ids(snapshot.tags) { it.id }
        require(
            snapshot.contacts.mapNotNull { it.androidContactId }.distinct().size ==
                snapshot.contacts.count { it.androidContactId != null }
        ) { "Duplicate Android contact" }
        require(
            snapshot.tags.map {
                it.name
            }.distinct().size == snapshot.tags.size
        ) { "Duplicate tag" }
        require(snapshot.facts.all { it.contactId in contactIds }) { "Orphan fact" }
        require(
            snapshot.eventContacts.all {
                it.eventId in eventIds && it.contactId in contactIds
            } &&
                snapshot.eventContacts.distinct().size == snapshot.eventContacts.size
        ) {
            "Invalid event contact link"
        }
        require(
            snapshot.eventTags.all { it.eventId in eventIds && it.tagId in tagIds } &&
                snapshot.eventTags.distinct().size == snapshot.eventTags.size
        ) {
            "Invalid event tag link"
        }
        require(
            snapshot.contactTags.all { it.contactId in contactIds && it.tagId in tagIds } &&
                snapshot.contactTags.distinct().size == snapshot.contactTags.size
        ) {
            "Invalid contact tag"
        }
        require(
            snapshot.relationships.all {
                it.contactA in contactIds && it.contactB in contactIds &&
                    it.contactA < it.contactB &&
                    it.label.isNotBlank()
            } &&
                snapshot.relationships.distinct().size == snapshot.relationships.size
        ) {
            "Invalid relationship"
        }
        val tagsById = snapshot.tags.associateBy { it.id }
        for (event in snapshot.events) {
            InlineTokens.validate(
                event.note,
                InlineTokens.decode(event.inlineTokens),
                snapshot.eventContacts.filter {
                    it.eventId == event.id
                }.map { it.contactId }.toSet(),
                snapshot.eventTags.filter {
                    it.eventId == event.id
                }.mapNotNull { tagsById[it.tagId]?.name }.toSet()
            )
        }
        return snapshot
    }
}

@Singleton
class DataExporter @Inject constructor(
    private val database: AppDatabase,
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository
) {
    suspend fun exportToJson(): String = withContext(Dispatchers.IO) {
        val snapshot = database.withTransaction {
            BackupSnapshot(
                exportedAt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }.format(Date()),
                contacts = database.contactDao().getAllIncludingHiddenOnce().map {
                    ContactRecord(
                        it.id, it.androidContactId, it.displayName,
                        it.phoneNumber,
                        it.email,
                        it.photoUri,
                        it.createdAt,
                        it.updatedAt,
                        it.isHidden,
                        it.deletionAllowed
                    )
                },
                facts = database.contactFactDao().getAllOnce().map {
                    FactRecord(it.id, it.contactId, it.text, it.createdAt, it.deletedAt)
                },
                events = database.eventDao().getAllOnce().map {
                    EventRecord(
                        it.id,
                        it.note,
                        it.eventDate,
                        it.createdAt,
                        it.updatedAt,
                        it.deletedAt,
                        it.inlineTokens
                    )
                },
                tags = database.tagDao().getAllOnce().map {
                    TagRecord(it.id, it.name, it.createdAt)
                },
                eventContacts = database.eventDao().getAllEventContacts().map {
                    EventContactRecord(it.eventId, it.contactId)
                },
                eventTags = database.eventDao().getAllEventTags().map {
                    EventTagRecord(it.eventId, it.tagId)
                },
                contactTags = database.profileLinksDao().allContactTags().map {
                    ContactTagRecord(it.contactId, it.tagId)
                },
                relationships = database.profileLinksDao().allRelationships().map {
                    RelationshipRecord(it.contactA, it.contactB, it.label)
                },
                followUps = database.followUpDao().getAllOnce().map {
                    FollowUpRecord(
                        it.id, it.contactId, it.text,
                        it.dueDate, it.completedAt, null, it.createdAt, it.updatedAt, it.deletedAt
                    )
                }
            )
        }
        BackupJson.encode(snapshot)
    }

    suspend fun restore(snapshot: BackupSnapshot) = withContext(Dispatchers.IO) {
        ReminderDeliveryLock.mutex.withLock {
            BackupJson.decode(BackupJson.encode(snapshot))
            settingsRepository.setFollowUpRemindersEnabled(false)
            database.withTransaction {
                database.eventDao().deleteAll()
                database.contactDao().deleteAll()
                database.tagDao().deleteAll()
                snapshot.contacts.forEach {
                    database.contactDao().insert(
                        Contact(
                            it.id, null, it.displayName,
                            it.phoneNumber,
                            it.email,
                            null,
                            it.createdAt,
                            it.updatedAt,
                            isHidden = it.isHidden,
                            deletionAllowed =
                            snapshot.schemaVersion >= 5 && it.deletionAllowed &&
                                it.androidContactId == null
                        )
                    )
                }
                snapshot.events.forEach {
                    database.eventDao().insert(
                        Event(
                            it.id,
                            it.note,
                            it.eventDate,
                            it.createdAt,
                            it.updatedAt,
                            it.deletedAt,
                            it.inlineTokens
                        )
                    )
                }
                snapshot.tags.forEach {
                    database.tagDao().insert(Tag(it.id, it.name, it.createdAt))
                }
                snapshot.facts.forEach {
                    database.contactFactDao().insert(
                        ContactFact(it.id, it.contactId, it.text, it.createdAt, it.deletedAt)
                    )
                }
                snapshot.followUps.forEach {
                    database.followUpDao().insert(
                        FollowUp(
                            it.id, it.contactId, it.text,
                            it.dueDate,
                            it.completedAt,
                            null,
                            it.createdAt,
                            it.updatedAt,
                            it.deletedAt
                        )
                    )
                }
                snapshot.eventContacts.forEach {
                    database.eventDao().insertEventContact(EventContact(it.eventId, it.contactId))
                }
                snapshot.contactTags.forEach {
                    database.profileLinksDao().addTag(ContactTag(it.contactId, it.tagId))
                }
                snapshot.relationships.forEach {
                    database.profileLinksDao().addRelationship(
                        Relationship(it.contactA, it.contactB, it.label)
                    )
                }
                snapshot.eventTags.forEach {
                    database.eventDao().insertEventTag(EventTag(it.eventId, it.tagId))
                }
            }
            NotificationManagerCompat.from(context).cancel(803)
            context.getSharedPreferences(
                "capture_draft",
                Context.MODE_PRIVATE
            ).edit { clear() }
            context.getSharedPreferences(
                "capture_drafts",
                Context.MODE_PRIVATE
            ).edit { clear() }
        }
    }
}
