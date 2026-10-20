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
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.preferencesStore: DataStore<Preferences> by preferencesDataStore(name = "preferences")

data class ChosenCity(val id: String, val name: String)

/** Per-device choices that are not secrets (the session is in [SessionStore]). */
@Singleton
class AppPreferences @Inject constructor(@ApplicationContext context: Context) {
    private val store = context.preferencesStore

    /**
     * The cities the Explore page's City tab shows, in the order chosen
     * (the web's multi-city selector). A device that remembered one city
     * before this keeps it.
     */
    val cities: Flow<List<ChosenCity>> = store.data.map { prefs ->
        prefs[CITIES]?.let(::decodeCities) ?: prefs[CITY_ID]?.let { listOf(ChosenCity(it, prefs[CITY_NAME].orEmpty())) }.orEmpty()
    }

    suspend fun setCities(cities: List<ChosenCity>) {
        store.edit { prefs ->
            prefs.remove(CITY_ID)
            prefs.remove(CITY_NAME)
            prefs[CITIES] = encodeCities(cities)
        }
    }

    /** "Previous selections" in the city picker: newest first, at most 10 (the web keeps 10 too). */
    val recentCities: Flow<List<ChosenCity>> = store.data.map { prefs -> prefs[RECENT_CITIES]?.let(::decodeCities).orEmpty() }

    suspend fun recordRecentCities(confirmed: List<ChosenCity>) {
        store.edit { prefs ->
            val existing = prefs[RECENT_CITIES]?.let(::decodeCities).orEmpty()
            val merged = (confirmed + existing.filterNot { e -> confirmed.any { it.id == e.id } }).take(10)
            prefs[RECENT_CITIES] = encodeCities(merged)
        }
    }

    private fun encodeCities(cities: List<ChosenCity>) =
        cities.joinToString(LINE) { it.id + FIELD + it.name.replace(FIELD, " ").replace(LINE, " ") }

    private fun decodeCities(raw: String): List<ChosenCity> =
        raw.split(LINE).mapNotNull { line -> line.split(FIELD, limit = 2).takeIf { it.size == 2 && it[0].isNotBlank() }?.let { ChosenCity(it[0], it[1]) } }

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

    /**
     * This phone's id for the sales register (POS-03): made once, kept for
     * the life of the install — across sign-outs, so the server can tell
     * which phone a sale came from. Not tied to any hardware identifier.
     */
    suspend fun posDeviceId(): String {
        store.data.first()[POS_DEVICE_ID]?.let { return it }
        var id = ""
        store.edit { prefs -> id = prefs[POS_DEVICE_ID] ?: UUID.randomUUID().toString().also { prefs[POS_DEVICE_ID] = it } }
        return id
    }

    /**
     * The stores the signed-in person runs, as (id, name, main branch id) —
     * saved at sign-in (POS-05) so the Register tab and the app shortcut
     * know, offline too, whether to show. Empty when signed out.
     */
    val posStores: Flow<List<Triple<String, String, String>>> = store.data.map { prefs ->
        prefs[POS_STORES].orEmpty().split(LINE).mapNotNull { row ->
            row.split(FIELD).takeIf { it.size == 3 }?.let { Triple(it[0], it[1], it[2]) }
        }
    }

    suspend fun setPosStores(stores: List<Triple<String, String, String>>) {
        store.edit { prefs ->
            prefs[POS_STORES] = stores.joinToString(LINE) { (id, name, branch) ->
                id + FIELD + name.replace(LINE, " ").replace(FIELD, " ") + FIELD + branch
            }
        }
    }

    /** Where the register was last used, as (storeId, branchId). */
    val posLastRegister: Flow<Pair<String, String>?> = store.data.map { prefs ->
        prefs[POS_LAST_REGISTER]?.split(FIELD)?.takeIf { it.size == 2 }?.let { it[0] to it[1] }
    }

    suspend fun setPosLastRegister(storeId: String, branchId: String) {
        store.edit { it[POS_LAST_REGISTER] = storeId + FIELD + branchId }
    }

    /** The branch the inventory / register last showed for [storeId] (POS-04). */
    fun posLastBranch(storeId: String): Flow<String?> = store.data.map { it[stringPreferencesKey("pos_branch_$storeId")] }

    suspend fun setPosLastBranch(storeId: String, branchId: String) {
        store.edit { it[stringPreferencesKey("pos_branch_$storeId")] = branchId }
    }

    /** [storeId]'s branches as (id, name) when last fetched — so the branch name shows offline. */
    fun posBranches(storeId: String): Flow<List<Pair<String, String>>> = store.data.map { prefs ->
        prefs[stringPreferencesKey("pos_branches_$storeId")].orEmpty().split(LINE).mapNotNull { row ->
            row.split(FIELD).takeIf { it.size == 2 }?.let { it[0] to it[1] }
        }
    }

    suspend fun setPosBranches(storeId: String, branches: List<Pair<String, String>>) {
        store.edit { prefs ->
            prefs[stringPreferencesKey("pos_branches_$storeId")] = branches.joinToString(LINE) { (id, name) ->
                id + FIELD + name.replace(LINE, " ").replace(FIELD, " ")
            }
        }
    }

    private companion object {
        val POS_DEVICE_ID = stringPreferencesKey("pos_device_id")
        val POS_STORES = stringPreferencesKey("pos_stores")
        val POS_LAST_REGISTER = stringPreferencesKey("pos_last_register")
        val CITY_ID = stringPreferencesKey("explore_city_id")
        val CITY_NAME = stringPreferencesKey("explore_city_name")
        val CITIES = stringPreferencesKey("explore_cities")
        val RECENT_CITIES = stringPreferencesKey("explore_recent_cities")
        // Record / unit separators: never part of a city's name.
        const val LINE = "\u001E"
        const val FIELD = "\u001F"
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
