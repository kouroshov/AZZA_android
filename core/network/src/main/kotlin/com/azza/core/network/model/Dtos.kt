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
    val media: List<ProductMediaDto> = emptyList(),
    val inventory: InventoryDto? = null,
    val offeringKind: String = "GOOD",
    val favoriteCount: Int = 0,
    // For the edit form:
    val categoryId: String? = null,
    val category: ProductCategoryDto? = null,
    val condition: String? = null,
    val status: String = "ACTIVE",
    val attributeValues: List<AttributeValueDto> = emptyList(),
    // A moderation hold (all null when there is none). Only the owner's
    // route (GET /products/mine/:id) ever answers with one set.
    val moderatedAt: String? = null,
    val moderationCause: String? = null,
    val moderationReason: String? = null,
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
data class AttributeRefDto(val slug: String, val dataType: String = "TEXT")

@Serializable
data class ProductMediaDto(
    val id: String,
    val storageKey: String,
    val url: String? = null,
    val mediaType: String = "IMAGE",
)

@Serializable
data class InventoryDto(val quantity: Int = 0, val reservedQuantity: Int = 0)
