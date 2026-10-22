package com.azza.core.data

import com.azza.core.common.Outcome
import com.azza.core.common.map
import com.azza.core.model.MapPoint
import com.azza.core.model.Money
import com.azza.core.model.ParcelDeal
import com.azza.core.model.ParcelListing
import com.azza.core.model.ParcelMapItem
import com.azza.core.model.ParcelOwner
import com.azza.core.model.ParcelRule
import com.azza.core.network.MediaUrlResolver
import com.azza.core.network.api.MapApi
import com.azza.core.network.api.SimilarParcelsRequest
import com.azza.core.network.safeApiCall
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json

/**
 * The real-estate map (apps/api's parcels module, 2026-10-22): the listings
 * in view with their point or boundary — boundaries of the same land arrive
 * as one item listing every agency's ad — and, while a boundary is drawn,
 * the boundaries already on the map for that land.
 */
@Singleton
class ParcelsRepository @Inject constructor(
    private val api: MapApi,
    private val json: Json,
    private val media: MediaUrlResolver,
) {
    suspend fun inView(north: Double, south: Double, east: Double, west: Double, deals: Set<ParcelDeal>?): Outcome<List<ParcelMapItem>> =
        safeApiCall(json) { api.parcels(north, south, east, west, deals?.takeIf { it.isNotEmpty() }?.joinToString(",") { it.name }).data }
            .map { page ->
                page.items.map { item ->
                    ParcelMapItem(
                        isBoundary = item.kind == "POLYGON",
                        centre = MapPoint(item.lng, item.lat),
                        ring = item.ring.orEmpty().mapNotNull { it.toPoint() },
                        deal = item.deal.toDeal(),
                        listings = item.listings.map { l ->
                            ParcelListing(
                                productId = l.productId,
                                title = l.title,
                                price = Money(l.priceAmountMinor, l.currency),
                                deal = l.deal.toDeal(),
                                areaM2 = l.areaM2,
                                imageUrl = l.image?.let { media.resolve(it.url ?: it.storageKey) },
                                owner = ParcelOwner(
                                    isStore = l.owner.kind == "STORE",
                                    handle = l.owner.slug ?: l.owner.username.orEmpty(),
                                    name = l.owner.name,
                                    avatarUrl = l.owner.avatarMediaKey?.let { media.resolve(it) },
                                ),
                            )
                        },
                    )
                }
            }

    suspend fun rule(categoryId: String): Outcome<ParcelRule> =
        safeApiCall(json) { api.parcelRule(categoryId).data }.map { ParcelRule(it.allowed, it.boundary) }

    /** Boundaries of nearly the same land, best match first (shape only). */
    suspend fun similar(ring: List<MapPoint>, excludeProductId: String?): Outcome<List<List<MapPoint>>> =
        safeApiCall(json) { api.similarParcels(SimilarParcelsRequest(ring.map { listOf(it.lng, it.lat) }, excludeProductId)).data }
            .map { list -> list.map { s -> s.ring.mapNotNull { it.toPoint() } } }
}

private fun List<Double>.toPoint(): MapPoint? = if (size == 2) MapPoint(this[0], this[1]) else null

private fun String?.toDeal(): ParcelDeal? = this?.let { runCatching { ParcelDeal.valueOf(it) }.getOrNull() }
