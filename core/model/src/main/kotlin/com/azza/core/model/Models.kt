package com.azza.core.model

data class User(
    val id: String,
    val email: String,
    val username: String,
    val firstName: String,
    val lastName: String,
    val avatarUrl: String?,
    val bio: String?,
    val usernameChangedAt: String? = null,
    val phoneNumber: String? = null,
    val province: String? = null,
    val city: String? = null,
) {
    val displayName: String
        get() = "$firstName $lastName".trim().ifEmpty { username }
}

enum class Availability { IN_STOCK, LOW_STOCK, OUT_OF_STOCK, UNKNOWN }

/** Who a post appears under: the posting account, or the store it belongs to. */
data class PostAuthor(
    val id: String,
    val handle: String,
    val avatarUrl: String?,
    val isStore: Boolean,
    /** A store's slug, used to open its page; null for a person. */
    val storeSlug: String?,
)

data class ProductCard(
    val id: String,
    val title: String,
    val price: Money,
    val discountPercentage: Double?,
    val imageUrl: String?,
    val categoryName: LocalizedText,
    val author: PostAuthor,
    val availability: Availability,
    val favoriteCount: Int,
    val viewCount: Int,
    /** ✅ (2026-10-23) «توافقی» instead of a price. */
    val priceNegotiable: Boolean = false,
)

data class ProductDetail(
    val id: String,
    val title: String,
    val description: String?,
    val price: Money,
    val discountPercentage: Double?,
    val imageUrls: List<String>,
    /** The page it appears under (the store, or the person), with a name to show. */
    val author: PostAuthor? = null,
    val authorName: String? = null,
    val sellerId: String,
    val storeId: String?,
    val quantityAvailable: Int?,
    val offeringKind: OfferingKind,
    val favoriteCount: Int,
    /** apps/api's ProductStatus (DRAFT, ACTIVE, SOLD, REJECTED, …). */
    val status: String = "ACTIVE",
    /** Set only on the owner's view of a listing an admin (or a suspension) took down. */
    val hold: ListingHold? = null,
    val options: List<ProductOption> = emptyList(),
    /** The category's typed details, in order (the web's "Specifications"). */
    val specs: List<Spec> = emptyList(),
    /** A legacy listing's free-form features (name → value). */
    val features: List<Pair<String, String>> = emptyList(),
    val barcode: String? = null,
    /** A personal listing's stock (a store's lives per branch). */
    val stockQuantity: Int? = null,
    val stockReserved: Int? = null,
    /** ✅ (2026-10-23) The kind of ad (for sale, for rent, wanted…); null on an older listing. */
    val listingTypeName: LocalizedText? = null,
    /** Whether it goes through the basket (not land and property, nor a "wanted" ad). */
    val purchasable: Boolean = true,
    /** A real-estate listing's place on the map. */
    val place: ProductPlace? = null,
    /** ✅ (2026-10-23) «اطلاعات تماس» has a number behind it. */
    val contactAvailable: Boolean = false,
    /** ✅ (2026-10-23) «توافقی»; the 30-day end; the last «نردبان» (ISO instants). */
    val priceNegotiable: Boolean = false,
    val expiresAt: String? = null,
    val bumpedAt: String? = null,
    /** ✅ (2026-10-23) a store's post whose store carries the «تأییدشده» badge. */
    val storeVerified: Boolean = false,
)

/** ✅ (2026-10-23) A saved search: its name, its filters as apps/api keeps them, alerts on or off. */
data class SavedSearch(
    val id: String,
    val label: String,
    val query: String?,
    val categoryId: String?,
    val listingType: String?,
    val cityId: String?,
    /** Rial major units, as stored. */
    val minPrice: Long?,
    val maxPrice: Long?,
    val condition: String?,
    /** The category's own fields, as JSON text (exploreFilters.ts's shape). */
    val attrsJson: String?,
    val notify: Boolean,
    val createdAt: String,
)

/** ✅ (2026-10-23) The number a listing shows, and who answers it. */
data class ContactDetails(val phone: String, val name: String, val isStore: Boolean)

/**
 * ✅ (2026-10-23) Where a property is: a drawn boundary or a point, what the
 * server measured, and the box [west, south, east, north] the map frames.
 */
data class ProductPlace(
    val isBoundary: Boolean,
    val areaM2: Double?,
    val perimeterM: Double?,
    val west: Double,
    val south: Double,
    val east: Double,
    val north: Double,
)

/** One typed detail; [yes] set for a yes/no attribute (worded by the UI). */
data class Spec(
    val labelEn: String,
    val labelFa: String,
    val valueEn: String,
    val valueFa: String,
    val unit: String?,
    val yes: Boolean? = null,
    /** ✅ (2026-10-23) a NUMBER field's value, formatted by the UI in the app's digits. */
    val number: Double? = null,
)

/** A seller's option on a listing; [maxQuantity] per ONE item (1 = once). */
data class ProductOption(val id: String, val name: String, val extra: Money, val maxQuantity: Int)

/**
 * Why a listing is down. [cause]: ADMIN_UNPUBLISHED (a decision about this
 * listing — the only one its owner can appeal), SELLER_SUSPENDED or
 * STORE_SUSPENDED (it went down with the account or the store).
 */
data class ListingHold(val cause: String, val reason: String?, val at: String) {
    val appealable: Boolean get() = cause == "ADMIN_UNPUBLISHED"
}

data class LocalizedText(val fa: String, val en: String, val ckb: String? = null) {
    /** Kurdish where it exists (2026-10-23: the newer categories and the kinds of ad), else Persian. */
    fun forLanguage(language: String): String = when (language) {
        "ckb" -> ckb?.takeIf { it.isNotBlank() } ?: fa
        "fa" -> fa
        else -> en
    }
}

data class Page<T>(
    val items: List<T>,
    val nextCursor: String?,
    val hasMore: Boolean,
    /** Set when this is the saved copy shown offline: when it was saved (epoch ms). */
    val savedAt: Long? = null,
    /** How many there are in all, when the list says (search does). */
    val total: Int? = null,
)

/**
 * The web's All / Personal / one-store filter on orders received and on
 * notifications: [context] is apps/api's `all` | `personal` | `store`.
 */
data class ContextFilter(val context: String = "all", val storeId: String? = null) {
    companion object {
        val ALL = ContextFilter()
        val PERSONAL = ContextFilter("personal")
        fun store(id: String) = ContextFilter("store", id)
    }
}

/** What can be chosen in that filter: the personal page and each store, by name. */
data class SellingContexts(val hasPersonal: Boolean = true, val stores: List<Pair<String, String>> = emptyList())
