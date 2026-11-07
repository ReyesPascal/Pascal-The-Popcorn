package com.arflix.tv.data.repository

import com.arflix.tv.data.merge.getMergedTvDetails
import com.arflix.tv.data.merge.getMergedTvSeason
import android.content.Context

import androidx.datastore.preferences.core.edit
import com.arflix.tv.R
import com.arflix.tv.data.api.TmdbApi
import com.arflix.tv.data.model.MediaItem
import com.arflix.tv.data.model.MediaType
import com.arflix.tv.data.model.NextEpisode
import com.arflix.tv.data.model.isNotResumable
import com.arflix.tv.util.Constants
import com.arflix.tv.util.AppLogger
import com.arflix.tv.util.settingsDataStore
import com.arflix.tv.util.watchDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.async
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.Locale
import java.text.SimpleDateFormat
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The app's own (local, per profile) watch data: Continue Watching, resume
 * positions, watched movies/episodes and Continue Watching dismissals.
 *
 * Everything is stored on the device in [watchDataStore] (Continue Watching and
 * watched marks) and [settingsDataStore] (dismissals). There is no remote
 * tracker; TMDB is only used to enrich Continue Watching cards with artwork,
 * overview and runtime.
 */
@Singleton
class LocalWatchRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tmdbApi: TmdbApi,
    private val profileManager: ProfileManager,
    private val continueWatchingUpdates: ContinueWatchingUpdates
) {
    private val gson = Gson()

    // Neutral, device-local storage; beta data does not require migration.
    private fun dismissedContinueWatchingKey() = profileManager.profileStringKey(DISMISSED_CONTINUE_WATCHING_KEY)
    private fun localContinueWatchingKey() = profileManager.profileStringKey("local_continue_watching_v1")
    private fun localWatchedMoviesKey() = profileManager.profileStringKey("local_watched_movies_v1")
    private fun localWatchedEpisodesKey() = profileManager.profileStringKey("local_watched_episodes_v1")

    @Volatile private var activeCacheProfileId: String? = null
    @Volatile private var cachedContinueWatching: List<ContinueWatchingItem> = emptyList()
    @Volatile private var cachedContinueWatchingProfileId: String? = null

    private fun currentProfileId(): String = profileManager.getProfileIdSync().ifBlank { "default" }

    @Synchronized
    private fun ensureProfileCacheScope() {
        val profileId = currentProfileId()
        if (activeCacheProfileId == profileId) return
        activeCacheProfileId = profileId
        clearProfileScopedMemoryCaches(clearPreloaded = false)
    }

    @Synchronized
    private fun clearProfileScopedMemoryCaches(clearPreloaded: Boolean) {
        watchedMoviesCache.clear()
        watchedEpisodesCache.clear()
        episodeWriteGenerations.clear()
        movieWriteGenerations.clear()
        cacheInitialized = false
        watchedCacheGeneration++
        watchedCacheReloads.reset()
        cachedContinueWatching = emptyList()
        cachedContinueWatchingProfileId = null
        if (clearPreloaded) {
            preloadedProfileCache.clear()
        }
    }

    // ========== Export / import of the saved local data ==========

    suspend fun exportDismissedContinueWatchingForProfiles(profileIds: List<String>): Map<String, String> {
        val prefs = context.settingsDataStore.data.first()
        val out = LinkedHashMap<String, String>()
        profileIds.forEach { profileId ->
            val key = profileManager.profileStringKeyFor(profileId, DISMISSED_CONTINUE_WATCHING_KEY)
            val raw = prefs[key]?.trim().orEmpty()
            if (raw.isNotEmpty()) {
                out[profileId] = raw
            }
        }
        return out
    }

    suspend fun importDismissedContinueWatchingForProfiles(values: Map<String, String>) {
        context.settingsDataStore.edit { prefs ->
            values.forEach { (profileId, raw) ->
                val key = profileManager.profileStringKeyFor(profileId, DISMISSED_CONTINUE_WATCHING_KEY)
                val value = raw.trim()
                if (value.isEmpty()) {
                    prefs.remove(key)
                } else {
                    prefs[key] = value
                }
            }
        }
    }

    suspend fun exportLocalContinueWatchingForProfiles(profileIds: List<String>): Map<String, List<ContinueWatchingItem>> {
        val prefs = context.watchDataStore.data.first()
        val out = LinkedHashMap<String, List<ContinueWatchingItem>>()
        profileIds.forEach { profileId ->
            val key = profileManager.profileStringKeyFor(profileId, "local_continue_watching_v1")
            val json = prefs[key]?.trim().orEmpty()
            if (json.isBlank()) return@forEach
            val items = decodeContinueWatchingList(json)
            if (items.isNotEmpty()) {
                out[profileId] = items
            }
        }
        return out
    }

    suspend fun importLocalContinueWatchingForProfiles(values: Map<String, List<ContinueWatchingItem>?>) {
        context.watchDataStore.edit { prefs ->
            values.forEach { (profileId, items) ->
                if (items == null) return@forEach
                val validItems = sanitizeContinueWatchingItems(items)
                // A corrupt non-empty snapshot must not erase this device's valid history.
                if (items.isNotEmpty() && validItems.isEmpty()) return@forEach
                val key = profileManager.profileStringKeyFor(profileId, "local_continue_watching_v1")
                if (validItems.isEmpty()) {
                    prefs.remove(key)
                } else {
                    prefs[key] = gson.toJson(validItems.take(Constants.MAX_CONTINUE_WATCHING))
                }
            }
        }
    }

    /**
     * Folds another device's saved resume points into this device's, keeping the
     * more recent entry per episode.
     *
     * Unlike [importLocalContinueWatchingForProfiles] this never replaces the
     * local list, because both devices write to it: a straight replace would
     * throw away progress made here since the snapshot was taken. This store is
     * the only place an exact resume position exists — trackers record a
     * percentage and no position — so it has to survive the round trip.
     */
    suspend fun mergeLocalContinueWatchingForProfiles(values: Map<String, List<ContinueWatchingItem>?>) {
        // One entry per title, matching what the decoder keeps on the way back out.
        fun showKey(item: ContinueWatchingItem) = "${item.mediaType}:${item.id}"
        val freshness = compareBy<ContinueWatchingItem> { it.updatedAtMs }
            .thenBy { it.resumePositionSeconds }
            .thenBy { it.progress }

        context.watchDataStore.edit { prefs ->
            values.forEach { (profileId, incoming) ->
                if (incoming.isNullOrEmpty()) return@forEach
                val remoteItems = sanitizeContinueWatchingItems(incoming)
                if (remoteItems.isEmpty()) return@forEach
                val key = profileManager.profileStringKeyFor(profileId, "local_continue_watching_v1")
                val existing = decodeContinueWatchingList(prefs[key]?.trim().orEmpty())

                val merged = (existing + remoteItems)
                    .groupBy(::showKey)
                    .map { (_, matches) -> matches.maxWith(freshness) }
                    .sortedByDescending { it.updatedAtMs }
                    .take(Constants.MAX_CONTINUE_WATCHING)
                if (merged.isNotEmpty()) {
                    prefs[key] = gson.toJson(merged)
                }
            }
        }
    }

    suspend fun exportLocalWatchedMoviesForProfiles(profileIds: List<String>): Map<String, List<Int>> {
        val prefs = context.watchDataStore.data.first()
        val out = LinkedHashMap<String, List<Int>>()
        profileIds.forEach { profileId ->
            val key = profileManager.profileStringKeyFor(profileId, "local_watched_movies_v1")
            val json = prefs[key]?.trim().orEmpty()
            if (json.isBlank()) return@forEach
            val ids = decodeIntList(json)
            if (ids.isNotEmpty()) {
                out[profileId] = ids
            }
        }
        return out
    }

    suspend fun importLocalWatchedMoviesForProfiles(values: Map<String, List<Int>>) {
        context.watchDataStore.edit { prefs ->
            values.forEach { (profileId, ids) ->
                val key = profileManager.profileStringKeyFor(profileId, "local_watched_movies_v1")
                if (ids.isEmpty()) {
                    prefs.remove(key)
                } else {
                    prefs[key] = gson.toJson(ids.distinct())
                }
            }
        }
    }

    suspend fun exportLocalWatchedEpisodesForProfiles(profileIds: List<String>): Map<String, List<String>> {
        val prefs = context.watchDataStore.data.first()
        val out = LinkedHashMap<String, List<String>>()
        profileIds.forEach { profileId ->
            val key = profileManager.profileStringKeyFor(profileId, "local_watched_episodes_v1")
            val json = prefs[key]?.trim().orEmpty()
            if (json.isBlank()) return@forEach
            val keys = decodeStringList(json)
            if (keys.isNotEmpty()) {
                out[profileId] = keys
            }
        }
        return out
    }

    suspend fun importLocalWatchedEpisodesForProfiles(values: Map<String, List<String>>) {
        context.watchDataStore.edit { prefs ->
            values.forEach { (profileId, keys) ->
                val key = profileManager.profileStringKeyFor(profileId, "local_watched_episodes_v1")
                if (keys.isEmpty()) {
                    prefs.remove(key)
                } else {
                    prefs[key] = gson.toJson(keys.distinct())
                }
            }
        }
    }

    // ========== Watched marks ==========

    /** Mark movie as watched: updates the in-memory cache, then the saved snapshot. */
    suspend fun markMovieWatched(tmdbId: Int) {
        loadWatchedCacheBeforeWrite()
        updateWatchedCache(tmdbId, null, null, true)
        persistLocalWatchedSnapshotForCurrentProfile()
        removeFromContinueWatchingCache(tmdbId, null, null, MediaType.MOVIE)
    }

    /** Mark movie as unwatched: updates the in-memory cache, then the saved snapshot. */
    suspend fun markMovieUnwatched(tmdbId: Int) {
        loadWatchedCacheBeforeWrite()
        updateWatchedCache(tmdbId, null, null, false)
        persistLocalWatchedSnapshotForCurrentProfile()
    }

    /** Mark episode as watched: updates the in-memory cache, then the saved snapshot. */
    suspend fun markEpisodeWatched(showTmdbId: Int, season: Int, episode: Int) {
        loadWatchedCacheBeforeWrite()
        updateWatchedCache(showTmdbId, season, episode, true)
        persistLocalWatchedSnapshotForCurrentProfile()
        removeFromContinueWatchingCache(showTmdbId, season, episode)
    }

    /** Mark episode as unwatched: updates the in-memory cache, then the saved snapshot. */
    suspend fun markEpisodeUnwatched(showTmdbId: Int, season: Int, episode: Int) {
        loadWatchedCacheBeforeWrite()
        updateWatchedCache(showTmdbId, season, episode, false)
        persistLocalWatchedSnapshotForCurrentProfile()
    }

    /** Mark the given episodes of a season as watched. */
    suspend fun markSeasonWatched(showTmdbId: Int, seasonNumber: Int, episodes: List<Int>): Boolean {
        if (episodes.isEmpty()) return true
        loadWatchedCacheBeforeWrite()
        episodes.forEach { ep ->
            updateWatchedCache(showTmdbId, seasonNumber, ep, true)
        }
        persistLocalWatchedSnapshotForCurrentProfile()
        return true
    }

    /** Mark the given episodes of a season as unwatched. */
    suspend fun removeSeasonFromHistory(showTmdbId: Int, seasonNumber: Int, episodes: List<Int>): Boolean {
        if (episodes.isEmpty()) return true
        loadWatchedCacheBeforeWrite()
        episodes.forEach { ep ->
            updateWatchedCache(showTmdbId, seasonNumber, ep, false)
        }
        persistLocalWatchedSnapshotForCurrentProfile()
        return true
    }

    /**
     * Watched episode keys ("show_tmdb:id:season:episode") of one show, from
     * the in-memory watched cache.
     */
    suspend fun getWatchedEpisodesForShow(tmdbId: Int): Set<String> {
        ensureProfileCacheScope()
        val prefix = "show_tmdb:$tmdbId:"
        return watchedEpisodesCache.filter { it.startsWith(prefix) }.toSet()
    }

    // ========== Continue Watching ==========

    /** Continue Watching for the active profile: the saved list, enriched from TMDB. */
    suspend fun getContinueWatching(): List<ContinueWatchingItem> {
        ensureProfileCacheScope()
        val requestProfileId = currentProfileId()
        val localItems = filterDismissedContinueWatchingItems(loadLocalContinueWatching())
        if (currentProfileId() != requestProfileId) throw kotlinx.coroutines.CancellationException("Profile changed during Continue Watching load")
        cachedContinueWatching = localItems
        cachedContinueWatchingProfileId = requestProfileId
        return localItems
    }

    fun getCachedContinueWatching(): List<ContinueWatchingItem> {
        return if (cachedContinueWatchingProfileId == profileManager.getProfileIdSync()) {
            cachedContinueWatching
        } else {
            emptyList()
        }
    }

    /**
     * Saved Continue Watching for Home startup, without TMDB enrichment or any
     * network call. Home refreshes (and enriches) it in the background afterwards.
     */
    suspend fun loadPersistedContinueWatchingForStartup(): List<ContinueWatchingItem> {
        ensureProfileCacheScope()
        val profileId = currentProfileId()
        if (cachedContinueWatchingProfileId == profileId && cachedContinueWatching.isNotEmpty()) {
            return filterDismissedContinueWatchingItems(cachedContinueWatching)
        }
        val cached = filterDismissedContinueWatchingItems(loadLocalContinueWatchingRaw())
        if (cached.isNotEmpty()) {
            cachedContinueWatching = cached
            cachedContinueWatchingProfileId = profileId
        }
        return cached
    }

    suspend fun preloadContinueWatchingForProfile(profileId: String) {
        val prefs = context.watchDataStore.data.first()
        val key = profileManager.profileStringKeyFor(profileId, "local_continue_watching_v1")
        preloadedProfileCache[profileId] = filterDismissedContinueWatchingItems(decodeContinueWatchingList(prefs[key].orEmpty()), profileId)
    }

    // Cache for preloaded profile data (keyed by profileId)
    private val preloadedProfileCache = ConcurrentHashMap<String, List<ContinueWatchingItem>>()

    /**
     * Activate preloaded cache for a profile - call when profile is selected.
     * This transfers preloaded data to the active cache for immediate use by HomeViewModel.
     * IMPORTANT: Always clears existing cache first to prevent cross-profile data leakage.
     */
    fun activatePreloadedCache(profileId: String) {
        activeCacheProfileId = profileId.ifBlank { "default" }
        // CRITICAL: Clear existing cache first to prevent profile data leakage
        cachedContinueWatching = emptyList()
        cachedContinueWatchingProfileId = null

        // Then load this profile's preloaded data if available
        val preloaded = preloadedProfileCache[profileId]
        if (!preloaded.isNullOrEmpty()) {
            cachedContinueWatching = preloaded
            cachedContinueWatchingProfileId = profileId
        }
    }

    /**
     * Clear continue watching cache - call when switching profiles
     */
    fun clearContinueWatchingCache() {
        ensureProfileCacheScope()
        cachedContinueWatching = emptyList()
        cachedContinueWatchingProfileId = null
    }

    /**
     * Clear ALL profile-specific caches - MUST be called when switching profiles
     * This ensures complete isolation between profiles and prevents data leakage
     */
    fun clearAllProfileCaches() {
        activeCacheProfileId = currentProfileId()
        clearProfileScopedMemoryCaches(clearPreloaded = true)
    }

    /**
     * Remove an episode from Continue Watching cache when marked as watched
     */
    suspend fun removeFromContinueWatchingCache(
        showTmdbId: Int,
        seasonNum: Int?,
        episodeNum: Int?,
        mediaType: MediaType? = if (seasonNum != null || episodeNum != null) MediaType.TV else null
    ) {
        ensureProfileCacheScope()
        val profileId = currentProfileId()
        removeFromLocalContinueWatching(showTmdbId, seasonNum, episodeNum, mediaType)

        cachedContinueWatching = cachedContinueWatching.filter { item ->
            // Keep items that don't match the watched episode
            !(item.id == showTmdbId &&
              (mediaType == null || item.mediaType == mediaType) &&
              (seasonNum == null || item.season == seasonNum) &&
              (episodeNum == null || item.episode == episodeNum))
        }
        cachedContinueWatchingProfileId = profileId
        preloadedProfileCache.computeIfPresent(profileId) { _, items ->
            items.filterNot { item ->
                item.id == showTmdbId &&
                    (mediaType == null || item.mediaType == mediaType) &&
                    (seasonNum == null || item.season == seasonNum) &&
                    (episodeNum == null || item.episode == episodeNum)
            }
        }
        continueWatchingUpdates.remove(profileId, mediaType, showTmdbId, seasonNum, episodeNum)
    }

    // ========== Saved Continue Watching ==========

    /**
     * Save playback progress to local Continue Watching (profile-scoped).
     * Called from PlayerViewModel when saving progress.
     */
    suspend fun saveLocalContinueWatching(
        mediaType: MediaType,
        tmdbId: Int,
        title: String,
        posterPath: String?,
        backdropPath: String?,
        season: Int?,
        episode: Int?,
        displaySeason: Int? = season,
        displayEpisode: Int? = episode,
        episodeTitle: String?,
        progress: Int, // 0-100
        positionSeconds: Long = 0L,
        durationSeconds: Long = 0L,
        streamKey: String? = null,
        streamAddonId: String? = null,
        streamTitle: String? = null,
        year: String = "",
        isUpNext: Boolean = false,
        episodeAirDate: String = "",
        emitUpdate: Boolean = true,
        addonNativeId: String? = null,
        addonNativeAddonId: String? = null,
        addonNativeType: String? = null,
    ) {
        ensureProfileCacheScope()
        if (isNotResumable(id = tmdbId, isAddonNative = !addonNativeId.isNullOrBlank())) {
            return
        }
        val hasMeaningfulPosition = positionSeconds >= 60L

        // Keep accidental taps out, but still keep real partial sessions on long content
        // where percent can be low while position is already meaningful.
        if (!isUpNext && ((progress < Constants.MIN_PROGRESS_THRESHOLD && !hasMeaningfulPosition) || progress >= Constants.WATCHED_THRESHOLD)) {
            // If watched (>= threshold), remove from Continue Watching
            if (progress >= Constants.WATCHED_THRESHOLD) {
                removeFromContinueWatchingCache(tmdbId, season, episode, mediaType)
            }
            return
        }

        val item = ContinueWatchingItem(
            id = tmdbId,
            title = title,
            mediaType = mediaType,
            progress = progress,
            resumePositionSeconds = positionSeconds.coerceAtLeast(0L),
            durationSeconds = durationSeconds.coerceAtLeast(0L),
            season = season,
            episode = episode,
            displaySeason = displaySeason,
            displayEpisode = displayEpisode,
            episodeTitle = episodeTitle,
            backdropPath = backdropPath,
            posterPath = posterPath,
            streamKey = streamKey,
            streamAddonId = streamAddonId,
            streamTitle = streamTitle,
            year = year,
            releaseDate = episodeAirDate,
            isUpNext = isUpNext,
            addonNativeId = addonNativeId,
            addonNativeAddonId = addonNativeAddonId,
            addonNativeType = addonNativeType,
            updatedAtMs = System.currentTimeMillis()
        )

        // Load existing items (raw - no enrichment needed when saving)
        val existingItems = loadLocalContinueWatchingRaw().toMutableList()

        // Remove ALL existing entries for this show/movie (will add updated one).
        // For TV: removes any episode of the same show, so CW only has the latest episode per show.
        existingItems.removeAll { existing ->
            existing.id == tmdbId && existing.mediaType == mediaType
        }

        // Add to front (most recent)
        existingItems.add(0, item)

        // Keep only top items
        val trimmed = existingItems.take(Constants.MAX_CONTINUE_WATCHING)

        // Persist
        val json = gson.toJson(trimmed)
        val saveKey = localContinueWatchingKey()
        context.watchDataStore.edit { prefs ->
            prefs[saveKey] = json
        }
        clearDismissedContinueWatching(mediaType, tmdbId, season, episode)

        cachedContinueWatching = trimmed
        preloadedProfileCache[currentProfileId()] = trimmed
        if (emitUpdate) {
            continueWatchingUpdates.upsert(currentProfileId(), item)
        }
    }

    /**
     * Remove item from local Continue Watching
     */
    private suspend fun removeFromLocalContinueWatching(
        tmdbId: Int,
        season: Int?,
        episode: Int?,
        mediaType: MediaType?
    ) {
        // Use raw method - no need to enrich items just to remove them
        val existingItems = loadLocalContinueWatchingRaw().toMutableList()
        val sizeBefore = existingItems.size

        existingItems.removeAll { item ->
            item.id == tmdbId &&
            (mediaType == null || item.mediaType == mediaType) &&
            (season == null || item.season == season) &&
            (episode == null || item.episode == episode)
        }

        if (existingItems.size != sizeBefore) {
            val json = gson.toJson(existingItems)
            context.watchDataStore.edit { prefs ->
                prefs[localContinueWatchingKey()] = json
            }
        }
    }

    /** Load saved history without making an optimistic local mark wait for remote providers. */
    private suspend fun loadWatchedCacheBeforeWrite() {
        val profileId = currentProfileId()
        while (true) {
            ensureProfileCacheScope()
            val generation = watchedCacheGeneration
            val (movies, episodes) = loadLocalWatchedSnapshotForCurrentProfile()
            synchronized(this) {
                if (currentProfileId() != profileId) throw kotlinx.coroutines.CancellationException("Profile changed during local watched load")
                if (generation == watchedCacheGeneration) {
                    // Do not undo an unwatch made while DataStore was being read.
                    watchedMoviesCache.addAll(movies.filter { !movieWriteGenerations.containsKey(it) })
                    watchedEpisodesCache.addAll(episodes.filter { !episodeWriteGenerations.containsKey(it) })
                    return
                }
            }
        }
    }

    private suspend fun persistLocalWatchedSnapshotForCurrentProfile() {
        val profileId = currentProfileId()
        val generation = watchedCacheGeneration
        val moviesKey = localWatchedMoviesKey()
        val episodesKey = localWatchedEpisodesKey()
        context.watchDataStore.edit { prefs ->
            synchronized(this) {
                if (currentProfileId() != profileId || generation != watchedCacheGeneration) {
                    throw kotlinx.coroutines.CancellationException("Watched scope changed before persistence")
                }
                // Merge within the DataStore transaction, not from a potentially stale snapshot.
                val movies = decodeIntList(prefs[moviesKey].orEmpty()).toMutableSet()
                val episodes = decodeStringList(prefs[episodesKey].orEmpty()).toMutableSet()
                movies.addAll(watchedMoviesCache)
                episodes.addAll(watchedEpisodesCache)
                movies.removeAll(movieWriteGenerations.keys.filter { it !in watchedMoviesCache }.toSet())
                episodes.removeAll(episodeWriteGenerations.keys.filter { it !in watchedEpisodesCache }.toSet())
                prefs[moviesKey] = gson.toJson(movies.sorted())
                prefs[episodesKey] = gson.toJson(episodes.sorted())
            }
        }
    }

    private suspend fun loadLocalWatchedSnapshotForCurrentProfile(): Pair<Set<Int>, Set<String>> {
        val prefs = context.watchDataStore.data.first()
        val movies = decodeIntList(prefs[localWatchedMoviesKey()].orEmpty()).toSet()
        val episodes = decodeStringList(prefs[localWatchedEpisodesKey()].orEmpty()).toSet()
        return movies to episodes
    }

    internal suspend fun getLocalWatchedSnapshot(): Pair<Set<Int>, Set<String>> {
        ensureProfileCacheScope()
        return loadLocalWatchedSnapshotForCurrentProfile()
    }

    private fun decodeContinueWatchingList(json: String): List<ContinueWatchingItem> {
        return decodeContinueWatchingCache(json, gson)
    }

    private fun decodeIntList(json: String): List<Int> {
        if (json.isBlank()) return emptyList()
        return try {
            val type = TypeToken.getParameterized(MutableList::class.java, Int::class.javaObjectType).type
            val items: List<Int> = gson.fromJson(json, type)
            items.distinct()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun decodeStringList(json: String): List<String> {
        if (json.isBlank()) return emptyList()
        return try {
            val type = TypeToken.getParameterized(MutableList::class.java, String::class.java).type
            val items: List<String> = gson.fromJson(json, type)
            items.filter { it.isNotBlank() }.distinct()
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Load local Continue Watching items (profile-scoped) - raw data without enrichment
     */
    private suspend fun loadLocalContinueWatchingRaw(): List<ContinueWatchingItem> {
        val key = localContinueWatchingKey()
        val prefs = context.watchDataStore.data.first()
        val json = prefs[key]
        if (json == null) {
            return emptyList()
        }
        return decodeContinueWatchingList(json).filterNot { item ->
            isNotResumable(id = item.id, isAddonNative = !item.addonNativeId.isNullOrBlank())
        }
    }


    /**
     * Get a single local Continue Watching entry (raw, without TMDB enrichment).
     * Used for resume playback.
     */
    suspend fun getLocalContinueWatchingEntry(
        mediaType: MediaType,
        tmdbId: Int,
        season: Int?,
        episode: Int?
    ): ContinueWatchingItem? {
        val items = loadLocalContinueWatchingRaw()
        return items.firstOrNull { item ->
            if (item.id != tmdbId) return@firstOrNull false
            if (item.mediaType != mediaType) return@firstOrNull false
            if (mediaType == MediaType.MOVIE) return@firstOrNull true
            item.season == season && item.episode == episode
        }
    }

    /**
     * Get best local Continue Watching entry for a show/movie regardless of provided episode.
     * Useful when remote history is unavailable but local playback progress exists.
     */
    suspend fun getBestLocalContinueWatchingEntry(
        mediaType: MediaType,
        tmdbId: Int
    ): ContinueWatchingItem? {
        val items = loadLocalContinueWatchingRaw()
            .filter { it.id == tmdbId && it.mediaType == mediaType }
        if (items.isEmpty()) return null
        return items.maxWithOrNull(
            compareBy<ContinueWatchingItem> { it.updatedAtMs }
                .thenBy { it.resumePositionSeconds.coerceAtLeast(0L) }
                .thenBy { it.progress.coerceAtLeast(0) }
        )
    }

    /**
     * Load local Continue Watching items enriched with TMDB data (overview, duration, etc.)
     */
    private suspend fun loadLocalContinueWatching(): List<ContinueWatchingItem> = coroutineScope {
        val rawItems = loadLocalContinueWatchingRaw()
        if (rawItems.isEmpty()) return@coroutineScope emptyList()

        // Enrich items with TMDB data in parallel (limited concurrency)
        val semaphore = kotlinx.coroutines.sync.Semaphore(5)
        val seasonCache = java.util.concurrent.ConcurrentHashMap<Pair<Int, Int>, Deferred<com.arflix.tv.data.api.TmdbSeasonDetails?>>()
        rawItems.map { item ->
            async {
                semaphore.withPermit {
                    enrichLocalContinueWatchingItem(item, seasonCache)
                }
            }
        }.awaitAll()
    }

    /**
     * Enrich arbitrary Continue Watching items with TMDB metadata so every
     * Continue Watching card renders the same details.
     */
    suspend fun enrichContinueWatchingItems(items: List<ContinueWatchingItem>): List<ContinueWatchingItem> = coroutineScope {
        val filtered = items.filterNot { item ->
            isNotResumable(id = item.id, isAddonNative = !item.addonNativeId.isNullOrBlank())
        }
        if (filtered.isEmpty()) return@coroutineScope emptyList()
        val semaphore = kotlinx.coroutines.sync.Semaphore(5)
        val seasonCache = java.util.concurrent.ConcurrentHashMap<Pair<Int, Int>, Deferred<com.arflix.tv.data.api.TmdbSeasonDetails?>>()
        filtered.map { item ->

            async {
                semaphore.withPermit {
                    enrichLocalContinueWatchingItem(item, seasonCache)
                }
            }
        }.awaitAll()
    }

    /** Enrich Continue Watching with series metadata and episode-specific artwork. */
    private suspend fun enrichLocalContinueWatchingItem(
        item: ContinueWatchingItem,
        seasonCache: java.util.concurrent.ConcurrentHashMap<Pair<Int, Int>, Deferred<com.arflix.tv.data.api.TmdbSeasonDetails?>> = java.util.concurrent.ConcurrentHashMap()
    ): ContinueWatchingItem = coroutineScope {
        if (item.id < 0) return@coroutineScope item
        // Skip only when all Continue Watching metrics are already present.
        val needsRuntime = item.durationSeconds <= 0L
        val needsEpisodeCounts = item.mediaType == MediaType.TV && item.totalEpisodes <= 0
        val needsEpisodeArtwork = item.mediaType == MediaType.TV &&
            item.season != null &&
            item.episode != null &&
            item.episodeStillPath.isNullOrBlank()
        if (!needsRuntime && !needsEpisodeCounts && !needsEpisodeArtwork && item.overview.isNotEmpty() && item.backdropPath?.startsWith("http") == true) {
            return@coroutineScope item
        }

        val apiKey = Constants.TMDB_API_KEY
        try {
            return@coroutineScope if (item.mediaType == MediaType.TV) {
                val details = try {
                    tmdbApi.getMergedTvDetails(item.id, apiKey)
                } catch (e: Exception) { AppLogger.e("LocalWatchRepository", "Silently returning null", e); null }

                // Get current season info for episode title and aired-episode counts.
                val seasonDetails = if (item.season != null && item.episode != null &&
                    (item.episodeTitle.isNullOrEmpty() || needsEpisodeCounts || needsEpisodeArtwork)
                ) {
                    try {
                        val cacheKey = Pair(item.id, item.season)
                        val newDeferred = CompletableDeferred<com.arflix.tv.data.api.TmdbSeasonDetails?>()
                        val existingDeferred = seasonCache.putIfAbsent(cacheKey, newDeferred)

                        val deferredSeason = if (existingDeferred == null) {
                            // We won the insert, do the network call
                            launch {
                                val result = try {
                                    tmdbApi.getMergedTvSeason(item.id, item.season, apiKey)
                                } catch (e: Exception) { AppLogger.e("LocalWatchRepository", "Silently returning null", e); null }
                                newDeferred.complete(result)
                            }
                            newDeferred
                        } else {
                            // Another coroutine is already fetching
                            existingDeferred
                        }

                        deferredSeason.await()
                    } catch (e: Exception) { AppLogger.e("LocalWatchRepository", "Silently returning null", e); null }
                } else null
                val episodeInfo = seasonDetails?.episodes?.find { it.episodeNumber == item.episode }

                val backdropUrl = details?.backdropPath?.let { "${Constants.BACKDROP_BASE_LARGE}$it" }
                val posterUrl = details?.posterPath?.let { "${Constants.IMAGE_BASE}$it" }
                val episodeStillUrl = episodeInfo?.stillPath?.let { "${Constants.IMAGE_BASE_LARGE}$it" }
                val totalEpisodeCount = if (item.totalEpisodes > 0) {
                    item.totalEpisodes
                } else {
                    estimateAiredEpisodeCount(
                        seasons = details?.seasons.orEmpty(),
                        currentSeason = item.season,
                        currentSeasonEpisodes = seasonDetails?.episodes
                    ) ?: 0
                }
                val watchedEpisodeCount = if (item.watchedEpisodes > 0) {
                    item.watchedEpisodes
                } else {
                    estimateWatchedEpisodesBeforeCurrent(
                        seasons = details?.seasons.orEmpty(),
                        currentSeason = item.season,
                        currentEpisode = item.episode
                    )?.coerceAtMost(totalEpisodeCount.takeIf { it > 0 } ?: Int.MAX_VALUE)
                }
                val runtimeMinutes = episodeInfo?.runtime ?: details?.episodeRunTime?.firstOrNull()

                item.copy(
                    overview = details?.overview ?: item.overview,  // Show overview, not episode
                    backdropPath = backdropUrl ?: item.backdropPath,
                    episodeStillPath = episodeStillUrl ?: item.episodeStillPath,
                    posterPath = posterUrl ?: item.posterPath,
                    year = details?.firstAirDate?.take(4) ?: item.year,
                    tmdbRating = details?.voteAverage?.let { String.format(Locale.US, "%.1f", it) } ?: item.tmdbRating.orEmpty(),
                    duration = runtimeMinutes?.let { "${it}m" } ?: item.duration,
                    durationSeconds = maxOf(item.durationSeconds, runtimeMinutesToSeconds(runtimeMinutes)),
                    episodeTitle = item.episodeTitle ?: episodeInfo?.name,
                    totalEpisodes = totalEpisodeCount,
                    watchedEpisodes = watchedEpisodeCount ?: item.watchedEpisodes
                )
            } else {
                val details = try {
                    tmdbApi.getMovieDetails(item.id, apiKey)
                } catch (e: Exception) { AppLogger.e("LocalWatchRepository", "Silently returning null", e); null }

                // Build full URLs for images
                val backdropUrl = details?.backdropPath?.let { "${Constants.BACKDROP_BASE_LARGE}$it" }
                val posterUrl = details?.posterPath?.let { "${Constants.IMAGE_BASE}$it" }

                item.copy(
                    overview = details?.overview ?: item.overview,
                    backdropPath = backdropUrl ?: item.backdropPath,
                    posterPath = posterUrl ?: item.posterPath,
                    year = details?.releaseDate?.take(4) ?: item.year,
                    tmdbRating = details?.voteAverage?.let { String.format(Locale.US, "%.1f", it) } ?: item.tmdbRating.orEmpty(),
                    duration = details?.runtime?.let { formatRuntime(it) } ?: item.duration,
                    durationSeconds = maxOf(item.durationSeconds, runtimeMinutesToSeconds(details?.runtime))
                )
            }
        } catch (_: Exception) {
            item // Return original on error
        }
    }

    /**
     * Get the saved Continue Watching, enriched from TMDB.
     * Returns items that were saved locally via saveLocalContinueWatching().
     */
    suspend fun getLocalContinueWatching(): List<ContinueWatchingItem> {
        return getContinueWatching()
    }

    /** Profile-scoped saved playback, without waiting for metadata or tracker requests. */
    internal suspend fun getLocalContinueWatchingSnapshot(): List<ContinueWatchingItem> {
        return loadLocalContinueWatchingRaw()
    }
    private fun formatRuntime(runtime: Int): String {
        val hours = runtime / 60
        val mins = runtime % 60
        return if (hours > 0) "${hours}h ${mins}m" else "${mins}m"
    }

    private fun runtimeMinutesToSeconds(minutes: Int?): Long {
        return minutes
            ?.takeIf { it > 0 }
            ?.toLong()
            ?.times(60L)
            ?: 0L
    }

    private suspend fun loadDismissedContinueWatching(): Map<String, Long> {
        val raw = context.settingsDataStore.data.first()[dismissedContinueWatchingKey()]
        return parseDismissedMap(raw)
    }

    private suspend fun persistDismissedContinueWatching(
        map: Map<String, Long>,
        profileId: String? = null
    ) {
        val storageKey = if (profileId.isNullOrBlank()) {
            dismissedContinueWatchingKey()
        } else {
            profileManager.profileStringKeyFor(profileId, DISMISSED_CONTINUE_WATCHING_KEY)
        }
        context.settingsDataStore.edit { prefs ->
            if (map.isEmpty()) {
                prefs.remove(storageKey)
            } else {
                prefs[storageKey] = encodeDismissedMap(map)
            }
        }
    }

    private suspend fun clearDismissedContinueWatching(
        mediaType: MediaType,
        tmdbId: Int,
        season: Int?,
        episode: Int?
    ) {
        val exactKey = buildContinueWatchingKey(mediaType, tmdbId, season, episode)
        val showKey = buildContinueWatchingShowKey(mediaType, tmdbId)
        context.settingsDataStore.edit { prefs ->
            val map = parseDismissedMap(prefs[dismissedContinueWatchingKey()])
            val exactRemoved = map.remove(exactKey) != null
            val showRemoved = map.remove(showKey) != null
            if (exactRemoved || showRemoved) {
                if (map.isEmpty()) {
                    prefs.remove(dismissedContinueWatchingKey())
                } else {
                    prefs[dismissedContinueWatchingKey()] = encodeDismissedMap(map)
                }
            }
        }
    }

    suspend fun dismissContinueWatching(item: MediaItem) {
        val key = buildContinueWatchingKey(item) ?: return
        val showKey = buildContinueWatchingShowKey(item.mediaType, item.id)
        val now = System.currentTimeMillis()
        context.settingsDataStore.edit { prefs ->
            val map = parseDismissedMap(prefs[dismissedContinueWatchingKey()])
            map[key] = now
            map[showKey] = now
            prefs[dismissedContinueWatchingKey()] = encodeDismissedMap(map)
        }

        cachedContinueWatching = cachedContinueWatching.filterNot {
            it.id == item.id && it.mediaType == item.mediaType
        }
        val activeProfileId = try {
            profileManager.getProfileIdSync()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        if (!activeProfileId.isNullOrBlank()) {
            preloadedProfileCache[activeProfileId] = preloadedProfileCache[activeProfileId]
                ?.filterNot { it.id == item.id && it.mediaType == item.mediaType }
                .orEmpty()
        }
    }

    suspend fun getDismissedContinueWatchingShowKeys(): Set<String> {
        val dismissed = loadDismissedContinueWatching()
        if (dismissed.isEmpty()) return emptySet()
        return dismissed.keys.mapNotNull { key ->
            when {
                key.startsWith("movie:") -> key.substringAfterLast(':').toIntOrNull()?.let { "MOVIE:$it" }
                key.startsWith("tv:") -> key.split(':').getOrNull(1)?.toIntOrNull()?.let { "TV:$it" }
                else -> null
            }
        }.toSet()
    }

    private fun buildContinueWatchingKey(item: ContinueWatchingItem): String? {
        return buildContinueWatchingKey(item.mediaType, item.id, item.season, item.episode)
    }

    private fun buildContinueWatchingKey(item: MediaItem): String? {
        val season = item.nextEpisode?.seasonNumber
        val episode = item.nextEpisode?.episodeNumber
        return buildContinueWatchingKey(item.mediaType, item.id, season, episode)
    }

    private fun buildContinueWatchingKey(
        mediaType: MediaType,
        tmdbId: Int,
        season: Int?,
        episode: Int?
    ): String {
        return if (mediaType == MediaType.MOVIE) {
            "movie:$tmdbId"
        } else {
            if (season != null && episode != null) {
                "tv:$tmdbId:$season:$episode"
            } else {
                "tv:$tmdbId"
            }
        }
    }

    private fun buildContinueWatchingShowKey(mediaType: MediaType, tmdbId: Int): String {
        return if (mediaType == MediaType.MOVIE) {
            "movie:$tmdbId"
        } else {
            "tv:$tmdbId"
        }
    }

    private fun parseDismissedMap(raw: String?): MutableMap<String, Long> {
        val map = mutableMapOf<String, Long>()
        if (raw.isNullOrBlank()) return map
        raw.split("|").forEach { entry ->
            val idx = entry.lastIndexOf(',')
            if (idx <= 0 || idx >= entry.length - 1) return@forEach
            val key = entry.substring(0, idx)
            val value = entry.substring(idx + 1).toLongOrNull() ?: return@forEach
            map[key] = value
        }
        return map
    }

    private fun encodeDismissedMap(map: Map<String, Long>): String {
        return map.entries.joinToString("|") { (key, value) -> "$key,$value" }
    }

    suspend fun filterDismissedContinueWatchingItems(
        items: List<ContinueWatchingItem>,
        profileId: String? = null
    ): List<ContinueWatchingItem> {
        if (items.isEmpty()) return emptyList()

        val dismissed = if (profileId.isNullOrBlank()) {
            loadDismissedContinueWatching()
        } else {
            val prefs = context.settingsDataStore.data.first()
            val key = profileManager.profileStringKeyFor(profileId, DISMISSED_CONTINUE_WATCHING_KEY)
            parseDismissedMap(prefs[key])
        }
        if (dismissed.isEmpty()) return items

        val updatedDismissed = dismissed.toMutableMap()
        val visible = items.filter { item ->
            val exactKey = buildContinueWatchingKey(item)
            val showKey = buildContinueWatchingShowKey(item.mediaType, item.id)
            val dismissedAt = listOfNotNull(
                dismissed[showKey],
                exactKey?.let(dismissed::get)
            ).maxOrNull() ?: return@filter true

            if (item.updatedAtMs > dismissedAt) {
                updatedDismissed.remove(showKey)
                exactKey?.let(updatedDismissed::remove)
                true
            } else {
                false
            }
        }
        if (updatedDismissed.size != dismissed.size) {
            persistDismissedContinueWatching(updatedDismissed, profileId)
        }
        return visible
    }
    // ========== Watched status cache ==========

    // In-memory cache for watched status (mirrors the saved local snapshot)
    private val watchedMoviesCache = mutableSetOf<Int>()
    private val watchedEpisodesCache = mutableSetOf<String>()
    // Monotonic local write-generation fence. Every user-intent write bumps
    // [watchedWriteGeneration] and records the per-key generation, so a load that
    // started before a mark or unmark never undoes it. Pure local ordering.
    private val watchedWriteGeneration = AtomicLong(0L)
    private val episodeWriteGenerations = ConcurrentHashMap<String, Long>()
    private val movieWriteGenerations = ConcurrentHashMap<Int, Long>()
    @Volatile private var cacheInitialized = false
    private val watchedCacheLoadMutex = Mutex()
    // Bumped by every invalidation and profile switch, so a load that started before one does not
    // mark the cache initialized with data from before it.
    @Volatile private var watchedCacheGeneration = 0L
    private val watchedCacheReloads = WatchedCacheReloads()

    /** Emits after the watched cache was reloaded following [invalidateWatchedCache]. */
    val watchedCacheReloaded: SharedFlow<Unit> get() = watchedCacheReloads.reloads

    /**
     * Invalidate watched cache - forces reload on next access
     */
    @Synchronized
    fun invalidateWatchedCache() {
        ensureProfileCacheScope()
        cacheInitialized = false
        watchedCacheGeneration++
        watchedCacheReloads.invalidated()
        watchedMoviesCache.clear()
        watchedEpisodesCache.clear()
        episodeWriteGenerations.clear()
        movieWriteGenerations.clear()
    }

    /**
     * Load the watched cache from the profile's saved local snapshot. Profile isolation
     * comes from [ensureProfileCacheScope] and the per-profile snapshot keys.
     */
    suspend fun initializeWatchedCache() {
        val profileId = currentProfileId()
        watchedCacheLoadMutex.withLock {
            while (true) {
                if (currentProfileId() != profileId) throw kotlinx.coroutines.CancellationException("Profile changed during watched load")
                ensureProfileCacheScope()
                if (cacheInitialized) return
                val generation = watchedCacheGeneration
                // Assemble privately. Never publish partial or obsolete results to another profile.
                val (localSnapshotMovies, localSnapshotEpisodes) = loadLocalWatchedSnapshotForCurrentProfile()
                val movies = localSnapshotMovies.toMutableSet()
                val episodes = localSnapshotEpisodes.toMutableSet()
                synchronized(this) {
                    if (currentProfileId() != profileId) throw kotlinx.coroutines.CancellationException("Profile changed during watched load")
                    if (generation == watchedCacheGeneration) {
                        // Local marks, including those made before this first load, win until
                        // an explicit invalidation starts a fresh reconciliation.
                        movieWriteGenerations.keys.forEach {
                            if (it in watchedMoviesCache) movies.add(it) else movies.remove(it)
                        }
                        episodeWriteGenerations.keys.forEach {
                            if (it in watchedEpisodesCache) episodes.add(it) else episodes.remove(it)
                        }
                        watchedMoviesCache.clear()
                        watchedMoviesCache.addAll(movies)
                        watchedEpisodesCache.clear()
                        watchedEpisodesCache.addAll(episodes)
                        cacheInitialized = true
                        watchedCacheReloads.loaded()
                        return
                    }
                }
            }
        }
    }

    /**
     * Update watched cache entry
     */
    @Synchronized
    private fun updateWatchedCache(tmdbId: Int, season: Int?, episode: Int?, watched: Boolean) {
        ensureProfileCacheScope()
        if (season == null || episode == null) {
            // Movie
            if (watched) {
                watchedMoviesCache.add(tmdbId)
            } else {
                watchedMoviesCache.remove(tmdbId)
            }
            // Issue 4: fence local movie writes (kept for symmetry; the episode
            // path is where stale remote overwrites regress badges today).
            movieWriteGenerations[tmdbId] = watchedWriteGeneration.incrementAndGet()
        } else {
            // Episode
            val key = buildEpisodeKey(tmdbId, season, episode)
            if (watched) {
                watchedEpisodesCache.add(key)
            } else {
                watchedEpisodesCache.remove(key)
            }
            // Issue 4: record the write generation for BOTH directions. A local
            // unwatch after the reconcile request was issued must also win over a
            // stale remote "completed=true".
            episodeWriteGenerations[key] = watchedWriteGeneration.incrementAndGet()
        }
    }

    /**
     * Check if movie is watched (uses cache)
     */
    fun isMovieWatched(tmdbId: Int): Boolean {
        ensureProfileCacheScope()
        return watchedMoviesCache.contains(tmdbId)
    }

    /**
     * Check if episode is watched (uses cache)
     */
    fun isEpisodeWatched(tmdbId: Int, season: Int, episode: Int): Boolean {
        ensureProfileCacheScope()
        val key = buildEpisodeKey(tmdbId, season, episode)
        return watchedEpisodesCache.contains(key)
    }

    /**
     * Get all watched movie IDs from cache
     */
    fun getWatchedMoviesFromCache(): Set<Int> {
        ensureProfileCacheScope()
        return watchedMoviesCache.toSet()
    }

    /**
     * Get all watched episode keys from cache
     */
    fun getWatchedEpisodesFromCache(): Set<String> {
        ensureProfileCacheScope()
        return watchedEpisodesCache.toSet()
    }

    /**
     * Check if show has any watched episodes - optimized to avoid full iteration
     */
    fun hasWatchedEpisodes(showTmdbId: Int): Boolean {
        ensureProfileCacheScope()
        val prefix = "show_tmdb:$showTmdbId:"
        return watchedEpisodesCache.any { it.startsWith(prefix) }
    }
}

/**
 * Continue watching item model
 */
data class ContinueWatchingItem(
    val id: Int,
    val title: String,
    val mediaType: MediaType,
    val progress: Int, // 0-100
    val resumePositionSeconds: Long = 0L,
    val durationSeconds: Long = 0L,
    val season: Int? = null,
    val episode: Int? = null,
    val displaySeason: Int? = season,
    val displayEpisode: Int? = episode,
    val episodeTitle: String? = null,
    val backdropPath: String? = null,
    val episodeStillPath: String? = null,
    val posterPath: String? = null,
    val streamKey: String? = null,
    val streamAddonId: String? = null,
    val streamTitle: String? = null,
    val year: String = "",
    val releaseDate: String = "",  // Full formatted date
    val isUpNext: Boolean = false,
    val overview: String = "",
    val imdbRating: String = "",
    val tmdbRating: String = "",
    val duration: String = "",
    val budget: Long? = null,
    val updatedAtMs: Long = 0L,
    val totalEpisodes: Int = 0,
    val watchedEpisodes: Int = 0,
    val addonNativeId: String? = null,
    val addonNativeAddonId: String? = null,
    // Portable, so a device restoring this entry asks the addon with its own type.
    val addonNativeType: String? = null
) {
    fun toMediaItem(context: Context? = null): MediaItem {
        val effectiveDurationSeconds = durationSeconds.takeIf { it > 0L } ?: parseRuntimeLabelSeconds(duration)
        val showPlaybackProgress = !isUpNext && progress in 1 until Constants.WATCHED_THRESHOLD
        val resumeSeconds = when {
            resumePositionSeconds > 0L -> resumePositionSeconds
            // Only derive resume position from progress if we have a meaningful duration
            // and progress is above a trivial threshold (>5%) to avoid showing bogus
            // resume times for placeholder "next episode" entries.
            !isUpNext && effectiveDurationSeconds > 0L && progress > 5 ->
                ((effectiveDurationSeconds * progress) / 100L).coerceAtLeast(1L)
            else -> 0L
        }
        val resumeLabel = resumeSeconds.takeIf { it > 0L }?.let { formatResumeClock(it) }

        val subtitle = if (mediaType == MediaType.TV && season != null && episode != null) {
            val shownSeason = displaySeason ?: season
            val shownEpisode = displayEpisode ?: episode
            val base = context?.getString(R.string.continue_season_episode, shownSeason, shownEpisode)
                ?: "Continue S${shownSeason}E${shownEpisode}"
            if (!resumeLabel.isNullOrBlank()) {
                context?.getString(R.string.continue_from, resumeLabel) ?: "$base from $resumeLabel"
            } else {
                base
            }
        } else {
            if (mediaType == MediaType.MOVIE) {
                if (!resumeLabel.isNullOrBlank()) {
                    context?.getString(R.string.continue_from, resumeLabel)
                        ?: "Continue from $resumeLabel"
                } else {
                    context?.getString(R.string.continue_label) ?: "Continue"
                }
            } else {
                context?.getString(R.string.component_label_tv_series) ?: "TV Series"
            }
        }

        val nextEp = if (mediaType == MediaType.TV && season != null && episode != null) {
            NextEpisode(
                id = 0,
                seasonNumber = season,
                episodeNumber = episode,
                name = episodeTitle ?: "Episode $episode"
            )
        } else null

        // Compute remaining time: duration - resume position
        val timeRemainingSeconds = when {
            effectiveDurationSeconds > 0L && resumePositionSeconds > 0L ->
                (effectiveDurationSeconds - resumePositionSeconds).coerceAtLeast(0L)
            !isUpNext && effectiveDurationSeconds > 0L && progress in 1 until Constants.WATCHED_THRESHOLD ->
                (effectiveDurationSeconds * (100L - progress) / 100L).coerceAtLeast(0L)
            else -> 0L
        }
        val timeRemainingLabel = if (showPlaybackProgress) {
            formatTimeRemainingCompact(timeRemainingSeconds)
        } else {
            null
        }

        val totalEpisodeCount = totalEpisodes.takeIf { mediaType == MediaType.TV && it > 0 }
        val watchedEpisodeCount = watchedEpisodes
            .takeIf { totalEpisodeCount != null && it > 0 }
            ?.coerceAtMost(totalEpisodeCount ?: 0)

        return MediaItem(
            id = id,
            title = title,
            subtitle = subtitle,
            overview = overview,
            year = year,
            releaseDate = releaseDate,
            imdbRating = "",
            tmdbRating = tmdbRating.orEmpty().ifBlank { imdbRating.orEmpty() },
            duration = duration,
            mediaType = mediaType,
            progress = progress,
            image = posterPath ?: backdropPath ?: "",
            backdrop = backdropPath,
            episodeStill = episodeStillPath,
            badge = null,
            budget = budget,
            nextEpisode = nextEp,
            totalEpisodes = totalEpisodeCount,
            watchedEpisodes = watchedEpisodeCount,
            timeRemainingLabel = timeRemainingLabel,
            showPlaybackProgress = showPlaybackProgress,
            addonNativeId = addonNativeId,
            addonNativeAddonId = addonNativeAddonId,
            addonNativeType = addonNativeType
        )
    }
}

private fun estimateWatchedEpisodesBeforeCurrent(
    seasons: List<com.arflix.tv.data.api.TmdbTvSeason>,
    currentSeason: Int?,
    currentEpisode: Int?
): Int? {
    if (currentSeason == null || currentEpisode == null) return null
    val previousSeasonCount = seasons
        .asSequence()
        .filter { it.seasonNumber > 0 && it.seasonNumber < currentSeason }
        .sumOf { it.episodeCount.coerceAtLeast(0) }
    return previousSeasonCount + (currentEpisode - 1).coerceAtLeast(0)
}

private fun estimateAiredEpisodeCount(
    seasons: List<com.arflix.tv.data.api.TmdbTvSeason>,
    currentSeason: Int?,
    currentSeasonEpisodes: List<com.arflix.tv.data.api.TmdbEpisode>?
): Int? {
    if (currentSeason == null) return null

    val previousSeasonCount = seasons
        .asSequence()
        .filter { season ->
            season.seasonNumber > 0 &&
                season.seasonNumber < currentSeason &&
                isAlreadyAiredDate(season.airDate)
        }
        .sumOf { it.episodeCount.coerceAtLeast(0) }

    val currentSeasonCount = currentSeasonEpisodes
        ?.count { episode -> isAlreadyAiredDate(episode.airDate) }
        ?: seasons
            .firstOrNull { it.seasonNumber == currentSeason }
            ?.takeIf { isAlreadyAiredDate(it.airDate) }
            ?.episodeCount
            ?.coerceAtLeast(0)
        ?: 0

    return (previousSeasonCount + currentSeasonCount).takeIf { it > 0 }
}

private fun isAlreadyAiredDate(rawDate: String?): Boolean {
    val value = rawDate?.trim().orEmpty()
    if (value.isEmpty()) return false
    return try {
        val parser = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        parser.isLenient = false
        val parsed = parser.parse(value) ?: return false
        parsed.time <= System.currentTimeMillis()
    } catch (_: Exception) {
        false
    }
}

private fun formatResumeClock(totalSeconds: Long): String {
    val safe = totalSeconds.coerceAtLeast(0L)
    val hours = safe / 3600
    val minutes = (safe % 3600) / 60
    val seconds = safe % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}
/**
 * Format seconds to a compact human-readable time remaining string.
 * e.g., "45min left", "1hr 15min left", "2hr left"
 */
private fun formatTimeRemainingCompact(totalSeconds: Long): String? {
    val safe = totalSeconds.coerceAtLeast(0L)
    if (safe < 60) return null // Less than a minute
    val hours = safe / 3600
    val minutes = (safe % 3600) / 60
    return when {
        hours > 0 && minutes > 0 -> "${hours}hr ${minutes}min left"
        hours > 0 -> "${hours}hr left"
        else -> "${minutes}min left"
    }
}

private fun parseRuntimeLabelSeconds(label: String): Long {
    val normalized = label.lowercase(Locale.US)
    if (normalized.isBlank()) return 0L

    var minutes = 0L
    LocalWatchRegexes.HOURS_REGEX.find(normalized)?.groupValues?.getOrNull(1)?.toLongOrNull()?.let { hours ->
        minutes += hours * 60L
    }
    LocalWatchRegexes.MINS_REGEX.find(normalized)?.groupValues?.getOrNull(1)?.toLongOrNull()?.let { mins ->
        minutes += mins
    }

    return minutes.takeIf { it > 0L }?.times(60L) ?: 0L
}

/** Per-profile key of the Continue Watching dismissals (name kept from earlier versions). */
private const val DISMISSED_CONTINUE_WATCHING_KEY = "dismissed_continue_watching_v1"

private fun buildEpisodeKey(showTmdbId: Int, season: Int, episode: Int): String =
    "show_tmdb:$showTmdbId:$season:$episode"

private object LocalWatchRegexes {
    val HOURS_REGEX = Regex("""(\d+)\s*h""")
    val MINS_REGEX = Regex("""(\d+)\s*m""")
}
