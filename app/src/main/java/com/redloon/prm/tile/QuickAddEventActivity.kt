package com.redloon.prm.tile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.redloon.prm.data.settings.SettingsRepository
import com.redloon.prm.ui.addevent.CaptureEntryContent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class QuickAddEventActivity : ComponentActivity() {
    @Inject lateinit var settings: SettingsRepository
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { CaptureEntryContent(settings, onClose = ::finish) }
    }
}
