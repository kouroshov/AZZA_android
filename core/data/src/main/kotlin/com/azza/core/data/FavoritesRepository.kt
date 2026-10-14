package com.azza.core.data

import com.azza.core.common.ApplicationScope
import com.azza.core.common.Outcome
import com.azza.core.common.map
import com.azza.core.datastore.SessionStore
import com.azza.core.model.FavoriteItem
import com.azza.core.model.Page
import com.azza.core.network.MediaUrlResolver
import com.azza.core.network.api.SocialApi
import com.azza.core.network.safeApiCall
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/**
 * Which products the signed-in person has favourited, held in memory so a
 * heart can be drawn without a request per product. Loaded once per sign-in
 * (the web reads the same set server-side per page render).
 */
@Singleton
class FavoritesRepository @Inject constructor(
    private val api: SocialApi,
    private val media: MediaUrlResolver,
    private val json: Json,
    sessionStore: SessionStore,
    @ApplicationScope scope: CoroutineScope,
) {
    private val _ids = MutableStateFlow<Set<String>>(emptySet())
    val favoriteIds: StateFlow<Set<String>> = _ids.asStateFlow()

    init {
        scope.launch {
            sessionStore.isSignedIn.collect { signedIn -> if (signedIn) loadAllIds() else _ids.value = emptySet() }
        }
    }

    /**
     * Optimistic: the heart changes at once and is put back if apps/api
     * refuses. Both directions are idempotent on the server.
     */
    suspend fun setFavorite(productId: String, favorite: Boolean): Outcome<Unit> {
        _ids.update { if (favorite) it + productId else it - productId }
        val result = safeApiCall(json) { if (favorite) api.favorite(productId) else api.unfavorite(productId) }
        if (result is Outcome.Failure) _ids.update { if (favorite) it - productId else it + productId }
        return result
    }

    suspend fun page(cursor: String?): Outcome<Page<FavoriteItem>> =
        safeApiCall(json) { api.favorites(cursor, take = 20).data }
            .map { Page(it.items.map { item -> item.toModel(media) }, it.nextCursor, it.hasMore) }

    private suspend fun loadAllIds() {
        val ids = mutableSetOf<String>()
        var cursor: String? = null
        // Bounded: a person with thousands of favourites still gets the first 1,200 marked.
        for (pageNumber in 0 until 20) {
            val from = cursor
            val result = safeApiCall(json) { api.favorites(from, take = 60).data }
            val page = (result as? Outcome.Success)?.value ?: break
            page.items.mapTo(ids) { it.product.id }
            cursor = page.nextCursor
            if (!page.hasMore || cursor == null) break
        }
        _ids.value = ids
    }
}
