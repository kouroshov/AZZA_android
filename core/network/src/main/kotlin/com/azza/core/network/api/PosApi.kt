package com.azza.core.network.api

import com.azza.core.network.model.ApiEnvelope
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
}
