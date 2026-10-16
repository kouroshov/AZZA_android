package com.azza.core.data

import android.net.Uri
import com.azza.core.common.AppError
import com.azza.core.common.Outcome
import com.azza.core.common.map
import com.azza.core.model.Page
import com.azza.core.network.MediaUrlResolver
import com.azza.core.network.api.AppealsApi
import com.azza.core.network.api.BookingApi
import com.azza.core.network.api.BookingSettingsRequest
import com.azza.core.network.api.BranchDetailDto
import com.azza.core.network.api.BranchRequest
import com.azza.core.network.api.CalendarApi
import com.azza.core.network.api.CalendarDto
import com.azza.core.network.api.CalendarExceptionDto
import com.azza.core.network.api.CalendarRequest
import com.azza.core.network.api.ClosureDto
import com.azza.core.network.api.ClosuresRequest
import com.azza.core.network.api.CreateAppealRequest
import com.azza.core.network.api.HoursDto
import com.azza.core.network.api.HoursRequest
import com.azza.core.network.api.LocationRequest
import com.azza.core.network.api.OwnListingsApi
import com.azza.core.network.api.OwnStoreDto
import com.azza.core.network.api.StoreManageApi
import com.azza.core.network.api.StoreRequest
import com.azza.core.network.api.WindowDto
import com.azza.core.network.model.SetQuantityRequest
import com.azza.core.network.safeApiCall
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

/** Why a store is closed: ADMIN_SUSPENDED (a decision about this store — appealable) or OWNER_SUSPENDED (with its owner's account). */
data class StoreHold(val cause: String, val reason: String?, val at: String) {
    val appealable: Boolean get() = cause == "ADMIN_SUSPENDED"
}

data class Occupation(val id: String, val nameEn: String, val nameFa: String) {
    fun name(language: String) = if (language == "fa" || language == "ckb") nameFa.ifBlank { nameEn } else nameEn.ifBlank { nameFa }
}

/** A store as its owner sees it. */
data class ManagedStore(
    val id: String,
    val slug: String,
    val name: String,
    val description: String?,
    val phone: String?,
    val followerCount: Int,
    val avatarUrl: String?,
    val occupation: Occupation?,
    val primaryBranchId: String?,
    val openStatus: String,
    val status: String,
    val hold: StoreHold?,
    val hasLocation: Boolean,
)

/** What the store form edits. */
data class StoreDraft(
    val name: String = "",
    val handle: String = "",
    val phone: String = "",
    val description: String = "",
    val occupation: Occupation? = null,
)

/** One day of a branch's week. Minutes from midnight; [closed] = shut all day. */
data class DayHours(val dayOfWeek: Int, val opens: Int = 9 * 60, val closes: Int = 18 * 60, val closed: Boolean = true)

data class Closure(val date: String, val reason: String?)

data class BranchLocation(val latitude: Double, val longitude: Double, val address: String, val areaId: String)

data class Branch(
    val id: String,
    val storeId: String,
    val name: String,
    /** Its address on the website (/stores/<store>/branches/<slug>). */
    val slug: String = "",
    val status: String,
    val phone: String?,
    val timezone: String,
    val openStatus: String,
    val location: BranchLocation?,
    /** Always seven days, Sunday (0) first, as apps/api numbers them. */
    val week: List<DayHours>,
    val closures: List<Closure>,
)

/** A store listing and how many of it this branch holds. */
data class StockLine(val productId: String, val title: String, val quantity: Int, val reserved: Int)

data class TimeWindow(val dayOfWeek: Int, val start: Int, val end: Int)

/** A calendar exception. The app edits the CLOSED ones (days off); EXTRA_OPEN ones set on the web are kept as they are. */
data class DayOff(val startsAt: String, val endsAt: String, val reason: String?, val kind: String = "CLOSED")

data class Timetable(val timezone: String, val windows: List<TimeWindow>, val daysOff: List<DayOff>, val services: List<TimetableService>)

data class TimetableService(val productId: String, val title: String, val durationMinutes: Int)

/** What differs per timed service (PUT /products/:id/booking). */
data class BookingSettings(
    val durationMinutes: Int = 60,
    val slotIntervalMinutes: Int = 60,
    val bufferBeforeMinutes: Int = 0,
    val bufferAfterMinutes: Int = 0,
    val minNoticeMinutes: Int = 60,
    val maxAdvanceDays: Int = 60,
    val depositPercent: Int = 0,
)

/**
 * Phase 5b: the stores the signed-in person owns and everything about
 * running one — details, photo, branches (hours, closures, location,
 * stock), the shared timetable, per-service booking settings, followers,
 * and the appeal when an admin suspended it. Every route is scoped to the
 * caller by apps/api; someone else's store answers 404 STORE_NOT_FOUND.
 */
@Singleton
class StoresRepository @Inject constructor(
    private val api: StoreManageApi,
    private val calendar: CalendarApi,
    private val booking: BookingApi,
    private val appeals: AppealsApi,
    private val own: OwnListingsApi,
    private val media: MediaUrlResolver,
    private val photos: PhotoCompressor,
    private val json: Json,
) {
    /** Creating a store needs an active seller subscription (apps/api's SellerEntitlementGuard). */
    suspend fun canCreate(): Outcome<Boolean> = safeApiCall(json) { api.eligibility().data }.map { it.eligible }

    suspend fun mine(): Outcome<List<ManagedStore>> = safeApiCall(json) { api.mine().data }.map { list -> list.map { it.toModel() } }

    suspend fun store(id: String): Outcome<ManagedStore> = mine().let { r ->
        when (r) {
            is Outcome.Success -> r.value.firstOrNull { it.id == id }?.let { Outcome.Success(it) }
                ?: Outcome.Failure(AppError.Api(404, "STORE_NOT_FOUND", "", null))
            is Outcome.Failure -> r
        }
    }

    suspend fun create(draft: StoreDraft): Outcome<ManagedStore> = safeApiCall(json) {
        api.create(
            StoreRequest(
                name = draft.name.trim(),
                description = draft.description.trim().ifEmpty { null },
                branchPhone = draft.phone.trim().ifEmpty { null },
                username = draft.handle.trim().lowercase().ifEmpty { null },
                occupationId = draft.occupation?.id,
            ),
        ).data
    }.map { it.toModel() }

    /** Sends every field: "" clears an optional one (a null would be dropped and change nothing). */
    suspend fun update(storeId: String, draft: StoreDraft, handleChanged: Boolean): Outcome<Unit> = safeApiCall(json) {
        api.update(
            storeId,
            StoreRequest(
                name = draft.name.trim(),
                description = draft.description.trim(),
                branchPhone = draft.phone.trim(),
                username = draft.handle.trim().lowercase().takeIf { handleChanged },
                occupationId = draft.occupation?.id,
            ),
        )
    }.map { }

    suspend fun uploadAvatar(storeId: String, uri: String): Outcome<Unit> {
        val bytes = withContext(Dispatchers.IO) { photos.toJpeg(Uri.parse(uri)) }
            ?: return Outcome.Failure(AppError.Unexpected(IllegalArgumentException("unreadable photo")))
        val part = MultipartBody.Part.createFormData("file", "store.jpg", bytes.toRequestBody("image/jpeg".toMediaType()))
        return safeApiCall(json) { api.uploadAvatar(storeId, part) }.map { }
    }

    suspend fun removeAvatar(storeId: String): Outcome<Unit> = safeApiCall(json) { api.removeAvatar(storeId) }.map { }

    suspend fun searchOccupations(q: String): Outcome<List<Occupation>> =
        safeApiCall(json) { api.searchOccupations(q.trim()).data }.map { list -> list.map { Occupation(it.id, it.name.en, it.name.fa) } }

    suspend fun followers(storeId: String, cursor: String?): Outcome<Page<FollowEntry>> = safeApiCall(json) { api.followers(storeId, cursor).data }.map { p ->
        Page(p.items.map { FollowEntry(it.id, it.displayName.ifBlank { it.username }, it.username, null) }, p.nextCursor, p.hasMore)
    }

    suspend fun appealSuspension(storeId: String, message: String): Outcome<Appeal> =
        safeApiCall(json) { appeals.create(CreateAppealRequest("STORE", storeId, message.trim())).data }
            .map { Appeal(it.id, it.holdAt, it.message, it.status, it.createdAt, it.decidedAt, it.decisionNote) }

    suspend fun appeals(storeId: String): Outcome<List<Appeal>> = safeApiCall(json) { appeals.mine(storeId = storeId).data }
        .map { list -> list.map { Appeal(it.id, it.holdAt, it.message, it.status, it.createdAt, it.decidedAt, it.decisionNote) } }

    // ---- branches

    suspend fun branches(storeId: String): Outcome<List<Branch>> = safeApiCall(json) { api.branches(storeId).data }.map { list -> list.map { it.toModel() } }

    suspend fun branch(storeId: String, branchId: String): Outcome<Branch> = safeApiCall(json) { api.branch(storeId, branchId).data }.map { it.toModel() }

    suspend fun createBranch(storeId: String, name: String, phone: String): Outcome<Branch> =
        safeApiCall(json) { api.createBranch(storeId, BranchRequest(name.trim(), phone.trim().ifEmpty { null })).data }.map { it.toModel() }

    suspend fun updateBranch(storeId: String, branchId: String, name: String, phone: String, active: Boolean): Outcome<Unit> =
        safeApiCall(json) { api.updateBranch(storeId, branchId, BranchRequest(name.trim(), phone.trim(), if (active) "ACTIVE" else "INACTIVE")) }.map { }

    suspend fun setHours(storeId: String, branchId: String, week: List<DayHours>): Outcome<Unit> = safeApiCall(json) {
        api.setHours(
            storeId,
            branchId,
            HoursRequest(week.map { d -> if (d.closed) HoursDto(d.dayOfWeek, isClosed = true) else HoursDto(d.dayOfWeek, d.opens, d.closes, false) }),
        )
    }.map { }

    suspend fun setClosures(storeId: String, branchId: String, closures: List<Closure>): Outcome<Unit> =
        safeApiCall(json) { api.setClosures(storeId, branchId, ClosuresRequest(closures.map { ClosureDto(it.date, it.reason?.ifBlank { null }) })) }.map { }

    suspend fun setLocation(storeId: String, branchId: String, location: BranchLocation): Outcome<Unit> = safeApiCall(json) {
        api.setLocation(storeId, branchId, LocationRequest(location.latitude, location.longitude, location.address.trim(), location.areaId))
    }.map { }

    /**
     * Every listing of the store with this branch's count — including ones
     * not stocked here yet (0): the branch's own list only knows the rows
     * it already has, and stock is set per listing.
     */
    suspend fun stock(storeId: String, branchId: String): Outcome<List<StockLine>> {
        // apps/api pages both lists at most 60 at a time.
        val held = mutableMapOf<String, com.azza.core.network.api.BranchStockDto>()
        var stockCursor: String? = null
        do {
            val page = when (val r = safeApiCall(json) { api.branchStock(storeId, branchId, stockCursor).data }) {
                is Outcome.Success -> r.value
                is Outcome.Failure -> return r
            }
            page.items.forEach { held[it.productId] = it }
            stockCursor = page.nextCursor.takeIf { page.hasMore }
        } while (stockCursor != null)
        val listings = mutableListOf<StockLine>()
        var cursor: String? = null
        do {
            val page = when (val r = safeApiCall(json) { own.list(storeId = storeId, cursor = cursor, take = 60).data }) {
                is Outcome.Success -> r.value
                is Outcome.Failure -> return r
            }
            page.items.filter { it.offeringKind == "GOOD" }.forEach { p ->
                val line = held[p.id]
                listings += StockLine(p.id, p.title, line?.quantity ?: 0, line?.reserved ?: 0)
            }
            cursor = page.nextCursor.takeIf { page.hasMore }
        } while (cursor != null)
        return Outcome.Success(listings)
    }

    suspend fun setQuantity(storeId: String, branchId: String, productId: String, quantity: Int): Outcome<Unit> =
        safeApiCall(json) { api.setQuantity(storeId, branchId, productId, SetQuantityRequest(quantity)) }

    /** One listing's stock at every branch of its store (0 where it is not stocked). */
    suspend fun stockByBranch(storeId: String, productId: String): Outcome<List<Pair<Branch, StockLine>>> {
        val branches = when (val r = branches(storeId)) {
            is Outcome.Success -> r.value
            is Outcome.Failure -> return r
        }
        return Outcome.Success(
            branches.map { b ->
                val line = (safeApiCall(json) { api.stockAt(storeId, b.id, productId).data } as? Outcome.Success)?.value
                b to StockLine(productId, "", line?.quantity ?: 0, line?.reserved ?: 0)
            },
        )
    }

    // ---- the timetable (the store's; per-service settings)

    suspend fun timetable(storeId: String): Outcome<Timetable> = safeApiCall(json) { calendar.get(storeId).data }.map { it.toModel() }

    suspend fun saveTimetable(storeId: String, t: Timetable): Outcome<Timetable> = safeApiCall(json) {
        calendar.update(
            CalendarRequest(
                timezone = t.timezone,
                availability = t.windows.map { WindowDto(it.dayOfWeek, it.start, it.end) },
                exceptions = t.daysOff.map { CalendarExceptionDto(it.kind, it.startsAt, it.endsAt, it.reason?.ifBlank { null }) },
            ),
            storeId,
        ).data
    }.map { it.toModel() }

    suspend fun bookingSettings(productId: String): Outcome<BookingSettings> = safeApiCall(json) { booking.setup(productId).data }.map {
        BookingSettings(
            durationMinutes = it.durationMinutes,
            slotIntervalMinutes = it.slotIntervalMinutes.takeIf { v -> v > 0 } ?: it.durationMinutes,
            bufferBeforeMinutes = it.bufferBeforeMinutes,
            bufferAfterMinutes = it.bufferAfterMinutes,
            minNoticeMinutes = it.minNoticeMinutes,
            maxAdvanceDays = it.maxAdvanceDays,
            depositPercent = it.depositPercent,
        )
    }

    suspend fun saveBookingSettings(productId: String, s: BookingSettings): Outcome<Unit> = safeApiCall(json) {
        calendar.saveSettings(
            productId,
            BookingSettingsRequest(s.durationMinutes, s.slotIntervalMinutes, s.bufferBeforeMinutes, s.bufferAfterMinutes, s.minNoticeMinutes, s.maxAdvanceDays, s.depositPercent),
        )
    }.map { }

    // ---- mapping

    private fun OwnStoreDto.toModel() = ManagedStore(
        id = id,
        slug = slug,
        name = name,
        description = description,
        phone = branchPhone,
        followerCount = followerCount,
        avatarUrl = media.resolve(avatarUrl),
        occupation = occupation?.let { Occupation(it.id, it.name.en, it.name.fa) },
        primaryBranchId = primaryBranchId,
        openStatus = openStatus,
        status = status,
        hold = moderation?.let { StoreHold(it.cause, it.reason, it.at) },
        hasLocation = location != null,
    )

    private fun BranchDetailDto.toModel(): Branch {
        val byDay = hours.associateBy { it.dayOfWeek }
        return Branch(
            id = id,
            storeId = storeId,
            name = name,
            slug = slug,
            status = status,
            phone = phone,
            timezone = timezone,
            openStatus = openStatus,
            location = location?.let { BranchLocation(it.latitude, it.longitude, it.address, it.areaId) },
            week = (0..6).map { d ->
                byDay[d]?.let { h ->
                    if (h.isClosed || h.opensMinute == null || h.closesMinute == null) DayHours(d, closed = true)
                    else DayHours(d, h.opensMinute!!, h.closesMinute!!, closed = false)
                } ?: DayHours(d, closed = true)
            },
            closures = upcomingClosures.map { Closure(it.date.take(10), it.reason) },
        )
    }

    private fun CalendarDto.toModel() = Timetable(
        timezone = timezone,
        windows = availability.map { TimeWindow(it.dayOfWeek, it.startMinute, it.endMinute) }.sortedWith(compareBy({ it.dayOfWeek }, { it.start })),
        daysOff = exceptions.map { DayOff(it.startsAt, it.endsAt, it.reason, it.kind) },
        services = services.map { TimetableService(it.productId, it.title, it.durationMinutes) },
    )
}
