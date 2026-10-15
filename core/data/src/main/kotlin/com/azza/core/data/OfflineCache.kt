package com.azza.core.data

import com.azza.core.common.AppError
import com.azza.core.common.ApplicationScope
import com.azza.core.common.Outcome
import com.azza.core.database.CachedResponse
import com.azza.core.database.CachedResponseDao
import com.azza.core.datastore.SessionStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

/** An answer, and — when it was the saved copy because the phone is offline — when it was saved. */
data class Fresh<T>(val value: T, val savedAt: Long? = null)

/**
 * What a screen shows offline (phase 4d). The FIRST page of the main lists
 * and the listings someone opened are saved as apps/api sent them; when a
 * later request gets no answer at all (AppError.Network), the saved copy is
 * returned instead, marked with [Fresh.savedAt]. Any answer from the server
 * — an error included — wins over the copy: offline is the only case.
 *
 * Personal answers (orders, notifications, the following feed) are deleted
 * when the account signs out. The cache is best-effort: a database error
 * never fails the request it was saving.
 */
@Singleton
class OfflineCache(
    private val dao: CachedResponseDao,
    private val json: Json,
    signedIn: Flow<Boolean>,
    scope: CoroutineScope,
    private val now: () -> Long = System::currentTimeMillis,
) {
    @Inject constructor(
        dao: CachedResponseDao,
        json: Json,
        sessionStore: SessionStore,
        @ApplicationScope scope: CoroutineScope,
    ) : this(dao, json, sessionStore.isSignedIn, scope)

    init {
        scope.launch {
            // drop(1): the state at start-up is not a sign-out.
            signedIn.drop(1).collect { if (!it) runCatching { dao.deletePersonal() } }
        }
    }

    suspend fun <T> load(
        key: String,
        personal: Boolean,
        serializer: KSerializer<T>,
        keepNewest: Int? = null,
        fetch: suspend () -> Outcome<T>,
    ): Outcome<Fresh<T>> = when (val result = fetch()) {
        is Outcome.Success -> {
            runCatching {
                dao.put(CachedResponse(key, json.encodeToString(serializer, result.value), now(), personal))
                keepNewest?.let { dao.trim(key.substringBefore(':') + ":", it) }
            }
            Outcome.Success(Fresh(result.value))
        }
        is Outcome.Failure -> {
            val saved = if (result.error == AppError.Network) runCatching { dao.get(key) }.getOrNull() else null
            val value = saved?.let { runCatching { json.decodeFromString(serializer, it.json) }.getOrNull() }
            if (saved != null && value != null) Outcome.Success(Fresh(value, saved.savedAt)) else result
        }
    }
}
