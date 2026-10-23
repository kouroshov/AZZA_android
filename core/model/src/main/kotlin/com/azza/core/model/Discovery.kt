package com.azza.core.model

data class SearchProduct(
    val id: String,
    val title: String,
    val price: Money,
    val discountPercentage: Double?,
    val imageUrl: String?,
    val categoryName: String,
    val sellerName: String,
    val storeName: String?,
    val cityName: String?,
    val availability: Availability,
    /** ✅ (2026-10-23) «توافقی». */
    val priceNegotiable: Boolean = false,
)

data class SearchUser(
    val id: String,
    val username: String,
    val displayName: String,
    val avatarUrl: String?,
    val isSeller: Boolean,
    val followerCount: Int,
)

data class SearchStore(
    val id: String,
    val slug: String,
    val name: String,
    val avatarUrl: String?,
    val verified: Boolean,
    val branchCount: Int,
    val followerCount: Int,
)

enum class SearchSort(val apiValue: String) {
    RELEVANCE("relevance"), NEWEST("newest"), PRICE_ASC("price_asc"), PRICE_DESC("price_desc"), POPULAR("popular")
}

/**
 * The product search's filters (the web's Filters panel). Prices are major
 * units as typed; blank = no bound.
 */
data class SearchFilters(
    val categoryId: String? = null,
    val categoryName: LocalizedText? = null,
    val condition: ItemCondition? = null,
    val availability: Availability? = null,
    val priceMin: String = "",
    val priceMax: String = "",
    /** ✅ (2026-10-23) The kind of ad (SALE, RENT, WANTED…), with its name for the chip. */
    val listingType: String? = null,
    val listingTypeName: LocalizedText? = null,
    /** ✅ (2026-10-23) The category's own fields (rooms, area, year…), by slug. */
    val attrs: Map<String, AttrFilter> = emptyMap(),
) {
    val count: Int
        get() = listOf(categoryId != null, condition != null, availability != null, priceMin.isNotBlank() || priceMax.isNotBlank(), listingType != null)
            .count { it } + attrs.size
    val isEmpty: Boolean get() = count == 0

    /** Search has no field or kind-of-ad filters: these run on the feed instead (apps/api's `attrs`). */
    val needsFeed: Boolean get() = listingType != null || attrs.isNotEmpty()
}

/**
 * ✅ (2026-10-23) One category-field filter, the shape apps/api's
 * attribute-filter.ts reads: options (any of), a yes, or a range. A MONEY
 * range is in rial major units — the sheet converts what is typed in toman.
 */
sealed interface AttrFilter {
    data class Options(val values: List<String>) : AttrFilter
    data object Yes : AttrFilter
    data class Range(val min: Double? = null, val max: Double? = null) : AttrFilter
}

enum class FavoriteUnavailable { DELETED, UNPUBLISHED, OUT_OF_STOCK }

data class FavoriteItem(
    val productId: String,
    val title: String,
    val price: Money,
    val imageUrl: String?,
    val unavailable: FavoriteUnavailable?,
)

data class AppNotification(
    val id: String,
    /** apps/api's NotificationType, kept as a string so a new type never breaks an installed app. */
    val type: String,
    val createdAt: String,
    val read: Boolean,
    val actorName: String?,
    val actorUsername: String?,
    val subjectTitle: String?,
    val storeName: String?,
    val productId: String?,
    val productImageUrl: String?,
    val orderId: String?,
    val sellerOrderId: String?,
    val ticketId: String? = null,
    val storeSlug: String? = null,
)
