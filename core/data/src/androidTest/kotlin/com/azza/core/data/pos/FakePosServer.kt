package com.azza.core.data.pos

import com.azza.core.network.api.PosApi
import com.azza.core.network.model.ApiEnvelope
import com.azza.core.network.model.CatalogPageDto
import com.azza.core.network.model.SyncSaleResultDto
import com.azza.core.network.model.SyncSalesRequest
import com.azza.core.network.model.SyncSalesResponseDto
import java.io.IOException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.HttpException
import retrofit2.Response

/**
 * apps/api's sync routes as the register sees them, in memory: a sale id is
 * recorded once (the server's primary key), a second copy is DUPLICATE.
 */
class FakePosServer : PosApi {
    /** Every sale id the "server" recorded, in order — never twice. */
    val recorded = LinkedHashSet<String>()

    /** Products the server does not know in this store: a sale with one is REJECTED. */
    val foreignProducts = mutableSetOf<String>()

    /** The next N sync calls get no answer at all (offline). */
    var offlineCalls = 0

    /** The next sync call is RECORDED, but its answer never reaches the phone. */
    var loseNextAnswer = false

    var syncCalls = 0
    val batchSizes = mutableListOf<Int>()

    /** Catalog pages by the cursor asked with (null = from scratch). */
    val catalogPages = mutableMapOf<String?, CatalogPageDto>()
    val catalogCursorsAsked = mutableListOf<String?>()
    val invalidCursors = mutableSetOf<String>()

    override suspend fun syncSales(storeId: String, branchId: String, body: SyncSalesRequest): ApiEnvelope<SyncSalesResponseDto> {
        syncCalls++
        if (offlineCalls > 0) {
            offlineCalls--
            throw IOException("offline")
        }
        batchSizes += body.sales.size
        val results = body.sales.mapIndexed { index, sale ->
            when {
                sale.items.any { it.productId in foreignProducts } ->
                    SyncSaleResultDto(id = sale.id, index = index, result = "REJECTED", reason = "PRODUCT_NOT_IN_STORE")
                recorded.add(sale.id) -> SyncSaleResultDto(id = sale.id, index = index, result = "ACCEPTED")
                else -> SyncSaleResultDto(id = sale.id, index = index, result = "DUPLICATE")
            }
        }
        if (loseNextAnswer) {
            loseNextAnswer = false
            throw IOException("connection reset after the server answered")
        }
        return ApiEnvelope(true, SyncSalesResponseDto(results))
    }

    override suspend fun catalog(storeId: String, branchId: String, cursor: String?, limit: Int): ApiEnvelope<CatalogPageDto> {
        catalogCursorsAsked += cursor
        if (cursor != null && cursor in invalidCursors) {
            val body = """{"success":false,"error":{"code":"SYNC_CURSOR_INVALID","message":"x"}}"""
            throw HttpException(Response.error<Any>(400, body.toResponseBody("application/json".toMediaType())))
        }
        val page = catalogPages[cursor] ?: error("No page for cursor $cursor")
        return ApiEnvelope(true, page)
    }

    // The inventory routes are not what these tests exercise.
    override suspend fun createItem(storeId: String, body: com.azza.core.network.model.CreateInventoryItemRequest) = error("not used")
    override suspend fun setVisibility(storeId: String, productId: String, body: com.azza.core.network.model.SetVisibilityRequest) = error("not used")
    override suspend fun attachCode(storeId: String, productId: String, body: com.azza.core.network.model.AttachCodeRequest) = error("not used")
    override suspend fun generateCode(storeId: String, productId: String) = error("not used")
    override suspend fun removeCode(storeId: String, productId: String, codeId: String) = error("not used")
    override suspend fun transfer(storeId: String, branchId: String, body: com.azza.core.network.model.StockTransferRequest) = error("not used")
}
