package com.arflix.tv.data.local

import android.content.Context
import java.io.File

/** Reclaims storage left by features removed in 1.0.2. Runs once after a successful cleanup. */
internal object LegacyDataCleanup {
    // Legacy storage list. Agent B's removed home-server storage names can be added here.
    internal val files = listOf("iptv_cache", "xtream_vod_disk_cache")
    internal val sharedPreferences = listOf(
        "arvio_iptv_prefs",
        "iptv_movie_source_cache_v1",
        "iptv_series_resolver_cache_v1",
        "arvio_epg_http_cache"
    )
    internal val databases = listOf("arvio_iptv_channels.db", "arvio_iptv_epg_index.db")
    internal val cacheFiles = listOf("sports-fixtures.json")
    internal val cachePrefixes = listOf("epg_", "sports-catalogue-v2-")
    private const val CLEANUP_VERSION = 1

    @Synchronized
    fun runOnce(context: Context) {
        val marker = context.getSharedPreferences("legacy_data_cleanup", Context.MODE_PRIVATE)
        if (marker.getInt("version", 0) >= CLEANUP_VERSION) return
        var complete = true
        fun delete(file: File) {
            if (file.exists() && !file.deleteRecursively()) complete = false
        }
        files.forEach { delete(File(context.filesDir, it)) }
        sharedPreferences.forEach { name ->
            // Delete the XML and its backup as well as evicting the in-memory preferences.
            if (android.os.Build.VERSION.SDK_INT >= 24) {
                if (!context.deleteSharedPreferences(name)) complete = false
            } else {
                if (!context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()) complete = false
                delete(File(context.applicationInfo.dataDir, "shared_prefs/$name.xml"))
                delete(File(context.applicationInfo.dataDir, "shared_prefs/$name.xml.bak"))
            }
        }
        databases.forEach { name ->
            val database = context.getDatabasePath(name)
            val exists = listOf("", "-journal", "-wal", "-shm").any { File(database.path + it).exists() }
            if (exists && !context.deleteDatabase(name)) complete = false
        }
        context.cacheDir.listFiles()?.filter { file ->
            cacheFiles.any { file.name == it || file.name == "$it.bak" } ||
                cachePrefixes.any { file.name.startsWith(it) }
        }?.forEach(::delete)
        if (complete) marker.edit().putInt("version", CLEANUP_VERSION).commit()
    }
}
