package com.azza.core.network.api

import com.azza.core.network.model.ApiEnvelope
import com.azza.core.network.model.MeDto
import com.azza.core.network.model.ProductDto
import com.azza.core.network.model.ProductsPageDto
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import okhttp3.MultipartBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

// ---------- phase 5a: the rest of the account (apps/api/src/users, auth, follow, appeals, products/mine) ----------

@Serializable
data class AvatarDto(val avatarUrl: String? = null)

@Serializable
data class UsernameAvailabilityDto(val available: Boolean, val normalizedUsername: String = "", val reason: String = "")

@Serializable
data class RenameUsernameRequest(val username: String)

@Serializable
data class EmailChangeRequest(val newEmail: String)

@Serializable
data class PendingEmailChangeDto(val newEmail: String, val expiresAt: String)

@Serializable
data class PendingEmailChangeEnvelopeDto(val pending: PendingEmailChangeDto? = null)

/** apps/api's user_addresses row (CreateAddressDto + id/isDefault). */
@Serializable
data class AddressDto(
    val id: String,
    val label: String? = null,
    val recipientName: String,
    val phoneE164: String? = null,
    val line1: String,
    val line2: String? = null,
    val city: String,
    val region: String? = null,
    val postalCode: String? = null,
    val countryCode: String = "IR",
    val isDefault: Boolean = false,
)

/**
 * Create and edit share this shape. No defaults on what apps/api requires:
 * the app's Json leaves out any property equal to its default, so a
 * defaulted `countryCode` would never be sent (VALIDATION_FAILED).
 */
@Serializable
data class AddressRequest(
    val label: String? = null,
    val recipientName: String,
    val phoneE164: String? = null,
    val line1: String,
    val line2: String? = null,
    val city: String,
    val region: String? = null,
    val postalCode: String? = null,
    val countryCode: String,
    val isDefault: Boolean,
)

interface ProfileApi {
    /** One image, multipart field `file` (the web's AvatarUpload). */
    @Multipart
    @POST("users/me/avatar")
    suspend fun uploadAvatar(@Part file: MultipartBody.Part): ApiEnvelope<AvatarDto>

    @DELETE("users/me/avatar")
    suspend fun removeAvatar(): ApiEnvelope<AvatarDto>

    @GET("users/username/availability")
    suspend fun usernameAvailability(@Query("username") username: String): ApiEnvelope<UsernameAvailabilityDto>

    /** Once every 30 days (USERNAME_CHANGE_TOO_SOON); the old handle is retired for good. */
    @PATCH("users/me/username")
    suspend fun renameUsername(@Body body: RenameUsernameRequest): ApiEnvelope<MeDto>

    /** A link goes to the NEW address; nothing changes until it is opened (on the website). */
    @POST("auth/email-change/request")
    suspend fun requestEmailChange(@Body body: EmailChangeRequest): ApiEnvelope<JsonElement>

    @GET("auth/email-change/pending")
    suspend fun pendingEmailChange(): ApiEnvelope<PendingEmailChangeEnvelopeDto>

    @POST("auth/email-change/cancel")
    suspend fun cancelEmailChange(): ApiEnvelope<JsonElement>

    @GET("users/me/addresses")
    suspend fun addresses(): ApiEnvelope<List<AddressDto>>

    @POST("users/me/addresses")
    suspend fun addAddress(@Body body: AddressRequest): ApiEnvelope<AddressDto>

    @PATCH("users/me/addresses/{id}")
    suspend fun updateAddress(@Path("id") id: String, @Body body: AddressRequest): ApiEnvelope<AddressDto>

    @DELETE("users/me/addresses/{id}")
    suspend fun deleteAddress(@Path("id") id: String)
}

// ---------- who follows me / whom I follow (own lists only — apps/api keeps them private) ----------

@Serializable
data class FollowerDto(
    val id: String,
    val username: String,
    val displayName: String,
    val followedAt: String,
    val isFollowedByMe: Boolean = false,
)

@Serializable
data class FollowersPageDto(
    val items: List<FollowerDto> = emptyList(),
    val nextCursor: String? = null,
    val hasMore: Boolean = false,
    val totalCount: Int? = null,
)

@Serializable
data class FollowingEntryDto(
    /** "user" or "store". */
    val kind: String,
    val id: String,
    val name: String,
    val username: String? = null,
    val slug: String? = null,
    val followedAt: String,
)

@Serializable
data class FollowingPageDto(
    val items: List<FollowingEntryDto> = emptyList(),
    val nextCursor: String? = null,
    val hasMore: Boolean = false,
    val totalCount: Int? = null,
)

interface FollowListsApi {
    @GET("users/me/followers")
    suspend fun followers(@Query("cursor") cursor: String?, @Query("take") take: Int = 30): ApiEnvelope<FollowersPageDto>

    @GET("users/me/following-list")
    suspend fun following(@Query("cursor") cursor: String?, @Query("take") take: Int = 30): ApiEnvelope<FollowingPageDto>
}

// ---------- appeals: "you took my listing down — look again" ----------

@Serializable
data class AppealDto(
    val id: String,
    val holdAt: String,
    val message: String,
    /** OPEN | ACCEPTED | REJECTED */
    val status: String,
    val createdAt: String,
    val decidedAt: String? = null,
    /** Written for the appellant to read. */
    val decisionNote: String? = null,
)

@Serializable
data class CreateAppealRequest(val targetType: String, val targetId: String, val message: String)

interface AppealsApi {
    @POST("appeals")
    suspend fun create(@Body body: CreateAppealRequest): ApiEnvelope<AppealDto>

    @GET("appeals/mine")
    suspend fun mine(@Query("productId") productId: String? = null, @Query("storeId") storeId: String? = null): ApiEnvelope<List<AppealDto>>
}

// ---------- the owner's own listings, every state (GET /products/mine) ----------

interface OwnListingsApi {
    @GET("products/mine")
    suspend fun list(
        @Query("personal") personal: Boolean? = null,
        @Query("storeId") storeId: String? = null,
        @Query("cursor") cursor: String? = null,
        @Query("take") take: Int = 30,
    ): ApiEnvelope<ProductsPageDto>

    /** One of the caller's own listings, a moderation hold's cause and reason included; 404 for anyone else's. */
    @GET("products/mine/{id}")
    suspend fun byId(@Path("id") id: String): ApiEnvelope<ProductDto>
}
