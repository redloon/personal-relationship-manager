package com.redloon.prm.testdata

import com.redloon.prm.data.db.dao.ContactDao
import com.redloon.prm.data.db.entity.Contact

object PopulationFixtures {
    suspend fun seedBackgroundPeople(contacts: ContactDao, count: Int = 60): List<Long> =
        (1..count).map { index ->
            contacts.insert(
                Contact(
                    displayName = "Zzz Background ${index.toString().padStart(3, '0')}",
                    email = "background$index@example.test",
                    createdAt = index.toLong(),
                    updatedAt = index.toLong()
                )
            )
        }
}
