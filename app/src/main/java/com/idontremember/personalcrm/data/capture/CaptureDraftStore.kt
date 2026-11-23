package com.idontremember.personalcrm.data.capture

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/** Storage only; editor ViewModels own loading, conflict resolution and save transitions. */
@Singleton
class CaptureDraftStore @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("capture_draft", Context.MODE_PRIVATE)
    fun loadCapture(key: String): CaptureDraft? =
        prefs.getString(key, null)?.let(DraftCodec::decodeCapture)
    fun saveCapture(key: String, value: CaptureDraft) {
        prefs.edit { putString(key, DraftCodec.encode(value)) }
    }
    fun loadFollowUp(key: String): FollowUpDraft? =
        prefs.getString(key, null)?.let(DraftCodec::decodeFollowUp)
    fun saveFollowUp(key: String, value: FollowUpDraft) {
        prefs.edit { putString(key, DraftCodec.encode(value)) }
    }
    fun remove(key: String) {
        prefs.edit { remove(key) }
    }
    fun observeNewCapture(): Flow<Boolean> = callbackFlow {
        fun publish() {
            trySend(loadCapture("draft")?.hasContent == true)
        }
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key ==
                null ||
                key == "draft"
            ) {
                publish()
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        publish()
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()
}
