package com.azza.core.network.api

import com.azza.core.network.model.ApiEnvelope
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import okhttp3.MultipartBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

// ---------- phase 5b: stores the signed-in person owns (apps/api/src/stores, branches, inventory, booking, subscriptions) ----------

@Serializable
data class LocalizedNameDto(val en: String = "", val fa: String = "")

@Serializable
data class StoreOccupationDto(val id: String, val code: String = "", val name: LocalizedNameDto = LocalizedNameDto())

@Serializable
data class HoursDto(val dayOfWeek: Int, val opensMinute: Int? = null, val closesMinute: Int? = null, val isClosed: Boolean = false)

@Serializable
data class LocationDto(
    val latitude: Double,
    val longitude: Double,
    val address: String = "",
    val areaId: String = "",
    val boundaryVerified: Boolean = false,
)

@Serializable
data class StoreHoldDto(val cause: String, val reason: String? = null, val at: String)

/** GET /stores/mine: the public store shape plus, for its owner, status and a hold's cause and reason. */
@Serializable
data class OwnStoreDto(
    val id: String,
    val slug: String,
    val name: String,
    val description: String? = null,
    val branchPhone: String? = null,
    val followerCount: Int = 0,
    val avatarUrl: String? = null,
    val occupation: StoreOccupationDto? = null,
    val primaryBranchId: String? = null,
    val openStatus: String = "UNKNOWN",
    val status: String = "ACTIVE",
    val moderation: StoreHoldDto? = null,
    val location: LocationDto? = null,
)

/** Create and edit. A null field is left out (the app's Json drops nulls), so an edit changes only what it sends. */
@Serializable
data class StoreRequest(
    val name: String? = null,
    val description: String? = null,
    val branchPhone: String? = null,
    /** The store's handle (/stores/<username>); optional on create — apps/api then makes one from the name. */
    val username: String? = null,
    val occupationId: String? = null,
)

@Serializable
data class OccupationResultDto(val id: String, val code: String = "", val level: String = "", val name: LocalizedNameDto = LocalizedNameDto())

@Serializable
data class EligibilityDto(val eligible: Boolean)

@Serializable
data class StoreFollowerDto(val id: String, val username: String, val displayName: String = "", val followedAt: String = "")

@Serializable
data class StoreFollowersPageDto(val items: List<StoreFollowerDto> = emptyList(), val nextCursor: String? = null, val hasMore: Boolean = false, val totalCount: Int? = null)

@Serializable
data class ClosureDto(val date: String, val reason: String? = null)

@Serializable
data class BranchDetailDto(
    val id: String,
    val storeId: String = "",
    val name: String = "",
    val slug: String = "",
    val status: String = "ACTIVE",
    val phone: String? = null,
    val timezone: String = "Asia/Tehran",
    val openStatus: String = "UNKNOWN",
    val location: LocationDto? = null,
    val hours: List<HoursDto> = emptyList(),
    val upcomingClosures: List<ClosureDto> = emptyList(),
    val createdAt: String = "",
)

@Serializable
data class BranchRequest(val name: String? = null, val phone: String? = null, val status: String? = null)

@Serializable
data class HoursRequest(val hours: List<HoursDto>)

@Serializable
data class ClosuresRequest(val closures: List<ClosureDto>)

@Serializable
data class LocationRequest(val latitude: Double, val longitude: Double, val address: String, val areaId: String)

@Serializable
data class BranchStockDto(
    val productId: String,
    val productTitle: String = "",
    val quantity: Int = 0,
    val reserved: Int = 0,
    val availableQuantity: Int = 0,
    val status: String = "ACTIVE",
)

@Serializable
data class BranchStockPageDto(val items: List<BranchStockDto> = emptyList(), val nextCursor: String? = null, val hasMore: Boolean = false)

interface StoreManageApi {
    @GET("subscriptions/eligibility")
    suspend fun eligibility(): ApiEnvelope<EligibilityDto>

    @GET("stores/mine")
    suspend fun mine(): ApiEnvelope<List<OwnStoreDto>>

    @POST("stores")
    suspend fun create(@Body body: StoreRequest): ApiEnvelope<OwnStoreDto>

    @PATCH("stores/{storeId}")
    suspend fun update(@Path("storeId") storeId: String, @Body body: StoreRequest): ApiEnvelope<JsonElement>

    @Multipart
    @POST("stores/{storeId}/avatar")
    suspend fun uploadAvatar(@Path("storeId") storeId: String, @Part file: MultipartBody.Part): ApiEnvelope<JsonElement>

    @DELETE("stores/{storeId}/avatar")
    suspend fun removeAvatar(@Path("storeId") storeId: String): ApiEnvelope<JsonElement>

    @GET("stores/{storeId}/followers")
    suspend fun followers(@Path("storeId") storeId: String, @Query("cursor") cursor: String?, @Query("take") take: Int = 30): ApiEnvelope<StoreFollowersPageDto>

    @GET("taxonomy/occupations/search")
    suspend fun searchOccupations(@Query("q") q: String, @Query("limit") limit: Int = 12): ApiEnvelope<List<OccupationResultDto>>

    @GET("stores/{storeId}/branches")
    suspend fun branches(@Path("storeId") storeId: String): ApiEnvelope<List<BranchDetailDto>>

    @GET("stores/{storeId}/branches/{branchId}")
    suspend fun branch(@Path("storeId") storeId: String, @Path("branchId") branchId: String): ApiEnvelope<BranchDetailDto>

    @POST("stores/{storeId}/branches")
    suspend fun createBranch(@Path("storeId") storeId: String, @Body body: BranchRequest): ApiEnvelope<BranchDetailDto>

    @PATCH("stores/{storeId}/branches/{branchId}")
    suspend fun updateBranch(@Path("storeId") storeId: String, @Path("branchId") branchId: String, @Body body: BranchRequest): ApiEnvelope<BranchDetailDto>

    @PUT("stores/{storeId}/branches/{branchId}/hours")
    suspend fun setHours(@Path("storeId") storeId: String, @Path("branchId") branchId: String, @Body body: HoursRequest): ApiEnvelope<JsonElement>

    @PUT("stores/{storeId}/branches/{branchId}/closures")
    suspend fun setClosures(@Path("storeId") storeId: String, @Path("branchId") branchId: String, @Body body: ClosuresRequest): ApiEnvelope<JsonElement>

    @PUT("stores/{storeId}/branches/{branchId}/location")
    suspend fun setLocation(@Path("storeId") storeId: String, @Path("branchId") branchId: String, @Body body: LocationRequest): ApiEnvelope<JsonElement>

    @GET("stores/{storeId}/branches/{branchId}/inventory")
    suspend fun branchStock(
        @Path("storeId") storeId: String,
        @Path("branchId") branchId: String,
        @Query("cursor") cursor: String? = null,
        @Query("take") take: Int = 60,
    ): ApiEnvelope<BranchStockPageDto>

    /** 204: the branch now holds exactly [SetQuantityRequest.quantity] of that listing. */
    @PUT("stores/{storeId}/branches/{branchId}/inventory/{productId}")
    suspend fun setQuantity(
        @Path("storeId") storeId: String,
        @Path("branchId") branchId: String,
        @Path("productId") productId: String,
        @Body body: com.azza.core.network.model.SetQuantityRequest,
    )
}

// ---------- the provider's timetable (one per store, or one per person) ----------

@Serializable
data class WindowDto(val dayOfWeek: Int, val startMinute: Int, val endMinute: Int)

@Serializable
data class CalendarExceptionDto(val kind: String = "CLOSED", val startsAt: String, val endsAt: String, val reason: String? = null)

/** A timed service on this calendar, with what differs per service (BookingServiceSummaryDto). */
@Serializable
data class CalendarServiceDto(
    val productId: String,
    val title: String = "",
    val status: String = "ACTIVE",
    val durationMinutes: Int = 60,
    val slotIntervalMinutes: Int = 60,
    val bufferBeforeMinutes: Int = 0,
    val bufferAfterMinutes: Int = 0,
    val minNoticeMinutes: Int = 0,
    val maxAdvanceDays: Int = 60,
    val payOnSite: Boolean = true,
)

@Serializable
data class CalendarDto(
    val calendarId: String? = null,
    val storeId: String? = null,
    val timezone: String = "Asia/Tehran",
    val availability: List<WindowDto> = emptyList(),
    val exceptions: List<CalendarExceptionDto> = emptyList(),
    val services: List<CalendarServiceDto> = emptyList(),
)

@Serializable
data class CalendarRequest(val timezone: String, val availability: List<WindowDto>, val exceptions: List<CalendarExceptionDto>)

/** PUT /products/:id/booking — only what differs per service; the week lives on the calendar. */
@Serializable
data class BookingSettingsRequest(
    val durationMinutes: Int,
    val slotIntervalMinutes: Int,
    val bufferBeforeMinutes: Int,
    val bufferAfterMinutes: Int,
    val minNoticeMinutes: Int,
    val maxAdvanceDays: Int,
    val depositPercent: Int,
)

interface CalendarApi {
    @GET("booking/calendar")
    suspend fun get(@Query("storeId") storeId: String? = null): ApiEnvelope<CalendarDto>

    @PUT("booking/calendar")
    suspend fun update(@Body body: CalendarRequest, @Query("storeId") storeId: String? = null): ApiEnvelope<CalendarDto>

    @PUT("products/{productId}/booking")
    suspend fun saveSettings(@Path("productId") productId: String, @Body body: BookingSettingsRequest): ApiEnvelope<JsonElement>
}
