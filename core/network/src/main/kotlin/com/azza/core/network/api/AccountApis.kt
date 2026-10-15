package com.azza.core.network.api

import com.azza.core.network.model.ApiEnvelope
import com.azza.core.network.model.MeDto
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

// ---------- account (apps/api/src/users, src/auth) ----------

/** PATCH /users/me — only the fields sent are changed (UpdateProfileDto). */
@Serializable
data class UpdateProfileRequest(val firstName: String? = null, val lastName: String? = null, val bio: String? = null)

@Serializable
data class ForgotPasswordRequest(val email: String)

interface AccountApi {
    @PATCH("users/me")
    suspend fun updateProfile(@Body body: UpdateProfileRequest): ApiEnvelope<MeDto>

    /** Public; the same answer whether or not the account exists (no enumeration). */
    @POST("auth/forgot-password")
    suspend fun forgotPassword(@Body body: ForgotPasswordRequest): ApiEnvelope<JsonElement>
}

// ---------- support tickets (apps/api/src/admin/tickets — the member side, /tickets) ----------

@Serializable
data class TicketSummaryDto(
    val id: String,
    val number: Long = 0,
    val subject: String,
    val category: String,
    val status: String,
    val createdAt: String,
    val lastMessageAt: String,
    val closedAt: String? = null,
    /** Support wrote last and the ticket is not closed. */
    val awaitingYou: Boolean = false,
)

@Serializable
data class TicketsPageDto(val items: List<TicketSummaryDto> = emptyList(), val nextCursor: String? = null, val hasMore: Boolean = false)

@Serializable
data class TicketMessageDto(val id: String, val from: String, val body: String, val createdAt: String)

@Serializable
data class TicketDetailDto(
    val id: String,
    val number: Long = 0,
    val subject: String,
    val category: String,
    val status: String,
    val lastMessageAt: String,
    val messages: List<TicketMessageDto> = emptyList(),
)

@Serializable
data class CreateTicketRequest(val subject: String, val category: String, val body: String)

@Serializable
data class TicketMessageRequest(val body: String)

interface SupportApi {
    @GET("tickets/mine")
    suspend fun mine(@Query("cursor") cursor: String?, @Query("take") take: Int = 20): ApiEnvelope<TicketsPageDto>

    @GET("tickets/{id}")
    suspend fun detail(@Path("id") id: String): ApiEnvelope<TicketDetailDto>

    @POST("tickets")
    suspend fun create(@Body body: CreateTicketRequest): ApiEnvelope<TicketDetailDto>

    @POST("tickets/{id}/messages")
    suspend fun reply(@Path("id") id: String, @Body body: TicketMessageRequest): ApiEnvelope<TicketDetailDto>

    @POST("tickets/{id}/close")
    suspend fun close(@Path("id") id: String): ApiEnvelope<TicketDetailDto>
}

// ---------- reports (POST /reports — member-facing, the reporter is the token's subject) ----------

@Serializable
data class CreateReportRequest(val targetType: String, val targetId: String, val reason: String, val details: String? = null)

interface ReportsApi {
    @POST("reports")
    suspend fun create(@Body body: CreateReportRequest): ApiEnvelope<JsonElement>
}
