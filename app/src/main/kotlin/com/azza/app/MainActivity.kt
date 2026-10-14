package com.azza.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.azza.app.ui.AzzaApp
import com.azza.core.designsystem.theme.AzzaTheme
import dagger.hilt.android.AndroidEntryPoint

// AppCompatActivity (not ComponentActivity) for per-app language on Android < 13.
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AzzaTheme {
                AzzaApp()
            }
        }
    }
}
