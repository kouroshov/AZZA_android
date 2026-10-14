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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** apps/api's token pair. Both are opaque to the app. */
data class SessionTokens(val accessToken: String, val refreshToken: String)

private val Context.sessionDataStore: DataStore<Preferences> by preferencesDataStore(name = "session")

/**
 * The one place the signed-in session is kept. Both tokens are stored
 * encrypted under a Keystore key (see [KeystoreCipher]); nothing else in the
 * app writes them.
 */
@Singleton
class SessionStore @Inject constructor(
    @ApplicationContext context: Context,
    private val cipher: KeystoreCipher,
) {
    private val store = context.sessionDataStore

    val tokens: Flow<SessionTokens?> = store.data
        .map { prefs ->
            val access = prefs[ACCESS]?.let(cipher::decrypt)
            val refresh = prefs[REFRESH]?.let(cipher::decrypt)
            if (access != null && refresh != null) SessionTokens(access, refresh) else null
        }
        .distinctUntilChanged()

    val isSignedIn: Flow<Boolean> = tokens.map { it != null }.distinctUntilChanged()

    suspend fun current(): SessionTokens? = tokens.first()

    suspend fun save(tokens: SessionTokens) {
        store.edit { prefs ->
            prefs[ACCESS] = cipher.encrypt(tokens.accessToken)
            prefs[REFRESH] = cipher.encrypt(tokens.refreshToken)
        }
    }

    suspend fun clear() {
        store.edit { it.clear() }
    }

    private companion object {
        val ACCESS = stringPreferencesKey("access")
        val REFRESH = stringPreferencesKey("refresh")
    }
}
