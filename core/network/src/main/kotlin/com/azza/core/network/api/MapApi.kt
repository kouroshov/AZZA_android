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
    ): ApiEnvelope<MapBranchesPageDto>

    /** The store preview a marker opens. Public. */
    @GET("map/branches/{branchId}")
    suspend fun preview(@Path("branchId") branchId: String): ApiEnvelope<BranchPreviewDto>
}
