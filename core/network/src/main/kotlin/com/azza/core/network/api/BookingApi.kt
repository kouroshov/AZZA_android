package com.azza.core.network.api

import com.azza.core.network.model.ApiEnvelope
import com.azza.core.network.model.AppointmentContactRequest
import com.azza.core.network.model.AppointmentDto
import com.azza.core.network.model.AvailabilityDto
import com.azza.core.network.model.BookingSetupDto
import com.azza.core.network.model.CheckoutResultDto
import com.azza.core.network.model.CreateBookingRequest
import com.azza.core.network.model.DepositCheckoutRequest
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface BookingApi {
    /** Public. 404 BOOKING_NOT_AVAILABLE for a listing that takes no appointments. */
    @GET("products/{productId}/booking")
    suspend fun setup(@Path("productId") productId: String): ApiEnvelope<BookingSetupDto>

    /** Public. Free slots per day, in UTC instants; [from] is a date (YYYY-MM-DD); the window is a week. */
    @GET("products/{productId}/booking/availability")
    suspend fun availability(@Path("productId") productId: String, @Query("from") from: String): ApiEnvelope<AvailabilityDto>

    /** CONFIRMED when there is no deposit; HELD (with holdExpiresAt) until the deposit is paid. 409 SLOT_TAKEN on a race. */
    @POST("appointments")
    suspend fun book(@Body body: CreateBookingRequest): ApiEnvelope<AppointmentDto>

    @GET("appointments/mine")
    suspend fun mine(@Query("scope") scope: String = "all"): ApiEnvelope<List<AppointmentDto>>

    @GET("appointments/received")
    suspend fun received(@Query("scope") scope: String = "all"): ApiEnvelope<List<AppointmentDto>>

    /** The three things a deposit collects (name, email, optional address); the customer's own to set. */
    @PUT("appointments/{id}/contact")
    suspend fun setContact(@Path("id") id: String, @Body body: AppointmentContactRequest): ApiEnvelope<AppointmentDto>

    /** Every appointment on one of the caller's calendars in a date range (at most a few weeks), all services. */
    @GET("booking/calendar/appointments")
    suspend fun calendarAppointments(
        @Query("storeId") storeId: String?,
        @Query("from") from: String,
        @Query("to") to: String,
    ): ApiEnvelope<CalendarAppointmentsDto>

    @POST("appointments/{id}/cancel")
    suspend fun cancel(@Path("id") id: String): ApiEnvelope<AppointmentDto>

    @POST("appointments/{id}/complete")
    suspend fun complete(@Path("id") id: String): ApiEnvelope<AppointmentDto>

    @POST("appointments/{id}/no-show")
    suspend fun noShow(@Path("id") id: String): ApiEnvelope<AppointmentDto>

    @POST("orders/checkout")
    suspend fun checkoutDeposits(@Body body: DepositCheckoutRequest): ApiEnvelope<CheckoutResultDto>
}

@kotlinx.serialization.Serializable
data class CalendarAppointmentsDto(
    val calendarId: String? = null,
    val timezone: String = "Asia/Tehran",
    val from: String = "",
    val to: String = "",
    val appointments: List<com.azza.core.network.model.AppointmentDto> = emptyList(),
)
