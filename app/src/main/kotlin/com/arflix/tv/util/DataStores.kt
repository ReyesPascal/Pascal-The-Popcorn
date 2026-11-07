package com.arflix.tv.util

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "settings_prefs",
    produceMigrations = { listOf(HomePresentationMigration()) }
)

const val WATCHLIST_ON_HOME = "watchlist_on_home"
// Device-local watch data. Beta history does not require a migration.
val Context.watchDataStore: DataStore<Preferences> by preferencesDataStore(name = "watch_prefs")
val Context.profilesDataStore: DataStore<Preferences> by preferencesDataStore(name = "profiles_prefs")
val Context.authDataStore: DataStore<Preferences> by preferencesDataStore(name = "auth_prefs")
