package com.azza.core.network.api

import com.azza.core.network.model.ApiEnvelope
import com.azza.core.network.model.BranchDto
import com.azza.core.network.model.CategoryHitDto
import com.azza.core.network.model.CategoryNodeDto
import com.azza.core.network.model.CategorySchemaDto
import com.azza.core.network.model.ProductDto
import com.azza.core.network.model.ProductStatusRequest
import com.azza.core.network.model.SetQuantityRequest
import com.azza.core.network.model.StoreDto
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.MultipartBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

interface CategoriesApi {
    /** The whole tree, roots first, each with its children. */
    @GET("categories")
    suspend fun tree(): ApiEnvelope<List<CategoryNodeDto>>

    @GET("categories/search")
    suspend fun search(@Query("q") q: String, @Query("limit") limit: Int = 20): ApiEnvelope<List<CategoryHitDto>>

    /** The fields a listing in this category takes, for this offering kind (the category's default when null). */
    @GET("taxonomy/categories/{categoryId}/schema")
    suspend fun schema(@Path("categoryId") categoryId: String, @Query("kind") kind: String? = null): ApiEnvelope<CategorySchemaDto>
}

interface ListingsApi {
    /**
     * Body: apps/api's CreateProductDto. Built as a JsonObject because
     * `attributeValues` holds mixed types (text, numbers, booleans, lists).
     */
    @POST("products")
    suspend fun create(@Body body: JsonObject): ApiEnvelope<ProductDto>

    @PATCH("products/{id}")
    suspend fun update(@Path("id") id: String, @Body body: JsonObject): ApiEnvelope<ProductDto>

    /** DRAFT → ACTIVE is publishing; apps/api checks the seller may. */
    @PATCH("products/{id}/status")
    suspend fun setStatus(@Path("id") id: String, @Body body: ProductStatusRequest): ApiEnvelope<JsonElement>

    /** One image per call, multipart field `file`; at most 5 MB, 10 per product. The first becomes the cover. */
    @Multipart
    @POST("products/{productId}/media")
    suspend fun uploadPhoto(@Path("productId") productId: String, @Part file: MultipartBody.Part): ApiEnvelope<JsonElement>

    @retrofit2.http.DELETE("products/{productId}/media/{mediaId}")
    suspend fun deletePhoto(@Path("productId") productId: String, @Path("mediaId") mediaId: String)

    @GET("stores/mine")
    suspend fun myStores(): ApiEnvelope<List<StoreDto>>

    @GET("stores/{storeId}/branches")
    suspend fun branches(@Path("storeId") storeId: String): ApiEnvelope<List<BranchDto>>

    /** A store's stock lives per branch (InventoryLocation). Owner only. */
    @PUT("stores/{storeId}/branches/{branchId}/inventory/{productId}")
    suspend fun setBranchQuantity(
        @Path("storeId") storeId: String,
        @Path("branchId") branchId: String,
        @Path("productId") productId: String,
        @Body body: SetQuantityRequest,
    ): ApiEnvelope<JsonElement>
}
