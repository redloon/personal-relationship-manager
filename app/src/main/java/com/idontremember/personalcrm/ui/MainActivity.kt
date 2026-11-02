package com.idontremember.personalcrm.ui

import android.content.Intent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.idontremember.personalcrm.ui.components.exposeTestTags
import com.idontremember.personalcrm.data.followups.FollowUpWorker
import com.idontremember.personalcrm.ui.followups.FollowUpsOverlay
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.idontremember.personalcrm.data.recovery.RecoveryRepository
import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.idontremember.personalcrm.data.settings.SettingsRepository
import com.idontremember.personalcrm.data.settings.ThemeMode
import com.idontremember.personalcrm.ui.navigation.AppNavigation
import com.idontremember.personalcrm.ui.theme.PersonalCRMTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject lateinit var recovery: RecoveryRepository

    private var openFollowUps by mutableStateOf(false)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(FollowUpWorker.OPEN_FOLLOW_UPS, false)) openFollowUps = true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        openFollowUps = intent.getBooleanExtra(FollowUpWorker.OPEN_FOLLOW_UPS, false)
        FollowUpWorker.ensureScheduled(this)
        lifecycleScope.launch { recovery.purgeExpired() }

        setContent {
            // P1: theme preferences apply reactively — no restart needed on change.
            val themeMode by settingsRepository.themeMode
                .collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)
            val dynamicColor by settingsRepository.dynamicColor
                .collectAsStateWithLifecycle(initialValue = true)
            val darkTheme = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            PersonalCRMTheme(darkTheme = darkTheme, dynamicColor = dynamicColor) {
                Surface(
                    modifier = Modifier.fillMaxSize().exposeTestTags(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation()
                    if (openFollowUps) FollowUpsOverlay(onDismiss = { openFollowUps = false })
                }
            }
        }
    }
}