package com.azza.core.data

import com.azza.core.common.Outcome
import com.azza.core.common.map
import com.azza.core.model.Money
import com.azza.core.network.MediaUrlResolver
import com.azza.core.network.api.MapApi
import com.azza.core.network.safeApiCall
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json

data class BranchMarker(
    val branchId: String,
    val storeName: String,
    val latitude: Double,
    val longitude: Double,
    /** OPEN, CLOSED or UNKNOWN (no hours published). */
    val status: String,
    val storeSlug: String = "",
    val branchName: String = "",
    val address: String? = null,
    val logoUrl: String? = null,
    val productCount: Int = 0,
)

data class BranchPreview(
    val storeSlug: String,
    val storeName: String,
    val verified: Boolean,
    val logoUrl: String?,
    val address: String,
    val phone: String?,
    val openStatus: String,
    val productCount: Int,
    /** From the viewer, when their position was given. */
    val distanceKm: Double? = null,
    val products: List<BranchPreviewProduct>,
)

data class BranchPreviewProduct(val id: String, val title: String, val price: Money, val imageUrl: String?)

/** The web's marketplace map (`/` on the web): store branches by viewport, and a store's preview. */
@Singleton
class MapRepository @Inject constructor(
    private val api: MapApi,
    private val media: MediaUrlResolver,
    private val json: Json,
) {
    suspend fun branches(north: Double, south: Double, east: Double, west: Double, categoryId: String? = null): Outcome<List<BranchMarker>> =
        safeApiCall(json) { api.branches(north, south, east, west, categoryId = categoryId).data }.map { page ->
            page.items.map {
                BranchMarker(
                    it.branchId, it.storeName, it.latitude, it.longitude, it.status,
                    storeSlug = it.storeSlug, branchName = it.branchName, address = it.address?.ifBlank { null },
                    logoUrl = media.resolve(it.logo), productCount = it.availableProductCount,
                )
            }
        }

    suspend fun preview(branchId: String, lat: Double? = null, lng: Double? = null): Outcome<BranchPreview> = safeApiCall(json) { api.preview(branchId, lat, lng).data }.map { p ->
        BranchPreview(
            storeSlug = p.storeSlug,
            storeName = p.storeName,
            verified = p.verificationStatus == "VERIFIED",
            logoUrl = media.resolve(p.logo),
            address = p.address,
            phone = p.phone,
            openStatus = p.openStatus,
            productCount = p.productCount,
            distanceKm = p.distanceKm,
            products = p.products.map { BranchPreviewProduct(it.id, it.title, Money.fromMajor(it.price, it.currency), media.resolve(it.primaryImage)) },
        )
    }
}
