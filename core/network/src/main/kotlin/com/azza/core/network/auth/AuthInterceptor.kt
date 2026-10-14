package com.azza.core.network.auth

import com.azza.core.datastore.SessionStore
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Attaches the access token, if there is a session. A public route ignores
 * it; a protected one needs it. When it has expired, [TokenAuthenticator]
 * handles the 401 that comes back.
 */
internal class AuthInterceptor @Inject constructor(
    private val sessionStore: SessionStore,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val builder = original.newBuilder()
            .header("Accept-Language", Locale.getDefault().language)
        if (original.header("Authorization") == null) {
            // OkHttp interceptors are synchronous; DataStore reads are fast after the first.
            val tokens = runBlocking { sessionStore.current() }
            if (tokens != null) builder.header("Authorization", "Bearer ${tokens.accessToken}")
        }
        return chain.proceed(builder.build())
    }
}
