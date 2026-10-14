package com.azza.core.network.auth

import com.azza.core.datastore.SessionStore
import com.azza.core.datastore.SessionTokens
import com.azza.core.network.api.AuthApi
import com.azza.core.network.model.RefreshRequest
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import retrofit2.HttpException

/**
 * On a 401, swaps the refresh token for a new pair once and retries.
 *
 * apps/api's refresh tokens are SINGLE-USE (rotated on every refresh), so
 * two requests that fail together must not both spend the same token: the
 * second would present a token the first already burned and the server
 * would treat it as reuse. Hence the mutex, and the check inside it — if the
 * stored access token is no longer the one this request carried, someone
 * else already refreshed and this request just retries with the new one.
 */
@Singleton
internal class TokenAuthenticator @Inject constructor(
    private val sessionStore: SessionStore,
    // Provider: AuthApi is built on a client that does not include this authenticator.
    private val refreshApi: Provider<AuthApi>,
) : Authenticator {

    private val mutex = Mutex()

    override fun authenticate(route: Route?, response: Response): Request? {
        val sentToken = response.request.header("Authorization")?.removePrefix("Bearer ") ?: return null
        // Retried once already and still 401: give up rather than loop.
        if (response.priorResponse?.code == 401) return null

        val fresh = runBlocking {
            mutex.withLock {
                val current = sessionStore.current() ?: return@withLock null
                if (current.accessToken != sentToken) return@withLock current
                refresh(current)
            }
        } ?: return null

        return response.request.newBuilder()
            .header("Authorization", "Bearer ${fresh.accessToken}")
            .build()
    }

    private suspend fun refresh(current: SessionTokens): SessionTokens? = try {
        val pair = refreshApi.get().refresh(RefreshRequest(current.refreshToken)).data
        SessionTokens(pair.accessToken, pair.refreshToken).also { sessionStore.save(it) }
    } catch (e: HttpException) {
        // The server refused the refresh token (expired, revoked, account
        // suspended — `sessions_valid_after`): this session is over.
        if (e.code() in 400..499) sessionStore.clear()
        null
    } catch (e: Exception) {
        // Network trouble: keep the session, the next request can try again.
        null
    }
}
