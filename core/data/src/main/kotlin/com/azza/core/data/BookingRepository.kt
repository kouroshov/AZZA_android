package com.azza.core.data

import com.azza.core.common.Outcome
import com.azza.core.common.map
import com.azza.core.model.Appointment
import com.azza.core.model.AppointmentStatus
import com.azza.core.model.BookingWeek
import com.azza.core.model.BookingDay
import com.azza.core.model.BookingSetup
import com.azza.core.model.Money
import com.azza.core.model.Slot
import com.azza.core.network.api.BookingApi
import com.azza.core.network.model.AppointmentContactRequest
import com.azza.core.network.model.AppointmentDto
import com.azza.core.network.model.CreateBookingRequest
import com.azza.core.network.model.DepositCheckoutRequest
import com.azza.core.network.safeApiCall
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json

/**
 * Appointments, apps/api's booking module. The no-double-booking rule is
 * the database's (an exclusion constraint): availability is a read that may
 * be stale, booking inserts and the constraint decides — a lost race comes
 * back as 409 SLOT_TAKEN, never as a check-then-act here.
 */
/** A provider's calendar over a few days: what the timetable's week grid shows. */
data class CalendarWeek(val timezone: String, val from: String, val to: String, val appointments: List<Appointment>)

@Singleton
class BookingRepository @Inject constructor(
    private val api: BookingApi,
    private val cart: CartRepository,
    private val json: Json,
) {
    suspend fun setup(productId: String): Outcome<BookingSetup> = safeApiCall(json) { api.setup(productId).data }.map {
        BookingSetup(
            durationMinutes = it.durationMinutes,
            timezone = it.timezone,
            depositPercent = it.depositPercent,
            deposit = Money(it.depositAmountMinor, it.currency),
            sessionPrice = Money(it.sessionPriceAmountMinor, it.currency),
        )
    }

    /** A week of free slots starting at [fromDate] (YYYY-MM-DD, in the provider's calendar). */
    suspend fun availability(productId: String, fromDate: String): Outcome<BookingWeek> =
        safeApiCall(json) { api.availability(productId, fromDate).data }.map { a ->
            BookingWeek(a.timezone, a.from, a.to, a.days.map { d -> BookingDay(d.date, d.slots.map { Slot(it.startsAt, it.endsAt) }) })
        }

    suspend fun book(productId: String, startsAt: String, note: String?): Outcome<Appointment> =
        safeApiCall(json) { api.book(CreateBookingRequest(productId, startsAt, note?.trim()?.ifEmpty { null })).data }.map { it.toModel() }

    suspend fun mine(): Outcome<List<Appointment>> = safeApiCall(json) { api.mine().data }.map { list -> list.map { it.toModel() } }

    suspend fun received(): Outcome<List<Appointment>> = safeApiCall(json) { api.received().data }.map { list -> list.map { it.toModel() } }

    /** One calendar's appointments from [from] to [to] (YYYY-MM-DD), a store's or (null) the personal one. */
    suspend fun calendarWeek(storeId: String?, from: String, to: String): Outcome<CalendarWeek> =
        safeApiCall(json) { api.calendarAppointments(storeId, from, to).data }.map { d ->
            CalendarWeek(d.timezone, d.from, d.to, d.appointments.map { it.toModel() })
        }

    suspend fun cancel(id: String): Outcome<Appointment> = safeApiCall(json) { api.cancel(id).data }.map { it.toModel() }

    suspend fun complete(id: String): Outcome<Appointment> = safeApiCall(json) { api.complete(id).data }.map { it.toModel() }

    suspend fun noShow(id: String): Outcome<Appointment> = safeApiCall(json) { api.noShow(id).data }.map { it.toModel() }

    /**
     * The web's deposit flow: the contact details the provider needs, then
     * the deposit as its own order (no address — nothing ships). Returns
     * the order id; the order is paid like any other (the test payment in dev).
     */
    suspend fun startDepositPayment(id: String, name: String, email: String, address: String?): Outcome<String> {
        val contact = safeApiCall(json) {
            api.setContact(id, AppointmentContactRequest(name.trim(), email.trim(), address?.trim()?.ifEmpty { null }))
        }
        if (contact is Outcome.Failure) return contact
        val order = safeApiCall(json) { api.checkoutDeposits(DepositCheckoutRequest(listOf(id))).data }.map { it.id }
        // A deposit checkout never touches the cart (apps/api), but re-read it so the badge is certainly right.
        cart.refresh()
        return order
    }

    private fun AppointmentDto.toModel() = Appointment(
        id = id,
        status = AppointmentStatus.of(status),
        startsAt = startsAt,
        endsAt = endsAt,
        timezone = timezone,
        holdExpiresAt = holdExpiresAt,
        price = Money(priceAmountMinor, currency),
        deposit = Money(depositAmountMinor, currency),
        note = customerNote,
        contactName = contactName,
        contactEmail = contactEmail,
        contactAddress = contactAddress,
        productId = product.id,
        productTitle = product.title,
        storeName = store?.name,
        counterpartyName = counterparty?.displayName?.takeIf { it.isNotBlank() } ?: counterparty?.username,
    )
}
