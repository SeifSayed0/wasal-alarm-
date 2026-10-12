package com.seifsayed0.wasalalarm.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.content.ContextCompat
import com.seifsayed0.wasalalarm.data.LocalDataStore
import com.seifsayed0.wasalalarm.data.TripStore
import com.seifsayed0.wasalalarm.domain.AppLanguage
import com.seifsayed0.wasalalarm.domain.ThemePreference
import com.seifsayed0.wasalalarm.service.LocationTrackingService

class AlarmActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION") window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        val settings = LocalDataStore(this).load().settings
        val trip = TripStore(this).load()
        val useDark = when (settings.theme) {
            ThemePreference.DARK -> true
            ThemePreference.LIGHT -> false
            ThemePreference.SYSTEM -> resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_YES
        }
        setContent {
            CompositionLocalProvider(LocalLayoutDirection provides if (settings.language == AppLanguage.ARABIC) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                MaterialTheme(colorScheme = if (useDark) darkColorScheme(
                    primary = Color(0xFF70D4C7), onPrimary = Color(0xFF003731), background = Color(0xFF101917), surface = Color(0xFF182320),
                    onSurface = Color(0xFFE1EFEB), error = Color(0xFFFFB4AB), errorContainer = Color(0xFF5D1717)
                ) else lightColorScheme(primary = Color(0xFF0D766E), error = Color(0xFF8B2E26))) {
                    AlarmScreen(
                        language = settings.language,
                        trip = trip,
                        onStop = ::stopAlarm,
                        onBack = { finish() }
                    )
                }
            }
        }
    }

    private fun stopAlarm() {
        val intent = Intent(this, LocationTrackingService::class.java).setAction(LocationTrackingService.ACTION_STOP)
        runCatching { startService(intent) }
        finish()
    }
}
