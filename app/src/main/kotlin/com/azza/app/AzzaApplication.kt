package com.azza.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import javax.inject.Provider
import okhttp3.OkHttpClient

@HiltAndroidApp
class AzzaApplication : Application(), ImageLoaderFactory {

    @Inject lateinit var okHttpClient: Provider<OkHttpClient>

    /**
     * Photos come from apps/api's `/media` route; Coil goes through the same
     * OkHttp client as the API calls — one connection pool, the same
     * User-Agent, and image requests visible in the network log.
     */
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .okHttpClient { okHttpClient.get() }
        .crossfade(true)
        .build()
}
