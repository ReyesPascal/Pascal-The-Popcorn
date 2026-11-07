package com.arflix.tv.ui.screens.home

import android.app.ActivityManager
import android.content.Context
import com.arflix.tv.util.settingsDataStore
import androidx.compose.runtime.mutableStateMapOf
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arflix.tv.data.model.Category
import com.arflix.tv.data.model.MediaItem
import com.arflix.tv.data.model.MediaType
import com.arflix.tv.data.model.isNotResumable
import com.arflix.tv.R
import com.arflix.tv.data.repository.MediaRepository
import com.arflix.tv.data.repository.LocalWatchRepository
import com.arflix.tv.data.repository.ContinueWatchingItem
import com.arflix.tv.data.repository.ContinueWatchingMerge
import com.arflix.tv.data.repository.ContinueWatchingUpdate
import com.arflix.tv.data.repository.ContinueWatchingUpdates
import com.arflix.tv.data.repository.LauncherContinueWatchingRepository
import com.arflix.tv.data.repository.ProfileManager
import com.arflix.tv.data.repository.StreamRepository
import com.arflix.tv.data.repository.WatchHistoryRepository
import com.arflix.tv.data.repository.WatchlistRepository
import com.arflix.tv.util.AppLogger
import com.arflix.tv.util.Constants
import com.arflix.tv.util.DeviceType
import com.arflix.tv.util.EpisodeAvailability
import com.arflix.tv.util.detectDeviceType
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.util.Collections
import java.util.Locale
import javax.inject.Inject

// Shared with the retained View all grid.
internal const val HOME_TV_LANDSCAPE_CARD_WIDTH_DP = 210
internal const val HOME_MOBILE_LANDSCAPE_CARD_WIDTH_DP = 200
internal const val HOME_TV_POSTER_CARD_WIDTH_DP = 105
internal const val HOME_MOBILE_POSTER_CARD_WIDTH_DP = 120

@androidx.compose.runtime.Immutable
data class HomeUiState(
    val isLoading: Boolean = false,
    val isInitialLoad: Boolean = false,
    val categories: List<Category> = emptyList(),
    val error: String? = null,
    val clockFormat: String = "24h",
    val toastMessage: String? = null,
    val toastType: ToastType = ToastType.INFO,
    val categoryHasMoreMap: Map<String, Boolean> = emptyMap(),
    val smoothScrolling: Boolean = false
)



internal fun startedShowIds(watchedEpisodeKeys: Set<String>): Set<Int> =
    watchedEpisodeKeys.mapNotNullTo(HashSet()) { key ->
        if (key.startsWith("show_tmdb:")) {
            key.removePrefix("show_tmdb:").substringBefore(':', "").toIntOrNull()
        } else null
    }

/**
 * Sets [MediaItem.isWatched] on the Home cards: a film when it is in the watched list, a series
 * when it has been started — the same reading Search and Discover use. Continue Watching keeps
 * its own progress bars and is left alone. Rows (and the list) that do not change come back as
 * the same instances, so a refresh that finds nothing new recomposes nothing.
 */
internal fun applyWatchedBadges(
    categories: List<Category>,
    watchedMovies: Set<Int>,
    startedShows: Set<Int>
): List<Category> {
    var anyChange = false
    val updated = categories.map { category ->
        if (category.id == "continue_watching") return@map category
        var categoryChanged = false
        val items = category.items.map { item ->
            val watched = when (item.mediaType) {
                MediaType.MOVIE -> item.id in watchedMovies
                MediaType.TV -> item.id in startedShows
            }
            if (item.isWatched == watched) {
                item
            } else {
                categoryChanged = true
                item.copy(isWatched = watched)
            }
        }
        if (categoryChanged) {
            anyChange = true
            category.copy(items = items)
        } else {
            category
        }
    }
    return if (anyChange) updated else categories
}

/** Carries watched-history badges onto freshly published rows. */
internal fun HomeUiState.withWatchedBadges(watchedMovies: Set<Int>, startedShows: Set<Int>): HomeUiState {
    val updatedCategories = applyWatchedBadges(categories, watchedMovies, startedShows)
    return if (updatedCategories === categories) this else copy(categories = updatedCategories)
}

internal fun watchedBadgesPassIsRedundant(
    rows: List<Category>,
    lastMarkedRows: List<Category>?,
    force: Boolean,
    sinceLastPassMs: Long,
    throttleMs: Long
): Boolean = !force && rows === lastMarkedRows && sinceLastPassMs < throttleMs

/**
 * How long a tick pass waits before it runs. The first pass after launch or a profile switch keeps
 * the startup pause, so it does not compete with the first frames. Once a pass has run, a pass
 * Home asks for on resume (back from Details) — or one that replaces such a pending pass — only
 * debounces briefly: the cache is loaded and the pass itself takes milliseconds.
 */
internal fun watchedBadgesPassDelayMs(
    quickRequested: Boolean,
    hadPass: Boolean,
    isLowRamDevice: Boolean
): Long = when {
    quickRequested && hadPass -> 300L
    isLowRamDevice -> 3_000L
    else -> 1_800L
}

enum class ToastType { SUCCESS, ERROR, INFO }

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val streamRepository: StreamRepository,
    private val localWatchRepository: LocalWatchRepository,
    private val watchHistoryRepository: WatchHistoryRepository,
    private val watchlistRepository: WatchlistRepository,
    private val launcherContinueWatchingRepository: LauncherContinueWatchingRepository,
    private val continueWatchingUpdates: ContinueWatchingUpdates,
    private val appForegroundSignals: com.arflix.tv.data.repository.AppForegroundSignals,
    private val profileManager: ProfileManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private fun continueWatchingKey(mediaType: MediaType, id: Int): String {
        return "${mediaType.name}:$id"
    }

    private suspend fun applyContinueWatchingDismissals(
        items: List<ContinueWatchingItem>
    ): List<ContinueWatchingItem> {
        val cloudFiltered = try {
            localWatchRepository.filterDismissedContinueWatchingItems(items)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            items
        }
        return cloudFiltered.filter { item ->
            val key = continueWatchingKey(item.mediaType, item.id)
            val dismissedAt = dismissedContinueWatchingAt[key] ?: return@filter true
            if (item.updatedAtMs > dismissedAt) {
                dismissedContinueWatchingAt.remove(key)
                true
            } else {
                false
            }
        }
    }





    private fun mergeContinueWatchingVisuals(
        preferred: ContinueWatchingItem,
        fallback: ContinueWatchingItem
    ): ContinueWatchingItem = ContinueWatchingMerge.mergeVisuals(preferred, fallback)

    private fun needsContinueWatchingArtworkRepair(item: ContinueWatchingItem): Boolean {
        return item.posterPath.isNullOrBlank() ||
            item.backdropPath.isNullOrBlank() ||
            (item.mediaType == MediaType.TV && item.season != null && item.episode != null && item.episodeStillPath.isNullOrBlank()) ||
            item.overview.isBlank() ||
            item.durationSeconds <= 0L
    }

    private suspend fun repairContinueWatchingMetadataIfNeeded(
        items: List<ContinueWatchingItem>
    ): List<ContinueWatchingItem> {
        if (items.none(::needsContinueWatchingArtworkRepair)) return items
        return runCatching {
            localWatchRepository.enrichContinueWatchingItems(items)
                .zip(items) { enriched, original ->
                    mergeContinueWatchingVisuals(enriched, original)
                }
                .ifEmpty { items }
        }.onFailure { error ->
            AppLogger.recordException(
                throwable = error,
                context = mapOf(
                    "error_area" to "ContinueWatching",
                    "cw_phase" to "metadata_repair"
                )
            )
        }.getOrDefault(items)
    }



    fun cacheItem(item: MediaItem) {
        mediaRepository.cacheItem(item)
    }

    fun cacheLogoUrl(mediaType: MediaType, mediaId: Int, logoUrl: String) {
        mediaRepository.cacheLogoUrl(mediaType, mediaId, logoUrl)
    }

    /** Hydrate the focused title in the View all preview. */
    suspend fun loadViewAllPreview(item: MediaItem): MediaItem = withContext(networkDispatcher) {
        if (item.id <= 0 || item.isAddonNative) return@withContext item
        mediaRepository.getCachedFullItem(item.mediaType, item.id)
            ?: runCatching { if (item.mediaType == MediaType.MOVIE) mediaRepository.getMovieDetails(item.id) else mediaRepository.getTvDetails(item.id) }.getOrNull()
            ?: item
    }

    private suspend fun sanitizeContinueWatchingItems(
        items: List<ContinueWatchingItem>, cacheOnly: Boolean = false
    ): List<ContinueWatchingItem> = coroutineScope {
        val nonLiveItems = items.filterNot { item ->
            isNotResumable(
                id = item.id,
                // Also drops live channels saved before they were played as live.
                isLiveStream = mediaRepository.isAddonNativeLiveChannel(item.id),
                isAddonNative = !item.addonNativeId.isNullOrBlank()
            )
        }
        if (nonLiveItems.isEmpty()) return@coroutineScope emptyList()
        val seasons = withContext(networkDispatcher) {
            loadContinueWatchingSeasons(nonLiveItems, cacheOnly,
                mediaRepository::peekCachedSeasonEpisodes, mediaRepository::getSeasonEpisodes)
        }
        nonLiveItems.mapNotNull { item ->
            if (!item.addonNativeId.isNullOrBlank()) mediaRepository.cacheItem(item.toMediaItem())
            if (item.mediaType != MediaType.TV) {

                return@mapNotNull item
            }

            val season = item.season
            val episode = item.episode
            if (season == null || episode == null) {
                return@mapNotNull item
            }

            // Locally persisted up-next pointers carry the episode air date. This lets a
            // caught-up show stay hidden even when TMDB is temporarily unavailable, then
            // become eligible again as soon as the stored date arrives.
            if (
                item.isUpNext && item.releaseDate.isNotBlank() &&
                !EpisodeAvailability.hasAired(item.releaseDate)
            ) {
                return@mapNotNull null
            }

            val cacheKey = item.id to season
            if (!item.addonNativeId.isNullOrBlank()) return@mapNotNull item
            validatedContinueWatchingEpisode(item, seasons[cacheKey])
        }
    }

    private val isTvDevice = detectDeviceType(context) == DeviceType.TV
    private val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    private val isLowRamDevice =
        activityManager.isLowRamDevice ||
            activityManager.memoryClass <= 256 ||
            (isTvDevice && activityManager.memoryClass <= 384)
    private val gson = com.google.gson.Gson()

    // Disk cache key for the home categories — profile-scoped so each profile gets
    // its own cached home screen. On app launch, the cached categories are shown
    // immediately (within 1 frame) while loadHomeData() fetches fresh data from
    // TMDB in the background. This eliminates the visible skeleton/loading phase
    // that made the app feel slow on startup.
    // Use a plain file in the cache dir instead of DataStore — DataStore has
    // a size limit and the categories JSON can be 200KB+. A cache file is faster
    // to read/write and doesn't trigger DataStore observers.
    private suspend fun continueWatchingCacheFile(): java.io.File {
        val profileId = profileManager.getProfileIdSync()
            .ifBlank { "default" }
            .replace(HomeVMRegexes.ALPHANUMERIC_REGEX, "_")
        val language = mediaRepository.contentLanguage
            .replace(HomeVMRegexes.ALPHANUMERIC_REGEX, "_")
        // The "_none" suffix is kept from earlier versions so the saved row survives the update.
        return java.io.File(
            context.filesDir,
            "home_continue_watching_v2_${profileId}_${language}_none.json"
        )
    }

    private suspend fun persistContinueWatchingCache(items: List<ContinueWatchingItem>) {
        if (items.isEmpty()) return
        runCatching {
            val target = continueWatchingCacheFile()
            val temporary = java.io.File(target.parentFile, "${target.name}.tmp")
            temporary.writeText(gson.toJson(items.take(Constants.MAX_CONTINUE_WATCHING)))
            if (target.exists() && !target.delete()) {
                temporary.delete()
                return@runCatching
            }
            if (!temporary.renameTo(target)) {
                temporary.copyTo(target, overwrite = true)
                temporary.delete()
            }
        }
    }

    private suspend fun loadContinueWatchingCache(): List<ContinueWatchingItem> = runCatching {
        val file = continueWatchingCacheFile()
        if (!file.exists() || file.length() > maxContinueWatchingCacheBytes) return emptyList()
        val json = file.readText()
        if (json.isBlank()) return emptyList()
        val parsed = com.arflix.tv.data.repository.decodeContinueWatchingCache(json, gson)
        parsed.filter { it.id > 0 && it.title.isNotBlank() }.take(Constants.MAX_CONTINUE_WATCHING)
    }.getOrDefault(emptyList())

    private val networkParallelism = if (isLowRamDevice) 4 else 8
    private val networkDispatcher = Dispatchers.IO.limitedParallelism(networkParallelism)
    private var lastContinueWatchingItems: List<MediaItem> = emptyList()
    private var lastContinueWatchingUpdateMs: Long = 0L
    private val dismissedContinueWatchingAt = Collections.synchronizedMap(mutableMapOf<String, Long>())
    private val CONTINUE_WATCHING_REFRESH_MS = 45_000L
    private val WATCHED_BADGES_REFRESH_MS = 90_000L

    private var lastWatchedBadgesRefreshMs: Long = 0L
    @Volatile private var lastWatchedBadgesCategories: List<Category>? = null
    @Volatile private var watchedBadgesQuickPending = false
    // What the last pass found watched (films, started shows), to mark re-published rows at once.
    @Volatile private var lastWatchedBadgesLookup: Pair<Set<Int>, Set<Int>>? = null
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()
    val cardLogoUrls = mutableStateMapOf<String, String>()
    private var refreshContinueWatchingJob: Job? = null
    private val continueWatchingRequests = ContinueWatchingRefreshRequests()
    private var cwFetchJob: Job? = null
    @Volatile private var homeTrackingRevision = 0L
    private var watchedBadgesJob: Job? = null
    private var activeRuntimeProfileId: String? = null
    private val startupCreatedAtMs = SystemClock.elapsedRealtime()
    private val startupSettleMs = if (isLowRamDevice) 5_000L else 4_000L

    // Phase 6.2-6.3: Fast scroll detection
    private val maxContinueWatchingCacheBytes = 2_000_000L
    private fun isStartupSettling(): Boolean {
        return SystemClock.elapsedRealtime() - startupCreatedAtMs < startupSettleMs
    }

    private suspend fun delayUntilStartupSettled(extraDelayMs: Long = 0L) {
        val waitMs = (startupCreatedAtMs + startupSettleMs + extraDelayMs) -
            SystemClock.elapsedRealtime()
        if (waitMs > 0L) {
            delay(waitMs)
        }
    }

    private fun launchContinueWatchingFetch() {
        val ticket = continueWatchingRequests.claim(force = false) ?: return
        cwFetchJob = viewModelScope.launch(Dispatchers.IO) {
            try {
            val localUpdateRevision = continueWatchingUpdates.revision
            val trackingRevision = homeTrackingRevision
            val cached = try {
                preloadStartupContinueWatchingItems()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (error: Exception) {
                AppLogger.recordException(
                    throwable = error,
                    context = mapOf(
                        "error_area" to "ContinueWatching",
                        "cw_phase" to "preload_startup"
                    )
                )
                emptyList()
            }
            if (cached.isNotEmpty() && continueWatchingUpdates.revision == localUpdateRevision &&
                trackingRevision == homeTrackingRevision) {
                publishContinueWatching(cached)
            }

            // FAST PATH — resolve from saved local data and publish immediately.
            val instant = try {
                resolveContinueWatchingItemsStable(forceFresh = false)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (error: Exception) {
                AppLogger.recordException(
                    throwable = error,
                    context = mapOf(
                        "error_area" to "ContinueWatching",
                        "cw_phase" to "instant"
                    )
                )
                null
            }
            if (instant != null && continueWatchingUpdates.revision == localUpdateRevision &&
                trackingRevision == homeTrackingRevision) {
                publishContinueWatching(instant)
            }

            // SLOW PATH — do a freshness refresh in the background. If it
            // returns something different, republish. Swallows transient
            // errors so the visible row doesn't blink back to empty.
            val fresh = try {
                resolveContinueWatchingItemsStable(forceFresh = true)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (error: Exception) {
                AppLogger.recordException(
                    throwable = error,
                    context = mapOf(
                        "error_area" to "ContinueWatching",
                        "cw_phase" to "fresh"
                    )
                )
                null
            }
            if (
                fresh != null &&
                fresh != instant &&
                continueWatchingUpdates.revision == localUpdateRevision && trackingRevision == homeTrackingRevision
            ) {
                publishContinueWatching(fresh)
            }
            } finally {
                if (continueWatchingRequests.finish(ticket) && isActive) {
                    withContext(Dispatchers.Main.immediate) { refreshContinueWatchingOnly(force = true) }
                }
            }
        }
    }

    private suspend fun publishContinueWatching(items: List<ContinueWatchingItem>) {
        if (items.isEmpty()) {
            withContext(Dispatchers.IO) {
                persistContinueWatchingCache(emptyList())
            }
            withContext(Dispatchers.Main) {
                lastContinueWatchingItems = emptyList()
                lastContinueWatchingUpdateMs = SystemClock.elapsedRealtime()
                val current = _uiState.value.categories.filterNot { it.id == "continue_watching" }
                if (current.size != _uiState.value.categories.size) {
                    _uiState.value = _uiState.value.copy(categories = current)
                }
            }
            return
        }
        withContext(Dispatchers.IO) {
            persistContinueWatchingCache(items)
        }
        val continueWatchingCategory = Category(
            id = "continue_watching",
            title = "Continue Watching",
            items = items.map { it.toMediaItem() }
        )
        continueWatchingCategory.items.forEach { mediaRepository.cacheItem(it) }
        lastContinueWatchingItems = continueWatchingCategory.items
        lastContinueWatchingUpdateMs = SystemClock.elapsedRealtime()
        withContext(Dispatchers.Main) {
            val current = _uiState.value.categories.toMutableList()
            val cwIdx = current.indexOfFirst { it.id == "continue_watching" }
            if (cwIdx >= 0) {
                current[cwIdx] = continueWatchingCategory
            } else {
                current.add(0, continueWatchingCategory)
            }
            _uiState.value = _uiState.value.copy(categories = current)
        }
    }

    /**
     * True when a base (non-Continue-Watching, non-collection) row already holds real content.
     * Mirrors the test [loadHomeData] uses to decide whether the skeleton is still needed.
     */

    fun refreshContinueWatchingOnly(force: Boolean = false) {
        val ticket = continueWatchingRequests.claim(force) ?: return
        refreshContinueWatchingJob = viewModelScope.launch {
            try {
                val now = SystemClock.elapsedRealtime()
                val startCategories = _uiState.value.categories
                val continueWatchingIndexAtStart = startCategories.indexOfFirst { it.id == "continue_watching" }
                val existingContinueWatching = startCategories.getOrNull(continueWatchingIndexAtStart)
                val hasPlaceholders = existingContinueWatching?.items?.any { it.isPlaceholder } == true

                // Allow refresh if we have placeholders (need to replace them), otherwise throttle
                if (!force && !hasPlaceholders && now - lastContinueWatchingUpdateMs < CONTINUE_WATCHING_REFRESH_MS) {
                    return@launch
                }

                val localUpdateRevision = continueWatchingUpdates.revision
                val trackingRevision = homeTrackingRevision
                val resolvedContinueWatching = resolveContinueWatchingItemsStable(forceFresh = force)
                if (continueWatchingUpdates.revision != localUpdateRevision || trackingRevision != homeTrackingRevision) return@launch

                if (resolvedContinueWatching.isNotEmpty()) {
                    withContext(Dispatchers.IO) {
                        persistContinueWatchingCache(resolvedContinueWatching)
                    }
                    val mergedContinueWatching = mergeContinueWatchingResumeData(resolvedContinueWatching)
                    val continueWatchingCategory = Category(
                        id = "continue_watching",
                        title = "Continue Watching",
                        items = mergedContinueWatching.map { it.toMediaItem() }
                    )
                    continueWatchingCategory.items.forEach { mediaRepository.cacheItem(it) }
                    lastContinueWatchingItems = continueWatchingCategory.items
                    lastContinueWatchingUpdateMs = now
                    val latestCategories = _uiState.value.categories.toMutableList()
                    val continueWatchingIndex = latestCategories.indexOfFirst { it.id == "continue_watching" }
                    if (continueWatchingIndex >= 0) {
                        latestCategories[continueWatchingIndex] = continueWatchingCategory
                    } else {
                        latestCategories.add(0, continueWatchingCategory)
                    }
                    _uiState.value = _uiState.value.copy(categories = latestCategories)
                    refreshWatchedBadges()
                } else {
                    if (force) {
                        // A forced refresh is authoritative, including empty.
                        publishContinueWatching(emptyList())
                        return@launch
                    }
                    // No new data from any source
                    val latestCategories = _uiState.value.categories.toMutableList()
                    val continueWatchingIndex = latestCategories.indexOfFirst { it.id == "continue_watching" }
                    val latestHasPlaceholders = latestCategories
                        .getOrNull(continueWatchingIndex)
                        ?.items
                        ?.any { it.isPlaceholder } == true
                    if (hasPlaceholders) {
                        // We had placeholders but no data loaded - remove the placeholder category
                        if (continueWatchingIndex >= 0) {
                            latestCategories.removeAt(continueWatchingIndex)
                            _uiState.value = _uiState.value.copy(categories = latestCategories)
                        }
                    } else if (!latestHasPlaceholders && continueWatchingIndex >= 0) {
                        // Continue Watching exists with real data - preserve it exactly as is
                        return@launch
                    } else if (lastContinueWatchingItems.isNotEmpty()) {
                        val persistedDismissedKeys = runCatching {
                            localWatchRepository.getDismissedContinueWatchingShowKeys()
                        }.getOrDefault(emptySet())
                        val safeItems = lastContinueWatchingItems.filterNot { item ->
                            val showKey = continueWatchingKey(item.mediaType, item.id)
                            dismissedContinueWatchingAt.containsKey(showKey) || persistedDismissedKeys.contains(showKey)
                        }
                        if (safeItems.isEmpty()) {
                            return@launch
                        }
                        val continueWatchingCategory = Category(
                            id = "continue_watching",
                            title = "Continue Watching",
                            items = safeItems
                        )
                        latestCategories.add(0, continueWatchingCategory)
                        _uiState.value = _uiState.value.copy(categories = latestCategories)
                    }
                    // Else: No data anywhere - nothing to show, UI already doesn't have it
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e

                // Silently fail - don't clear existing data on error
                AppLogger.e("HomeVM", "launchContinueWatchingFetch failed", e)
            } finally {
                if (continueWatchingRequests.finish(ticket) && isActive) {
                    refreshContinueWatchingOnly(force = true)
                }
            }
        }
    }

    private suspend fun loadContinueWatchingFromHistoryStable(): List<ContinueWatchingItem> {
        return try {
            val entries = watchHistoryRepository.getContinueWatching()
            if (entries.isEmpty()) return emptyList()
            val mapped = entries.distinctBy { entry ->
                "${entry.media_type}:${entry.show_tmdb_id}"
            }.map(ContinueWatchingMerge::fromHistory)
            localWatchRepository.enrichContinueWatchingItems(mapped)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList()
        }
    }

    private suspend fun resolveContinueWatchingItemsStable(forceFresh: Boolean): List<ContinueWatchingItem> {
        val historyItems = loadContinueWatchingFromHistoryStable()
        val localItems = loadSavedContinueWatchingSnapshot()
        val items = ContinueWatchingMerge.merge(historyItems.ifEmpty { localItems }, localItems, historyItems)

        val repairedItems = repairContinueWatchingMetadataIfNeeded(items)
        return applyContinueWatchingDismissals(sanitizeContinueWatchingItems(repairedItems))
            .filter { item ->
                item.isUpNext || item.progress in 1..99 || item.resumePositionSeconds > 0L
            }
            .take(Constants.MAX_CONTINUE_WATCHING)
    }

    private suspend fun preloadStartupContinueWatchingItems(): List<ContinueWatchingItem> {
        // Startup must never wait for network traffic: this uses only saved data.
        val localItems = loadSavedContinueWatchingSnapshot()
        val diskItems = loadContinueWatchingCache()
        val items = if (diskItems.isNotEmpty()) {
            diskItems
        } else {
            val savedItems = try {
                localWatchRepository.loadPersistedContinueWatchingForStartup()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                emptyList()
            }
            if (savedItems.isNotEmpty()) {
                savedItems
            } else {
                localItems
            }
        }

        val cachedItems = ContinueWatchingMerge.merge(items, localItems)
        return applyContinueWatchingDismissals(sanitizeContinueWatchingItems(cachedItems, cacheOnly = true))
            .filter { item ->
                item.progress in 0..99 || item.resumePositionSeconds > 0L
            }
            .take(Constants.MAX_CONTINUE_WATCHING)
    }

    private suspend fun loadSavedContinueWatchingSnapshot(): List<ContinueWatchingItem> {
        return try {
            localWatchRepository.getLocalContinueWatchingSnapshot()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList()
        }
    }

    private suspend fun mergeContinueWatchingResumeData(
        items: List<ContinueWatchingItem>
    ): List<ContinueWatchingItem> {
        if (items.isEmpty()) return emptyList()
        return try {
            val historyEntries = watchHistoryRepository.getContinueWatching()
            if (historyEntries.isEmpty()) return items

            val sortedHistory = historyEntries.sortedByDescending { it.updated_at ?: it.paused_at.orEmpty() }
            val byExactKey = sortedHistory.associateBy { entry ->
                "${entry.media_type}:${entry.show_tmdb_id}:${entry.season ?: -1}:${entry.episode ?: -1}"
            }
            val byShowKey = sortedHistory.associateBy { entry ->
                "${entry.media_type}:${entry.show_tmdb_id}"
            }

            items.map { item ->
                val mediaTypeKey = if (item.mediaType == MediaType.TV) "tv" else "movie"
                val exactKey = "$mediaTypeKey:${item.id}:${item.season ?: -1}:${item.episode ?: -1}"
                val showKey = "$mediaTypeKey:${item.id}"
                // Only use exact episode-matched history, NOT show-level fallback.
                // Show-level fallback caused old episode's position to leak to the next episode.
                val match = byExactKey[exactKey]
                if (match == null) {
                    item
                } else {
                    val storedProgress = (match.progress * 100f).toInt()
                    val derivedProgress = if (storedProgress <= 0 && match.duration_seconds > 0 && match.position_seconds > 0) {
                        ((match.position_seconds.toFloat() / match.duration_seconds.toFloat()) * 100f).toInt()
                    } else {
                        storedProgress
                    }
                    item.copy(
                        progress = derivedProgress.coerceIn(0, 100),
                        resumePositionSeconds = match.position_seconds.coerceAtLeast(0L),
                        durationSeconds = maxOf(item.durationSeconds, match.duration_seconds.coerceAtLeast(0L)),
                        season = item.season ?: match.season,
                        episode = item.episode ?: match.episode,
                        episodeTitle = item.episodeTitle ?: match.episode_title
                    )
                }
            }
        } catch (e: Exception) {
                if (e is CancellationException) throw e

            items
        }
    }

    /** Home resumed (e.g. back from Details): the watched history may have changed meanwhile. */
    fun refreshWatchedBadgesOnResume() {
        refreshWatchedBadges(force = true)
    }

    /**
     * The long-press menu just marked a title: the watched cache already holds it, but the rows are
     * the ones the last pass marked, so no pass would run before the next return to Home.
     * `immediate` runs it now, past the throttle; `force` keeps a pass that freshly published rows
     * restart (the next-episode branch refreshes Continue Watching) on the short delay.
     */
    private fun refreshWatchedBadgesAfterMark() {
        refreshWatchedBadges(immediate = true, force = true)
    }

    private fun refreshWatchedBadges(immediate: Boolean = false, force: Boolean = false) {
        val now = SystemClock.elapsedRealtime()
        if (!immediate && watchedBadgesPassIsRedundant(
                rows = _uiState.value.categories,
                lastMarkedRows = lastWatchedBadgesCategories,
                force = force,
                sinceLastPassMs = now - lastWatchedBadgesRefreshMs,
                throttleMs = WATCHED_BADGES_REFRESH_MS
            )
        ) return

        // A resume pass stays quick even when rows published right after it restart the debounce.
        val quick = force || (watchedBadgesQuickPending && watchedBadgesJob?.isActive == true)
        watchedBadgesJob?.cancel()
        watchedBadgesQuickPending = quick
        val passDelayMs = watchedBadgesPassDelayMs(
            quickRequested = quick,
            hadPass = lastWatchedBadgesRefreshMs != 0L,
            isLowRamDevice = isLowRamDevice
        )
        val profileId = profileManager.getProfileIdSync()
        watchedBadgesJob = viewModelScope.launch {
            if (!immediate) {
                delay(passDelayMs)
            }
            try {
                // The watched cache is the same source Search, Discover and Details mark from.
                withContext(networkDispatcher) {
                    localWatchRepository.initializeWatchedCache()
                }
                if (_uiState.value.categories.isEmpty()) return@launch

                val (watchedMovies, startedShows) = withContext(Dispatchers.Default) {
                    val watchedMovies = localWatchRepository.getWatchedMoviesFromCache()
                    // Index the history once instead of scanning it for every distinct show.
                    val startedShows = startedShowIds(localWatchRepository.getWatchedEpisodesFromCache())
                    watchedMovies to startedShows
                }
                if (profileManager.getProfileIdSync() != profileId) return@launch

                // Install the lookup before notifying the Main-thread rows collector, which
                // must never reapply the previous history to a newly marked state.
                lastWatchedBadgesLookup = watchedMovies to startedShows
                lastWatchedBadgesRefreshMs = SystemClock.elapsedRealtime()
                val marked = _uiState.updateAndGet { it.withWatchedBadges(watchedMovies, startedShows) }
                lastWatchedBadgesCategories = marked.categories
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                AppLogger.e("HomeVM", "refreshWatchedBadges failed: ${e.message}", e)
            }
        }
    }

    fun toggleWatchlist(item: MediaItem) {
        viewModelScope.launch {
            try {
                val isInWatchlist = watchlistRepository.isInWatchlist(item.mediaType, item.id)
                if (isInWatchlist) {
                    watchlistRepository.removeFromWatchlist(item.mediaType, item.id)
                } else {
                    watchlistRepository.addToWatchlist(item.mediaType, item.id, item)
                }
                _uiState.value = _uiState.value.copy(
                    toastMessage = if (isInWatchlist) context.getString(R.string.watchlist_toast_removed) else context.getString(R.string.added_to_watchlist),
                    toastType = ToastType.SUCCESS
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                AppLogger.recordException(
                    throwable = e,
                    context = mapOf(
                        "error_area" to "Watchlist",
                        "watchlist_phase" to "home_toggle",
                        "media_type" to item.mediaType.name.lowercase()
                    )
                )
                _uiState.value = _uiState.value.copy(
                    toastMessage = context.getString(R.string.details_failed_update_watchlist),
                    toastType = ToastType.ERROR
                )
            }
        }
    }

    private suspend fun persistFollowingEpisodePointer(
        item: MediaItem,
        watchedSeason: Int,
        watchedEpisode: Int,
    ) {
        val currentSeasonEpisodes = mediaRepository.getSeasonEpisodes(item.id, watchedSeason)
            .orEmpty()
            .sortedBy { it.episodeNumber }
        if (currentSeasonEpisodes.isEmpty()) return
        val followingEpisode = currentSeasonEpisodes.firstOrNull {
            it.episodeNumber > watchedEpisode
        } ?: mediaRepository.getSeasonEpisodes(item.id, watchedSeason + 1)
            .orEmpty()
            .sortedBy { it.episodeNumber }
            .firstOrNull()
            ?: return

        val hasAired = EpisodeAvailability.hasAired(followingEpisode.airDate)
        localWatchRepository.saveLocalContinueWatching(
            mediaType = MediaType.TV,
            tmdbId = item.id,
            title = item.title,
            posterPath = item.image,
            backdropPath = item.backdrop,
            season = followingEpisode.tmdbSeasonNumber,
            episode = followingEpisode.tmdbEpisodeNumber,
            displaySeason = followingEpisode.seasonNumber,
            displayEpisode = followingEpisode.episodeNumber,
            episodeTitle = followingEpisode.name,
            progress = 0,
            positionSeconds = 0L,
            durationSeconds = 0L,
            year = item.year,
            isUpNext = true,
            episodeAirDate = followingEpisode.airDate.orEmpty(),
            emitUpdate = hasAired,
        )
    }

    fun toggleWatched(item: MediaItem) {
        viewModelScope.launch {
            try {
                if (item.mediaType == MediaType.MOVIE) {
                    if (item.isWatched) {
                        localWatchRepository.markMovieUnwatched(item.id)
                        refreshWatchedBadgesAfterMark()
                        _uiState.value = _uiState.value.copy(
                            toastMessage = context.getString(R.string.details_marked_unwatched),
                            toastType = ToastType.SUCCESS
                        )
                    } else {
                        localWatchRepository.markMovieWatched(item.id)
                        refreshWatchedBadgesAfterMark()
                        watchHistoryRepository.removeFromHistory(item.id, null, null)
                        _uiState.value = _uiState.value.copy(
                            toastMessage = context.getString(R.string.details_marked_watched),
                            toastType = ToastType.SUCCESS
                        )
                    }
                } else {
                    val nextEp = item.nextEpisode
                    if (nextEp != null) {
                        // OPTIMISTIC UI UPDATE: Remove from CW and show toast immediately
                        val updatedCategories = _uiState.value.categories.map { category ->
                            if (category.id == "continue_watching") {
                                category.copy(items = category.items.filter { it.id != item.id })
                            } else {
                                category
                            }
                        }.filter { category ->
                            category.id != "continue_watching" || category.items.isNotEmpty()
                        }

                        _uiState.value = _uiState.value.copy(
                            categories = updatedCategories,
                            toastMessage = context.getString(R.string.details_episode_marked_watched, nextEp.seasonNumber, nextEp.episodeNumber),
                            toastType = ToastType.SUCCESS
                        )

                        // Save after the UI update
                        localWatchRepository.markEpisodeWatched(item.id, nextEp.seasonNumber, nextEp.episodeNumber)
                        refreshWatchedBadgesAfterMark()
                        watchHistoryRepository.removeFromHistory(item.id, nextEp.seasonNumber, nextEp.episodeNumber)

                        try {
                            persistFollowingEpisodePointer(
                                item = item,
                                watchedSeason = nextEp.seasonNumber,
                                watchedEpisode = nextEp.episodeNumber,
                            )
                            lastContinueWatchingUpdateMs = 0L
                            refreshContinueWatchingOnly(force = true)
                                } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
                    } else {
                        _uiState.value = _uiState.value.copy(
                            toastMessage = context.getString(R.string.home_no_episode_info),
                            toastType = ToastType.ERROR
                        )
                    }
                }
                try {
                    launcherContinueWatchingRepository.refreshForCurrentProfile()
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.value = _uiState.value.copy(
                    toastMessage = context.getString(R.string.details_failed_update_watched),
                    toastType = ToastType.ERROR
                )
            }
        }
    }

    fun markWatched(item: MediaItem) {
        viewModelScope.launch {
            try {
                if (item.mediaType == MediaType.MOVIE) {
                    if (!item.isWatched) {
                        localWatchRepository.markMovieWatched(item.id)
                        watchHistoryRepository.removeFromHistory(item.id, null, null)
                        _uiState.value = _uiState.value.copy(
                            toastMessage = context.getString(R.string.details_marked_watched),
                            toastType = ToastType.SUCCESS
                        )
                    } else {
                        _uiState.value = _uiState.value.copy(
                            toastMessage = context.getString(R.string.home_already_watched),
                            toastType = ToastType.INFO
                        )
                    }
                } else {
                    val nextEp = item.nextEpisode
                    if (nextEp != null) {
                        // OPTIMISTIC UI UPDATE: Remove from CW and show toast immediately
                        val updatedCategories = _uiState.value.categories.map { category ->
                            if (category.id == "continue_watching") {
                                category.copy(items = category.items.filter { it.id != item.id })
                            } else {
                                category
                            }
                        }.filter { category ->
                            category.id != "continue_watching" || category.items.isNotEmpty()
                        }

                        _uiState.value = _uiState.value.copy(
                            categories = updatedCategories,
                            toastMessage = context.getString(R.string.details_episode_marked_watched, nextEp.seasonNumber, nextEp.episodeNumber),
                            toastType = ToastType.SUCCESS
                        )

                        // Save after the UI update
                        try {
                            localWatchRepository.markEpisodeWatched(item.id, nextEp.seasonNumber, nextEp.episodeNumber)
                        } catch (e: Exception) {
                            if (e is CancellationException) throw e
                        }
                        try {
                            watchHistoryRepository.removeFromHistory(item.id, nextEp.seasonNumber, nextEp.episodeNumber)
                        } catch (e: Exception) {
                            if (e is CancellationException) throw e
                        }

                        try {
                            persistFollowingEpisodePointer(
                                item = item,
                                watchedSeason = nextEp.seasonNumber,
                                watchedEpisode = nextEp.episodeNumber,
                            )
                            // Reset throttle so refresh actually runs
                            lastContinueWatchingUpdateMs = 0L
                            refreshContinueWatchingOnly(force = true)
                        } catch (e: Exception) {
                            if (e is CancellationException) throw e
                        }
                    } else {
                        _uiState.value = _uiState.value.copy(
                            toastMessage = context.getString(R.string.home_no_episode_info),
                            toastType = ToastType.ERROR
                        )
                    }
                }
                try {
                    launcherContinueWatchingRepository.refreshForCurrentProfile()
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.value = _uiState.value.copy(
                    toastMessage = context.getString(R.string.details_failed_update_watched),
                    toastType = ToastType.ERROR
                )
            }
        }
    }

    fun dismissToast() {
        _uiState.value = _uiState.value.copy(toastMessage = null)
    }

    suspend fun isInWatchlist(item: MediaItem): Boolean {
        return watchlistRepository.isInWatchlist(item.mediaType, item.id)
    }

    fun removeFromContinueWatching(item: MediaItem) {
        viewModelScope.launch {
            try {
                val season = if (item.mediaType == MediaType.TV) item.nextEpisode?.seasonNumber else null
                val episode = if (item.mediaType == MediaType.TV) item.nextEpisode?.episodeNumber else null
                dismissedContinueWatchingAt[continueWatchingKey(item.mediaType, item.id)] = System.currentTimeMillis()

                watchHistoryRepository.removeFromHistory(item.id, season, episode)
                localWatchRepository.removeFromContinueWatchingCache(item.id, null, null, item.mediaType)
                localWatchRepository.dismissContinueWatching(item)

                val updatedCategories = _uiState.value.categories.map { category ->
                    if (category.id == "continue_watching") {
                        category.copy(items = category.items.filter { it.id != item.id })
                    } else {
                        category
                    }
                }.filter { category ->
                    category.id != "continue_watching" || category.items.isNotEmpty()
                }

                _uiState.value = _uiState.value.copy(
                    categories = updatedCategories,
                    toastMessage = context.getString(R.string.home_removed_continue_watching),
                    toastType = ToastType.SUCCESS
                )
                try {
                    launcherContinueWatchingRepository.refreshForCurrentProfile()
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                }
                updatedCategories.firstOrNull { it.id == "continue_watching" }?.let { category ->
                    lastContinueWatchingItems = category.items
                    lastContinueWatchingUpdateMs = SystemClock.elapsedRealtime()
                } ?: run {
                    lastContinueWatchingItems = emptyList()
                    lastContinueWatchingUpdateMs = SystemClock.elapsedRealtime()
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.value = _uiState.value.copy(
                    toastMessage = context.getString(R.string.home_failed_remove_continue_watching),
                    toastType = ToastType.ERROR
                )
            }
        }
    }


    init {
        mediaRepository.contentLanguage = "en"
        viewModelScope.launch {
            _uiState
                .map { it.categories }
                .distinctUntilChanged { old, new -> old === new }
                .collect { rows ->
                    if (rows.isEmpty() || rows === lastWatchedBadgesCategories) return@collect
                    lastWatchedBadgesLookup?.let { (watchedMovies, startedShows) ->
                        val marked = _uiState.updateAndGet { it.withWatchedBadges(watchedMovies, startedShows) }
                        lastWatchedBadgesCategories = marked.categories
                    }
                    refreshWatchedBadges()
                }
        }
        viewModelScope.launch {
            // Refresh local progress when the app returns to the foreground.
            appForegroundSignals.returnedToForeground.collect {
                refreshContinueWatchingOnly(force = true)
            }
        }
        viewModelScope.launch {
            continueWatchingUpdates.updates.collect { update ->
                val activeProfileId = profileManager.getProfileIdSync().ifBlank { "default" }
                if (update.profileId != activeProfileId) return@collect

                val categories = when (update) {
                    is ContinueWatchingUpdate.Upsert -> {
                        val item = update.item.toMediaItem(context)
                        mediaRepository.cacheItem(item)
                        ContinueWatchingRowReducer.upsert(_uiState.value.categories, item)
                    }
                    is ContinueWatchingUpdate.Remove -> ContinueWatchingRowReducer.remove(
                        categories = _uiState.value.categories,
                        mediaType = update.mediaType,
                        tmdbId = update.tmdbId,
                        season = update.season,
                        episode = update.episode
                    )
                }
                val continueWatchingItems = categories
                    .firstOrNull { it.id == "continue_watching" }
                    ?.items
                    .orEmpty()
                lastContinueWatchingItems = continueWatchingItems
                lastContinueWatchingUpdateMs = SystemClock.elapsedRealtime()
                _uiState.value = _uiState.value.copy(categories = categories)
            }
        }
        viewModelScope.launch {
            localWatchRepository.watchedCacheReloaded.collect { refreshWatchedBadges(immediate = true) }
        }
        viewModelScope.launch {
            profileManager.activeProfileId.distinctUntilChanged().collect { profileId ->
                if (activeRuntimeProfileId != null && activeRuntimeProfileId != profileId) {
                    cwFetchJob?.cancel()
                    refreshContinueWatchingJob?.cancel()
                    continueWatchingRequests.reset()
                    dismissedContinueWatchingAt.clear()
                    lastContinueWatchingItems = emptyList()
                    lastContinueWatchingUpdateMs = 0L
                    _uiState.value = HomeUiState()
                }
                activeRuntimeProfileId = profileId
                launchContinueWatchingFetch()
            }
        }
        viewModelScope.launch {
            observeHomeProfilePreferences(profileManager.activeProfileId, context.settingsDataStore.data)
                .collect { preferences ->
                    _uiState.update { it.copy(clockFormat = preferences.clockFormat, smoothScrolling = preferences.smoothScrolling) }
                }
        }
    }


}

private object HomeVMRegexes {
    val ALPHANUMERIC_REGEX = Regex("[^A-Za-z0-9_.-]")
}
