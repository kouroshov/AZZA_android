package com.azza.core.model

// The real-estate map (product owner, 2026-10-22; apps/api's parcels
// module): a listing's optional point or drawn boundary, and the map of them.

/** A coordinate as GeoJSON orders it. */
data class MapPoint(val lng: Double, val lat: Double)

enum class ParcelMode { NONE, POINT, POLYGON }

/** The listing form's working copy: what is drawn, even while not finished. */
data class ParcelDraft(
    val mode: ParcelMode = ParcelMode.NONE,
    val point: MapPoint? = null,
    val ring: List<MapPoint> = emptyList(),
)

/** Whether a category goes on the map: a point, and (land) a boundary too. */
data class ParcelRule(val allowed: Boolean, val boundary: Boolean)

/** The deal the map colours by — the listing's category. MIXED: a group of different deals. */
enum class ParcelDeal { SALE, RENT, SHORT_TERM, PARTNERSHIP, PRESALE, MIXED }

data class ParcelOwner(
    val isStore: Boolean,
    /** The store's slug, or the person's username. */
    val handle: String,
    val name: String,
    val avatarUrl: String?,
)

data class ParcelListing(
    val productId: String,
    val title: String,
    val price: Money,
    val deal: ParcelDeal?,
    val areaM2: Double?,
    val imageUrl: String?,
    val owner: ParcelOwner,
)

/** One place on the map: a point, or a boundary several agencies may share (newest listing first). */
data class ParcelMapItem(
    val isBoundary: Boolean,
    val centre: MapPoint,
    val ring: List<MapPoint>,
    val deal: ParcelDeal?,
    val listings: List<ParcelListing>,
)
