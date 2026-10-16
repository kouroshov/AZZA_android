package com.azza.core.model

data class PublicProfile(
    val id: String,
    val username: String,
    val displayName: String,
    val avatarUrl: String?,
    val bio: String?,
    val cityName: String?,
    val followerCount: Int,
)

data class StorePage(
    val id: String,
    val ownerId: String,
    val slug: String,
    val name: String,
    val description: String?,
    val avatarUrl: String?,
    val followerCount: Int,
    /** apps/api's open-now verdict: OPEN, CLOSED or UNKNOWN (no hours recorded). */
    val openStatus: String,
)

/** One square in a profile's grid of posts. */
data class PostTile(
    val id: String,
    val title: String,
    val imageUrl: String?,
    val price: Money,
    val discountPercentage: Double?,
    /** Shown on the owner's own grid only: DRAFT, SOLD, REJECTED (held)… */
    val status: String = "ACTIVE",
)

/** A province or city. [latitude]/[longitude]: its centre, when apps/api knows it (to move a map there). */
data class Area(val id: String, val name: String, val latitude: Double? = null, val longitude: Double? = null)

/** A city-search hit: a city to pick, or a province to open. */
data class PlaceHit(val area: Area, val isProvince: Boolean, val provinceName: String?)
