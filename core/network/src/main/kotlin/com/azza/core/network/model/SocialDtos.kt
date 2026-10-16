package com.azza.core.network.model

import kotlinx.serialization.Serializable

// ---------- public profiles (GET /users/by-username/:username) ----------

@Serializable
data class PublicProfileDto(
    val id: String,
    val username: String,
    val displayName: String = "",
    val avatarUrl: String? = null,
    val bio: String? = null,
    val cityName: String? = null,
    val isSeller: Boolean = false,
    val followerCount: Int = 0,
    val postCount: Int = 0,
)

// ---------- stores (GET /stores/:idOrSlug) ----------

@Serializable
data class StoreDto(
    val id: String,
    val ownerId: String,
    val slug: String,
    val name: String,
    val description: String? = null,
    val followerCount: Int = 0,
    val avatarUrl: String? = null,
    val openStatus: String = "UNKNOWN",
    val branchPhone: String? = null,
    val postCount: Int = 0,
    val owner: StoreOwnerDto? = null,
    val hours: List<com.azza.core.network.api.HoursDto> = emptyList(),
    val occupation: com.azza.core.network.api.StoreOccupationDto? = null,
    val location: com.azza.core.network.api.LocationDto? = null,
)

@Serializable
data class StoreOwnerDto(val username: String, val displayName: String = "")

// ---------- product lists (GET /products) — Prisma rows, keyset-paged ----------

@Serializable
data class ProductsPageDto(
    val items: List<ProductDto>,
    val nextCursor: String? = null,
    val hasMore: Boolean = false,
)

// ---------- follow (apps/api/src/follow) ----------

/** `GET /users/me/following` — everyone and every store the caller follows (capped server-side). */
@Serializable
data class FollowingSetDto(
    val users: List<IdDto> = emptyList(),
    val stores: List<IdDto> = emptyList(),
)

@Serializable
data class IdDto(val id: String)

// ---------- geography (GET /geography/provinces[/:id/cities]) ----------

@Serializable
data class AreaDto(val id: String, val name: String, val type: String = "", val center: LatLngDto? = null)

@Serializable
data class PlaceHitDto(
    val id: String,
    val name: String,
    val type: String = "",
    val province: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val source: String = "local",
)

@Serializable
data class LatLngDto(val lat: Double, val lng: Double)
