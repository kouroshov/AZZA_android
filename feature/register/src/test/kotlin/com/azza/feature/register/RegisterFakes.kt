package com.azza.feature.register

import com.azza.core.common.Outcome
import com.azza.core.data.pos.RegisterRepository
import com.azza.core.data.pos.SaleRecorder
import com.azza.core.model.DaySummary
import com.azza.core.model.PaymentMethod
import com.azza.core.model.PosStoreRef
import com.azza.core.model.QueuedSale
import com.azza.core.model.SaleLine
import com.azza.core.model.ServerSale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeRegisterRepository : RegisterRepository {
    override val stores = MutableStateFlow(listOf(PosStoreRef("s1", "Corner shop", "b1")))
    override val lastPlace = MutableStateFlow<Pair<String, String>?>(null)
    override suspend fun rememberPlace(storeId: String, branchId: String) {
        lastPlace.value = storeId to branchId
    }

    var summary: Outcome<DaySummary> = Outcome.Success(DaySummary(2, 30_000, 20_000, 10_000, 1, 0, "IRR"))
    val serverSales = mutableListOf<ServerSale>()
    val voided = mutableListOf<String>()

    override suspend fun daySummary(storeId: String, branchId: String) = summary
    override suspend fun todaysSales(storeId: String, branchId: String): Outcome<List<ServerSale>> = Outcome.Success(serverSales.toList())
    override suspend fun voidSale(storeId: String, saleId: String): Outcome<Unit> {
        voided += saleId
        serverSales.replaceAll { if (it.id == saleId) it.copy(voided = true) else it }
        return Outcome.Success(Unit)
    }
}

/** The phone's sale queue, in memory. */
class FakeSaleRecorder : SaleRecorder {
    private val queue = MutableStateFlow<List<QueuedSale>>(emptyList())
    override val pendingCount = queue.map { it.size }
    override val pending = queue
    override val rejected = MutableStateFlow<List<QueuedSale>>(emptyList())

    override suspend fun recordSale(storeId: String, branchId: String, paymentMethod: PaymentMethod, lines: List<SaleLine>, soldAtMillis: Long): String {
        val id = "sale-${queue.value.size + 1}"
        queue.value = queue.value + QueuedSale(id, storeId, branchId, paymentMethod, lines.sumOf { it.unitPriceMinor * it.quantity }, soldAtMillis, lines, null, null)
        return id
    }

    override suspend fun dismissRejected(id: String) {
        rejected.value = rejected.value.filterNot { it.id == id }
    }
}
