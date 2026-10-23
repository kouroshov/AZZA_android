package com.azza.core.network.model

import kotlinx.serialization.Serializable

// Mirrors of apps/api's JSON. Only the fields the app reads are declared;
// the shared Json ignores the rest, so the API can grow without breaking an
// installed app. Keep these 1:1 with the server's names.

// ---------- auth (apps/api/src/auth) ----------

@Serializable
data class LoginRequest(val email: String, val password: String)

@Serializable
data class RegisterRequest(
    val email: String,
    val username: String,
    val password: String,
    val firstName: String,
    val lastName: String,
    val phoneNumber: String? = null,
    val province: String? = null,
    val city: String? = null,
)

@Serializable
data class RefreshRequest(val refreshToken: String)

@Serializable
data class PublicUserDto(
    val id: String,
    val email: String,
    val username: String,
    val firstName: String = "",
    val lastName: String = "",
)

/** `POST /auth/login` and `POST /auth/register`. */
@Serializable
data class AuthResultDto(
    val user: PublicUserDto,
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: String,
)

/** `POST /auth/refresh` — the old refresh token is spent; this pair replaces it. */
@Serializable
data class TokenPairDto(val accessToken: String, val refreshToken: String, val expiresIn: String)

// ---------- users (GET /users/me) ----------

@Serializable
data class MeDto(
    val id: String,
    val email: String,
    val username: String,
    val profile: ProfileDto? = null,
    /** The last username change; a new one is allowed 30 days after it. */
    val usernameChangedAt: String? = null,
)

@Serializable
data class ProfileDto(
    val firstName: String = "",
    val lastName: String = "",
    val avatarMediaKey: String? = null,
    val bio: String? = null,
    val phoneNumber: String? = null,
    val province: String? = null,
    val city: String? = null,
)

// ---------- discovery (GET /explore) — FeedPageResponseDto ----------

@Serializable
data class FeedPageDto(
    val items: List<FeedCardDto>,
    val nextCursor: String? = null,
    val hasMore: Boolean = false,
)

@Serializable
data class FeedCardDto(
    val id: String,
    val title: String,
    /** Major units, already converted by the server (fromMinorUnits). */
    val price: Double,
    val currency: String = "IRR",
    val discountPercentage: Double? = null,
    /** ✅ (2026-10-23) «توافقی»: no fixed price (stored as 0). */
    val priceNegotiable: Boolean = false,
    /** A storage KEY or a URL — resolve with MediaUrlResolver. */
    val primaryImage: String? = null,
    val category: FeedCategoryDto,
    val store: FeedStoreDto? = null,
    val postedBy: PostedByDto,
    val availability: String = "UNKNOWN",
    val favoriteCount: Int = 0,
    val viewCount: Int = 0,
)

@Serializable
data class FeedCategoryDto(val id: String, val slug: String, val name: LocalizedNameDto)

@Serializable
data class LocalizedNameDto(val fa: String = "", val en: String = "")

@Serializable
data class FeedStoreDto(val id: String, val slug: String, val name: String, val avatarKey: String? = null)

@Serializable
data class PostedByDto(val id: String, val username: String, val avatarKey: String? = null)

// ---------- products (GET /products/:id) — a Prisma row with productInclude ----------

@Serializable
data class ProductDto(
    val id: String,
    val title: String,
    val description: String? = null,
    @Serializable(with = FlexibleLongSerializer::class)
    val priceAmountMinor: Long,
    val currency: String = "IRR",
    @Serializable(with = FlexibleDoubleSerializer::class)
    val discountPercentage: Double? = null,
    val sellerId: String,
    val storeId: String? = null,
    /** Who sells it: public fields only (apps/api, 2026-10-17). */
    val seller: ProductSellerDto? = null,
    val store: ProductStoreDto? = null,
    val media: List<ProductMediaDto> = emptyList(),
    val inventory: InventoryDto? = null,
    val offeringKind: String = "GOOD",
    val favoriteCount: Int = 0,
    // For the edit form:
    val categoryId: String? = null,
    val category: ProductCategoryDto? = null,
    val condition: String? = null,
    /** ✅ (2026-10-23) the kind of ad; null on a listing older than listing types. */
    val listingType: ProductListingTypeDto? = null,
    val status: String = "ACTIVE",
    val attributeValues: List<AttributeValueDto> = emptyList(),
    // A moderation hold (all null when there is none). Only the owner's
    // route (GET /products/mine/:id) ever answers with one set.
    val moderatedAt: String? = null,
    val moderationCause: String? = null,
    val moderationReason: String? = null,
    /** The seller's options ("extra sauce"), in their order. */
    val options: List<ProductOptionDto> = emptyList(),
    /** Free-form features of a listing posted before typed attributes (attributesSchemaHash null). */
    val attributesCache: kotlinx.serialization.json.JsonElement? = null,
    val attributesSchemaHash: String? = null,
    /** EAN-13 made by the database for every listing. */
    val barcode: String? = null,
    /** A real-estate listing's point or boundary on the map (2026-10-22). */
    val parcel: ProductParcelDto? = null,
    /**
     * ✅ (2026-10-23) Whether it goes through the basket — false for a
     * "wanted" ad, a job, a giveaway, and anything in land and property.
     */
    val purchasable: Boolean = true,
    /** ✅ (2026-10-23) whether «اطلاعات تماس» has a number behind it, and the seller's own switch. */
    val contactAvailable: Boolean = false,
    val showPhone: Boolean = false,
    /** ✅ (2026-10-23) «توافقی», the 30-day end, and the last «نردبان». */
    val priceNegotiable: Boolean = false,
    val expiresAt: String? = null,
    val bumpedAt: String? = null,
)

/** ✅ (2026-10-23) GET /products/:id/similar. */
@Serializable
data class SimilarProductsDto(val items: List<ProductDto> = emptyList())

/** ✅ (2026-10-23) A saved search: apps/api's feed filters, prices in rial major units. */
@Serializable
data class SavedSearchDto(
    val id: String,
    val label: String,
    val filters: kotlinx.serialization.json.JsonObject = kotlinx.serialization.json.JsonObject(emptyMap()),
    val notify: Boolean = true,
    val createdAt: String = "",
)

@Serializable
data class CreateSavedSearchRequest(val label: String, val filters: kotlinx.serialization.json.JsonObject)

@Serializable
data class SavedSearchNotifyRequest(val notify: Boolean)

@Serializable
data class ProductParcelDto(
    val kind: String,
    @Serializable(with = FlexibleDoubleSerializer::class)
    val latitude: Double? = null,
    @Serializable(with = FlexibleDoubleSerializer::class)
    val longitude: Double? = null,
    val ring: List<List<Double>>? = null,
    // ✅ (2026-10-23) measured by the server, and the box "show on the map" frames.
    @Serializable(with = FlexibleDoubleSerializer::class)
    val areaM2: Double? = null,
    @Serializable(with = FlexibleDoubleSerializer::class)
    val perimeterM: Double? = null,
    @Serializable(with = FlexibleDoubleSerializer::class)
    val minLat: Double? = null,
    @Serializable(with = FlexibleDoubleSerializer::class)
    val maxLat: Double? = null,
    @Serializable(with = FlexibleDoubleSerializer::class)
    val minLng: Double? = null,
    @Serializable(with = FlexibleDoubleSerializer::class)
    val maxLng: Double? = null,
)

@Serializable
data class ProductOptionDto(
    val id: String,
    val name: String,
    @Serializable(with = FlexibleLongSerializer::class)
    val extraPriceAmountMinor: Long = 0,
    val currency: String = "IRR",
    /** How many of it ONE unit of the product can take: 1 = a checkbox, up to 10. */
    val maxQuantity: Int = 1,
)

@Serializable
data class ProductCategoryDto(val id: String, val nameEn: String = "", val nameFa: String? = null)

/** One stored attribute value: exactly one `value*` column is set, by the attribute's type. */
@Serializable
data class AttributeValueDto(
    val categoryAttribute: AttributeRefDto,
    val valueText: String? = null,
    @Serializable(with = FlexibleDoubleSerializer::class)
    val valueNumber: Double? = null,
    val valueBoolean: Boolean? = null,
    val valueDate: String? = null,
    val valueJson: kotlinx.serialization.json.JsonElement? = null,
)

@Serializable
data class AttributeRefDto(
    val slug: String,
    val dataType: String = "TEXT",
    val name: String = "",
    val nameFa: String? = null,
    val unit: String? = null,
    /** ENUM / MULTI_ENUM choices: [{ value, label: { en, fa } }]. */
    val options: kotlinx.serialization.json.JsonElement? = null,
)

@Serializable
data class ProductMediaDto(
    val id: String,
    val storageKey: String,
    val url: String? = null,
    val mediaType: String = "IMAGE",
)

@Serializable
data class InventoryDto(val quantity: Int = 0, val reservedQuantity: Int = 0)

@Serializable
data class ProductSellerProfileDto(val firstName: String = "", val lastName: String = "", val avatarMediaKey: String? = null)

@Serializable
data class ProductSellerDto(val username: String, val profile: ProductSellerProfileDto? = null)

@Serializable
data class ProductStoreDto(
    val slug: String,
    val name: String = "",
    val avatarMediaKey: String? = null,
    /** ✅ (2026-10-23) VERIFIED = the «تأییدشده» badge, given by an admin. */
    val verificationStatus: String? = null,
)

@Serializable
data class ProductListingTypeDto(
    val code: String,
    val priceMode: String = "REQUIRED",
    val purchasable: Boolean = true,
    val nameEn: String = "",
    val nameFa: String? = null,
    val nameCkb: String? = null,
)

@Serializable
data class ContactDto(val phone: String, val name: String = "", val isStore: Boolean = false)
