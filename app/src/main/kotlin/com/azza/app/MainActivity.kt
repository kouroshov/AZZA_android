package com.azza.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.azza.core.datastore.AppPreferences
import javax.inject.Inject
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.azza.app.ui.AzzaApp
import com.azza.core.designsystem.theme.AzzaTheme
import dagger.hilt.android.AndroidEntryPoint

// AppCompatActivity (not ComponentActivity) for per-app language on Android < 13.
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    @Inject lateinit var preferences: AppPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
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
                AzzaApp()
            }
        }
    }
}
