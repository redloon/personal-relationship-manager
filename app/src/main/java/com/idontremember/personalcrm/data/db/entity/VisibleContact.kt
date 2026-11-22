package com.idontremember.personalcrm.data.db.entity

import androidx.room.DatabaseView
import androidx.room.Embedded

/** Default source for person discovery. Saved associations resolve against contacts. */
@DatabaseView(value = "SELECT * FROM contacts WHERE is_hidden = 0", viewName = "visible_contacts")
data class VisibleContact(@Embedded val contact: Contact)
