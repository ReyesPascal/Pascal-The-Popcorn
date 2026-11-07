package com.arflix.tv

import android.app.ActivityManager
import android.app.Application
import android.content.ComponentCallbacks2
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.WorkManager
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.decode.SvgDecoder
import coil.disk.DiskCache
import coil.imageLoader
import coil.memory.MemoryCache
import com.arflix.tv.network.OkHttpProvider
import com.arflix.tv.data.repository.WatchlistRepository
import com.arflix.tv.data.repository.ProfileManager
import com.arflix.tv.util.AppLogger
import com.arflix.tv.util.DeviceType
import com.arflix.tv.util.detectDeviceType
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.arflix.tv.util.settingsDataStore
import javax.inject.Inject

/**
 * ARVIO TV Application class
 */
@HiltAndroidApp
class ArflixApplication : Application(), Configuration.Provider, ImageLoaderFactory {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    @Volatile
    private var appImageLoader: ImageLoader? = null

    @Inject
    lateinit var workerFactory: HiltWorkerFactory
    @Inject
    lateinit var profileManager: ProfileManager
    @Inject
    lateinit var watchlistRepository: WatchlistRepository

    /**
     * [OkHttpProvider.init] must run before *anything* first touches [OkHttpProvider.client]:
     * the client is built once, lazily, and keeps whatever cache it had at that moment — with no
     * app context there is no disk cache, for the whole process lifetime.
     *
     * `onCreate()` is too late. Hilt performs this Application's field injection inside
     * `super.onCreate()`, and those injected repositories construct Retrofit — and therefore the
     * OkHttpClient — before the first line of our own `onCreate()` body runs. `attachBaseContext`
     * is the first callback where a context exists, so the cache is configured from here.
     */
    override fun attachBaseContext(base: android.content.Context) {
        super.attachBaseContext(base)
        OkHttpProvider.init(this)
        // Personal build: device-side settings (TMDB key, addons) before anything uses them.
        com.arflix.tv.util.PersonalConfig.load(this)
    }

    override fun onCreate() {
        com.arflix.tv.network.initializeNetworkPlatform(this)
        super.onCreate()
        instance = this
        com.arflix.tv.util.PtpDiag.init(this)
        appScope.launch(Dispatchers.IO) { cancelLegacyWatchSyncWork() }

        appScope.launch(Dispatchers.IO) {
            com.arflix.tv.data.local.LegacyDataCleanup.runOnce(this@ArflixApplication)
        }

        // Initialize global DNS provider and user agent from DataStore before network calls.
        appScope.launch(Dispatchers.IO) {
            val prefs = settingsDataStore.data.first()
            val dnsKey = androidx.datastore.preferences.core.stringPreferencesKey(OkHttpProvider.DNS_PROVIDER_PREF_KEY)
            val dnsPref = prefs[dnsKey]
            val provider = OkHttpProvider.parseDnsProvider(dnsPref)
            OkHttpProvider.setDnsProvider(provider)

            val uaKey = androidx.datastore.preferences.core.stringPreferencesKey(OkHttpProvider.USER_AGENT_PREF_KEY)
            val savedUserAgent = prefs[uaKey].orEmpty()
            OkHttpProvider.setCustomUserAgent(savedUserAgent)

            try {
                OkHttpProvider.dns.lookup("image.tmdb.org")
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                AppLogger.recordException(e)
            }
        }

        // Resolve the built-in profile id asynchronously to avoid blocking cold start.

        appScope.launch {
            try {
                profileManager.initialize()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                AppLogger.recordException(e)
            }
            // Preload watchlist cache in background for instant display
            try {
                watchlistRepository.getWatchlistItems()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                AppLogger.recordException(e)
            }
        }
    }

    override fun newImageLoader(): ImageLoader {
        val isTvDevice = detectDeviceType(this) == DeviceType.TV
        val isLowRamDevice = isLowRamDevice()
        return ImageLoader.Builder(this)
            // Use the dedicated Coil HTTP client instead of the main API client.
            // Avoids logging interceptor overhead and connection pool contention.
            .okHttpClient(OkHttpProvider.coilClient)
            .memoryCache {
                MemoryCache.Builder(this)
                    // The 2 GB Android TV dump showed Arvio spending most of
                    // its memory in native bitmap/texture allocations
                    // (255 MB native heap, 77 MB GPU cache). Use a fixed TV
                    // image budget instead of a percent of largeHeap memory so
                    // low-RAM TVs do not drift into zram pressure while rows
                    // are being scrolled.
                    .maxSizeBytes(
                        when {
                            isTvDevice && isLowRamDevice -> 32 * 1024 * 1024
                            isTvDevice -> 48 * 1024 * 1024
                            else -> 64 * 1024 * 1024
                        }
                    )
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(if (isTvDevice) 128L * 1024L * 1024L else 96L * 1024L * 1024L)
                    .build()
            }
            .crossfade(false)
            .respectCacheHeaders(false)
            .allowRgb565(true)
            .bitmapConfig(Bitmap.Config.RGB_565)
            // No global placeholder — card composables use their own surface
            // background color as the visual placeholder. A global placeholder
            // causes a dark-rectangle flash behind transparent clearlogo PNGs
            // on the home hero. Error = transparent so failed loads are invisible
            // (the card surface background is the fallback visual).
            .error(android.R.color.transparent)
            .components {
                add(SvgDecoder.Factory())
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .build()
            .also { appImageLoader = it }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (
            level == ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN ||
            level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE
        ) {
            clearImageMemoryCaches()
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        clearImageMemoryCaches()
    }

    private fun clearImageMemoryCaches() {
        appImageLoader?.memoryCache?.clear()
        // Settings can replace Coil's global loader after DNS changes; clear the
        // active loader too so memory-pressure callbacks always affect what UI uses.
        try {
            imageLoader.memoryCache?.clear()
        } catch (e: Exception) {
            AppLogger.recordException(e)
        }
    }

    private fun isLowRamDevice(): Boolean {
        val activityManager = getSystemService(ACTIVITY_SERVICE) as? ActivityManager
        return activityManager?.isLowRamDevice == true
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(android.util.Log.ASSERT)
            .build()

    /**
     * Cancels the background watch-history sync jobs that 1.0.1 and earlier
     * queued by their original unique names. Harmless when none exist.
     */
    fun cancelLegacyWatchSyncWork() {
        val manager = WorkManager.getInstance(this)
        // Literal identifiers cancel work queued by 1.0.1, whose worker is now deleted.
        manager.cancelUniqueWork("trakt_sync_worker")
        manager.cancelUniqueWork("trakt_sync_on_open")
    }

    companion object {
        lateinit var instance: ArflixApplication
            private set

        fun trimImageMemory() {
            if (::instance.isInitialized) {
                instance.clearImageMemoryCaches()
            }
        }
    }
}
