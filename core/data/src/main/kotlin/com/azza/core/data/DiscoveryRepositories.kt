package com.azza.core.data

import com.azza.core.common.ApplicationScope
import com.azza.core.common.Outcome
import com.azza.core.common.map
import com.azza.core.datastore.AppPreferences
import com.azza.core.datastore.SessionStore
import com.azza.core.model.AppNotification
import com.azza.core.model.Page
import com.azza.core.model.SearchProduct
import com.azza.core.model.SearchSort
import com.azza.core.model.SearchStore
import com.azza.core.model.SearchUser
import com.azza.core.network.MediaUrlResolver
import com.azza.core.network.api.NotificationsApi
import com.azza.core.network.api.SearchApi
import com.azza.core.network.safeApiCall
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

@Singleton
class SearchRepository @Inject constructor(
    private val api: SearchApi,
    private val media: MediaUrlResolver,
    private val json: Json,
) {
    suspend fun products(query: String, sort: SearchSort, cursor: String?): Outcome<Page<SearchProduct>> =
        safeApiCall(json) { api.products(query.ifBlank { null }, sort.apiValue, cursor).data }
            .map { Page(it.items.map { p -> p.toModel(media) }, it.nextCursor, it.hasMore) }

    suspend fun users(query: String, cursor: String?): Outcome<Page<SearchUser>> =
        safeApiCall(json) { api.users(query.ifBlank { null }, cursor).data }
            .map { Page(it.items.map { u -> u.toModel(media) }, it.nextCursor, it.hasMore) }

    suspend fun stores(query: String, cursor: String?): Outcome<Page<SearchStore>> =
        safeApiCall(json) { api.stores(query.ifBlank { null }, cursor).data }
            .map { Page(it.items.map { s -> s.toModel(media) }, it.nextCursor, it.hasMore) }
}

/**
 * In-app notifications (there is no push channel yet — docs/ROADMAP.md
 * phase 4). The badge refreshes when the app comes forward and on
 * navigation, like the web's badge refreshes on route change.
 */
@Singleton
class NotificationsRepository @Inject constructor(
    private val api: NotificationsApi,
    private val media: MediaUrlResolver,
    private val json: Json,
    private val preferences: AppPreferences,
    sessionStore: SessionStore,
    @ApplicationScope scope: CoroutineScope,
) {
    private val _unread = MutableStateFlow(0)
    val unreadCount: StateFlow<Int> = _unread.asStateFlow()

    private var signedIn = false

    init {
        scope.launch {
            sessionStore.isSignedIn.collect { now ->
                signedIn = now
                if (now) {
                    refreshUnreadCount()
                } else {
                    _unread.value = 0
                    // The next account on this phone starts its own line.
                    preferences.setNotifiedUpTo(null)
                }
            }
        }
    }

    suspend fun refreshUnreadCount() {
        if (!signedIn) return
        (safeApiCall(json) { api.unreadCount().data } as? Outcome.Success)?.let { _unread.value = it.value.unreadCount }
    }

    suspend fun page(cursor: String?): Outcome<Page<AppNotification>> =
        safeApiCall(json) { api.list(cursor).data }
            .map { Page(it.items.map { n -> n.toModel(media) }, it.nextCursor, it.hasMore) }

    /**
     * For the background poll: unread notifications newer than the last poll,
     * oldest first. The first poll after sign-in only draws the line, so a
     * phone is never flooded with an account's history.
     *
     * apps/api writes `createdAt` with `Date.toISOString()` — always the same
     * width, always UTC — so the strings order as the instants do.
     */
    suspend fun unseenSinceLastPoll(): Outcome<List<AppNotification>> =
        safeApiCall(json) { api.list(cursor = null, take = POLL_PAGE).data }.map { page ->
            val items = page.items.map { it.toModel(media) }
            val line = preferences.notifiedUpTo.first()
            val newest = items.maxOfOrNull { it.createdAt }
            // "0" orders before every timestamp: "polled once, nothing yet".
            if (newest != null && (line == null || newest > line)) preferences.setNotifiedUpTo(newest)
            else if (line == null) preferences.setNotifiedUpTo("0")
            if (line == null) emptyList() else items.filter { !it.read && it.createdAt > line }.sortedBy { it.createdAt }
        }

    suspend fun markAllRead() {
        (safeApiCall(json) { api.markAllRead().data } as? Outcome.Success)?.let { _unread.value = it.value.unreadCount }
    }
}

/** One page is enough: fifteen minutes rarely brings more, and the shade summarises the rest. */
private const val POLL_PAGE = 20
