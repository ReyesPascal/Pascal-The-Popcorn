package com.arflix.tv.util

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Personal build: shared playback settings contract.
 *
 * showAllSources = false (default): Play starts the best source for this TV straight away.
 * showAllSources = true: Play opens the full source list, as stock ARVIO does.
 */
object PersonalPrefs {
    const val APP_LANGUAGE = "en-US"
    const val SUBTITLE = "English"
    const val SECONDARY_SUBTITLE = "Off"
    const val AUDIO = "Auto (Original)"

    fun autoPlayNextShowKey(tmdbId: Int) = booleanPreferencesKey("autoplay_next_show_$tmdbId")

    fun autoPlayNextShowFlow(context: Context, tmdbId: Int): Flow<Boolean> =
        context.settingsDataStore.data.map { it[autoPlayNextShowKey(tmdbId)] ?: false }

    suspend fun setAutoPlayNextShow(context: Context, tmdbId: Int, enabled: Boolean) {
        context.settingsDataStore.edit { it[autoPlayNextShowKey(tmdbId)] = enabled }
    }

    val SHOW_ALL_SOURCES = booleanPreferencesKey("personal_show_all_sources")

    fun showAllSourcesFlow(context: Context): Flow<Boolean> =
        context.settingsDataStore.data.map { it[SHOW_ALL_SOURCES] ?: false }

    suspend fun showAllSources(context: Context): Boolean = showAllSourcesFlow(context).first()

    suspend fun setShowAllSources(context: Context, value: Boolean) {
        context.settingsDataStore.edit { it[SHOW_ALL_SOURCES] = value }
    }
}

/** Fixed personal-build subtitle appearance and selection defaults. */
object PersonalSubtitles {
    const val SIZE = "Medium"
    const val SIZE_PCT = 100
    const val COLOR = "White"
    const val OFFSET = "Bottom"
    const val VERTICAL_PCT = 2
    const val STYLE = "Bold"
    const val FONT = "System"
    const val STYLIZED = true
    const val AI_FIND_BEST_MATCH = false
    const val HEARING_SYNC = false
    const val PRELOAD = true
    const val FILTER_BY_LANGUAGE = true
    const val FORCED = false
}
