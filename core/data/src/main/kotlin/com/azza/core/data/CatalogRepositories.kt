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
import javax.inject.Inject
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
}

@Singleton
class ProductRepository @Inject constructor(
    private val api: ProductsApi,
    private val media: MediaUrlResolver,
    private val json: Json,
) {
    suspend fun product(id: String, source: String? = null): Outcome<ProductDetail> =
        safeApiCall(json) { api.byId(id, source).data }.map { it.toModel(media) }
}

@Singleton
class UserRepository @Inject constructor(
    private val api: UsersApi,
    private val media: MediaUrlResolver,
    private val json: Json,
) {
    suspend fun me(): Outcome<User> = safeApiCall(json) { api.me().data }.map { it.toModel(media) }
}
