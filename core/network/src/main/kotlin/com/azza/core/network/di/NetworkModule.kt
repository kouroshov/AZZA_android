package com.azza.core.network.di

import com.azza.core.network.ApiBaseUrl
import com.azza.core.network.BareClient
import com.azza.core.network.ClientUserAgent
import com.azza.core.network.api.AccountApi
import com.azza.core.network.api.AuthApi
import com.azza.core.network.api.ReportsApi
import com.azza.core.network.api.SupportApi
import com.azza.core.network.api.MapApi
import com.azza.core.network.api.BookingApi
import com.azza.core.network.api.CategoriesApi
import com.azza.core.network.api.ListingsApi
import com.azza.core.network.api.FollowApi
import com.azza.core.network.api.GeographyApi
import com.azza.core.network.api.StoresApi
import com.azza.core.network.api.CartApi
import com.azza.core.network.api.DiscountsApi
import com.azza.core.network.api.NotificationsApi
import com.azza.core.network.api.OrdersApi
import com.azza.core.network.api.PaymentsApi
import com.azza.core.network.api.SearchApi
import com.azza.core.network.api.SocialApi
import com.azza.core.network.api.DiscoveryApi
import com.azza.core.network.api.ProductsApi
import com.azza.core.network.api.UsersApi
import com.azza.core.network.auth.AuthInterceptor
import com.azza.core.network.auth.TokenAuthenticator
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

@Module
@InstallIn(SingletonComponent::class)
internal object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    private fun userAgentInterceptor(userAgent: String) = Interceptor { chain ->
        chain.proceed(chain.request().newBuilder().header("User-Agent", userAgent).build())
    }

    private fun logging() = HttpLoggingInterceptor().apply {
        // BASIC never prints headers or bodies: no token or password reaches logcat.
        level = HttpLoggingInterceptor.Level.BASIC
    }

    @Provides
    @Singleton
    @BareClient
    fun provideBareClient(@ClientUserAgent userAgent: String): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(userAgentInterceptor(userAgent))
        .addInterceptor(logging())
        .build()

    @Provides
    @Singleton
    fun provideClient(
        @BareClient bare: OkHttpClient,
        authInterceptor: AuthInterceptor,
        authenticator: TokenAuthenticator,
    ): OkHttpClient = bare.newBuilder()
        .addInterceptor(authInterceptor)
        .authenticator(authenticator)
        .build()

    private fun retrofit(baseUrl: String, client: OkHttpClient, json: Json): Retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    @Provides
    @Singleton
    fun provideRetrofit(@ApiBaseUrl baseUrl: String, client: OkHttpClient, json: Json): Retrofit =
        retrofit(baseUrl, client, json)

    /** Login/register/refresh/logout go out without a bearer token and must never trigger a refresh. */
    @Provides
    @Singleton
    fun provideAuthApi(@ApiBaseUrl baseUrl: String, @BareClient client: OkHttpClient, json: Json): AuthApi =
        retrofit(baseUrl, client, json).create(AuthApi::class.java)

    @Provides
    @Singleton
    fun provideUsersApi(retrofit: Retrofit): UsersApi = retrofit.create(UsersApi::class.java)

    @Provides
    @Singleton
    fun provideDiscoveryApi(retrofit: Retrofit): DiscoveryApi = retrofit.create(DiscoveryApi::class.java)

    @Provides
    @Singleton
    fun provideProductsApi(retrofit: Retrofit): ProductsApi = retrofit.create(ProductsApi::class.java)

    @Provides
    @Singleton
    fun provideCartApi(retrofit: Retrofit): CartApi = retrofit.create(CartApi::class.java)

    @Provides
    @Singleton
    fun provideOrdersApi(retrofit: Retrofit): OrdersApi = retrofit.create(OrdersApi::class.java)

    @Provides
    @Singleton
    fun providePaymentsApi(retrofit: Retrofit): PaymentsApi = retrofit.create(PaymentsApi::class.java)

    @Provides
    @Singleton
    fun provideDiscountsApi(retrofit: Retrofit): DiscountsApi = retrofit.create(DiscountsApi::class.java)

    @Provides
    @Singleton
    fun provideSearchApi(retrofit: Retrofit): SearchApi = retrofit.create(SearchApi::class.java)

    @Provides
    @Singleton
    fun provideSocialApi(retrofit: Retrofit): SocialApi = retrofit.create(SocialApi::class.java)

    @Provides
    @Singleton
    fun provideNotificationsApi(retrofit: Retrofit): NotificationsApi = retrofit.create(NotificationsApi::class.java)

    @Provides
    @Singleton
    fun provideStoresApi(retrofit: Retrofit): StoresApi = retrofit.create(StoresApi::class.java)

    @Provides
    @Singleton
    fun provideFollowApi(retrofit: Retrofit): FollowApi = retrofit.create(FollowApi::class.java)

    @Provides
    @Singleton
    fun provideGeographyApi(retrofit: Retrofit): GeographyApi = retrofit.create(GeographyApi::class.java)

    @Provides
    @Singleton
    fun provideCategoriesApi(retrofit: Retrofit): CategoriesApi = retrofit.create(CategoriesApi::class.java)

    @Provides
    @Singleton
    fun provideListingsApi(retrofit: Retrofit): ListingsApi = retrofit.create(ListingsApi::class.java)

    @Provides
    @Singleton
    fun provideBookingApi(retrofit: Retrofit): BookingApi = retrofit.create(BookingApi::class.java)

    @Provides
    @Singleton
    fun provideMapApi(retrofit: Retrofit): MapApi = retrofit.create(MapApi::class.java)

    @Provides
    @Singleton
    fun provideAccountApi(retrofit: Retrofit): AccountApi = retrofit.create(AccountApi::class.java)

    @Provides
    @Singleton
    fun provideSupportApi(retrofit: Retrofit): SupportApi = retrofit.create(SupportApi::class.java)

    @Provides
    @Singleton
    fun provideReportsApi(retrofit: Retrofit): ReportsApi = retrofit.create(ReportsApi::class.java)
}
