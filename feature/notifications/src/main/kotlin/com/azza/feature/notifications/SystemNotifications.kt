package com.azza.feature.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.azza.core.model.AppNotification
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Set on the launch intent of a notification tap: open the Notifications page. */
const val EXTRA_OPEN_NOTIFICATIONS = "com.azza.extra.OPEN_NOTIFICATIONS"

/**
 * The phone's notification shade. One entry per new notification, up to
 * [MAX_SINGLE]; more than that arrive as one "N new notifications". Every
 * tap opens the in-app list, which is where the detail and the links are.
 */
@Singleton
class SystemNotifications @Inject constructor(@ApplicationContext private val context: Context) {
    private val manager = NotificationManagerCompat.from(context)

    fun canPost(): Boolean =
        manager.areNotificationsEnabled() &&
            (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)

    fun post(items: List<AppNotification>) {
        if (items.isEmpty() || !canPost()) return
        val res = localized().resources
        ensureChannel(res.getString(R.string.n_channel_activity))
        val open = openListIntent()
        try {
            if (items.size > MAX_SINGLE) {
                manager.notify(
                    SUMMARY_ID,
                    builder(open)
                        .setContentTitle(res.getString(R.string.app_brand))
                        .setContentText(res.getQuantityString(R.plurals.n_shade_summary, items.size, items.size))
                        .build(),
                )
            } else {
                items.forEach { n ->
                    val body = notificationBody(res, n)
                    manager.notify(
                        n.id.hashCode(),
                        builder(open)
                            .setContentTitle(notificationTitle(res, n))
                            .setContentText(body)
                            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                            .build(),
                    )
                }
            }
        } catch (_: SecurityException) {
            // The permission was withdrawn between the check and the post.
        }
    }

    fun clearAll() = manager.cancelAll()

    private fun builder(open: PendingIntent) = NotificationCompat.Builder(context, CHANNEL_ACTIVITY)
        .setSmallIcon(R.drawable.ic_stat_notification)
        .setContentIntent(open)
        .setAutoCancel(true)
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .setCategory(NotificationCompat.CATEGORY_SOCIAL)

    private fun openListIntent(): PendingIntent {
        val intent = (context.packageManager.getLaunchIntentForPackage(context.packageName) ?: Intent())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(EXTRA_OPEN_NOTIFICATIONS, true)
        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun ensureChannel(name: String) {
        if (Build.VERSION.SDK_INT < 26) return
        // Re-creating a channel only updates its name — the user's own settings for it stay.
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel(CHANNEL_ACTIVITY, name, NotificationManager.IMPORTANCE_DEFAULT))
    }

    /**
     * The app's chosen language, also from the background. From Android 13
     * the system applies it to every context; below, AppCompat applies it to
     * activities only, so the poll asks for it explicitly (it is known once
     * the app has been opened in this process; before that, the phone's).
     */
    private fun localized(): Context {
        val chosen = AppCompatDelegate.getApplicationLocales()
        if (chosen.isEmpty || Build.VERSION.SDK_INT >= 33) return context
        val config = Configuration(context.resources.configuration).apply { setLocales(LocaleList.forLanguageTags(chosen.toLanguageTags())) }
        return context.createConfigurationContext(config)
    }

    private companion object {
        const val CHANNEL_ACTIVITY = "activity"
        const val SUMMARY_ID = 1
        const val MAX_SINGLE = 4
    }
}
