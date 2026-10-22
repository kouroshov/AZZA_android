package com.azza.core.network.api

import com.azza.core.network.model.ApiEnvelope
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

@Serializable
data class MapBranchesPageDto(val items: List<BranchMarkerDto> = emptyList(), val nextCursor: String? = null, val hasMore: Boolean = false)

@Serializable
data class BranchMarkerDto(
    val branchId: String,
    val storeId: String,
    val storeName: String,
    val storeSlug: String,
    val branchName: String = "",
    val address: String? = null,
    val latitude: Double,
    val longitude: Double,
    val logo: String? = null,
    val availableProductCount: Int = 0,
    val status: String = "UNKNOWN",
)

@Serializable
data class BranchPreviewDto(
    val storeId: String,
    val storeSlug: String,
    val storeName: String,
    val verificationStatus: String = "",
    val branchId: String,
    val branchName: String = "",
    val logo: String? = null,
    val address: String = "",
    val phone: String? = null,
    val openStatus: String = "UNKNOWN",
    val distanceKm: Double? = null,
    val productCount: Int = 0,
    val products: List<BranchPreviewProductDto> = emptyList(),
)

@Serializable
data class BranchPreviewProductDto(
    val id: String,
    val title: String,
    /** Major units, like the feeds. */
    val price: Double,
    val currency: String = "IRR",
    val primaryImage: String? = null,
)

// ✅ (2026-10-22) The real-estate map (apps/api parcels module).
@kotlinx.serialization.Serializable
data class ParcelMapPageDto(val items: List<ParcelMapItemDto> = emptyList(), val truncated: Boolean = false)

@kotlinx.serialization.Serializable
data class ParcelMapItemDto(
    val kind: String,
    val lat: Double,
    val lng: Double,
    /** [lng, lat] pairs, not closed; null for a point. */
    val ring: List<List<Double>>? = null,
    val deal: String? = null,
    val listings: List<ParcelListingDto> = emptyList(),
)

@kotlinx.serialization.Serializable
data class ParcelListingDto(
    val productId: String,
    val title: String,
    @kotlinx.serialization.Serializable(with = com.azza.core.network.model.FlexibleLongSerializer::class)
    val priceAmountMinor: Long,
    val currency: String = "IRR",
    val deal: String? = null,
    val areaM2: Double? = null,
    val image: ParcelImageDto? = null,
    val owner: ParcelOwnerDto,
)

@kotlinx.serialization.Serializable
data class ParcelImageDto(val url: String? = null, val storageKey: String)

@kotlinx.serialization.Serializable
data class ParcelOwnerDto(
    val kind: String,
    val slug: String? = null,
    val username: String? = null,
    val name: String,
    val avatarMediaKey: String? = null,
)

@kotlinx.serialization.Serializable
data class ParcelRuleDto(val allowed: Boolean = false, val boundary: Boolean = false, val deal: String? = null)

@kotlinx.serialization.Serializable
data class SimilarParcelsRequest(val ring: List<List<Double>>, val excludeProductId: String? = null)

@kotlinx.serialization.Serializable
data class SimilarParcelDto(val ring: List<List<Double>>, val overlap: Double, val areaM2: Double)

interface MapApi {
    /** Real-estate listings with a point or boundary inside the viewport, grouped by land. Public. */
    @GET("map/parcels")
    suspend fun parcels(
        @Query("north") north: Double,
        @Query("south") south: Double,
        @Query("east") east: Double,
        @Query("west") west: Double,
        /** Comma-separated deals; null = all. */
        @Query("deal") deal: String? = null,
    ): ApiEnvelope<ParcelMapPageDto>

    /** Whether a category goes on the real-estate map, and with a boundary. Public. */
    @GET("map/parcels/rules")
    suspend fun parcelRule(@Query("categoryId") categoryId: String): ApiEnvelope<ParcelRuleDto>

    /** Boundaries already on the map that cover nearly the same land. Signed in. */
    @retrofit2.http.POST("map/parcels/similar")
    suspend fun similarParcels(@retrofit2.http.Body body: SimilarParcelsRequest): ApiEnvelope<List<SimilarParcelDto>>

    /** Branches inside the viewport (the bounds are validated together by apps/api). Public. */
    @GET("map/branches")
    suspend fun branches(
        @Query("north") north: Double,
        @Query("south") south: Double,
        @Query("east") east: Double,
        @Query("west") west: Double,
        @Query("take") take: Int = 200,
        @Query("categoryId") categoryId: String? = null,
    ): ApiEnvelope<MapBranchesPageDto>

    /** The store preview a marker opens. Public. */
    @GET("map/branches/{branchId}")
    suspend fun preview(
        @Path("branchId") branchId: String,
        /** The viewer's position, when known: apps/api answers the distance to it. */
        @Query("lat") lat: Double? = null,
        @Query("lng") lng: Double? = null,
    ): ApiEnvelope<BranchPreviewDto>

    /** Shops in all of Iran and per city (the zoomed-out map's circles). Public. */
    @GET("map/summary")
    suspend fun summary(@Query("categoryId") categoryId: String? = null): ApiEnvelope<MapSummaryDto>

    /** The way from [fromLat]/[fromLng] to a branch: car, foot or bike. Public. */
    @GET("map/branches/{branchId}/directions")
    suspend fun directions(
        @Path("branchId") branchId: String,
        @Query("fromLat") fromLat: Double,
        @Query("fromLng") fromLng: Double,
        @Query("mode") mode: String,
    ): ApiEnvelope<DirectionsDto>
}

@Serializable
data class DirectionsStepDto(
    val type: String,
    val modifier: String? = null,
    val name: String = "",
    val exit: Int? = null,
    val distanceMeters: Double = 0.0,
    val durationSeconds: Double = 0.0,
    /** [lng, lat] */
    val location: List<Double>,
)

@Serializable
data class DirectionsPointDto(val latitude: Double, val longitude: Double)

@Serializable
data class DirectionsDto(
    val branchId: String,
    val mode: String = "car",
    val distanceMeters: Double,
    val durationSeconds: Double,
    /** [lng, lat] pairs */
    val geometry: List<List<Double>>,
    val steps: List<DirectionsStepDto> = emptyList(),
    val destination: DirectionsPointDto,
)

@Serializable
data class MapCitySummaryDto(val cityId: String, val name: String = "", val latitude: Double, val longitude: Double, val storeCount: Int = 0)

@Serializable
data class MapSummaryDto(val storeCount: Int = 0, val cities: List<MapCitySummaryDto> = emptyList())
