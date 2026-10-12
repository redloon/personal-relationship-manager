package com.idontremember.personalcrm.tile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.idontremember.personalcrm.ui.addevent.AddEventSheet
import com.idontremember.personalcrm.ui.theme.PersonalCRMTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Transparent activity that hosts the AddEventSheet.
 * Launched by the Quick Settings tile for frictionless event entry.
 * Uses a dialog/overlay theme so it feels like a floating modal.
 */
@AndroidEntryPoint
class QuickAddEventActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            PersonalCRMTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                ) {
                    AddEventSheet(
                        onDismiss = { finish() },
                        onSaved = { finish() }
                    )
                }
            }
        }
    }
}