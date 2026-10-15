package com.azza.app.di

import com.azza.app.BuildConfig
import com.azza.core.common.WebLinks
import com.azza.core.network.ApiBaseUrl
import com.azza.core.network.ClientUserAgent
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Environment facts only :app knows: which apps/api to talk to, and who we are. */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @ApiBaseUrl
    fun provideApiBaseUrl(): String = BuildConfig.API_BASE_URL

    /** The website: what Share sends and which links the app opens itself. */
    @Provides
    @Singleton
    fun provideWebLinks(): WebLinks = WebLinks(BuildConfig.WEB_BASE_URL)

    @Provides
    @ClientUserAgent
    fun provideUserAgent(): String = "AZZA-Android/${BuildConfig.VERSION_NAME}"
}
