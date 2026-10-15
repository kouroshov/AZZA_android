package com.azza.core.model

data class CategoryNode(
    val id: String,
    val name: LocalizedText,
    val allowedKinds: List<OfferingKind>,
    val defaultKind: OfferingKind?,
    val children: List<CategoryNode>,
)

/** A search hit, with the names of its ancestors ("Home & Kitchen › Furniture"). */
data class CategoryHit(val id: String, val name: LocalizedText, val path: List<LocalizedText>)

data class OwnStore(val id: String, val name: String)

/** The listing's condition — also context for conditional fields ("mileage" unless NEW). */
enum class ItemCondition { NEW, LIKE_NEW, USED, FOR_PARTS }

/**
 * Everything the create form collects. Prices are what the seller typed, in
 * major units; apps/api converts to minor units, and is the authority on
 * every rule (CLAUDE.md: a personal listing is one physical item).
 */
data class ListingDraft(
    /** Null = the personal page. */
    val storeId: String?,
    val offeringKind: OfferingKind,
    val categoryId: String,
    val title: String,
    val description: String,
    val price: String,
    val discountPercentage: String,
    val condition: ItemCondition,
    /** Stores only; a personal listing's quantity is always 1, set by the server. */
    val quantity: String,
    val attributeValues: Map<String, Any>,
    /** Content URIs of the photos, in order; the first is the cover. */
    val photos: List<String>,
)

/** A saved listing, as the edit form needs it. */
data class EditableListing(
    val id: String,
    val storeId: String?,
    val offeringKind: OfferingKind,
    val categoryId: String,
    val categoryName: LocalizedText,
    val title: String,
    val description: String,
    val price: String,
    val discountPercentage: String,
    val condition: ItemCondition,
    /** Raw form values per slug: String, Boolean or List<String>. */
    val attributes: Map<String, Any>,
    val photos: List<ExistingPhoto>,
)

data class ExistingPhoto(val mediaId: String, val url: String)

/**
 * How a post ended. [productId] is kept when photos failed: the listing
 * exists, hidden, and posting again continues it rather than making a
 * second one (the web's rule).
 */
data class ListingSubmitResult(
    val productId: String,
    val published: Boolean,
    val failedPhotos: List<String>,
    /** A store post whose stock could not be placed at a branch (no active branch). */
    val stockNotPlaced: Boolean,
)
