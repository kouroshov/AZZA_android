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

interface MapApi {
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
