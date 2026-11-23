package com.idontremember.personalcrm.data.db

import com.idontremember.personalcrm.data.capture.InlineToken
import com.idontremember.personalcrm.data.capture.InlineTokens

/** Caller owns the transaction. Includes trashed notes and never rewrites prose or timestamps. */
internal fun AppDatabase.rewriteEventTokens(transform: (List<InlineToken>) -> List<InlineToken>) {
    check(inTransaction())
    val updates = mutableListOf<Pair<Long, String>>()
    openHelper.readableDatabase.query("SELECT id, inline_tokens FROM events").use { cursor ->
        while (cursor.moveToNext()) {
            val before = InlineTokens.decode(cursor.getString(1))
            val after = transform(before)
            if (after != before) updates += cursor.getLong(0) to InlineTokens.encode(after)
        }
    }
    for ((id, tokens) in updates) openHelper.writableDatabase.execSQL(
        "UPDATE events SET inline_tokens = ? WHERE id = ?", arrayOf<Any>(tokens, id))
}
