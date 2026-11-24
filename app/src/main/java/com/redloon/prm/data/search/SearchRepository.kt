package com.redloon.prm.data.search

import com.redloon.prm.data.db.AppDatabase
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.map

@Singleton
class SearchRepository @Inject constructor(database: AppDatabase) {
    val entries = database.recallDao().rows().map { rows -> rows.map(RecallRow::toEntry) }
}
