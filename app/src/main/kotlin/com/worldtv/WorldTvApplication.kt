package com.worldtv

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.worldtv.core.network.di.MediaClient
import com.worldtv.data.sync.SyncScheduler
import com.worldtv.feature.radio.RadioController
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import okhttp3.OkHttpClient
import okio.Path.Companion.toOkioPath

@HiltAndroidApp
class WorldTvApplication : Application(), Configuration.Provider, SingletonImageLoader.Factory {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    @Inject lateinit var syncScheduler: SyncScheduler

    // Injected for its side effect: RadioController's resume collector only exists once
    // something constructs the singleton. On a phone the now-playing bar does that on
    // every start; on TV nothing does until the radio screen opens, so a viewer who
    // watches video first and then radio would get no resume until then. This does NOT
    // call connect() — binding the media service at process start would start it for
    // nothing.
    @Inject lateinit var radioController: RadioController

    @Inject @field:MediaClient lateinit var okHttpClient: OkHttpClient

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        // KEEP policies make this idempotent, so calling it on every start costs
        // nothing and survives the user clearing app data.
        syncScheduler.scheduleAll()
    }

    /**
     * Coil is capped hard on memory.
     *
     * A TV grid of logos will happily fill the default cache — a quarter of the heap —
     * on a 1 GB box and then start evicting the decoded video frames underneath it.
     */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { okHttpClient }))
            }
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.10)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache").toOkioPath())
                    .maxSizeBytes(64L * 1024 * 1024)
                    .build()
            }
            .build()
}
