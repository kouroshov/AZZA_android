package com.azza.core.network.api

import kotlinx.serialization.Serializable
import com.azza.core.network.model.AddCartItemRequest
import com.azza.core.network.model.AreaDto
import com.azza.core.network.model.PlaceHitDto
import com.azza.core.network.model.FollowingSetDto
import com.azza.core.network.model.ProductsPageDto
import com.azza.core.network.model.PublicProfileDto
import com.azza.core.network.model.StoreDto
import com.azza.core.network.model.ApiEnvelope
import com.azza.core.network.model.CartDto
import com.azza.core.network.model.CheckoutRequest
import com.azza.core.network.model.CheckoutResultDto
import com.azza.core.network.model.FavoritesPageDto
import com.azza.core.network.model.MarkReadRequest
import com.azza.core.network.model.NotificationsPageDto
import com.azza.core.network.model.OrdersPageDto
import com.azza.core.network.model.PaymentRequest
import com.azza.core.network.model.PlacedOrderDetailDto
import com.azza.core.network.model.PlacedOrderSummaryDto
import com.azza.core.network.model.ReceivedOrderDetailDto
import com.azza.core.network.model.ReceivedOrderSummaryDto
import com.azza.core.network.model.SearchPageDto
import com.azza.core.network.model.SearchProductDto
import com.azza.core.network.model.SearchStoreDto
import com.azza.core.network.model.SearchUserDto
import com.azza.core.network.model.UnreadCountDto
import com.azza.core.network.model.UpdateCartItemRequest
import com.azza.core.network.model.ValidateDiscountRequest
import com.azza.core.network.model.ValidateDiscountResponse
import com.azza.core.network.model.AuthResultDto
import com.azza.core.network.model.FeedPageDto
import com.azza.core.network.model.LoginRequest
import com.azza.core.network.model.MeDto
import com.azza.core.network.model.ProductDto
import com.azza.core.network.model.RefreshRequest
import com.azza.core.network.model.RegisterRequest
import com.azza.core.network.model.TokenPairDto
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.PUT
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

    @GET("users/by-username/{username}")
    suspend fun byUsername(@Path("username") username: String): ApiEnvelope<PublicProfileDto>
}

interface StoresApi {
    /** By id or slug. Public; a suspended or unknown store is 404 STORE_NOT_FOUND. */
    @GET("stores/{idOrSlug}")
    suspend fun get(@Path("idOrSlug") idOrSlug: String): ApiEnvelope<StoreDto>
}

interface FollowApi {
    @POST("users/{id}/follow")
    suspend fun followUser(@Path("id") userId: String)

    @DELETE("users/{id}/follow")
    suspend fun unfollowUser(@Path("id") userId: String)

    @POST("stores/{id}/follow")
    suspend fun followStore(@Path("id") storeId: String)

    @DELETE("stores/{id}/follow")
    suspend fun unfollowStore(@Path("id") storeId: String)

    @GET("users/me/following")
    suspend fun following(): ApiEnvelope<FollowingSetDto>
}

interface GeographyApi {
    @GET("geography/provinces")
    suspend fun provinces(): ApiEnvelope<List<AreaDto>>

    @GET("geography/provinces/{provinceId}/cities")
    suspend fun cities(@Path("provinceId") provinceId: String): ApiEnvelope<List<AreaDto>>

    /** Provinces and cities by name (the web's city search); `source = nominatim` hits are not AZZA areas. */
    @GET("geography/search")
    suspend fun search(@Query("q") q: String): ApiEnvelope<List<PlaceHitDto>>
}

interface DiscoveryApi {
    @GET("explore")
    suspend fun explore(
        @Query("cursor") cursor: String? = null,
        @Query("take") take: Int = 20,
        @Query("categoryId") categoryId: String? = null,
        @Query("q") query: String? = null,
    ): ApiEnvelope<FeedPageDto>

    /** Posts from the people and stores the caller follows. Authenticated. */
    @GET("feed/following")
    suspend fun following(@Query("cursor") cursor: String? = null, @Query("take") take: Int = 20): ApiEnvelope<FeedPageDto>

    /** [cities]: comma-separated city ids (or slugs). */
    @GET("cities/feed")
    suspend fun cities(
        @Query("cities") cities: String,
        @Query("cursor") cursor: String? = null,
        @Query("take") take: Int = 20,
    ): ApiEnvelope<FeedPageDto>
}

interface ProductsApi {
    @GET("products/{id}")
    suspend fun byId(
        @Path("id") id: String,
        /**
         * The discovery surface the view came from — one of apps/api's
         * TrackingQueryDto SOURCES (GLOBAL_EXPLORE, CITY_FEED, FOLLOWING_FEED,
         * MAP, STORE_PAGE, PROFILE). Anything else is a 400, so null when unknown.
         */
        @Query("source") source: String? = null,
    ): ApiEnvelope<ProductDto>

    /**
     * Public, ACTIVE listings, newest first. `storeId`: one store's page;
     * `sellerId` + `personal=true`: one person's personal page (sellerId
     * alone also matches their stores' listings).
     */
    /** A label's EAN-13 → the listing it belongs to (public; the same visibility as [byId]). */
    @GET("products/barcode/{code}")
    suspend fun byBarcode(@Path("code") code: String): ApiEnvelope<BarcodeMatchDto>

    @GET("products")
    suspend fun list(
        @Query("sellerId") sellerId: String? = null,
        @Query("storeId") storeId: String? = null,
        @Query("personal") personal: Boolean? = null,
        @Query("cursor") cursor: String? = null,
        @Query("take") take: Int = 30,
    ): ApiEnvelope<ProductsPageDto>
}

interface CartApi {
    @GET("cart")
    suspend fun get(): ApiEnvelope<CartDto>

    @POST("cart/items")
    suspend fun add(@Body body: AddCartItemRequest): ApiEnvelope<CartDto>

    /** Quantity 0 removes the line. */
    @PUT("cart/items/{productId}")
    suspend fun update(@Path("productId") productId: String, @Body body: UpdateCartItemRequest): ApiEnvelope<CartDto>

    @DELETE("cart/items/{productId}")
    suspend fun remove(@Path("productId") productId: String): ApiEnvelope<CartDto>
}

interface OrdersApi {
    @POST("orders/checkout")
    suspend fun checkout(@Body body: CheckoutRequest): ApiEnvelope<CheckoutResultDto>

    @GET("orders/placed")
    suspend fun placed(@Query("cursor") cursor: String?, @Query("take") take: Int = 20): ApiEnvelope<OrdersPageDto<PlacedOrderSummaryDto>>

    @GET("orders/placed/{orderId}")
    suspend fun placedDetail(@Path("orderId") orderId: String): ApiEnvelope<PlacedOrderDetailDto>

    @GET("orders/received")
    suspend fun received(
        @Query("cursor") cursor: String?,
        @Query("take") take: Int = 20,
        @Query("context") context: String? = null,
        @Query("storeId") storeId: String? = null,
    ): ApiEnvelope<OrdersPageDto<ReceivedOrderSummaryDto>>

    /** Which selling contexts the caller has received orders in (the filter's choices). */
    @GET("orders/received/contexts")
    suspend fun receivedContexts(): ApiEnvelope<ReceivedContextsDto>

    @GET("orders/received/{sellerOrderId}")
    suspend fun receivedDetail(@Path("sellerOrderId") sellerOrderId: String): ApiEnvelope<ReceivedOrderDetailDto>

    @POST("orders/received/{sellerOrderId}/ship")
    suspend fun markShipped(@Path("sellerOrderId") sellerOrderId: String): ApiEnvelope<kotlinx.serialization.json.JsonElement>
}

interface PaymentsApi {
    /**
     * Dev/test only: marks the caller's own PENDING order paid (apps/api
     * `ENABLE_TEST_PAYMENT_SIMULATION`; 403 TEST_PAYMENTS_DISABLED when off).
     * The web's "Pay now (test)" button calls exactly this.
     */
    @POST("payments/simulate")
    suspend fun simulate(@Body body: PaymentRequest): ApiEnvelope<kotlinx.serialization.json.JsonElement>
}

interface DiscountsApi {
    @POST("discount-codes/validate")
    suspend fun validate(@Body body: ValidateDiscountRequest): ApiEnvelope<ValidateDiscountResponse>
}

interface SearchApi {
    @GET("search/products")
    suspend fun products(
        @Query("q") q: String?,
        @Query("sort") sort: String?,
        @Query("cursor") cursor: String?,
        @Query("categoryId") categoryId: String? = null,
        @Query("condition") condition: String? = null,
        @Query("availability") availability: String? = null,
        /** Major units, as typed (apps/api converts). */
        @Query("priceMin") priceMin: String? = null,
        @Query("priceMax") priceMax: String? = null,
        @Query("take") take: Int = 20,
    ): ApiEnvelope<SearchPageDto<SearchProductDto>>

    @GET("search/users")
    suspend fun users(@Query("q") q: String?, @Query("cursor") cursor: String?, @Query("take") take: Int = 20): ApiEnvelope<SearchPageDto<SearchUserDto>>

    @GET("search/stores")
    suspend fun stores(@Query("q") q: String?, @Query("cursor") cursor: String?, @Query("take") take: Int = 20): ApiEnvelope<SearchPageDto<SearchStoreDto>>
}

interface SocialApi {
    @POST("products/{id}/favorite")
    suspend fun favorite(@Path("id") productId: String)

    @DELETE("products/{id}/favorite")
    suspend fun unfavorite(@Path("id") productId: String)

    @GET("users/me/favorites")
    suspend fun favorites(@Query("cursor") cursor: String?, @Query("take") take: Int = 60): ApiEnvelope<FavoritesPageDto>
}

interface NotificationsApi {
    @GET("notifications")
    suspend fun list(
        @Query("cursor") cursor: String?,
        @Query("take") take: Int = 20,
        @Query("context") context: String? = null,
        @Query("storeId") storeId: String? = null,
    ): ApiEnvelope<NotificationsPageDto>

    @GET("notifications/unread-count")
    suspend fun unreadCount(): ApiEnvelope<UnreadCountDto>

    @POST("notifications/read")
    suspend fun markAllRead(@Body body: MarkReadRequest = MarkReadRequest()): ApiEnvelope<UnreadCountDto>
}

@Serializable
data class BarcodeMatchDto(val productId: String)

@Serializable
data class ReceivedContextStoreDto(val storeId: String, val storeName: String = "")

@Serializable
data class ReceivedContextsDto(val hasPersonalOrders: Boolean = false, val stores: List<ReceivedContextStoreDto> = emptyList())
