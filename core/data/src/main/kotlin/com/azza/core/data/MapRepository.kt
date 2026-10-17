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

/** One city's circle on the zoomed-out map. */
data class CityCount(val cityId: String, val name: String, val latitude: Double, val longitude: Double, val stores: Int)

/** Shops in all of Iran, and per city. */
data class MapSummary(val stores: Int, val cities: List<CityCount>)

enum class TravelMode(val api: String) { CAR("car"), FOOT("foot"), BIKE("bike") }

/** A point on the way. */
data class GeoPoint(val latitude: Double, val longitude: Double)

/** One maneuver (OSRM's): what to do at [at], then follow [road] for [distanceMeters]. */
data class DirectionStep(
    val type: String,
    val modifier: String?,
    val road: String,
    val exit: Int?,
    val distanceMeters: Double,
    val durationSeconds: Double,
    val at: GeoPoint,
)

data class Directions(
    val mode: TravelMode,
    val distanceMeters: Double,
    val durationSeconds: Double,
    val line: List<GeoPoint>,
    val steps: List<DirectionStep>,
    val destination: GeoPoint,
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

    suspend fun summary(categoryId: String?): Outcome<MapSummary> = safeApiCall(json) { api.summary(categoryId).data }.map { s ->
        MapSummary(s.storeCount, s.cities.map { CityCount(it.cityId, it.name, it.latitude, it.longitude, it.storeCount) })
    }

    /** The way from [from] to the branch (apps/api asks its routing server). */
    suspend fun directions(branchId: String, from: GeoPoint, mode: TravelMode): Outcome<Directions> =
        safeApiCall(json) { api.directions(branchId, from.latitude, from.longitude, mode.api).data }.map { d ->
            Directions(
                mode = mode,
                distanceMeters = d.distanceMeters,
                durationSeconds = d.durationSeconds,
                line = d.geometry.mapNotNull { p -> if (p.size >= 2) GeoPoint(p[1], p[0]) else null },
                steps = d.steps.mapNotNull { s ->
                    if (s.location.size < 2) null
                    else DirectionStep(s.type, s.modifier, s.name, s.exit, s.distanceMeters, s.durationSeconds, GeoPoint(s.location[1], s.location[0]))
                },
                destination = GeoPoint(d.destination.latitude, d.destination.longitude),
            )
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
