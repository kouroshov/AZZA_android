package com.azza.core.data.pos

import com.azza.core.common.AppError
import com.azza.core.common.Outcome
import com.azza.core.database.pos.PendingSaleEntity
import com.azza.core.database.pos.PendingSaleItemEntity
import com.azza.core.database.pos.PendingSaleWithItems
import com.azza.core.database.pos.PosDao
import com.azza.core.database.pos.SaleDestination
import com.azza.core.datastore.AppPreferences
import com.azza.core.model.PaymentMethod
import com.azza.core.model.QueuedSale
import com.azza.core.model.SaleLine
import com.azza.core.network.api.PosApi
import com.azza.core.network.model.SyncSaleDto
import com.azza.core.network.model.SyncSaleItemDto
import com.azza.core.network.model.SyncSalesRequest
import com.azza.core.network.safeApiCall
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

/**
 * Completed sales on their way to the server (POS-03). A sale is written to
 * the phone FIRST — with its id generated here, which is the server's
 * idempotency key — and sent later by [SaleUploadWorker]. Sending one twice
 * (a retry after the answer was lost, the app killed mid-upload) records it
 * once: the server answers DUPLICATE and the copy here is dropped.
 */
/** What the register needs from the sale queue — an interface so screens can be tested with a fake. */
interface SaleRecorder {
    val pendingCount: Flow<Int>
    val pending: Flow<List<QueuedSale>>
    val rejected: Flow<List<QueuedSale>>

    suspend fun recordSale(
        storeId: String,
        branchId: String,
        paymentMethod: PaymentMethod,
        lines: List<SaleLine>,
        soldAtMillis: Long = System.currentTimeMillis(),
    ): String

    suspend fun dismissRejected(id: String)
}

@Singleton
class SaleQueue @Inject constructor(
    private val dao: PosDao,
    private val preferences: AppPreferences,
    private val scheduler: PosSyncScheduler,
) : SaleRecorder {
    /** Sales still to send. */
    override val pendingCount: Flow<Int> = dao.observePendingCount()

    override val pending: Flow<List<QueuedSale>> = dao.observePending().map { list -> list.map { it.toModel() } }

    /** Sales the server refused, newest first, with its reason. They stay until dismissed. */
    override val rejected: Flow<List<QueuedSale>> = dao.observeRejected().map { list -> list.map { it.toModel() } }

    /**
     * Records a completed sale: the sale, its lines and the local shop-floor
     * counts in one Room transaction, then asks WorkManager to send it.
     * Returns the sale's id. The total is computed here from the lines.
     */
    override suspend fun recordSale(
        storeId: String,
        branchId: String,
        paymentMethod: PaymentMethod,
        lines: List<SaleLine>,
        soldAtMillis: Long,
    ): String {
        require(lines.isNotEmpty()) { "A sale has at least one line." }
        require(lines.all { it.quantity > 0 && it.unitPriceMinor >= 0 }) { "Quantities are positive and prices not negative." }
        val id = UUID.randomUUID().toString()
        dao.recordSale(
            PendingSaleEntity(
                id = id,
                storeId = storeId,
                branchId = branchId,
                deviceId = preferences.posDeviceId(),
                paymentMethod = paymentMethod.name,
                totalAmountMinor = lines.sumOf { it.unitPriceMinor * it.quantity },
                soldAt = soldAtMillis,
                status = PendingSaleEntity.PENDING,
                rejectReason = null,
                rejectDetails = null,
                lastError = null,
                attempts = 0,
            ),
            lines.map { PendingSaleItemEntity(saleId = id, productId = it.productId, name = it.name, quantity = it.quantity, unitPriceAmountMinor = it.unitPriceMinor) },
        )
        scheduler.enqueueUpload()
        return id
    }

    /** Forgets a refused sale once the person has seen it. */
    override suspend fun dismissRejected(id: String) = dao.dismissRejected(id)
}

/** How one upload run ended — what the worker tells WorkManager. */
sealed interface UploadRun {
    /** Everything that could be sent was; [touched] got sales recorded (refresh their catalogs). */
    data class Done(val touched: Set<SaleDestination>) : UploadRun

    /** No answer (offline, server error): try again later, with backoff. */
    data class Retry(val touched: Set<SaleDestination>) : UploadRun
}

/**
 * Sends the queue in batches of at most [BATCH] per store/branch. ACCEPTED
 * and DUPLICATE leave the queue; REJECTED stays, with its reason. A batch
 * that gets no answer is retried as a whole — safe, because the server
 * keys every sale on its id.
 */
@Singleton
class SaleUploader @Inject constructor(
    private val dao: PosDao,
    private val api: PosApi,
    private val json: Json,
) {
    private val mutex = Mutex()

    suspend fun uploadAll(): UploadRun = mutex.withLock {
        val touched = mutableSetOf<SaleDestination>()
        var retry = false
        for (destination in dao.pendingDestinations()) {
            while (true) {
                val batch = dao.pendingBatch(destination.storeId, destination.branchId, BATCH)
                if (batch.isEmpty()) break
                val request = SyncSalesRequest(batch.map { it.toDto() })
                when (val r = safeApiCall(json) { api.syncSales(destination.storeId, destination.branchId, request).data }) {
                    is Outcome.Success -> {
                        val done = mutableListOf<String>()
                        for (result in r.value.results) {
                            val sale = batch.getOrNull(result.index)?.sale ?: continue
                            when (result.result) {
                                "ACCEPTED", "DUPLICATE" -> done += sale.id
                                "REJECTED" -> dao.markRejected(sale.id, result.reason ?: "REJECTED", result.details?.toString())
                            }
                        }
                        if (done.isNotEmpty()) {
                            dao.deleteSales(done)
                            touched += destination
                        }
                        // A batch the server answered but did not resolve (should not
                        // happen) must not loop forever.
                        if (done.isEmpty() && r.value.results.none { it.result == "REJECTED" }) break
                    }
                    is Outcome.Failure -> {
                        val error = r.error
                        dao.markAttemptFailed(batch.map { it.sale.id }, error.describe())
                        val transient = error is AppError.Network ||
                            error is AppError.Unexpected ||
                            (error is AppError.Api && (error.status >= 500 || error.status == 429))
                        if (transient) {
                            retry = true
                        }
                        // Not transient (signed out, another account's store — 404,
                        // a refused batch): these sales wait, untouched, for the
                        // account they belong to. Nothing is deleted.
                        break
                    }
                }
            }
            if (retry) break
        }
        if (retry) UploadRun.Retry(touched) else UploadRun.Done(touched)
    }

    companion object {
        const val BATCH = 100
    }
}

/** ISO 8601 in UTC with milliseconds. Not java.time: the app runs from Android 7. */
internal fun isoUtc(millis: Long): String =
    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(millis))

private fun AppError.describe(): String = when (this) {
    is AppError.Api -> code
    AppError.Network -> "NETWORK"
    AppError.Unauthenticated -> "UNAUTHENTICATED"
    is AppError.Unexpected -> "UNEXPECTED"
}

private fun PendingSaleWithItems.toDto() = SyncSaleDto(
    id = sale.id,
    deviceId = sale.deviceId,
    paymentMethod = sale.paymentMethod,
    totalAmountMinor = sale.totalAmountMinor.toString(),
    soldAt = isoUtc(sale.soldAt),
    items = items.map { SyncSaleItemDto(it.productId, it.quantity, it.unitPriceAmountMinor.toString()) },
)

internal fun PendingSaleWithItems.toModel() = QueuedSale(
    id = sale.id,
    storeId = sale.storeId,
    branchId = sale.branchId,
    paymentMethod = runCatching { PaymentMethod.valueOf(sale.paymentMethod) }.getOrDefault(PaymentMethod.CASH),
    totalMinor = sale.totalAmountMinor,
    soldAtMillis = sale.soldAt,
    lines = items.map { SaleLine(it.productId, it.name, it.quantity, it.unitPriceAmountMinor) },
    rejectReason = sale.rejectReason,
    lastError = sale.lastError,
)
