package com.azza.core.data

import com.azza.core.network.api.OwnListingsApi
import com.azza.core.network.model.FeedPageDto
import com.azza.core.network.model.ProductDto
import com.azza.core.common.Outcome
import com.azza.core.common.map
import com.azza.core.model.Page
import com.azza.core.model.ProductCard
import com.azza.core.model.ProductDetail
import com.azza.core.model.User
import com.azza.core.network.MediaUrlResolver
import com.azza.core.network.api.DiscoveryApi
import com.azza.core.network.api.ProductsApi
import com.azza.core.network.api.UsersApi
import com.azza.core.network.safeApiCall
import com.azza.core.common.ApplicationScope
import com.azza.core.datastore.SessionStore
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Singleton
import kotlinx.serialization.json.Json

@Singleton
class FeedRepository @Inject constructor(
    private val api: DiscoveryApi,
    private val media: MediaUrlResolver,
    private val json: Json,
    private val offline: OfflineCache,
) {
    /** The Global Explore feed — the web's home page. Keyset-paged: pass the previous page's cursor. */
    suspend fun explore(cursor: String?, pageSize: Int = 20): Outcome<Page<ProductCard>> =
        firstPageSaved("explore", personal = false, cursor) { safeApiCall(json) { api.explore(cursor = cursor, take = pageSize).data } }

    /** Posts from the people and stores the signed-in person follows (the web's /following). */
    suspend fun following(cursor: String?, pageSize: Int = 20): Outcome<Page<ProductCard>> =
        firstPageSaved("following", personal = true, cursor) { safeApiCall(json) { api.following(cursor, pageSize).data } }

    /** One or more cities' local feed, by city id. */
    suspend fun cities(cityIds: List<String>, cursor: String?, pageSize: Int = 20): Outcome<Page<ProductCard>> =
        safeApiCall(json) { api.cities(cityIds.joinToString(","), cursor, pageSize).data }
            .map { page -> Page(page.items.map { it.toModel(media) }, page.nextCursor, page.hasMore) }

    /** The first page goes through the offline cache; later pages need the network anyway. */
    private suspend fun firstPageSaved(
        key: String,
        personal: Boolean,
        cursor: String?,
        fetch: suspend () -> Outcome<FeedPageDto>,
    ): Outcome<Page<ProductCard>> {
        val result = if (cursor == null) offline.load(key, personal, FeedPageDto.serializer(), fetch = fetch) else fetch().map { Fresh(it) }
        return result.map { (page, savedAt) -> Page(page.items.map { it.toModel(media) }, page.nextCursor, page.hasMore, savedAt) }
    }
}

@Singleton
class ProductRepository @Inject constructor(
    private val api: ProductsApi,
    private val media: MediaUrlResolver,
    private val json: Json,
    private val offline: OfflineCache,
    private val own: OwnListingsApi,
) {
    /** One of the caller's own listings in any state — a hold's cause and reason included (GET /products/mine/:id). */
    suspend fun ownProduct(id: String): Outcome<ProductDetail> = safeApiCall(json) { own.byId(id).data }.map { it.toModel(media) }

    /** The last [OPENED_KEPT] listings opened stay readable offline (personal: they are a history). */
    suspend fun product(id: String, source: String? = null): Outcome<ProductDetail> =
        offline.load("product:$id", personal = true, ProductDto.serializer(), keepNewest = OPENED_KEPT) {
            safeApiCall(json) { api.byId(id, source).data }
        }.map { it.value.toModel(media) }

    /** The listing a scanned label belongs to; 404 PRODUCT_NOT_FOUND when none (or none visible). */
    suspend fun idByBarcode(code: String): Outcome<String> =
        safeApiCall(json) { api.byBarcode(code).data }.map { it.productId }
}

@Singleton
class UserRepository @Inject constructor(
    private val api: UsersApi,
    private val media: MediaUrlResolver,
    private val json: Json,
    sessionStore: SessionStore,
    @ApplicationScope scope: CoroutineScope,
) {
    private val _me = MutableStateFlow<User?>(null)

    /** The signed-in person, once known; null when signed out. Lets a profile tell "this is me". */
    val currentUser: StateFlow<User?> = _me.asStateFlow()

    init {
        scope.launch {
            sessionStore.isSignedIn.collect { signedIn -> if (signedIn) me() else _me.value = null }
        }
    }

    suspend fun me(): Outcome<User> =
        safeApiCall(json) { api.me().data }.map { it.toModel(media) }.also { if (it is Outcome.Success) _me.value = it.value }
}

private const val OPENED_KEPT = 100
