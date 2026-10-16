package com.azza.app

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.azza.app.ui.AzzaApp
import com.azza.core.common.WebLinks
import com.azza.core.datastore.AppPreferences
import com.azza.core.designsystem.theme.AzzaTheme
import com.azza.core.ui.AppUnlock
import com.azza.core.ui.LocalWebLinks
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

// AppCompatActivity (not ComponentActivity) for per-app language on Android < 13;
// it is also the FragmentActivity the fingerprint prompt needs.
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    @Inject lateinit var preferences: AppPreferences

    @Inject lateinit var webLinks: WebLinks

    /** An intent still to act on (a notification tap); cleared once the app has. */
    private val pendingIntent = MutableStateFlow<Intent?>(null)

    /**
     * The app lock (Settings → "Lock with fingerprint"): null until the
     * preference is read, so nothing private is drawn before it is known.
     * Locks at a cold start and after [RELOCK_AFTER_MS] in the background;
     * not on a rotation or a language change (those recreate the activity).
     */
    private val locked = MutableStateFlow<Boolean?>(null)
    private var leftAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        // Not again after a rotation or a language change: that intent was handled.
        if (savedInstanceState == null) pendingIntent.value = intent
        if (savedInstanceState?.getBoolean(KEY_UNLOCKED) == true) {
            locked.value = false
        } else {
            lifecycleScope.launch { locked.value = preferences.appLock.first() }
        }
        setContent {
            // Dark until someone chooses otherwise — the web's default too.
            val choice by preferences.theme.collectAsStateWithLifecycle(initialValue = null)
            val dark = when (choice) {
                "LIGHT" -> false
                "SYSTEM" -> isSystemInDarkTheme()
                else -> true
            }
            // System-bar icons follow the app's theme, not the phone's.
            LaunchedEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            AzzaTheme(darkTheme = dark) {
                val lockState by locked.collectAsStateWithLifecycle()
                when (lockState) {
                    null -> Box(Modifier.fillMaxSize().background(AzzaTheme.colors.bg))
                    true -> LockScreen(onUnlock = ::unlock)
                    false -> {
                        val incoming by pendingIntent.collectAsStateWithLifecycle()
                        CompositionLocalProvider(LocalWebLinks provides webLinks) {
                            AzzaApp(incoming = incoming, onIncomingHandled = { pendingIntent.value = null })
                        }
                    }
                }
            }
        }
    }

    private fun unlock() {
        AppUnlock.prompt(this, getString(R.string.lock_prompt_title), getString(R.string.lock_cancel)) { ok -> if (ok) locked.value = false }
    }

    override fun onStart() {
        super.onStart()
        if (leftAt != 0L && SystemClock.elapsedRealtime() - leftAt > RELOCK_AFTER_MS) {
            lifecycleScope.launch { if (preferences.appLock.first()) locked.value = true }
        }
    }

    override fun onStop() {
        super.onStop()
        leftAt = SystemClock.elapsedRealtime()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_UNLOCKED, locked.value == false)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        pendingIntent.value = intent
    }

    private companion object {
        const val KEY_UNLOCKED = "azza.unlocked"
        const val RELOCK_AFTER_MS = 60_000L
    }
}

/** Shown instead of the app while it is locked; the prompt opens at once, and again from the button. */
@Composable
private fun LockScreen(onUnlock: () -> Unit) {
    LaunchedEffect(Unit) { onUnlock() }
    Box(Modifier.fillMaxSize().background(AzzaTheme.colors.bg), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(Icons.Outlined.Lock, null, tint = AzzaTheme.colors.textSecondary, modifier = Modifier.size(48.dp))
            Text(stringResource(R.string.lock_title), style = MaterialTheme.typography.titleMedium)
            Button(onClick = onUnlock) { Text(stringResource(R.string.lock_unlock)) }
        }
    }
}
