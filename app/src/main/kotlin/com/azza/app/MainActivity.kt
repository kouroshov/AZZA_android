package com.azza.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
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
        // Light icons on the app's dark chrome, whatever the phone's own theme is.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent {
            AzzaTheme {
                AzzaApp()
            }
        }
    }
}
