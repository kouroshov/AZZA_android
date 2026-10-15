package com.azza.app.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.azza.app.MainActivity
import com.azza.app.R
import com.azza.core.common.ApplicationScope
import com.azza.core.data.AuthRepository
import com.azza.core.data.CartRepository
import com.azza.core.data.NotificationsRepository
import com.azza.core.datastore.AppPreferences
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/** Set on a widget tap's launch intent: which screen to open (AzzaApp reads it). */
const val EXTRA_OPEN_SCREEN = "com.azza.extra.OPEN_SCREEN"

enum class WidgetTarget { SEARCH, SCAN, NOTIFICATIONS, BASKET }

/**
 * The home-screen widget (phase 4d): four shortcuts — search, scan,
 * notifications, basket — with the last known counts on the last two. It
 * shows saved numbers (AppPreferences), never fetches: [WidgetCounts] keeps
 * them current while the app or the notification poll is running.
 */
class AzzaWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val preferences = EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java).preferences()
        provideContent {
            val counts by preferences.widgetCounts.collectAsState(initial = AppPreferences.WidgetCounts())
            GlanceTheme { Content(context, counts) }
        }
    }

    @Composable
    private fun Content(context: Context, counts: AppPreferences.WidgetCounts) {
        Column(
            GlanceModifier.fillMaxSize().background(GlanceTheme.colors.widgetBackground).cornerRadius(20.dp).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                context.getString(R.string.app_name),
                style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 16.sp, color = GlanceTheme.colors.onSurface),
            )
            Spacer(GlanceModifier.height(8.dp))
            Row(GlanceModifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Shortcut(context, WidgetTarget.SEARCH, R.drawable.ic_widget_search, R.string.widget_search, null, GlanceModifier.defaultWeight())
                Shortcut(context, WidgetTarget.SCAN, R.drawable.ic_widget_scan, R.string.widget_scan, null, GlanceModifier.defaultWeight())
                Shortcut(context, WidgetTarget.NOTIFICATIONS, R.drawable.ic_widget_notifications, R.string.widget_notifications, counts.unread.takeIf { counts.signedIn }, GlanceModifier.defaultWeight())
                Shortcut(context, WidgetTarget.BASKET, R.drawable.ic_widget_basket, R.string.widget_basket, counts.basket.takeIf { counts.signedIn }, GlanceModifier.defaultWeight())
            }
        }
    }

    @Composable
    private fun Shortcut(context: Context, target: WidgetTarget, icon: Int, label: Int, count: Int?, modifier: GlanceModifier) {
        val open = Intent(context, MainActivity::class.java)
            .setAction("com.azza.action.OPEN_${target.name}") // distinct per button, so the four PendingIntents differ
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(EXTRA_OPEN_SCREEN, target.name)
        Column(
            modifier.clickable(actionStartActivity(open)).padding(vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(contentAlignment = Alignment.TopEnd) {
                Box(
                    GlanceModifier.size(44.dp).background(GlanceTheme.colors.secondaryContainer).cornerRadius(22.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        ImageProvider(icon),
                        contentDescription = null,
                        modifier = GlanceModifier.size(22.dp),
                        colorFilter = ColorFilter.tint(GlanceTheme.colors.onSecondaryContainer),
                    )
                }
                if (count != null && count > 0) {
                    Box(
                        GlanceModifier.background(GlanceTheme.colors.error).cornerRadius(9.dp).padding(horizontal = 5.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (count > 99) "99+" else count.toString(),
                            style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = GlanceTheme.colors.onError),
                        )
                    }
                }
            }
            Text(context.getString(label), style = TextStyle(fontSize = 12.sp, color = GlanceTheme.colors.onSurface), maxLines = 1)
        }
    }
}

class AzzaWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AzzaWidget()
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun preferences(): AppPreferences
}

/**
 * Keeps the widget's numbers current: the unread count (refreshed by the
 * app and by every notification poll) and the basket's item count. Saved,
 * so a widget redrawn later — or after the app is gone — still has them.
 * Signed out: no numbers at all.
 */
@Singleton
class WidgetCounts @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: AppPreferences,
    private val notifications: NotificationsRepository,
    private val cart: CartRepository,
    private val auth: AuthRepository,
    @ApplicationScope private val scope: CoroutineScope,
) {
    @OptIn(kotlinx.coroutines.FlowPreview::class)
    fun start() {
        scope.launch {
            combine(auth.isSignedIn, notifications.unreadCount, cart.cart) { signedIn, unread, basket ->
                AppPreferences.WidgetCounts(signedIn, unread, basket?.itemCount ?: 0)
            }
                .distinctUntilChanged()
                .debounce(500) // start-up delivers the three in quick succession
                .collect { counts ->
                    preferences.setWidgetCounts(counts)
                    runCatching { AzzaWidget().updateAll(context) }
                }
        }
    }
}
