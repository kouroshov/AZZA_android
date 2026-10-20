package com.azza.core.network.api

import com.azza.core.network.model.ApiEnvelope
import com.azza.core.network.model.AttachCodeRequest
import com.azza.core.network.model.CreateInventoryItemRequest
import com.azza.core.network.model.InventoryCodeDto
import com.azza.core.network.model.InventoryItemRefDto
import com.azza.core.network.model.RegisterSaleDto
import com.azza.core.network.model.RegisterSalesPageDto
import com.azza.core.network.model.RegisterSummaryDto
import com.azza.core.network.model.SetVisibilityRequest
import com.azza.core.network.model.StockTransferRequest
import kotlinx.serialization.json.JsonElement
import retrofit2.http.DELETE
import retrofit2.http.PATCH
import com.azza.core.network.model.CatalogPageDto
import com.azza.core.network.model.SyncSalesRequest
import com.azza.core.network.model.SyncSalesResponseDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * The sales register's two sync routes (apps/api POS-02; shapes in the web
 * repo's docs/architecture/pos-register-api.md). Both operate one store:
 * anyone but its owner gets 404 STORE_NOT_FOUND.
 */
interface PosApi {
    @GET("stores/{storeId}/branches/{branchId}/catalog")
    suspend fun catalog(
        @Path("storeId") storeId: String,
        @Path("branchId") branchId: String,
        @Query("cursor") cursor: String?,
        @Query("limit") limit: Int = 200,
    ): ApiEnvelope<CatalogPageDto>

    /** Always 200 with a result per sale; see [SyncSalesResponseDto]. */
    @POST("stores/{storeId}/branches/{branchId}/sales/sync")
    suspend fun syncSales(
        @Path("storeId") storeId: String,
        @Path("branchId") branchId: String,
        @Body body: SyncSalesRequest,
    ): ApiEnvelope<SyncSalesResponseDto>

    // ---------------------------------------------------------------- inventory (POS-01)

    /** A PRIVATE item; 409 CODE_ALREADY_ASSIGNED (details: productId, name) if the code is taken. */
    @POST("stores/{storeId}/inventory/items")
    suspend fun createItem(@Path("storeId") storeId: String, @Body body: CreateInventoryItemRequest): ApiEnvelope<InventoryItemRefDto>

    /** 400 PRODUCT_NOT_PUBLISHABLE with details.missing when making it public. */
    @PATCH("stores/{storeId}/products/{productId}/visibility")
    suspend fun setVisibility(@Path("storeId") storeId: String, @Path("productId") productId: String, @Body body: SetVisibilityRequest): ApiEnvelope<JsonElement>

    @POST("stores/{storeId}/products/{productId}/codes")
    suspend fun attachCode(@Path("storeId") storeId: String, @Path("productId") productId: String, @Body body: AttachCodeRequest): ApiEnvelope<InventoryCodeDto>

    @POST("stores/{storeId}/products/{productId}/codes/generate")
    suspend fun generateCode(@Path("storeId") storeId: String, @Path("productId") productId: String): ApiEnvelope<InventoryCodeDto>

    @DELETE("stores/{storeId}/products/{productId}/codes/{codeId}")
    suspend fun removeCode(@Path("storeId") storeId: String, @Path("productId") productId: String, @Path("codeId") codeId: String)

    @POST("stores/{storeId}/branches/{branchId}/stock/transfer")
    suspend fun transfer(@Path("storeId") storeId: String, @Path("branchId") branchId: String, @Body body: StockTransferRequest): ApiEnvelope<JsonElement>

    // ---------------------------------------------------------------- register reports (POS-02)

    /** One Asia/Tehran day at one branch; [date] YYYY-MM-DD, today when null. */
    @GET("stores/{storeId}/branches/{branchId}/sales/summary")
    suspend fun salesSummary(@Path("storeId") storeId: String, @Path("branchId") branchId: String, @Query("date") date: String? = null): ApiEnvelope<RegisterSummaryDto>

    /** Newest first; [from] inclusive, [to] exclusive (ISO 8601). */
    @GET("stores/{storeId}/branches/{branchId}/sales")
    suspend fun sales(
        @Path("storeId") storeId: String,
        @Path("branchId") branchId: String,
        @Query("from") from: String?,
        @Query("to") to: String?,
        @Query("cursor") cursor: String? = null,
        @Query("take") take: Int = 60,
    ): ApiEnvelope<RegisterSalesPageDto>

    /** Owner only; idempotent. Gives the shop floor its units back. */
    @POST("stores/{storeId}/sales/{saleId}/void")
    suspend fun voidSale(@Path("storeId") storeId: String, @Path("saleId") saleId: String): ApiEnvelope<RegisterSaleDto>
}
