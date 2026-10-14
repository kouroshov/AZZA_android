package com.azza.core.network.di

import com.azza.core.network.ApiBaseUrl
import com.azza.core.network.BareClient
import com.azza.core.network.ClientUserAgent
import com.azza.core.network.api.AuthApi
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
}
