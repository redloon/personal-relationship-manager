package com.idontremember.personalcrm.ui.addevent

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.idontremember.personalcrm.data.settings.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** Only accepts incoming text; no records are created until the user taps Save. */
@AndroidEntryPoint
class ShareCaptureActivity : ComponentActivity() {
    @Inject lateinit var settings: SettingsRepository
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val incoming = if (intent.action == Intent.ACTION_SEND) {
            val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString().orEmpty()
            val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT).orEmpty()
            listOf(subject.takeIf { it.isNotBlank() && !text.startsWith(it) }, text.takeIf { it.isNotBlank() })
                .filterNotNull().joinToString("\n").takeIf { it.isNotBlank() }
        } else null
        setContent { CaptureEntryContent(settings, incoming, onClose = ::finish) }
    }
}
