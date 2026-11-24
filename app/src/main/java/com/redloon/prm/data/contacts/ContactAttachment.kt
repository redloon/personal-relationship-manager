package com.redloon.prm.data.contacts

import com.redloon.prm.data.db.entity.Contact

/** Legacy attachments lack a provider lookup key. Never overwrite a changed identity blindly. */
internal fun legacyAttachmentMatches(saved: Contact, provider: Contact): Boolean =
    saved.androidContactId == provider.androidContactId &&
        saved.displayName == provider.displayName &&
        (saved.phoneNumber == null || saved.phoneNumber == provider.phoneNumber) &&
        (saved.email == null || saved.email == provider.email)
