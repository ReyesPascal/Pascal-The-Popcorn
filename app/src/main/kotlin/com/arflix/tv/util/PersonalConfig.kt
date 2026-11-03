package com.arflix.tv.util

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.File

/**
 * Personal build: settings read at start from a file on the device, so they change without a rebuild.
 *
 *   /sdcard/Android/data/com.arvio.tv/files/personal-config.json
 *   {"tmdb_api_key": "…", "addons": ["https://…/manifest.json", …]}
 *
 * Push a new file with adb and restart the app. A missing file or key keeps the compiled values
 * (secrets.properties, BundledAddons).
 */
object PersonalConfig {
    private const val TAG = "PersonalConfig"
    const val FILE_NAME = "personal-config.json"

    @Volatile var tmdbApiKey: String = ""
        private set
    @Volatile var addonUrls: List<String> = emptyList()
        private set

    fun load(context: Context) {
        val file = runCatching { context.getExternalFilesDir(null)?.let { File(it, FILE_NAME) } }.getOrNull()
        if (file == null || !file.isFile) {
            Log.i(TAG, "no $FILE_NAME, using compiled values")
            return
        }
        runCatching {
            val json = JSONObject(file.readText())
            tmdbApiKey = json.optString("tmdb_api_key", "").trim()
            json.optJSONArray("addons")?.let { array ->
                addonUrls = (0 until array.length()).mapNotNull { i ->
                    array.optString(i, "").trim().takeIf { it.isNotBlank() }
                }
            }
            Log.i(TAG, "loaded $FILE_NAME: tmdb key ${if (tmdbApiKey.isBlank()) "absent" else "set"}, ${addonUrls.size} addon(s)")
        }.onFailure { Log.w(TAG, "unreadable $FILE_NAME, using compiled values", it) }
    }
}
