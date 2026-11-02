package com.idontremember.personalcrm.data.search

import com.idontremember.personalcrm.data.db.AppDatabase
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SearchRepository @Inject constructor(database: AppDatabase) {
    val entries = database.recallDao().entries()
}
