package com.azza.app

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.compose.runtime.CompositionLocalProvider
import com.azza.core.common.WebLinks
import com.azza.core.datastore.AppPreferences
import com.azza.core.ui.LocalWebLinks
import javax.inject.Inject
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.azza.app.ui.AzzaApp
import com.azza.core.designsystem.theme.AzzaTheme
import dagger.hilt.android.AndroidEntryPoint

// AppCompatActivity (not ComponentActivity) for per-app language on Android < 13.
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    @Inject lateinit var preferences: AppPreferences

    @Inject lateinit var webLinks: WebLinks

    /** An intent still to act on (a notification tap); cleared once the app has. */
    private val pendingIntent = MutableStateFlow<Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        // Not again after a rotation or a language change: that intent was handled.
        if (savedInstanceState == null) pendingIntent.value = intent
        setContent {
            // Dark until someone chooses otherwise — the web's default too.
            val choice by preferences.theme.collectAsStateWithLifecycle(initialValue = null)
            val dark = when (choice) {
                "LIGHT" -> false
                "SYSTEM" -> isSystemInDarkTheme()
                else -> true
            }
            // System-bar icons follow the app's theme, not the phone's.
            androidx.compose.runtime.LaunchedEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            AzzaTheme(darkTheme = dark) {
                val incoming by pendingIntent.collectAsStateWithLifecycle()
                CompositionLocalProvider(LocalWebLinks provides webLinks) {
                    AzzaApp(incoming = incoming, onIncomingHandled = { pendingIntent.value = null })
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        pendingIntent.value = intent
    }
}
