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
)
