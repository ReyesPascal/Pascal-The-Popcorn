package com.arflix.tv.ui.screens.home

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

internal data class HomeProfilePreferences(val clockFormat: String, val smoothScrolling: Boolean)

internal fun readHomeProfilePreferences(preferences: Preferences, profileId: String): HomeProfilePreferences =
    HomeProfilePreferences(
        preferences[stringPreferencesKey("profile_${profileId}_clock_format")] ?: "24h",
        preferences[booleanPreferencesKey("profile_${profileId}_smooth_scrolling")] ?: false
    )

internal fun observeHomeProfilePreferences(
    activeProfileId: Flow<String>, preferences: Flow<Preferences>
): Flow<HomeProfilePreferences> = activeProfileId.combine(preferences) { profileId, prefs ->
    readHomeProfilePreferences(prefs, profileId)
}.distinctUntilChanged()
