package com.azza.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.preferencesStore: DataStore<Preferences> by preferencesDataStore(name = "preferences")

data class ChosenCity(val id: String, val name: String)

/** Per-device choices that are not secrets (the session is in [SessionStore]). */
@Singleton
class AppPreferences @Inject constructor(@ApplicationContext context: Context) {
    private val store = context.preferencesStore

    /** The Explore page's City tab. */
    val city: Flow<ChosenCity?> = store.data.map { prefs ->
        val id = prefs[CITY_ID] ?: return@map null
        ChosenCity(id, prefs[CITY_NAME].orEmpty())
    }

    /** "DARK" / "LIGHT" / "SYSTEM"; null = never chosen (dark, like the web). */
    val theme: Flow<String?> = store.data.map { it[THEME] }

    suspend fun setTheme(value: String) {
        store.edit { it[THEME] = value }
    }

    /** Show new notifications in the phone's notification shade (the background poll). */
    val phoneNotifications: Flow<Boolean> = store.data.map { it[PHONE_NOTIFICATIONS] ?: true }

    suspend fun setPhoneNotifications(on: Boolean) {
        store.edit { it[PHONE_NOTIFICATIONS] = on }
    }

    /**
     * The newest notification time the poll has already seen (apps/api's
     * ISO-8601 `createdAt`); null = not yet polled since sign-in.
     */
    val notifiedUpTo: Flow<String?> = store.data.map { it[NOTIFIED_UP_TO] }

    suspend fun setNotifiedUpTo(value: String?) {
        store.edit { if (value == null) it.remove(NOTIFIED_UP_TO) else it[NOTIFIED_UP_TO] = value }
    }

    /** Android 13+'s notification permission is asked once; after that it lives in the phone's settings. */
    val notificationPermissionAsked: Flow<Boolean> = store.data.map { it[PERMISSION_ASKED] ?: false }

    suspend fun setNotificationPermissionAsked() {
        store.edit { it[PERMISSION_ASKED] = true }
    }

    /** The options chosen for basket lines (BasketOptions' JSON); null = none. */
    val basketOptions: Flow<String?> = store.data.map { it[BASKET_OPTIONS] }

    suspend fun setBasketOptions(value: String?) {
        store.edit { if (value == null) it.remove(BASKET_OPTIONS) else it[BASKET_OPTIONS] = value }
    }

    /** Ask for the fingerprint (or the phone's screen lock) when the app is opened. Off by default. */
    val appLock: Flow<Boolean> = store.data.map { it[APP_LOCK] ?: false }

    suspend fun setAppLock(on: Boolean) {
        store.edit { it[APP_LOCK] = on }
    }

    /** What the home-screen widget shows: kept current by the app, read by the widget. */
    data class WidgetCounts(val signedIn: Boolean = false, val unread: Int = 0, val basket: Int = 0)

    val widgetCounts: Flow<WidgetCounts> = store.data.map {
        WidgetCounts(it[WIDGET_SIGNED_IN] ?: false, it[WIDGET_UNREAD] ?: 0, it[WIDGET_BASKET] ?: 0)
    }

    suspend fun setWidgetCounts(counts: WidgetCounts) {
        store.edit {
            it[WIDGET_SIGNED_IN] = counts.signedIn
            it[WIDGET_UNREAD] = counts.unread
            it[WIDGET_BASKET] = counts.basket
        }
    }

    suspend fun setCity(city: ChosenCity?) {
        store.edit { prefs ->
            if (city == null) {
                prefs.remove(CITY_ID)
                prefs.remove(CITY_NAME)
            } else {
                prefs[CITY_ID] = city.id
                prefs[CITY_NAME] = city.name
            }
        }
    }

    private companion object {
        val CITY_ID = stringPreferencesKey("explore_city_id")
        val CITY_NAME = stringPreferencesKey("explore_city_name")
        val THEME = stringPreferencesKey("theme")
        val PHONE_NOTIFICATIONS = booleanPreferencesKey("phone_notifications")
        val NOTIFIED_UP_TO = stringPreferencesKey("notified_up_to")
        val PERMISSION_ASKED = booleanPreferencesKey("notification_permission_asked")
        val WIDGET_SIGNED_IN = booleanPreferencesKey("widget_signed_in")
        val APP_LOCK = booleanPreferencesKey("app_lock")
        val BASKET_OPTIONS = stringPreferencesKey("basket_options")
        val WIDGET_UNREAD = intPreferencesKey("widget_unread")
        val WIDGET_BASKET = intPreferencesKey("widget_basket")
    }
}
