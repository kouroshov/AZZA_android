package com.azza.core.network.model

import kotlinx.serialization.Serializable

// ---------- search (apps/api/src/search) ----------

@Serializable
data class SearchPageDto<T>(
    val items: List<T>,
    val nextCursor: String? = null,
    val hasMore: Boolean = false,
    val total: Int = 0,
)

@Serializable
data class SearchProductDto(
    val id: String,
    val title: String,
    /** Major units, like the discovery feed. */
    val price: Double,
    val currency: String = "IRR",
    val discountPercentage: Double? = null,
    val primaryImage: String? = null,
    val categoryName: String = "",
    val availability: String = "UNKNOWN",
    val sellerName: String = "",
    val storeName: String? = null,
    val cityName: String? = null,
)

@Serializable
data class SearchUserDto(
    val id: String,
    val username: String,
    val displayName: String = "",
    val avatarUrl: String? = null,
    val isSeller: Boolean = false,
    val followerCount: Int = 0,
)

@Serializable
data class SearchStoreDto(
    val id: String,
    val slug: String,
    val name: String,
    val avatarUrl: String? = null,
    val verificationStatus: String = "",
    val branchCount: Int = 0,
    val followerCount: Int = 0,
)

// ---------- favourites (GET /users/me/favorites) ----------

@Serializable
data class FavoritesPageDto(
    val items: List<FavoriteListItemDto>,
    val nextCursor: String? = null,
    val hasMore: Boolean = false,
)

@Serializable
data class FavoriteListItemDto(val favoritedAt: String, val product: FavoriteProductDto)

@Serializable
data class FavoriteProductDto(
    val id: String,
    val title: String,
    val price: Double,
    val currency: String = "IRR",
    val primaryImage: String? = null,
    val unavailableReason: String? = null,
)

// ---------- notifications ----------

@Serializable
data class NotificationsPageDto(
    val items: List<NotificationDto>,
    val nextCursor: String? = null,
    val hasMore: Boolean = false,
    val unreadCount: Int? = null,
)

@Serializable
data class NotificationDto(
    val id: String,
    val type: String,
    val createdAt: String,
    val read: Boolean,
    val actorName: String? = null,
    val actorUsername: String? = null,
    val subjectTitle: String? = null,
    val context: NotificationContextDto? = null,
    val productId: String? = null,
    /** A storage key (or URL), like every other image apps/api sends. */
    val productImage: String? = null,
    val orderId: String? = null,
    val sellerOrderId: String? = null,
    val ticketId: String? = null,
)

@Serializable
data class NotificationContextDto(val type: String = "PERSONAL", val storeName: String? = null, val storeSlug: String? = null)

@Serializable
data class UnreadCountDto(val unreadCount: Int)

/** Empty body = mark everything read. */
@Serializable
class MarkReadRequest
