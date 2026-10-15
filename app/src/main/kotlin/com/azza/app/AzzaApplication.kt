package com.azza.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.azza.feature.notifications.NotificationPolling
import coil.ImageLoader
import coil.ImageLoaderFactory
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import javax.inject.Provider
import okhttp3.OkHttpClient

@HiltAndroidApp
class AzzaApplication : Application(), ImageLoaderFactory, Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    @Inject lateinit var notificationPolling: NotificationPolling

    // WorkManager is initialised on demand with Hilt's factory, so a worker can
    // be injected (the default initializer is removed in the manifest).
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        notificationPolling.start()
    }

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
