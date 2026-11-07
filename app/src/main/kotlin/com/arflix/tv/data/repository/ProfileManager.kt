package com.arflix.tv.data.repository

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.arflix.tv.util.profilesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The one built-in profile and the profile-scoped preference keys.
 *
 * There is exactly one profile, fixed for the life of an install. Its id is whatever the first
 * profile-era launch stored in the profiles DataStore ("active_profile_id", a UUID created the
 * first time the personal build started), or [BUILT_IN_PROFILE_ID] on a fresh install. Reusing
 * the stored id keeps every existing "profile_<id>_..." key (Continue Watching, watched,
 * settings) pointing at the same data. The id is read once and never changes afterwards.
 *
 * All repositories that need profile isolation use this to get prefixed keys.
 * Example: Instead of "access_token", use profileManager.profileKey("access_token")
 * which returns "profile_<id>_access_token"
 */
@Singleton
class ProfileManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        /** Id used when no id was stored by an earlier launch (fresh install). */
        const val BUILT_IN_PROFILE_ID = "default"
        private val STORED_PROFILE_ID_KEY = stringPreferencesKey("active_profile_id")
    }

    private val builtInId: String by lazy {
        runBlocking(Dispatchers.IO) {
            context.profilesDataStore.data.first()[STORED_PROFILE_ID_KEY]
                ?.takeIf { it.isNotBlank() }
                ?: BUILT_IN_PROFILE_ID
        }
    }

    /**
     * The profile id as a state flow. It has a single value and never changes.
     * First access may read the DataStore once; ArflixApplication calls [initialize] early so
     * this normally returns at once.
     */
    val currentProfileId: StateFlow<String> by lazy { MutableStateFlow(getProfileIdSync()).asStateFlow() }

    /** Flow of the profile id. It has a single value and never changes. */
    val activeProfileId: Flow<String> get() = currentProfileId

    /**
     * Get the profile id synchronously. Blocks for one small DataStore read the first time
     * only, if [initialize] or [getProfileId] has not already resolved it.
     */
    fun getProfileIdSync(): String =
        builtInId

    /**
     * Get the profile id (suspend version).
     */
    suspend fun getProfileId(): String =
        withContext(Dispatchers.IO) { builtInId }

    /**
     * Resolve the profile id - call this early in app startup (off the main thread).
     */
    suspend fun initialize() {
        getProfileId()
    }

    // ========== Profile-Scoped Key Generators ==========

    /**
     * Create a profile-scoped string preference key
     */
    fun profileStringKey(name: String): Preferences.Key<String> {
        return stringPreferencesKey("profile_${getProfileIdSync()}_$name")
    }

    /**
     * Create a profile-scoped string preference key for a specific profile ID
     * (does not mutate current profile state).
     */
    fun profileStringKeyFor(profileId: String, name: String): Preferences.Key<String> {
        return stringPreferencesKey("profile_${profileId}_$name")
    }

    /**
     * Create a profile-scoped long preference key
     */
    fun profileLongKey(name: String): Preferences.Key<Long> {
        return longPreferencesKey("profile_${getProfileIdSync()}_$name")
    }

    /**
     * Create a profile-scoped long preference key for a specific profile ID
     * (does not mutate current profile state).
     */
    fun profileLongKeyFor(profileId: String, name: String): Preferences.Key<Long> {
        return longPreferencesKey("profile_${profileId}_$name")
    }

    /**
     * Create a profile-scoped boolean preference key
     */
    fun profileBooleanKey(name: String): Preferences.Key<Boolean> {
        return booleanPreferencesKey("profile_${getProfileIdSync()}_$name")
    }

    /**
     * Create a profile-scoped boolean preference key for a specific profile ID
     * (does not mutate current profile state).
     */
    fun profileBooleanKeyFor(profileId: String, name: String): Preferences.Key<Boolean> {
        return booleanPreferencesKey("profile_${profileId}_$name")
    }

    /**
     * Create a profile-scoped int preference key
     */
    fun profileIntKey(name: String): Preferences.Key<Int> {
        return intPreferencesKey("profile_${getProfileIdSync()}_$name")
    }

    /**
     * Create a profile-scoped int preference key for a specific profile ID
     * (does not mutate current profile state).
     */
    fun profileIntKeyFor(profileId: String, name: String): Preferences.Key<Int> {
        return intPreferencesKey("profile_${profileId}_$name")
    }

    /**
     * Get the key prefix for the profile
     */
    fun getKeyPrefix(): String = "profile_${getProfileIdSync()}_"
}
