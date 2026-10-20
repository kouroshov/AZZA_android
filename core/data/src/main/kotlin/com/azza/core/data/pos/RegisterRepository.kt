package com.azza.core.data.pos

import com.azza.core.common.Outcome
import com.azza.core.common.map
import com.azza.core.datastore.AppPreferences
import com.azza.core.model.DaySummary
import com.azza.core.model.PaymentMethod
import com.azza.core.model.PosStoreRef
import com.azza.core.model.SaleLine
import com.azza.core.model.ServerSale
import com.azza.core.network.api.PosApi
import com.azza.core.network.model.RegisterSaleDto
import com.azza.core.network.safeApiCall
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.Calendar
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

/**
 * The sales register's own reads and writes (POS-05), beside the catalog
 * ([InventoryRepository]) and the queue ([SaleRecorder]): which stores the
 * person runs (saved at sign-in, so it works offline), where the register
 * was last used, and the server's view of a day — which needs a network.
 */
interface RegisterRepository {
    /** The stores the signed-in person runs; empty = no Register tab. */
    val stores: Flow<List<PosStoreRef>>

    /** (storeId, branchId) where the register was last used. */
    val lastPlace: Flow<Pair<String, String>?>
    suspend fun rememberPlace(storeId: String, branchId: String)

    suspend fun daySummary(storeId: String, branchId: String): Outcome<DaySummary>

    /** Today's sales (Asia/Tehran) at the branch, newest first. */
    suspend fun todaysSales(storeId: String, branchId: String): Outcome<List<ServerSale>>

    suspend fun voidSale(storeId: String, saleId: String): Outcome<Unit>
}

@Singleton
class DefaultRegisterRepository @Inject constructor(
    private val api: PosApi,
    private val preferences: AppPreferences,
    private val json: Json,
) : RegisterRepository {
    override val stores: Flow<List<PosStoreRef>> =
        preferences.posStores.map { list -> list.map { (id, name, branch) -> PosStoreRef(id, name, branch) } }

    override val lastPlace: Flow<Pair<String, String>?> = preferences.posLastRegister

    override suspend fun rememberPlace(storeId: String, branchId: String) = preferences.setPosLastRegister(storeId, branchId)

    override suspend fun daySummary(storeId: String, branchId: String): Outcome<DaySummary> =
        safeApiCall(json) { api.salesSummary(storeId, branchId).data }.map { s ->
            DaySummary(
                count = s.count,
                totalMinor = s.totalAmountMinor,
                cashMinor = s.totalByPaymentMethod["CASH"]?.toLongOrNull() ?: 0,
                cardMinor = s.totalByPaymentMethod["CARD_TERMINAL"]?.toLongOrNull() ?: 0,
                flagged = s.flaggedCount,
                voided = s.voidedCount,
                currency = s.currency,
            )
        }

    override suspend fun todaysSales(storeId: String, branchId: String): Outcome<List<ServerSale>> {
        val (from, to) = tehranToday()
        return safeApiCall(json) { api.sales(storeId, branchId, isoUtc(from), isoUtc(to)).data }.map { page -> page.items.map { it.toModel() } }
    }

    override suspend fun voidSale(storeId: String, saleId: String): Outcome<Unit> =
        safeApiCall(json) { api.voidSale(storeId, saleId) }.map { }

    /** Today's bounds in Asia/Tehran, the server's calendar for the day summary. */
    private fun tehranToday(): Pair<Long, Long> {
        val day = Calendar.getInstance(TimeZone.getTimeZone("Asia/Tehran")).apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val start = day.timeInMillis
        day.add(Calendar.DAY_OF_MONTH, 1)
        return start to day.timeInMillis
    }
}

private fun RegisterSaleDto.toModel() = ServerSale(
    id = id,
    paymentMethod = runCatching { PaymentMethod.valueOf(paymentMethod) }.getOrDefault(PaymentMethod.CASH),
    totalMinor = totalAmountMinor,
    currency = currency,
    soldAtIso = soldAt,
    voided = status == "VOIDED",
    flagged = priceMismatch || stockWentNegative || clockSkew,
    lines = items.map { SaleLine(it.productId, it.title, it.quantity, it.unitPriceAmountMinor) },
)

@Module
@InstallIn(SingletonComponent::class)
abstract class RegisterModule {
    @Binds
    abstract fun register(impl: DefaultRegisterRepository): RegisterRepository

    @Binds
    abstract fun recorder(impl: SaleQueue): SaleRecorder
}
