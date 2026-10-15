package com.azza.core.data

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
) {
    /** The Global Explore feed — the web's home page. Keyset-paged: pass the previous page's cursor. */
    suspend fun explore(cursor: String?, pageSize: Int = 20): Outcome<Page<ProductCard>> =
        safeApiCall(json) { api.explore(cursor = cursor, take = pageSize).data }
            .map { page -> Page(page.items.map { it.toModel(media) }, page.nextCursor, page.hasMore) }

    /** Posts from the people and stores the signed-in person follows (the web's /following). */
    suspend fun following(cursor: String?, pageSize: Int = 20): Outcome<Page<ProductCard>> =
        safeApiCall(json) { api.following(cursor, pageSize).data }
            .map { page -> Page(page.items.map { it.toModel(media) }, page.nextCursor, page.hasMore) }

    /** One or more cities' local feed, by city id. */
    suspend fun cities(cityIds: List<String>, cursor: String?, pageSize: Int = 20): Outcome<Page<ProductCard>> =
        safeApiCall(json) { api.cities(cityIds.joinToString(","), cursor, pageSize).data }
            .map { page -> Page(page.items.map { it.toModel(media) }, page.nextCursor, page.hasMore) }
}

@Singleton
class ProductRepository @Inject constructor(
    private val api: ProductsApi,
    private val media: MediaUrlResolver,
    private val json: Json,
) {
    suspend fun product(id: String, source: String? = null): Outcome<ProductDetail> =
        safeApiCall(json) { api.byId(id, source).data }.map { it.toModel(media) }

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
