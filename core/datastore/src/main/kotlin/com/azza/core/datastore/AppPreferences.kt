package com.azza.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
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
    }
}
