package com.redloon.prm.ui.addevent

import android.content.Context
import com.redloon.prm.data.capture.CaptureDraftStore

/** Home observes the same format and content rule as the editor. */
fun observeCaptureDraft(context: Context) = CaptureDraftStore(context).observeNewCapture()
