package com.arflix.tv.util.diag

import android.app.Activity
import android.app.Application
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.Debug
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.arflix.tv.BuildConfig
import com.arflix.tv.util.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Observers only: no preference writes, network requests, or playback interaction. */
internal object AppDiagnostics {
    private val scheduler = Executors.newSingleThreadScheduledExecutor { task -> Thread(task, "PTPDiag-monitor").apply { isDaemon = true } }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val main = Handler(Looper.getMainLooper())
    private val freezeLock = Any()
    private var pendingAt = 0L
    private var frozen = false
    private var started = 0
    private var foreground = false
    private var seenForeground = false
    private var cpuMs = 0L
    private var cpuWallMs = 0L
    @Volatile private var legacyProcessStartMs: Long? = null

    fun install(app: Application) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                PtpDiag.flush()
                val recent = PtpDiag.recent(50)
                PtpDiag.dump("app-crash", error.stackTraceToString(), "thread" to thread.name.take(128), "section" to "stack")
                recent.forEachIndexed { index, line ->
                    PtpDiag.dump("app-crash", line, "thread" to thread.name.take(128), "section" to "recent", "recentIndex" to index)
                }
                PtpDiag.flush()
            } catch (_: Throwable) { }
            finally { previous?.uncaughtException(thread, error) }
        }
        scheduler.execute {
            if (Build.VERSION.SDK_INT < 24) runCatching {
                val ticks = DiagProcessStat.startTicks(File("/proc/self/stat").readText())
                legacyProcessStartMs = ticks * 1000L / android.system.Os.sysconf(android.system.OsConstants._SC_CLK_TCK)
            }
            PtpDiag.i("app-start", "versionName" to BuildConfig.VERSION_NAME, "versionCode" to BuildConfig.VERSION_CODE,
            "buildType" to BuildConfig.BUILD_TYPE, "model" to Build.MODEL, "manufacturer" to Build.MANUFACTURER,
            "sdk" to Build.VERSION.SDK_INT, "start" to "cold", "uptimeMs" to SystemClock.uptimeMillis(),
            "processUptimeMs" to processUptime())
        }
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            private val background = Runnable {
                if (started == 0 && foreground) {
                    foreground = false
                    PtpDiag.i("app-background", "processUptimeMs" to processUptime())
                }
            }
            override fun onActivityStarted(activity: Activity) {
                started++
                main.removeCallbacks(background)
                if (!foreground) {
                    foreground = true
                    PtpDiag.i("app-foreground", "start" to if (seenForeground) "warm" else "cold", "processUptimeMs" to processUptime())
                    seenForeground = true
                }
            }
            override fun onActivityStopped(activity: Activity) { started--; if (started == 0) main.postDelayed(background, 700) }
            override fun onActivityCreated(activity: Activity, state: Bundle?) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
        app.registerComponentCallbacks(object : ComponentCallbacks2 {
            override fun onConfigurationChanged(configuration: Configuration) = Unit
            override fun onLowMemory() { PtpDiag.w("app-low-memory", "callback" to "onLowMemory", "level" to null) }
            override fun onTrimMemory(level: Int) { PtpDiag.w("app-low-memory", "callback" to "onTrimMemory", "level" to level) }
        })
        scheduler.scheduleAtFixedRate({ runCatching { checkMainThread() } }, 0, 1, TimeUnit.SECONDS)
        scheduler.scheduleAtFixedRate({ runCatching { stats() } }, 0, 30, TimeUnit.SECONDS)
        scheduler.execute { runCatching { network(app) } }
        scope.launch {
            runCatching { settings(app) }.onFailure { PtpDiag.w("app-settings", "readError" to it.javaClass.simpleName) }
        }
    }

    private fun processUptime(): Long = if (Build.VERSION.SDK_INT >= 24) SystemClock.elapsedRealtime() - android.os.Process.getStartElapsedRealtime() else legacyProcessStartMs?.let { SystemClock.elapsedRealtime() - it } ?: -1L

    private fun checkMainThread() {
        val freeze = synchronized(freezeLock) {
            val now = SystemClock.uptimeMillis()
            if (pendingAt == 0L) {
                pendingAt = now
                main.post {
                    val duration = synchronized(freezeLock) {
                        val duration = if (frozen) SystemClock.uptimeMillis() - pendingAt else null
                        pendingAt = 0
                        frozen = false
                        duration
                    }
                    if (duration != null) PtpDiag.i("app-freeze-end", "blockedMs" to duration)
                }
                null
            } else if (!frozen && now - pendingAt >= 2000) {
                frozen = true
                (now - pendingAt) to Looper.getMainLooper().thread.stackTrace.take(40).joinToString("\n")
            } else null
        }
        // Never make the recovering main thread wait for the synchronous disk dump.
        if (freeze != null) PtpDiag.dump("app-freeze", freeze.second,
            "blockedMs" to freeze.first, "thread" to Looper.getMainLooper().thread.name)
    }

    private fun stats() {
        val runtime = Runtime.getRuntime()
        val memory = Debug.MemoryInfo().also { Debug.getMemoryInfo(it) }
        val now = SystemClock.elapsedRealtime()
        val stat = File("/proc/self/stat").readText()
        val ticksPerSecond = android.system.Os.sysconf(android.system.OsConstants._SC_CLK_TCK)
        val sample = DiagProcessStat.parse(stat)
        val cpu = sample.first * 1000L / ticksPerSecond
        val percent = if (cpuWallMs != 0L && now > cpuWallMs) (cpu - cpuMs) * 100.0 / (now - cpuWallMs) else null
        cpuMs = cpu
        cpuWallMs = now
        PtpDiag.i("app-stats", "javaHeapUsedBytes" to runtime.totalMemory() - runtime.freeMemory(),
            "javaHeapMaxBytes" to runtime.maxMemory(), "nativeHeapBytes" to Debug.getNativeHeapAllocatedSize(),
            "pssBytes" to memory.totalPss.toLong() * 1024, "cpuPercent" to percent, "threadCount" to sample.second,
            "processUptimeMs" to processUptime())
    }

    private fun network(app: Application) {
        val manager = app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        fun report(network: Network?, caps: NetworkCapabilities?, change: String) {
            var wifi: WifiInfo? = if (Build.VERSION.SDK_INT >= 29) caps?.transportInfo as? WifiInfo else null
            if (wifi == null && caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) {
                wifi = runCatching {
                    if (app.checkSelfPermission(android.Manifest.permission.ACCESS_WIFI_STATE) == android.content.pm.PackageManager.PERMISSION_GRANTED)
                        (app.getSystemService(Context.WIFI_SERVICE) as WifiManager).connectionInfo else null
                }.getOrNull()
            }
            val type = when {
                caps == null -> "none"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "vpn"
                else -> "other"
            }
            PtpDiag.i("app-network", "change" to change, "networkId" to network?.toString(), "type" to type,
                "validated" to (caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true),
                "wifiRssiDbm" to wifi?.rssi?.takeIf { it != -127 }, "wifiLinkSpeedMbps" to wifi?.linkSpeed?.takeIf { it >= 0 })
        }
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { runCatching { report(network, manager.getNetworkCapabilities(network), "available") } }
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) { runCatching { report(network, caps, "capabilities") } }
            override fun onLost(network: Network) { runCatching { report(network, null, "lost") } }
        }
        if (Build.VERSION.SDK_INT >= 24) manager.registerDefaultNetworkCallback(callback)
        else manager.registerNetworkCallback(NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build(), callback)
        val active = manager.activeNetwork
        report(active, active?.let { manager.getNetworkCapabilities(it) }, "initial")
    }

    private suspend fun settings(app: Application) {
        val prefs = app.settingsDataStore.data.first()
        val fixed = listOf("language" to PersonalPrefs.APP_LANGUAGE, "subtitle" to PersonalPrefs.SUBTITLE,
            "secondarySubtitle" to PersonalPrefs.SECONDARY_SUBTITLE, "audio" to PersonalPrefs.AUDIO,
            "showAllSources" to (prefs[PersonalPrefs.SHOW_ALL_SOURCES] ?: false),
            "configuredAddonCount" to PersonalConfig.addonUrls.size, "tmdbKeyConfigured" to PersonalConfig.tmdbApiKey.isNotBlank(),
            "subtitleSize" to PersonalSubtitles.SIZE, "subtitleSizePct" to PersonalSubtitles.SIZE_PCT,
            "subtitleColor" to PersonalSubtitles.COLOR, "subtitleOffset" to PersonalSubtitles.OFFSET,
            "subtitleVerticalPct" to PersonalSubtitles.VERTICAL_PCT, "subtitleStyle" to PersonalSubtitles.STYLE,
            "subtitleFont" to PersonalSubtitles.FONT, "subtitleStylized" to PersonalSubtitles.STYLIZED,
            "subtitlePreload" to PersonalSubtitles.PRELOAD, "subtitleFilterByLanguage" to PersonalSubtitles.FILTER_BY_LANGUAGE,
            "subtitleForced" to PersonalSubtitles.FORCED, "subtitleAiMatch" to PersonalSubtitles.AI_FIND_BEST_MATCH,
            "subtitleHearingSync" to PersonalSubtitles.HEARING_SYNC)
        fixed.chunked(18).forEachIndexed { index, values ->
            PtpDiag.i("app-settings", *(listOf("section" to "personal", "part" to index + 1) + values).toTypedArray())
        }
        val allowed = setOf("device_mode_override", "skip_profile_selection", "oled_black_background", "accent_color",
            "last_app_language", "subtitle_preload_enabled", "dolby_vision_compat", "personal_show_all_sources",
            "dns_provider_global", "card_layout_mode", "watchlist_on_home", "content_language", "default_subtitle",
            "default_audio_language", "frame_rate_matching_mode", "clock_format", "smooth_scrolling", "spoiler_blur",
            "volume_boost_db", "show_loading_stats", "subtitle_size", "subtitle_color", "subtitle_offset", "subtitle_style",
            "subtitle_font", "subtitle_stylized", "filter_subtitles_by_lang", "use_forced_subtitles", "secondary_subtitle", "include_specials")
        val saved = prefs.asMap().entries.filter { entry ->
            val name = entry.key.name
            name in allowed || name.startsWith("autoplay_next_show_") ||
                (name.startsWith("profile_") && allowed.any { name.endsWith("_$it") })
        }.sortedBy { it.key.name }
        saved.forEach { (key, value) ->
            PtpDiag.i("app-settings", "section" to "saved", "key" to key.name, "value" to value)
        }
    }
}
