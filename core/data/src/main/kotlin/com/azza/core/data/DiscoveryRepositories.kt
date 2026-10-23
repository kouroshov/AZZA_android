package com.azza.core.data

import com.azza.core.model.ContextFilter
import com.azza.core.model.SearchFilters
import com.azza.core.network.model.NotificationsPageDto
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
    private val discovery: com.azza.core.network.api.DiscoveryApi,
    private val media: MediaUrlResolver,
    private val json: Json,
) {
    suspend fun products(query: String, sort: SearchSort, cursor: String?, filters: SearchFilters = SearchFilters()): Outcome<Page<SearchProduct>> =
        if (filters.needsFeed) feedProducts(query, sort, cursor, filters) else safeApiCall(json) {
            api.products(
                q = query.ifBlank { null },
                sort = sort.apiValue,
                cursor = cursor,
                categoryId = filters.categoryId,
                condition = filters.condition?.name,
                availability = filters.availability?.name,
                // ✅ (2026-10-23) a typed range is toman; apps/api filters in rial.
                priceMin = filters.priceMin.trim().ifBlank { null }?.let(::tomanToRialText),
                priceMax = filters.priceMax.trim().ifBlank { null }?.let(::tomanToRialText),
            ).data
        }.map { Page(it.items.map { p -> p.toModel(media) }, it.nextCursor, it.hasMore, total = it.total) }

    /**
     * ✅ (2026-10-23) A kind of ad or a category's own fields: search does not
     * index those, so the same list comes from the feed (`GET /explore`),
     * which filters on both — the web's Explore filters.
     */
    private suspend fun feedProducts(query: String, sort: SearchSort, cursor: String?, filters: SearchFilters): Outcome<Page<SearchProduct>> =
        safeApiCall(json) {
            discovery.explore(
                cursor = cursor,
                take = 20,
                categoryId = filters.categoryId,
                query = query.ifBlank { null },
                listingType = filters.listingType,
                attrs = filters.attrs.takeIf { it.isNotEmpty() }?.let(::attrsJson),
                minPrice = filters.priceMin.trim().ifBlank { null }?.let(::tomanToRialText)?.toLongOrNull(),
                maxPrice = filters.priceMax.trim().ifBlank { null }?.let(::tomanToRialText)?.toLongOrNull(),
                condition = filters.condition?.name,
                sort = when (sort) {
                    SearchSort.PRICE_ASC -> "cheapest"
                    SearchSort.PRICE_DESC -> "expensive"
                    else -> null
                },
            ).data
        }.map { page ->
            Page(
                page.items.map { c ->
                    SearchProduct(
                        id = c.id,
                        title = c.title,
                        price = com.azza.core.model.Money.fromMajor(c.price, c.currency),
                        discountPercentage = c.discountPercentage,
                        imageUrl = media.resolve(c.primaryImage),
                        categoryName = c.category.name.fa.ifBlank { c.category.name.en },
                        sellerName = c.postedBy.username,
                        storeName = c.store?.name,
                        cityName = null,
                        availability = runCatching { com.azza.core.model.Availability.valueOf(c.availability) }
                            .getOrDefault(com.azza.core.model.Availability.UNKNOWN),
                        priceNegotiable = c.priceNegotiable,
                    )
                },
                page.nextCursor,
                page.hasMore,
            )
        }

    suspend fun users(query: String, cursor: String?): Outcome<Page<SearchUser>> =
        safeApiCall(json) { api.users(query.ifBlank { null }, cursor).data }
            .map { Page(it.items.map { u -> u.toModel(media) }, it.nextCursor, it.hasMore, total = it.total) }

    suspend fun stores(query: String, cursor: String?): Outcome<Page<SearchStore>> =
        safeApiCall(json) { api.stores(query.ifBlank { null }, cursor).data }
            .map { Page(it.items.map { s -> s.toModel(media) }, it.nextCursor, it.hasMore, total = it.total) }
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
    private val offline: OfflineCache,
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

    suspend fun page(cursor: String?, filter: ContextFilter = ContextFilter.ALL): Outcome<Page<AppNotification>> {
        val fetch = suspend { safeApiCall(json) { api.list(cursor, context = filter.context.takeIf { it != "all" }, storeId = filter.storeId).data } }
        val result = if (cursor == null && filter == ContextFilter.ALL) offline.load("notifications", personal = true, NotificationsPageDto.serializer(), fetch = fetch) else fetch().map { Fresh(it) }
        return result.map { (p, savedAt) -> Page(p.items.map { n -> n.toModel(media) }, p.nextCursor, p.hasMore, savedAt) }
    }

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

/** "۱۲۰۰" or "1,200" toman → "12000" rial; anything unreadable is passed on unchanged. */
/** apps/api's `attrs` JSON object (attribute-filter.ts). */
fun attrsJson(attrs: Map<String, com.azza.core.model.AttrFilter>): String =
    kotlinx.serialization.json.buildJsonObject {
        attrs.forEach { (slug, f) ->
            when (f) {
                is com.azza.core.model.AttrFilter.Options -> put(slug, kotlinx.serialization.json.JsonArray(f.values.map { kotlinx.serialization.json.JsonPrimitive(it) }))
                com.azza.core.model.AttrFilter.Yes -> put(slug, kotlinx.serialization.json.JsonPrimitive(true))
                is com.azza.core.model.AttrFilter.Range -> put(
                    slug,
                    kotlinx.serialization.json.buildJsonObject {
                        f.min?.let { put("min", kotlinx.serialization.json.JsonPrimitive(it)) }
                        f.max?.let { put("max", kotlinx.serialization.json.JsonPrimitive(it)) }
                    },
                )
            }
        }
    }.toString()

/** The reverse of [attrsJson], for a saved search; anything unknown is dropped. */
fun parseAttrsJson(text: String?): Map<String, com.azza.core.model.AttrFilter> {
    val obj = text?.let { runCatching { Json.parseToJsonElement(it) as? kotlinx.serialization.json.JsonObject }.getOrNull() } ?: return emptyMap()
    return obj.mapNotNull { (slug, v) ->
        val filter: com.azza.core.model.AttrFilter? = when (v) {
            is kotlinx.serialization.json.JsonArray -> com.azza.core.model.AttrFilter.Options(
                v.mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.takeIf { p -> p.isString }?.content },
            ).takeIf { it.values.isNotEmpty() }
            is kotlinx.serialization.json.JsonPrimitive -> if (v.content == "true") com.azza.core.model.AttrFilter.Yes else null
            is kotlinx.serialization.json.JsonObject -> com.azza.core.model.AttrFilter.Range(
                (v["min"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toDoubleOrNull(),
                (v["max"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toDoubleOrNull(),
            ).takeIf { it.min != null || it.max != null }
            else -> null
        }
        filter?.let { slug to it }
    }.toMap()
}

private fun tomanToRialText(toman: String): String {
    val n = com.azza.core.model.SchemaRules.toLatinDigits(toman).replace(Regex("[,٬]"), "").toDoubleOrNull() ?: return toman
    return com.azza.core.model.Money.tomanToRial(n).toLong().toString()
}
