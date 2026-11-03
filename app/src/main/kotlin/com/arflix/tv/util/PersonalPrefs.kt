package com.arflix.tv.util

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Personal build: shared settings contract (see PERSONAL_PLAN.md). Owned by the lead; agents read only.
 *
 * showAllSources = false (default): Play starts the best source for this TV straight away.
 * showAllSources = true: Play opens the full source list, as stock ARVIO does.
 */
object PersonalPrefs {
    val SHOW_ALL_SOURCES = booleanPreferencesKey("personal_show_all_sources")

    fun showAllSourcesFlow(context: Context): Flow<Boolean> =
        context.settingsDataStore.data.map { it[SHOW_ALL_SOURCES] ?: false }

    suspend fun showAllSources(context: Context): Boolean = showAllSourcesFlow(context).first()

    suspend fun setShowAllSources(context: Context, value: Boolean) {
        context.settingsDataStore.edit { it[SHOW_ALL_SOURCES] = value }
    }
}
