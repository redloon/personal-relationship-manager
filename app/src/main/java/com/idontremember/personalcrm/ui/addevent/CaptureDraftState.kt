package com.idontremember.personalcrm.ui.addevent

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import org.json.JSONObject

/** Observe the existing single new-capture draft; editing drafts have separate keys. */
fun observeCaptureDraft(context: Context): Flow<Boolean> = callbackFlow {
    val prefs = context.getSharedPreferences("capture_draft", Context.MODE_PRIVATE)
    fun publish() {
        trySend(runCatching {
            val draft = JSONObject(prefs.getString("draft", null) ?: return@runCatching false)
            draft.optString("note").isNotBlank() || (draft.optJSONArray("contacts")?.length() ?: 0) > 0 ||
                (draft.optJSONArray("tags")?.length() ?: 0) > 0
        }.getOrDefault(false))
    }
    val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key -> if (key == "draft" || key == null) publish() }
    prefs.registerOnSharedPreferenceChangeListener(listener)
    publish()
    awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
}.distinctUntilChanged()
