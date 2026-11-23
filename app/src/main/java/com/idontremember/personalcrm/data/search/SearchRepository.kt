package com.idontremember.personalcrm.data.search

import com.idontremember.personalcrm.data.db.AppDatabase
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SearchRepository @Inject constructor(database: AppDatabase) {
    val entries = database.recallDao().rows().map { rows -> rows.map(RecallRow::toEntry) }
}
