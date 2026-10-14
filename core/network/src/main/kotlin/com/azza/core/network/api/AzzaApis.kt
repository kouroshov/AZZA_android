package com.azza.core.network.api

import com.azza.core.network.model.ApiEnvelope
import com.azza.core.network.model.AuthResultDto
import com.azza.core.network.model.FeedPageDto
import com.azza.core.network.model.LoginRequest
import com.azza.core.network.model.MeDto
import com.azza.core.network.model.ProductDto
import com.azza.core.network.model.RefreshRequest
import com.azza.core.network.model.RegisterRequest
import com.azza.core.network.model.TokenPairDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

// One interface per apps/api controller. Paths are relative to the base URL,
// which already ends in /api/v1/ — never add a leading slash here, it would
// drop the /api/v1 prefix.

interface AuthApi {
    @POST("auth/login")
    suspend fun login(@Body body: LoginRequest): ApiEnvelope<AuthResultDto>

    @POST("auth/register")
    suspend fun register(@Body body: RegisterRequest): ApiEnvelope<AuthResultDto>

    @POST("auth/refresh")
    suspend fun refresh(@Body body: RefreshRequest): ApiEnvelope<TokenPairDto>

    /** 204 No Content. */
    @POST("auth/logout")
    suspend fun logout(@Body body: RefreshRequest)
}

interface UsersApi {
    @GET("users/me")
    suspend fun me(): ApiEnvelope<MeDto>
}

interface DiscoveryApi {
    @GET("explore")
    suspend fun explore(
        @Query("cursor") cursor: String? = null,
        @Query("take") take: Int = 20,
        @Query("categoryId") categoryId: String? = null,
        @Query("q") query: String? = null,
    ): ApiEnvelope<FeedPageDto>
}

interface ProductsApi {
    @GET("products/{id}")
    suspend fun byId(
        @Path("id") id: String,
        @Query("source") source: String? = "android",
    ): ApiEnvelope<ProductDto>
}
