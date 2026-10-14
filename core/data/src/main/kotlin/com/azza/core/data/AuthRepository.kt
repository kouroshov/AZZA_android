package com.azza.core.data

import com.azza.core.common.Outcome
import com.azza.core.common.map
import com.azza.core.datastore.SessionStore
import com.azza.core.datastore.SessionTokens
import com.azza.core.network.api.AuthApi
import com.azza.core.network.model.LoginRequest
import com.azza.core.network.model.RefreshRequest
import com.azza.core.network.model.RegisterRequest
import com.azza.core.network.safeApiCall
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json

/**
 * Signs in against apps/api — the same accounts as the website, since both
 * read the one `users` / `user_auth` table. apps/api verifies either hash
 * scheme (bcrypt rows from the web's signup, Argon2id rows of its own).
 */
@Singleton
class AuthRepository @Inject constructor(
    private val api: AuthApi,
    private val sessionStore: SessionStore,
    private val json: Json,
) {
    val isSignedIn: Flow<Boolean> = sessionStore.isSignedIn

    suspend fun login(email: String, password: String): Outcome<Unit> =
        safeApiCall(json) { api.login(LoginRequest(email.trim(), password)).data }
            .also { if (it is Outcome.Success) sessionStore.save(SessionTokens(it.value.accessToken, it.value.refreshToken)) }
            .map { }

    suspend fun register(
        email: String,
        username: String,
        password: String,
        firstName: String,
        lastName: String,
    ): Outcome<Unit> =
        safeApiCall(json) {
            api.register(
                RegisterRequest(
                    email = email.trim(),
                    username = username.trim(),
                    password = password,
                    firstName = firstName.trim(),
                    lastName = lastName.trim(),
                ),
            ).data
        }
            .also { if (it is Outcome.Success) sessionStore.save(SessionTokens(it.value.accessToken, it.value.refreshToken)) }
            .map { }

    /** Revokes the refresh token server-side (best effort), then forgets the session locally regardless. */
    suspend fun logout() {
        sessionStore.current()?.let { tokens ->
            safeApiCall(json) { api.logout(RefreshRequest(tokens.refreshToken)) }
        }
        sessionStore.clear()
    }
}
