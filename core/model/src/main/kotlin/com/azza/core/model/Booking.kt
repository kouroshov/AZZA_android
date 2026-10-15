package com.azza.core.model

/** How a bookable listing takes appointments (its bookable_config + the provider's calendar). */
data class BookingSetup(
    val durationMinutes: Int,
    /** The provider's timezone: every time is shown in it, as the web does. */
    val timezone: String,
    val depositPercent: Int,
    val deposit: Money,
    val sessionPrice: Money,
) {
    val requiresDeposit: Boolean get() = deposit.amountMinor > 0
}

/** One bookable hour; instants are ISO-8601 UTC strings, as apps/api sends them. */
data class Slot(val startsAt: String, val endsAt: String)

data class BookingDay(val date: String, val slots: List<Slot>)

data class BookingWeek(val timezone: String, val from: String, val to: String, val days: List<BookingDay>)

enum class AppointmentStatus {
    HELD, CONFIRMED, COMPLETED, CANCELLED, NO_SHOW, EXPIRED, UNKNOWN;

    companion object {
        fun of(raw: String) = runCatching { valueOf(raw) }.getOrDefault(UNKNOWN)
    }
}

data class Appointment(
    val id: String,
    val status: AppointmentStatus,
    val startsAt: String,
    val endsAt: String,
    val timezone: String,
    val holdExpiresAt: String?,
    val price: Money,
    val deposit: Money,
    val note: String?,
    val contactName: String?,
    val contactEmail: String?,
    val contactAddress: String?,
    val productId: String,
    val productTitle: String,
    val storeName: String?,
    /** The other side: the provider for "mine", the customer for "received". */
    val counterpartyName: String?,
)
