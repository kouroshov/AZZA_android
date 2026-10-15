package com.azza.core.network.model

import kotlinx.serialization.Serializable

// apps/api/src/booking — GET /products/:id/booking[/availability], /appointments

@Serializable
data class BookingSetupDto(
    val productId: String,
    val durationMinutes: Int,
    val timezone: String = "Asia/Tehran",
    val payOnSite: Boolean = true,
    val depositPercent: Int = 0,
    @Serializable(with = FlexibleLongSerializer::class) val depositAmountMinor: Long = 0,
    @Serializable(with = FlexibleLongSerializer::class) val sessionPriceAmountMinor: Long = 0,
    val currency: String = "IRR",
)

@Serializable
data class AvailabilityDto(
    val timezone: String = "Asia/Tehran",
    val durationMinutes: Int = 60,
    val depositPercent: Int = 0,
    val from: String,
    val to: String,
    val days: List<BookingDayDto> = emptyList(),
)

@Serializable
data class BookingDayDto(val date: String, val slots: List<SlotDto> = emptyList())

@Serializable
data class SlotDto(val startsAt: String, val endsAt: String)

@Serializable
data class CreateBookingRequest(val productId: String, val startsAt: String, val note: String? = null)

@Serializable
data class AppointmentContactRequest(val contactName: String, val contactEmail: String, val contactAddress: String? = null)

@Serializable
data class AppointmentDto(
    val id: String,
    val status: String,
    val startsAt: String,
    val endsAt: String,
    val timezone: String = "Asia/Tehran",
    val holdExpiresAt: String? = null,
    @Serializable(with = FlexibleLongSerializer::class) val priceAmountMinor: Long = 0,
    val currency: String = "IRR",
    @Serializable(with = FlexibleLongSerializer::class) val depositAmountMinor: Long = 0,
    val customerNote: String? = null,
    val contactName: String? = null,
    val contactEmail: String? = null,
    val contactAddress: String? = null,
    val product: AppointmentProductDto,
    val store: AppointmentStoreDto? = null,
    val counterparty: AppointmentPartyDto? = null,
)

@Serializable
data class AppointmentProductDto(val id: String, val title: String)

@Serializable
data class AppointmentStoreDto(val id: String, val name: String, val slug: String = "")

@Serializable
data class AppointmentPartyDto(val id: String, val username: String? = null, val displayName: String? = null)

/** POST /orders/checkout for deposits only: no items, no address — nothing ships. */
@Serializable
data class DepositCheckoutRequest(val appointmentIds: List<String>)
