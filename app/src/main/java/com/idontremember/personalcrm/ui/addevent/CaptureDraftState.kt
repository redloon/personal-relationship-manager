package com.idontremember.personalcrm.ui.addevent

import android.content.Context
import com.idontremember.personalcrm.data.capture.CaptureDraftStore

/** Home observes the same format and content rule as the editor. */
fun observeCaptureDraft(context: Context) = CaptureDraftStore(context).observeNewCapture()
