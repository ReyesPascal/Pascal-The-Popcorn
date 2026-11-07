package com.arflix.tv.ui.screens.details

import com.arflix.tv.data.merge.ShowMergeTable
import com.arflix.tv.data.merge.getMergedTvDetails
import com.arflix.tv.data.merge.getMergedTvSeason
import android.content.Context
import android.util.Log
import com.arflix.tv.R
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arflix.tv.data.model.Addon
import com.arflix.tv.data.model.AddonType
import com.arflix.tv.data.model.AnimeStructuringStyle
import com.arflix.tv.util.animeEpisodesForDisplay
import com.arflix.tv.data.model.Episode
import com.arflix.tv.data.model.EpisodeIdentity
import com.arflix.tv.data.model.MediaItem
import com.arflix.tv.data.model.MediaType
import com.arflix.tv.data.model.RecentPlayedSource
import com.arflix.tv.data.model.StreamSource
import com.arflix.tv.data.model.Subtitle
import com.arflix.tv.data.api.TmdbApi
import com.arflix.tv.data.repository.ContinueWatchingMerge
import com.arflix.tv.data.repository.LauncherContinueWatchingRepository
import com.arflix.tv.data.repository.MediaRepository
import com.arflix.tv.data.repository.ProfileManager
import com.arflix.tv.data.repository.RecentPlayedSourceRepository
import com.arflix.tv.data.model.StreamIntegrationType
import com.arflix.tv.data.repository.StreamIntegrationRepository
import com.arflix.tv.data.repository.StreamRepository
import com.arflix.tv.data.repository.providerScopedStreamIdentity
import com.arflix.tv.data.repository.LocalWatchRepository
import com.arflix.tv.network.NetworkMonitor
import com.arflix.tv.network.NetworkType
import com.arflix.tv.network.TmdbPriorityDispatcher
import com.arflix.tv.network.TmdbPriorityDispatcher.Priority
import com.arflix.tv.util.DeviceType
import com.arflix.tv.util.detectDeviceType
import com.arflix.tv.data.repository.WatchHistoryRepository
import com.arflix.tv.data.repository.WatchlistRepository
import com.arflix.tv.util.AppLogger
import com.arflix.tv.util.PtpDiag
import com.arflix.tv.network.diagSafeText
import com.arflix.tv.util.BestSourcePicker
import com.arflix.tv.util.AnimeMapper
import com.arflix.tv.util.AnimeSeasonStructure
import com.arflix.tv.util.Constants
import com.arflix.tv.util.EpisodeAvailability
import com.arflix.tv.util.settingsDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

data class DetailsUiState(
    val isLoading: Boolean = true,
    val item: MediaItem? = null,
    val imdbId: String? = null,  // Real IMDB ID for stream resolution
    val tvdbId: Int? = null,     // TVDB ID for Kitsu anime mapping
    val logoUrl: String? = null,
    /** Personal build page timer: the logo lookup has finished (found or not). */
    val logoLoadFinished: Boolean = false,
    val episodes: List<Episode> = emptyList(),
    val episodePlaybackProgress: Map<EpisodeIdentity, EpisodePlaybackProgress> = emptyMap(),
    val totalSeasons: Int = 1,
    val currentSeason: Int = 1,
    val isSeasonLoading: Boolean = false,
    val error: String? = null,
    // Streams
    val streams: List<StreamSource> = emptyList(),
    val streamsEpisodeIdentity: EpisodeIdentity? = null,
    // The source the loaded streams' title/episode was last really watched from.
    val recentSource: RecentPlayedSource? = null,
    val subtitles: List<Subtitle> = emptyList(),
    val isLoadingStreams: Boolean = false,
    val streamSearchStartTime: Long = 0L,
    val completedAddons: Int = 0,
    val totalAddons: Int = 0,
    val hasStreamingAddons: Boolean = true,
    val addonOrderedIds: List<String> = emptyList(),
    val isInWatchlist: Boolean = false,
    // Toast
    val toastMessage: String? = null,
    val toastType: ToastType = ToastType.INFO,
    // Genre names
    val genres: List<String> = emptyList(),
    val language: String? = null,
    // Show status
    val showStatus: String? = null,
    // Initial positions for Continue Watching navigation
    val initialEpisodeIndex: Int = 0,
    val initialSeasonIndex: Int = 0,
    // Season progress: Map<seasonNumber, Pair<watchedCount, totalCount>>
    val seasonProgress: Map<Int, Pair<Int, Int>> = emptyMap(),
    val playSeason: Int? = null,
    val playEpisode: Int? = null,
    val playTmdbSeason: Int? = null,
    val playTmdbEpisode: Int? = null,
    val playLabel: String? = null,
    val playPositionMs: Long? = null,
    val autoPlaySingleSource: Boolean = true,
    val autoPlayMinQuality: String = "Any",
    val animeStructuringStyle: AnimeStructuringStyle = AnimeStructuringStyle.BROADCAST,
    val hasAlternateAnimeStructure: Boolean = false,
)


private data class PlayTarget(
    val season: Int? = null,
    val episode: Int? = null,
    val label: String,
    val positionMs: Long? = null
)

private data class SeasonProgressResult(
    val progress: Map<Int, Pair<Int, Int>>,
    val hasWatched: Boolean,
    val nextUnwatched: Pair<Int, Int>?,
    val nextUnaired: Pair<Int, Int>? = null,
    val nextUnairedAirDate: String = "",
    val isComplete: Boolean = true,
)

private data class ResumeInfo(
    val season: Int? = null,
    val episode: Int? = null,
    val label: String,
    val positionMs: Long
)

// TMDB Genre mappings
private val movieGenres = mapOf(
    28 to "Action", 12 to "Adventure", 16 to "Animation", 35 to "Comedy",
    80 to "Crime", 99 to "Documentary", 18 to "Drama", 10751 to "Family",
    14 to "Fantasy", 36 to "History", 27 to "Horror", 10402 to "Music",
    9648 to "Mystery", 10749 to "Romance", 878 to "Sci-Fi", 10770 to "TV Movie",
    53 to "Thriller", 10752 to "War", 37 to "Western"
)

private val tvGenres = mapOf(
    10759 to "Action & Adventure", 16 to "Animation", 35 to "Comedy",
    80 to "Crime", 99 to "Documentary", 18 to "Drama", 10751 to "Family",
    10762 to "Kids", 9648 to "Mystery", 10763 to "News", 10764 to "Reality",
    10765 to "Sci-Fi & Fantasy", 10766 to "Soap", 10767 to "Talk",
    10768 to "War & Politics", 37 to "Western"
)

private val languages = mapOf(
    "en" to "English", "es" to "Spanish", "fr" to "French", "de" to "German",
    "it" to "Italian", "pt" to "Portuguese", "ja" to "Japanese", "ko" to "Korean",
    "zh" to "Chinese", "hi" to "Hindi", "ru" to "Russian", "ar" to "Arabic",
    "nl" to "Dutch", "sv" to "Swedish", "pl" to "Polish", "tr" to "Turkish",
    "th" to "Thai", "vi" to "Vietnamese", "id" to "Indonesian", "tl" to "Tagalog"
)


enum class ToastType {
    SUCCESS, ERROR, INFO
}

private fun isSupplementalStream(stream: StreamSource): Boolean =
    false

private fun Addon.isVodStreamingAddon(): Boolean =
    isEnabled &&
        type != AddonType.SUBTITLE

@HiltViewModel
class DetailsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mediaRepository: MediaRepository,
    private val profileManager: ProfileManager,
    private val localWatchRepository: LocalWatchRepository,
    private val streamRepository: StreamRepository,
    private val networkMonitor: NetworkMonitor,
    private val tmdbPriorityDispatcher: TmdbPriorityDispatcher,
    private val animeMapper: AnimeMapper,
    private val tmdbApi: TmdbApi,
    private val watchHistoryRepository: WatchHistoryRepository,
    private val watchlistRepository: WatchlistRepository,
    private val launcherContinueWatchingRepository: LauncherContinueWatchingRepository,
    private val streamIntegrationRepository: StreamIntegrationRepository,
    private val recentPlayedSourceRepository: RecentPlayedSourceRepository,
 ) : ViewModel() {

    /** The id of the one built-in profile. */
    fun profileId(): String = profileManager.getProfileIdSync()

    companion object {
        private const val TAG = "DetailsViewModel"
        // Issue 2: dwell before proactive scraping. TV users D-pad through Similar
        // chains quickly; mobile users browse on metered connections. The dwell lets
        // fast navigation outrun the scrape. Play/Sources focus can fast-path via
        // requestStreamPrefetchNow() (wired fully once DetailsActionSection exists).
        private const val STREAM_PREFETCH_DWELL_TV_MS = 1000L
        private const val STREAM_PREFETCH_DWELL_MOBILE_MS = 600L
    }

    private val _uiState = MutableStateFlow(DetailsUiState())
    val uiState: StateFlow<DetailsUiState> = _uiState.asStateFlow()

    private var currentMediaType: MediaType = MediaType.MOVIE
    private var currentMediaId: Int = 0
    private var animeSeasonStructure: AnimeSeasonStructure? = null
    private var availableAnimeStructure: AnimeSeasonStructure? = null
    // The episode the user last started playing from this screen, kept so that returning from the
    // player (which recreates the details screen) points the Continue button at that episode even
    // when the play was too brief to record a resume point. Scoped to a media id.
    private var lastPlayedMediaId: Int = 0
    private var lastPlayedIdentity: EpisodeIdentity? = null

    fun recordPlayedEpisode(mediaId: Int, identity: EpisodeIdentity?) {
        lastPlayedMediaId = mediaId
        lastPlayedIdentity = identity
    }
    private var loadStreamsJob: kotlinx.coroutines.Job? = null
    private var loadStreamsRequestId: Long = 0L
    private var focusedStreamPrewarmJob: kotlinx.coroutines.Job? = null
    private var streamListPrewarmJob: kotlinx.coroutines.Job? = null
    private var lastStreamListPrewarmKey: String = ""
    // Guards overlapping season loads: only the most recently requested season may apply its result.
    private var seasonLoadJob: kotlinx.coroutines.Job? = null
    private var seasonPrefetchJob: kotlinx.coroutines.Job? = null
    private var seasonLoadRequestedSeason: Int = -1
    @Volatile private var initialLoadComplete = false

    /** Personal build "[PageTiming]" timer: when the current loadDetails() started (System.nanoTime). */
    private var pageTimingStartNanos: Long = 0L
    /** Milliseconds since the current loadDetails() started, or -1 before any load. */
    fun pageTimingElapsedMs(): Long =
        if (pageTimingStartNanos == 0L) -1L else (System.nanoTime() - pageTimingStartNanos) / 1_000_000L
    /** Set by the screen once the opened->drawn line was printed for this load. */
    var pageTimingReported: Boolean = false
    // Watchlist taps flip the bookmark at once; the local writes then run one at
    // a time so a quick double tap cannot land out of order.
    private val watchlistWriteMutex = Mutex()
    // Writes outlive page loads. Keep their saved state and latest intent attached to the title,
    // not to whichever page happens to be visible when a slow remote write completes.
    private class WatchlistSaveState {
        var saved: Boolean? = null
        var wanted: Boolean? = null
        var version = 0L
        var pendingWrites = 0
    }
    private val watchlistSaveStates = mutableMapOf<Pair<MediaType, Int>, WatchlistSaveState>()

    private enum class WatchlistWrite { SAVED, UNCHANGED, FAILED }

    init {
        if (com.arflix.tv.BuildConfig.PERSONAL_BUILD) {
            // Personal build: follow "Show all sources" live (Settings may change it while this screen
            // is in the back stack), and have the TV scan ready before Play needs it.
            viewModelScope.launch {
                com.arflix.tv.util.PersonalPrefs.showAllSourcesFlow(context).collect { showAll ->
                    _uiState.value = _uiState.value.copy(autoPlaySingleSource = !showAll)
                }
            }
            viewModelScope.launch {
                try {
                    com.arflix.tv.util.TvCapabilities.ensureScanned(context)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "TV capability scan failed", e)
                }
            }
        }
    }

    private fun peekAnimeDisplaySeason(
        tmdbId: Int,
        displaySeason: Int,
        structure: AnimeSeasonStructure
    ): List<Episode>? {
        val identities = structure.seasons[displaySeason] ?: return null
        val canonicalSeasonsNeeded = identities.map { it.tmdbSeason }.distinct()
        val cachedCanonicalBySeason = canonicalSeasonsNeeded.associateWith { season ->
            mediaRepository.peekCachedSeasonEpisodes(tmdbId, season) ?: return null
        }
        return identities.mapNotNull { identity ->
            val episodesForSeason = cachedCanonicalBySeason[identity.tmdbSeason] ?: return@mapNotNull null
            episodesForSeason.find { it.episodeNumber == identity.tmdbEpisode }?.copy(
                seasonNumber = identity.displaySeason,
                episodeNumber = identity.displayEpisode,
                identity = identity
            )
        }
    }
    private fun autoPlaySingleSourceKey() = profileManager.profileBooleanKey("auto_play_single_source")
    private fun animeStructuringStyleKey() = profileManager.profileStringKey(AnimeStructuringStyle.PREFERENCE_KEY)

    private fun isBlankRating(value: String): Boolean {
        return value.isBlank() || value == "0.0" || value == "0"
    }



    private fun mergeItem(primary: MediaItem, fallback: MediaItem?): MediaItem {
        if (fallback == null) return primary
        return primary.copy(
            title = primary.title.ifBlank { fallback.title },
            subtitle = primary.subtitle.ifBlank { fallback.subtitle },
            overview = primary.overview.ifBlank { fallback.overview },
            year = primary.year.ifBlank { fallback.year },
            releaseDate = primary.releaseDate ?: fallback.releaseDate,
            rating = primary.rating.ifBlank { fallback.rating },
            contentRating = primary.contentRating ?: fallback.contentRating,
            duration = primary.duration.ifBlank { fallback.duration },
            imdbRating = if (isBlankRating(primary.imdbRating)) fallback.imdbRating else primary.imdbRating,
            tmdbRating = if (isBlankRating(primary.tmdbRating)) fallback.tmdbRating else primary.tmdbRating,
            image = primary.image.ifBlank { fallback.image },
            backdrop = primary.backdrop ?: fallback.backdrop,
            primaryNetworkLogo = primary.primaryNetworkLogo ?: fallback.primaryNetworkLogo,
            genreIds = if (primary.genreIds.isEmpty()) fallback.genreIds else primary.genreIds,
            originalLanguage = primary.originalLanguage ?: fallback.originalLanguage,
            originalTitle = primary.originalTitle ?: fallback.originalTitle,
            isOngoing = primary.isOngoing || fallback.isOngoing,
            totalEpisodes = primary.totalEpisodes ?: fallback.totalEpisodes,
            watchedEpisodes = primary.watchedEpisodes ?: fallback.watchedEpisodes,
            budget = primary.budget ?: fallback.budget,
            revenue = primary.revenue ?: fallback.revenue,
            status = primary.status ?: fallback.status
        )
    }

    fun loadDetails(mediaType: MediaType, mediaId: Int, initialSeason: Int? = null, initialEpisode: Int? = null) {
        if (mediaType == MediaType.TV && ShowMergeTable.isSecondary(mediaId)) {
            loadDetails(mediaType, ShowMergeTable.canonicalId(mediaId),
                initialSeason?.let { ShowMergeTable.imdbEpisode(mediaId, it, initialEpisode ?: 1)?.season }, initialEpisode)
            return
        }
        currentMediaType = mediaType
        currentMediaId = mediaId
        pageTimingStartNanos = System.nanoTime()
        pageTimingReported = false
        animeSeasonStructure = null
        availableAnimeStructure = null
        initialLoadComplete = false
        streamListPrewarmJob?.cancel()
        focusedStreamPrewarmJob?.cancel()
        seasonLoadJob?.cancel()
        seasonPrefetchJob?.cancel()
        prefetchDwellJob?.cancel()
        prefetchDwellJob = null
        pendingPrefetchImdbId = null
        sharedPrefetch?.release()
        sharedPrefetch = null
        sharedPrefetchTarget = null
        prefetchJob?.cancel()
        prefetchJob = null
        seasonLoadRequestedSeason = -1
        lastStreamListPrewarmKey = ""

        viewModelScope.launch {
            try {
                val prefs = context.settingsDataStore.data.first()
                // Personal build: Play starts the best source for this TV unless "Show all sources" is on.
                val autoPlaySingleSource = if (com.arflix.tv.BuildConfig.PERSONAL_BUILD) {
                    !(prefs[com.arflix.tv.util.PersonalPrefs.SHOW_ALL_SOURCES] ?: false)
                } else {
                    prefs[autoPlaySingleSourceKey()] ?: true
                }
                val autoPlayMinQuality = "Any"
                val animeStructuringStyle = AnimeStructuringStyle.fromId(prefs[animeStructuringStyleKey()])

                val previousState = _uiState.value
                val previousMatches = previousState.item?.id == mediaId &&
                    previousState.item?.mediaType == mediaType
                // When re-entering the same show (e.g. the details screen was recreated behind the
                // player), the ViewModel survives in the back stack but the nav-arg episode target is
                // stale (frozen at the original deeplink). Prefer the episode the user last played
                // (recorded even for brief plays) so the Continue button follows it; fall back to
                // re-deriving from the viewed season. The season rail keeps whatever season the user
                // was browsing (previousState.currentSeason).
                val reentry = previousMatches && previousState.currentSeason > 0
                val playedIdentity = lastPlayedIdentity?.takeIf { lastPlayedMediaId == mediaId }
                val playedSeason = playedIdentity?.tmdbSeason
                val playedEpisode = playedIdentity?.tmdbEpisode
                val initialSeason = when {
                    reentry -> playedSeason
                    else -> initialSeason
                }
                val initialEpisode = when {
                    reentry -> playedEpisode
                    else -> initialEpisode
                }
                val seasonToLoad = when {
                    reentry -> previousState.currentSeason
                    else -> initialSeason ?: 1
                }
                val hasExplicitEpisodeTarget = mediaType == MediaType.TV && initialSeason != null && initialEpisode != null
                val previousItem = _uiState.value.item?.takeIf {
                    it.id == mediaId && it.mediaType == mediaType
                }
                val cachedFullItem = mediaRepository.getCachedFullItem(mediaType, mediaId)
                val cachedItem = cachedFullItem
                    ?: mediaRepository.getCachedItem(mediaType, mediaId)
                    ?: mediaRepository.getCachedItemFromDisk(mediaType, mediaId)
                val initialItem = cachedItem ?: previousItem
                val cachedLogoUrl = mediaRepository.peekCachedLogoUrl(mediaType, mediaId)
                    ?: previousState.logoUrl?.takeIf { previousMatches }
                val cachedEpisodes = if (mediaType == MediaType.TV) {
                    mediaRepository.peekCachedSeasonEpisodes(mediaId, seasonToLoad)
                        ?: previousState.episodes.takeIf { previousMatches && previousState.currentSeason == seasonToLoad }
                } else null
                val cachedTotalSeasons = if (mediaType == MediaType.TV) {
                    cachedFullItem?.totalEpisodes?.coerceAtLeast(1)
                        ?: previousState.totalSeasons.takeIf { previousMatches && !previousState.isLoading }
                        ?: 1
                } else {
                    1
                }

                val watchlistSaveState = watchlistSaveStates.getOrPut(mediaType to mediaId) { WatchlistSaveState() }
                val watchlistVersionAtLoad = watchlistSaveState.version
                val watchlistPendingAtLoad = watchlistSaveState.pendingWrites > 0
                if (!watchlistPendingAtLoad) watchlistSaveState.saved = null

                _uiState.value = DetailsUiState(
                    isLoading = initialItem == null,
                    item = initialItem,
                    isInWatchlist = watchlistSaveState.wanted ?: false,
                    logoUrl = cachedLogoUrl,
                    logoLoadFinished = cachedLogoUrl != null,
                    episodes = cachedEpisodes ?: emptyList(),
                    currentSeason = seasonToLoad,
                    totalSeasons = cachedTotalSeasons,
                    playSeason = initialSeason,
                    playEpisode = initialEpisode,
                    playTmdbSeason = initialSeason,
                    playTmdbEpisode = initialEpisode,
                    playLabel = if (mediaType == MediaType.TV && initialSeason != null && initialEpisode != null) {
                        context.getString(R.string.continue_season_episode, initialSeason, initialEpisode)
                    } else {
                        null
                    },
                    autoPlaySingleSource = autoPlaySingleSource,
                    autoPlayMinQuality = autoPlayMinQuality
                )

                fun logDetailsLoadFailure(label: String, throwable: Throwable) {
                    if (throwable is CancellationException) throw throwable
                    Log.w(TAG, "Failed to load details $label", throwable)
                }

                suspend fun <T> loadDetailsPart(label: String, block: suspend () -> T): T? {
                    return try {
                        block()
                    } catch (e: Exception) {
                        if (e is kotlinx.coroutines.CancellationException) throw e
                        logDetailsLoadFailure(label, e)
                        null
                    }
                }

                // Issue 1: primary TMDB metadata funnels through the shared
                // IMMEDIATE budget so Home background decoration yields to it.
                val itemDeferred = async {
                    tmdbPriorityDispatcher.withPermit(Priority.IMMEDIATE) {
                        loadDetailsPart("item") {
                            if (mediaType == MediaType.TV) {
                                mediaRepository.getTvDetails(mediaId)
                            } else {
                                mediaRepository.getMovieDetails(mediaId)
                            }
                        }
                    }
                }
                val watchlistDeferred = async {
                    // Local membership only: a cached lookup or one DataStore snapshot.
                    loadDetailsPart("watchlist") {
                        watchlistRepository.isInWatchlist(mediaType, mediaId)
                    } ?: false
                }
                // Fetch real IMDB ID and TVDB ID from TMDB external_ids endpoint
                val externalIdsDeferred = async {
                    tmdbPriorityDispatcher.withPermit(Priority.IMMEDIATE) {
                        resolveExternalIds(mediaType, mediaId)
                    }
                }
                val resumeDeferred = async { fetchResumeInfo(mediaId, mediaType, initialSeason, initialEpisode) }
                // Fetch logo URL concurrently with details to avoid ~1s delay
                val logoDeferred = async {
                    tmdbPriorityDispatcher.withPermit(Priority.IMMEDIATE) {
                        mediaRepository.getLogoUrl(mediaType, mediaId)
                    }
                }

                // For TV shows, also load episodes
                val episodesDeferred = if (mediaType == MediaType.TV) {
                    async {
                        tmdbPriorityDispatcher.withPermit(Priority.IMMEDIATE) {
                            loadDetailsPart("season $seasonToLoad episodes") {
                                mediaRepository.getSeasonEpisodes(mediaId, seasonToLoad)
                            } ?: emptyList<Episode>()
                        }
                    }
                } else null

                // For TV shows, fetch season progress (watched/total per season).
                // IMPORTANT: Initialize watched cache FIRST so fetchSeasonProgress()
                // can read from the in-memory cache rather than falling back to a
                // backend query that may return stale or empty data. The async below
                // starts immediately, but initializeWatchedCache() runs synchronously
                // before it, ensuring the cache is populated before fetchSeasonProgress
                // checks getWatchedEpisodesFromCache().
                if (mediaType == MediaType.TV) {
                    runCatching { localWatchRepository.initializeWatchedCache() }
                }
                val seasonProgressDeferred = if (mediaType == MediaType.TV) {
                    async { fetchSeasonProgress(mediaId) }
                } else null

                val requestMediaId = mediaId
                val requestMediaType = mediaType
                fun isCurrentRequest(): Boolean {
                    return currentMediaId == requestMediaId && currentMediaType == requestMediaType
                }
                fun updateState(block: (DetailsUiState) -> DetailsUiState) {
                    if (!isCurrentRequest()) return
                    _uiState.value = block(_uiState.value)
                }

                val isAnime = mediaType == MediaType.TV && (
                    animeMapper.isAnimeContent(mediaId, initialItem?.genreIds.orEmpty(), initialItem?.originalLanguage)
                )
                val animeStructureDeferred = if (isAnime) {
                    async(Dispatchers.IO) {
                        animeMapper.resolveAnimeSeasonStructure(mediaId)
                    }
                } else null

                val loadedItem = runCatching { itemDeferred.await() }.getOrNull()
                val item = loadedItem ?: initialItem
                if (item == null) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = context.getString(R.string.details_error_load_failed)
                    )
                    return@launch
                }
                val mergedItem = mergeItem(item, initialItem)
                val hasTrustedTvDetails = mediaType != MediaType.TV || loadedItem != null || cachedFullItem != null

                val isActuallyAnime = mediaType == MediaType.TV && (
                    isAnime || animeMapper.isAnimeContent(mediaId, mergedItem.genreIds, mergedItem.originalLanguage)
                )
                val structure = if (isActuallyAnime) {
                    animeStructureDeferred?.await() ?: animeMapper.resolveAnimeSeasonStructure(mediaId)
                } else null

                availableAnimeStructure = structure
                // Alternate broadcast seasons omit TMDB specials. An explicit season-zero
                // destination must keep canonical episodes rather than becoming season one.
                val activeStructure = if (animeStructuringStyle == AnimeStructuringStyle.BROADCAST && seasonToLoad != 0) structure else null
                animeSeasonStructure = activeStructure

                // Resolve TV show seasonal episodes directly without intermediate layout flash
                val resolvedTotalSeasons: Int
                val resolvedCurrentSeason: Int
                val resolvedEpisodes: List<Episode>
                val displayTarget: EpisodeIdentity?

                if (activeStructure != null) {
                    val canonicalTargetSeason = seasonToLoad
                    val canonicalTargetEpisode = initialEpisode ?: 1
                    val target = activeStructure.identityForTmdb(canonicalTargetSeason, canonicalTargetEpisode)
                    val displaySeason = target?.displaySeason ?: seasonToLoad.coerceIn(1, activeStructure.seasonCount)
                    val episodes = loadAnimeDisplaySeason(mediaId, displaySeason, activeStructure)

                    resolvedTotalSeasons = activeStructure.seasonCount
                    resolvedCurrentSeason = displaySeason
                    resolvedEpisodes = episodes
                    displayTarget = target

                    // Pre-fetch all underlying TMDB seasons in background for 0ms season transitions
                    // (personal build: not done; a season loads when it is picked)
                    if (!com.arflix.tv.BuildConfig.PERSONAL_BUILD) launch(Dispatchers.IO) {
                        val neededTmdbSeasons = activeStructure.seasons.values.flatten().map { it.tmdbSeason }.distinct()
                        for (s in neededTmdbSeasons) {
                            if (isCurrentRequest() && mediaRepository.peekCachedSeasonEpisodes(mediaId, s) == null) {
                                runCatching { mediaRepository.getSeasonEpisodes(mediaId, s) }
                            }
                        }
                    }
                } else if (mediaType == MediaType.TV) {
                    val tmdbSeasons = if (hasTrustedTvDetails) {
                        mergedItem.totalEpisodes?.coerceAtLeast(1) ?: 1
                    } else {
                        1
                    }
                    val canonicalEpisodes = runCatching { episodesDeferred?.await() }.getOrNull() ?: emptyList()
                    val displayedEpisodes = normalizeAnimeEpisodesForDisplay(
                        tmdbId = mediaId,
                        displaySeason = seasonToLoad,
                        item = mergedItem,
                        canonicalEpisodes = canonicalEpisodes,
                        style = animeStructuringStyle,
                    )

                    resolvedTotalSeasons = tmdbSeasons
                    resolvedCurrentSeason = seasonToLoad
                    resolvedEpisodes = displayedEpisodes
                    displayTarget = null

                    // Pre-fetch remaining seasons in background
                    // (personal build: not done; only the adjacent seasons are warmed below)
                    if (tmdbSeasons > 1 && !com.arflix.tv.BuildConfig.PERSONAL_BUILD) {
                        launch(Dispatchers.IO) {
                            for (s in 1..tmdbSeasons) {
                                if (s != seasonToLoad && isCurrentRequest()) {
                                    if (mediaRepository.peekCachedSeasonEpisodes(mediaId, s) == null) {
                                        runCatching { mediaRepository.getSeasonEpisodes(mediaId, s) }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    resolvedTotalSeasons = 1
                    resolvedCurrentSeason = 1
                    resolvedEpisodes = emptyList()
                    displayTarget = null
                }

                // Map genre IDs to names
                val genreMap = if (mediaType == MediaType.TV) tvGenres else movieGenres
                val genreNames = mergedItem.genreIds.mapNotNull { genreMap[it] }.take(4)

                // Get language name
                val languageName = mergedItem.originalLanguage?.let { languages[it] ?: it.uppercase() }

                // Get show status
                val showStatus = if (mediaType == MediaType.TV) mergedItem.status else null

                // Initialize watched cache lazily to avoid wiping optimistic local state.
                if (runCatching { localWatchRepository.getWatchedEpisodesFromCache().isEmpty() }.getOrDefault(true)) {
                    runCatching { localWatchRepository.initializeWatchedCache() }
                }

                // Check if item is watched (movies: marked watched; TV: started)
                val isWatched = if (mediaType == MediaType.MOVIE) {
                    localWatchRepository.isMovieWatched(mediaId)
                } else {
                    // For TV shows, check if any episode is watched
                    localWatchRepository.hasWatchedEpisodes(mediaId)
                }
                val itemWithWatchedStatus = mergedItem.copy(isWatched = isWatched)

                val initialSeasonIndex = (resolvedCurrentSeason - 1).coerceAtLeast(0)

                val baseState = _uiState.value.copy(
                    isLoading = false,
                    item = itemWithWatchedStatus,
                    animeStructuringStyle = animeStructuringStyle,
                    hasAlternateAnimeStructure = (availableAnimeStructure != null),
                    totalSeasons = resolvedTotalSeasons,
                    currentSeason = resolvedCurrentSeason,
                    episodes = resolvedEpisodes,
                    initialSeasonIndex = initialSeasonIndex,
                    genres = genreNames,
                    language = languageName,
                    showStatus = showStatus,
                    playSeason = displayTarget?.displaySeason ?: _uiState.value.playSeason,
                    playEpisode = displayTarget?.displayEpisode ?: _uiState.value.playEpisode,
                    playTmdbSeason = seasonToLoad,
                    playTmdbEpisode = initialEpisode ?: 1,
                    playLabel = displayTarget?.let {
                        if (hasExplicitEpisodeTarget || resolvedEpisodes.any { ep -> ep.isWatched }) {
                            context.getString(
                                R.string.continue_season_episode,
                                it.displaySeason,
                                it.displayEpisode
                            )
                        } else {
                            context.getString(R.string.play_start_s1e1)
                        }
                    } ?: _uiState.value.playLabel
                )
                _uiState.value = baseState


                if (mediaType == MediaType.TV) {
                    launch {
                        val progress = runCatching { fetchSeasonProgress(mediaId) }.getOrNull()
                        if (isCurrentRequest() && progress != null) {
                            updateState { state -> state.copy(seasonProgress = progress.progress) }
                        }
                    }
                }

                launch {
                    val externalIds = runCatching { externalIdsDeferred.await() }.getOrNull()
                    val imdbId = externalIds?.imdbId
                    val tvdbId = externalIds?.tvdbId
                    if (!imdbId.isNullOrBlank()) {
                        mediaRepository.cacheImdbId(mediaType, mediaId, imdbId)
                        updateState { state -> state.copy(imdbId = imdbId, tvdbId = tvdbId) }
                        // Prefetch streams in background as soon as IMDB ID is available.
                        // When the user presses Play or opens Sources, results are already
                        // cached in StreamRepository, so loading appears near-instant.
                        val prefetchSeason = if (mediaType == MediaType.TV) (initialSeason ?: 1) else null
                        val prefetchEpisode = if (mediaType == MediaType.TV) (initialEpisode ?: 1) else null
                        // Issue 2: dwell-gated (TV 1000ms / mobile 600ms + WiFi-only on
                        // mobile) so fast Similar-chain browsing does not fan out to
                        // every addon. Back-nav cancels via cancelStreamPrefetch().
                        if (isCurrentRequest()) {
                            scheduleStreamPrefetch(imdbId, prefetchSeason, prefetchEpisode)
                        }


                    } else if (tvdbId != null) {
                        updateState { state -> state.copy(tvdbId = tvdbId) }
                    }
                }

                // Logo URL was fetched concurrently with details via logoDeferred
                launch {
                    val logoUrl = runCatching { logoDeferred.await() }.getOrNull()
                    if (logoUrl != null && logoUrl != _uiState.value.logoUrl) {
                        updateState { state -> state.copy(logoUrl = logoUrl, logoLoadFinished = true) }
                    } else {
                        updateState { state -> state.copy(logoLoadFinished = true) }
                    }
                }


                launch {
                    val episodes = runCatching { episodesDeferred?.await() }.getOrNull()
                    if (!episodes.isNullOrEmpty()) {
                        val resumeTarget = runCatching { resumeDeferred.await() }.getOrNull()
                        val fallbackTargetSeason = initialSeason ?: resumeTarget?.season
                        val fallbackTargetEpisode = initialEpisode ?: resumeTarget?.episode

                        // Decorate episodes with watched status from cache
                        val watchedKeys = runCatching {
                            localWatchRepository.getWatchedEpisodesForShow(mediaId)
                        }.getOrDefault(emptySet())
                        val decoratedEpisodes = if (watchedKeys.isNotEmpty()) {
                            episodes.map { ep ->
                                val key = "show_tmdb:$mediaId:${ep.seasonNumber}:${ep.episodeNumber}"
                                if (watchedKeys.contains(key)) ep.copy(isWatched = true) else ep
                            }
                        } else if (fallbackTargetSeason != null) {
                            episodes.map { ep ->
                                when {
                                    ep.seasonNumber < fallbackTargetSeason -> ep.copy(isWatched = true)
                                    ep.seasonNumber == fallbackTargetSeason -> ep.copy(
                                        isWatched = ep.episodeNumber < (fallbackTargetEpisode ?: 1)
                                    )
                                    else -> ep
                                }
                            }
                        } else {
                            val seasonNum = episodes.firstOrNull()?.seasonNumber
                            val progress = seasonNum?.let { _uiState.value.seasonProgress[it] }
                            val isFullyWatchedSeason = progress != null && progress.second > 0 && progress.first >= progress.second
                            if (isFullyWatchedSeason) {
                                episodes.map { it.copy(isWatched = true) }
                            } else {
                                episodes
                            }
                        }

                        val targetEpisodeForRow = if (initialSeason == seasonToLoad) initialEpisode else null
                        val nextUnwatchedEpisode = decoratedEpisodes.firstOrNull { !it.isWatched }
                        // Focus the explicit target episode if the caller passed one (deeplink /
                        // Continue Watching tile), otherwise focus the first unwatched episode so
                        // users don't have to scroll past every episode they've already seen.
                        // When every episode in the current season is watched we fall back to
                        // the first episode (index 0) — from there the user can jump to the next
                        // season via the season selector. See issue #117.
                        val initialEpisodeIndex = when {
                            targetEpisodeForRow != null ->
                                decoratedEpisodes.indexOfFirst { it.episodeNumber == targetEpisodeForRow }.coerceAtLeast(0)
                            nextUnwatchedEpisode != null ->
                                decoratedEpisodes.indexOf(nextUnwatchedEpisode).coerceAtLeast(0)
                            else -> 0
                        }
                        val hasWatchedEpisodes = decoratedEpisodes.any { it.isWatched }
                        val playbackProgress = fetchEpisodePlaybackProgress(mediaId, decoratedEpisodes)
                        updateState { state ->
                            val shouldUseEpisodeTarget = !hasExplicitEpisodeTarget &&
                                (state.playLabel.isNullOrBlank() || state.playLabel == context.getString(R.string.play_start_s1e1))
                            state.copy(
                                episodes = decoratedEpisodes,
                                episodePlaybackProgress = playbackProgress,
                                initialEpisodeIndex = initialEpisodeIndex,
                                playSeason = if (shouldUseEpisodeTarget) {
                                    nextUnwatchedEpisode?.seasonNumber ?: if (hasWatchedEpisodes) 1 else state.playSeason
                                } else state.playSeason,
                                playEpisode = if (shouldUseEpisodeTarget) {
                                    nextUnwatchedEpisode?.episodeNumber ?: if (hasWatchedEpisodes) 1 else state.playEpisode
                                } else state.playEpisode,
                                playLabel = if (shouldUseEpisodeTarget) {
                                    if (hasWatchedEpisodes && nextUnwatchedEpisode != null) {
                                        context.getString(R.string.continue_season_episode, nextUnwatchedEpisode.seasonNumber, nextUnwatchedEpisode.episodeNumber)
                                    } else {
                                        context.getString(R.string.play_start_s1e1)
                                    }
                                } else state.playLabel
                            )
                        }
                    }
                    initialLoadComplete = true
                }

                launch {
                    val isInWatchlist = runCatching { watchlistDeferred.await() }.getOrDefault(false)
                    // A tap while this was loading already set the bookmark; the older read must
                    // not undo it.
                    if (!isCurrentRequest() || watchlistSaveState.version != watchlistVersionAtLoad ||
                        watchlistPendingAtLoad) return@launch
                    watchlistSaveState.saved = isInWatchlist
                    watchlistSaveState.wanted = isInWatchlist
                    updateState { state -> state.copy(isInWatchlist = isInWatchlist) }
                }

                launch {
                    val structureAtRead = animeSeasonStructure
                    val seasonProgressResult = if (structureAtRead == null) {
                        runCatching { seasonProgressDeferred?.await() }.getOrNull()
                    } else {
                        runCatching { fetchSeasonProgress(mediaId) }.getOrNull()
                    }
                    val baseSeasonProgress = seasonProgressResult?.progress ?: emptyMap()
                    val resumeTarget = runCatching { resumeDeferred.await() }.getOrNull()
                    val fallbackTargetSeason = initialSeason ?: resumeTarget?.season
                    val fallbackTargetEpisode = initialEpisode ?: resumeTarget?.episode
                    val seasonProgress = if (
                        fallbackTargetSeason != null &&
                        baseSeasonProgress.values.none { it.first > 0 }
                    ) {
                        val derived = baseSeasonProgress.toMutableMap()
                        val targetSeason = fallbackTargetSeason
                        val targetEpisode = fallbackTargetEpisode ?: 1
                        for ((seasonNum, counts) in baseSeasonProgress) {
                            val totalCount = counts.second
                            val watchedCount = when {
                                seasonNum < targetSeason -> totalCount
                                seasonNum == targetSeason -> (targetEpisode - 1).coerceIn(0, totalCount)
                                else -> 0
                            }
                            derived[seasonNum] = Pair(watchedCount, totalCount)
                        }
                        derived
                    } else {
                        baseSeasonProgress
                    }
                    val resolvedTotalSeasons = if (mediaType == MediaType.TV) {
                        maxOf(baseState.totalSeasons, seasonProgress.keys.maxOrNull() ?: 0, 1)
                    } else {
                        baseState.totalSeasons
                    }
                    updateState { state ->
                        if (structureAtRead == null && animeSeasonStructure != null) {
                            state
                        } else {
                            state.copy(
                                seasonProgress = seasonProgress,
                                totalSeasons = resolvedTotalSeasons
                            )
                        }
                    }
                }

                launch {
                    val resumeInfo = runCatching { resumeDeferred.await() }.getOrNull()
                    if (initialSeason != null && initialEpisode != null && mediaType == MediaType.TV) {
                        val matchedResume = resumeInfo?.takeIf {
                            it.season == initialSeason && it.episode == initialEpisode
                        }
                        updateState { state ->
                            val displayTarget = animeSeasonStructure?.identityForTmdb(
                                initialSeason,
                                initialEpisode
                            )
                            state.copy(
                                playSeason = displayTarget?.displaySeason ?: initialSeason,
                                playEpisode = displayTarget?.displayEpisode ?: initialEpisode,
                                playTmdbSeason = initialSeason,
                                playTmdbEpisode = initialEpisode,
                                playLabel = displayTarget?.let {
                                    context.getString(R.string.continue_season_episode, it.displaySeason, it.displayEpisode)
                                } ?: matchedResume?.label ?: context.getString(R.string.continue_season_episode, initialSeason, initialEpisode),
                                playPositionMs = matchedResume?.positionMs
                            )
                        }
                        return@launch
                    }
                    if (resumeInfo != null) {
                        // Fast path: show Continue immediately from local history.
                        val playTarget = buildPlayTarget(mediaType, null, resumeInfo)
                        updateState { state ->
                            val displayTarget = playTarget?.let { target ->
                                val targetSeason = target.season ?: return@let null
                                val targetEpisode = target.episode ?: return@let null
                                animeSeasonStructure?.identityForTmdb(targetSeason, targetEpisode)
                            }
                            state.copy(
                                playSeason = displayTarget?.displaySeason ?: playTarget?.season,
                                playEpisode = displayTarget?.displayEpisode ?: playTarget?.episode,
                                playTmdbSeason = playTarget?.season,
                                playTmdbEpisode = playTarget?.episode,
                                playLabel = displayTarget?.let {
                                    if (playTarget?.label == context.getString(R.string.play_start_s1e1)) {
                                        context.getString(R.string.play_start_s1e1)
                                    } else {
                                        context.getString(R.string.continue_season_episode, it.displaySeason, it.displayEpisode)
                                    }
                                } ?: playTarget?.label,
                                playPositionMs = playTarget?.positionMs
                            )
                        }
                    } else {
                        val seasonProgressResult = runCatching { seasonProgressDeferred?.await() }.getOrNull()
                        val playTarget = buildPlayTarget(mediaType, seasonProgressResult, null)
                        updateState { state ->
                            val displayTarget = playTarget?.let { target ->
                                val targetSeason = target.season ?: return@let null
                                val targetEpisode = target.episode ?: return@let null
                                animeSeasonStructure?.identityForTmdb(targetSeason, targetEpisode)
                            }
                            state.copy(
                                playSeason = displayTarget?.displaySeason ?: playTarget?.season,
                                playEpisode = displayTarget?.displayEpisode ?: playTarget?.episode,
                                playTmdbSeason = playTarget?.season,
                                playTmdbEpisode = playTarget?.episode,
                                playLabel = displayTarget?.let {
                                    if (playTarget?.label == context.getString(R.string.play_start_s1e1)) {
                                        context.getString(R.string.play_start_s1e1)
                                     } else {
                                        context.getString(R.string.continue_season_episode, it.displaySeason, it.displayEpisode)
                                    }
                                } ?: playTarget?.label,
                                playPositionMs = playTarget?.positionMs
                            )
                        }
                    }
                }

                if (mediaType == MediaType.TV) {
                    prefetchAdjacentSeasons(mediaId, seasonToLoad, baseState.totalSeasons)

                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message
                )
            }
        }
    }

    fun loadSeason(seasonNumber: Int) {
        if (currentMediaType != MediaType.TV) return
        // Don't reload if already on this season with loaded episodes and not in loading state
        if (_uiState.value.currentSeason == seasonNumber && _uiState.value.episodes.isNotEmpty() && !_uiState.value.isSeasonLoading) return
        // Ignore a duplicate request for a load that is already in flight
        if (seasonLoadRequestedSeason == seasonNumber && seasonLoadJob?.isActive == true) return

        seasonLoadJob?.cancel()
        seasonLoadRequestedSeason = seasonNumber

        val structure = animeSeasonStructure
        // Fast-path: Check in-memory cache synchronously for instant 0ms transition!
        val cachedEpisodes = if (structure != null) {
            peekAnimeDisplaySeason(currentMediaId, seasonNumber, structure)
        } else {
            mediaRepository.peekCachedSeasonEpisodes(currentMediaId, seasonNumber)?.let { canonical ->
                normalizeAnimeEpisodesForDisplay(
                    tmdbId = currentMediaId,
                    displaySeason = seasonNumber,
                    item = _uiState.value.item,
                    canonicalEpisodes = canonical
                )
            }
        }
        if (cachedEpisodes != null && cachedEpisodes.isNotEmpty()) {
            val watchedKeys = localWatchRepository.getWatchedEpisodesFromCache()
            val decorated = if (watchedKeys.isNotEmpty()) {
                cachedEpisodes.map { ep ->
                    val key = "show_tmdb:$currentMediaId:${ep.tmdbSeasonNumber}:${ep.tmdbEpisodeNumber}"
                    if (watchedKeys.contains(key)) ep.copy(isWatched = true) else ep
                }
            } else {
                val progress = _uiState.value.seasonProgress[seasonNumber]
                val isFullyWatchedSeason = progress != null && progress.second > 0 && progress.first >= progress.second
                if (isFullyWatchedSeason) cachedEpisodes.map { it.copy(isWatched = true) } else cachedEpisodes
            }
            _uiState.value = _uiState.value.copy(
                currentSeason = seasonNumber,
                episodes = decorated,
                episodePlaybackProgress = emptyMap(),
                isSeasonLoading = false
            )
            refreshEpisodePlaybackProgress(decorated)
            prefetchAdjacentSeasons(currentMediaId, seasonNumber, _uiState.value.totalSeasons)
            return
        }

        // Cache miss: Optimistically update selected season pill immediately and show loading skeleton
        _uiState.value = _uiState.value.copy(
            currentSeason = seasonNumber,
            episodes = emptyList(),
            episodePlaybackProgress = emptyMap(),
            isSeasonLoading = true
        )

        seasonLoadJob = viewModelScope.launch {
            try {
                val episodes = if (structure != null) {
                    loadAnimeDisplaySeason(currentMediaId, seasonNumber, structure)
                } else {
                    val canonicalEpisodes = mediaRepository.getSeasonEpisodes(currentMediaId, seasonNumber)
                    normalizeAnimeEpisodesForDisplay(
                        tmdbId = currentMediaId,
                        displaySeason = seasonNumber,
                        item = _uiState.value.item,
                        canonicalEpisodes = canonicalEpisodes
                    )
                }
                // A newer request superseded this one while we were fetching — drop the stale result.
                if (seasonLoadRequestedSeason != seasonNumber) return@launch
                if (episodes.isNotEmpty()) {
                    // Decorate episodes with watched status from cache
                    val watchedKeys = runCatching {
                        localWatchRepository.getWatchedEpisodesForShow(currentMediaId)
                    }.getOrDefault(emptySet())
                    val decoratedEpisodes = if (watchedKeys.isNotEmpty()) {
                        episodes.map { ep ->
                            val key = "show_tmdb:$currentMediaId:${ep.tmdbSeasonNumber}:${ep.tmdbEpisodeNumber}"
                            if (watchedKeys.contains(key)) ep.copy(isWatched = true) else ep
                        }
                    } else {
                        val progress = _uiState.value.seasonProgress[seasonNumber]
                        val isFullyWatchedSeason = progress != null && progress.second > 0 && progress.first >= progress.second
                        if (isFullyWatchedSeason) {
                            episodes.map { it.copy(isWatched = true) }
                        } else {
                            episodes
                        }
                    }

                    // Re-check after the watched-status fetch so a superseded request never overwrites
                    if (seasonLoadRequestedSeason != seasonNumber) return@launch
                    _uiState.value = _uiState.value.copy(
                        episodes = decoratedEpisodes,
                        currentSeason = seasonNumber,
                        isSeasonLoading = false
                    )
                    refreshEpisodePlaybackProgress(decoratedEpisodes)
                    prefetchAdjacentSeasons(currentMediaId, seasonNumber, _uiState.value.totalSeasons)
                } else {
                    _uiState.value = _uiState.value.copy(
                        isSeasonLoading = false,
                        toastMessage = context.getString(R.string.details_no_episodes_season, seasonNumber),
                        toastType = ToastType.ERROR
                    )
                }
            } catch (e: CancellationException) {
                // Superseded by a newer season request
                throw e
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSeasonLoading = false,
                    toastMessage = context.getString(R.string.details_failed_load_season, seasonNumber),
                    toastType = ToastType.ERROR
                )
            }
        }
    }

    private suspend fun fetchEpisodePlaybackProgress(
        mediaId: Int,
        episodes: List<Episode>,
    ): Map<EpisodeIdentity, EpisodePlaybackProgress> {
        val history = runCatching { watchHistoryRepository.getWatchHistory() }
            .getOrDefault(emptyList())
        return episodePlaybackProgress(mediaId, episodes, history)
    }

    private fun refreshEpisodePlaybackProgress(episodes: List<Episode>) {
        val mediaId = currentMediaId
        if (mediaId == 0 || currentMediaType != MediaType.TV || episodes.isEmpty()) return
        val identities = episodes.mapTo(hashSetOf()) { it.identity }
        viewModelScope.launch {
            val playbackProgress = fetchEpisodePlaybackProgress(mediaId, episodes)
            if (currentMediaId != mediaId || currentMediaType != MediaType.TV) return@launch
            if (_uiState.value.episodes.mapTo(hashSetOf()) { it.identity } != identities) return@launch
            _uiState.value = _uiState.value.copy(episodePlaybackProgress = playbackProgress)
        }
    }

    private fun prefetchAdjacentSeasons(mediaId: Int, selectedSeason: Int, totalSeasons: Int) {
        seasonPrefetchJob?.cancel()
        val seasons = listOf(selectedSeason - 1, selectedSeason + 1)
            .filter { it in 1..totalSeasons }
        if (seasons.isEmpty()) return

        val structure = animeSeasonStructure
        seasonPrefetchJob = viewModelScope.launch(Dispatchers.IO) {
            seasons.forEach { season ->
                try {
                    if (structure != null) {
                        loadAnimeDisplaySeason(mediaId, season, structure)
                    } else {
                        mediaRepository.getSeasonEpisodes(mediaId, season)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                }
            }
        }
    }

    private suspend fun loadAnimeDisplaySeason(
        tmdbId: Int,
        displaySeason: Int,
        structure: AnimeSeasonStructure
    ): List<Episode> {
        val identities = structure.seasons[displaySeason] ?: return emptyList()
        val canonicalBySeason = identities
            .map { it.tmdbSeason }
            .distinct()
            .associateWith { season ->
                mediaRepository.getSeasonEpisodes(tmdbId, season).associateBy { it.episodeNumber }
            }
        return identities.mapNotNull { identity ->
            canonicalBySeason[identity.tmdbSeason]
                ?.get(identity.tmdbEpisode)
                ?.copy(
                    seasonNumber = identity.displaySeason,
                    episodeNumber = identity.displayEpisode,
                    identity = identity
                )
        }
    }

    fun resolveEpisodeIdentity(
        displaySeason: Int?,
        displayEpisode: Int?,
        tmdbSeason: Int?,
        tmdbEpisode: Int?
    ): EpisodeIdentity? {
        if (displaySeason == null || displayEpisode == null || tmdbSeason == null || tmdbEpisode == null) {
            return null
        }
        return animeSeasonStructure?.identityForDisplay(displaySeason, displayEpisode)
            ?: animeSeasonStructure?.identityForTmdb(tmdbSeason, tmdbEpisode)
            ?: EpisodeIdentity(displaySeason, displayEpisode, tmdbSeason, tmdbEpisode)
    }

    private fun normalizeAnimeEpisodesForDisplay(
        tmdbId: Int,
        displaySeason: Int,
        item: MediaItem?,
        canonicalEpisodes: List<Episode>,
        style: AnimeStructuringStyle = _uiState.value.animeStructuringStyle,
    ): List<Episode> {
        return animeEpisodesForDisplay(
            episodes = canonicalEpisodes,
            style = style,
            isAnime = style == AnimeStructuringStyle.BROADCAST &&
                animeMapper.isAnimeContent(tmdbId, item?.genreIds.orEmpty(), item?.originalLanguage),
            displaySeason = displaySeason,
        )
    }

    fun toggleWatched(episodeIndex: Int? = null) {
        val currentItem = _uiState.value.item ?: return

        if (currentMediaType == MediaType.TV) {
            val targetEpisode = _uiState.value.episodes.getOrNull(episodeIndex ?: 0)
            if (targetEpisode == null) {
                _uiState.value = _uiState.value.copy(
                    toastMessage = context.getString(R.string.details_no_episode_selected),
                    toastType = ToastType.ERROR
                )
                return
            }
            markEpisodeWatched(
                season = targetEpisode.seasonNumber,
                episode = targetEpisode.episodeNumber,
                watched = !targetEpisode.isWatched
            )
            return
        }

        viewModelScope.launch {
            try {
                val newWatched = !currentItem.isWatched
                if (newWatched) {
                    localWatchRepository.markMovieWatched(currentMediaId)
                    watchHistoryRepository.removeFromHistory(currentMediaId, null, null)
                } else {
                    localWatchRepository.markMovieUnwatched(currentMediaId)
                }
                _uiState.value = _uiState.value.copy(
                    item = currentItem.copy(isWatched = newWatched),
                    toastMessage = if (newWatched) context.getString(R.string.details_marked_watched) else context.getString(R.string.details_marked_unwatched),
                    toastType = ToastType.SUCCESS
                )
                runCatching { launcherContinueWatchingRepository.refreshForCurrentProfile() }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    toastMessage = context.getString(R.string.details_failed_update_watched),
                    toastType = ToastType.ERROR
                )
            }
        }
    }

    fun toggleWatchlist() {
        val currentItem = _uiState.value.item ?: return
        val mediaType = currentMediaType
        val mediaId = currentMediaId
        if (currentItem.mediaType != mediaType || currentItem.id != mediaId) return
        val saveState = watchlistSaveStates.getOrPut(mediaType to mediaId) { WatchlistSaveState() }
        val previousInWatchlist = _uiState.value.isInWatchlist
        val newInWatchlist = !previousInWatchlist
        saveState.wanted = newInWatchlist
        val toggleVersion = ++saveState.version
        saveState.pendingWrites++

        // Show the new state right away: the cloud push behind it can
        // take several seconds, and the bookmark used to wait for both.
        _uiState.value = _uiState.value.copy(
            isInWatchlist = newInWatchlist,
            toastMessage = if (newInWatchlist) context.getString(R.string.added_to_watchlist) else context.getString(R.string.watchlist_toast_removed),
            toastType = ToastType.SUCCESS
        )

        viewModelScope.launch {
            val result = try {
                watchlistWriteMutex.withLock {
                    val wanted = saveState.wanted ?: newInWatchlist
                    // Unknown initial membership is not proof that a cancelling tap needs no write.
                    if (wanted == saveState.saved) return@withLock WatchlistWrite.UNCHANGED
                    try {
                        writeWatchlist(currentItem, mediaType, mediaId, wanted)
                        saveState.saved = wanted
                        WatchlistWrite.SAVED
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        Log.e("DetailsViewModel", "Error updating watchlist: ${e.message}", e)
                        AppLogger.e("DetailsViewModel", "Error updating watchlist: ${e.message}", e)
                        WatchlistWrite.FAILED
                    }
                }
            } finally {
                saveState.pendingWrites--
            }
            when (result) {
                WatchlistWrite.SAVED -> Unit
                WatchlistWrite.FAILED -> {
                    // Only the latest tap may roll back; an older one is settled by the newer write.
                    val isCurrentTitle = currentMediaType == mediaType && currentMediaId == mediaId
                    if (toggleVersion == saveState.version) {
                        saveState.wanted = saveState.saved ?: previousInWatchlist
                        if (isCurrentTitle) {
                            _uiState.value = _uiState.value.copy(
                                isInWatchlist = saveState.wanted == true,
                                toastMessage = context.getString(R.string.details_failed_update_watchlist),
                                toastType = ToastType.ERROR
                            )
                        }
                    }
                }
                WatchlistWrite.UNCHANGED -> Unit
            }
        }
    }

    private suspend fun writeWatchlist(item: MediaItem, mediaType: MediaType, mediaId: Int, inWatchlist: Boolean) {
        if (inWatchlist) {
            // Pass the full MediaItem so it appears instantly in watchlist
            watchlistRepository.addToWatchlist(mediaType, mediaId, item)
        } else {
            watchlistRepository.removeFromWatchlist(mediaType, mediaId)
        }
    }

    fun dismissToast() {
        _uiState.value = _uiState.value.copy(toastMessage = null)
    }

    fun showToast(message: String, type: ToastType = ToastType.INFO) {
        _uiState.value = _uiState.value.copy(
            toastMessage = message,
            toastType = type
        )
    }

    private fun isPendingDebridStream(stream: StreamSource): Boolean {
        val text = listOfNotNull(stream.source, stream.addonName, stream.quality, stream.url)
            .joinToString(" ")
            .lowercase()
        return listOf(
            "torrent being downloaded",
            "being downloaded",
            "still downloading",
            "queued",
            "not cached",
            "uncached",
            "cache pending",
            "caching",
            "processing torrent",
            "download in progress"
        ).any { text.contains(it) }
    }

    private fun sortPlayableStreamsFirst(streams: List<StreamSource>): List<StreamSource> {
        return streams.sortedBy { if (isPendingDebridStream(it)) 1 else 0 }
    }

    /**
     * Refresh watched badges and continue target when returning from Player.
     * Uses local caches/history first for near-instant UI updates.
     */
    fun refreshAfterPlayerReturn() {
        val tmdbId = currentMediaId
        if (tmdbId == 0) return
        // Don't run during initial load — would overwrite episodes/seasonProgress with empty data.
        // The isLoading guard alone is insufficient: cached items set isLoading=false immediately,
        // but episodes haven't been populated yet. Wait for the episodes coroutine to finish.
        if (_uiState.value.isLoading || !initialLoadComplete) return
        val mediaType = currentMediaType

        viewModelScope.launch {
            // Force-refresh watched episodes from backend (not just in-memory cache)
            // to pick up episodes marked watched during playback.
            val watchedKeys = if (mediaType == MediaType.TV) {
                try {
                    localWatchRepository.getWatchedEpisodesForShow(tmdbId)
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    localWatchRepository.getWatchedEpisodesFromCache()
                }
            } else {
                emptySet()
            }

            // Build all updates in one shot to avoid partial state writes
            val currentState = _uiState.value

            // 1. Update main item watched status
            val updatedItem = currentState.item?.let { currentItem ->
                val watched = if (mediaType == MediaType.MOVIE) {
                    localWatchRepository.isMovieWatched(tmdbId)
                } else {
                    localWatchRepository.hasWatchedEpisodes(tmdbId) ||
                        watchedKeys.any { it.startsWith("show_tmdb:$tmdbId:") }
                }
                currentItem.copy(isWatched = watched)
            }

            // 2. Update episode watched badges and season progress
            // Read the LATEST state for episodes — the snapshot captured above may be stale
            // if loadDetails() child coroutines populated episodes after we started.
            val latestForEpisodes = _uiState.value
            var updatedEpisodes = latestForEpisodes.episodes
            var updatedProgress = latestForEpisodes.seasonProgress
            if (mediaType == MediaType.TV && latestForEpisodes.episodes.isNotEmpty()) {
                val prefix = "show_tmdb:$tmdbId:"
                if (watchedKeys.any { it.startsWith(prefix) }) {
                    updatedEpisodes = latestForEpisodes.episodes.map { ep ->
                        val key = "show_tmdb:$tmdbId:${ep.tmdbSeasonNumber}:${ep.tmdbEpisodeNumber}"
                        ep.copy(isWatched = ep.isWatched || watchedKeys.contains(key))
                    }
                    val season = latestForEpisodes.currentSeason
                    val progress = latestForEpisodes.seasonProgress.toMutableMap()
                    progress[season] = Pair(updatedEpisodes.count { it.isWatched }, updatedEpisodes.size)
                    updatedProgress = progress
                }
            }

            // 3. Re-derive play target: try resume info first, then next unwatched episode
            val quickResume = fetchResumeInfoFromHistoryOnly(tmdbId, mediaType)
            val playTarget = if (quickResume != null) {
                PlayTarget(
                    season = quickResume.season,
                    episode = quickResume.episode,
                    label = quickResume.label,
                    positionMs = quickResume.positionMs
                )
            } else if (mediaType == MediaType.TV) {
                // No resume point (episode was finished) — find the next unwatched episode
                deriveNextUnwatchedPlayTarget(tmdbId, watchedKeys)
            } else {
                null
            }

            // Read latest state to avoid overwriting concurrent updates (e.g. seasonProgress)
            val latestState = _uiState.value
            val playbackProgress = if (mediaType == MediaType.TV) {
                fetchEpisodePlaybackProgress(tmdbId, updatedEpisodes)
            } else {
                emptyMap()
            }
            val displayPlayTarget = playTarget?.let { target ->
                val season = target.season ?: return@let null
                val episode = target.episode ?: return@let null
                animeSeasonStructure?.identityForTmdb(season, episode)
            }
            _uiState.value = latestState.copy(
                item = updatedItem ?: latestState.item,
                // Only overwrite episodes if we actually computed watched badges;
                // otherwise keep the latest (avoids blanking if episodes were populated concurrently)
                episodes = if (updatedEpisodes.isNotEmpty()) updatedEpisodes else latestState.episodes,
                episodePlaybackProgress = playbackProgress,
                // Only update seasonProgress if we actually computed new data; preserve existing otherwise
                seasonProgress = if (updatedProgress !== latestForEpisodes.seasonProgress) updatedProgress else latestState.seasonProgress,
                playSeason = displayPlayTarget?.displaySeason ?: playTarget?.season ?: latestState.playSeason,
                playEpisode = displayPlayTarget?.displayEpisode ?: playTarget?.episode ?: latestState.playEpisode,
                playTmdbSeason = playTarget?.season ?: latestState.playTmdbSeason,
                playTmdbEpisode = playTarget?.episode ?: latestState.playTmdbEpisode,
                playLabel = displayPlayTarget?.let {
                    if (playTarget?.label == context.getString(R.string.play_start_s1e1)) {
                        context.getString(R.string.play_start_s1e1)
                    } else {
                        context.getString(R.string.continue_season_episode, it.displaySeason, it.displayEpisode)
                    }
                } ?: playTarget?.label ?: latestState.playLabel,
                playPositionMs = playTarget?.positionMs ?: 0L
            )
        }
    }

    /**
     * Find the next unwatched episode across all seasons to set the play button target.
     */
    private suspend fun deriveNextUnwatchedPlayTarget(tmdbId: Int, watchedKeys: Set<String>): PlayTarget? {
        return try {
            val tvDetails = tmdbApi.getMergedTvDetails(tmdbId, Constants.TMDB_API_KEY)
            val hasWatchedForShow = watchedKeys.any { it.startsWith("show_tmdb:$tmdbId:") }
            for (seasonNum in 1..tvDetails.numberOfSeasons) {
                val seasonDetails = try {
                    tmdbApi.getMergedTvSeason(tmdbId, seasonNum, Constants.TMDB_API_KEY)
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    null
                } ?: continue
                val firstUnwatched = seasonDetails.episodes.filter { it.seasonNumber == seasonNum }.firstOrNull { episode ->
                    val key = "show_tmdb:$tmdbId:$seasonNum:${episode.episodeNumber}"
                    !watchedKeys.contains(key)
                }
                if (firstUnwatched != null) {
                    val label = if (hasWatchedForShow) {
                        context.getString(R.string.continue_season_episode, seasonNum, firstUnwatched.episodeNumber)
                    } else {
                        context.getString(R.string.play_start_s1e1)
                    }
                    return PlayTarget(
                        season = seasonNum,
                        episode = firstUnwatched.episodeNumber,
                        label = label
                    )
                }
            }
            // All episodes watched — offer restart
            PlayTarget(season = 1, episode = 1, label = context.getString(R.string.play_start_s1e1))
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            null
        }
    }

    // ========== Person Modal ==========




    // ========== Stream Resolution ==========

    /**
     * Silently prefetch streams in the background so they are cached by the time
     * the user presses Play or opens Sources. Does not update UI loading state —
     * it only populates StreamRepository's internal cache.
     */
    private var prefetchJob: kotlinx.coroutines.Job? = null
    private var sharedPrefetch: com.arflix.tv.data.repository.InFlightSearches.Lease<com.arflix.tv.data.repository.ProgressiveStreamResult>? = null
    private var sharedPrefetchTarget: Triple<String, Int?, Int?>? = null
    private var prefetchDwellJob: kotlinx.coroutines.Job? = null
    private var pendingPrefetchImdbId: String? = null
    private var pendingPrefetchSeason: Int? = null
    private var pendingPrefetchEpisode: Int? = null

    /**
     * Cancel any pending or running proactive scrape. Called on back-nav / dispose
     * so leaving within the dwell window does not leak a scraping session, and on
     * each new loadDetails() so Similar-chain browsing supersedes the previous job.
     */
    fun cancelStreamPrefetch() {
        prefetchDwellJob?.cancel()
        prefetchDwellJob = null
        pendingPrefetchImdbId = null
        sharedPrefetch?.release()
        sharedPrefetch = null
        sharedPrefetchTarget = null
        prefetchJob?.cancel()
        prefetchJob = null
    }

    fun markStreamPrefetchPlayPressed(imdbId: String?, season: Int?, episode: Int?) {
        if (sharedPrefetchTarget == Triple(imdbId, season, episode)) {
            sharedPrefetch?.markPlayPressed()
        }
    }

    /**
     * TV intent fast-path: fired when focus reaches the Play/Sources action row
     * (to be wired by DetailsActionSection in Issue 5 Layer 2). Fires the pending
     * scrape immediately instead of waiting out the dwell.
     */
    fun requestStreamPrefetchNow() {
        val imdbId = pendingPrefetchImdbId ?: return
        val dwell = prefetchDwellJob ?: return
        if (!dwell.isActive) return
        val season = pendingPrefetchSeason
        val episode = pendingPrefetchEpisode
        prefetchDwellJob?.cancel()
        prefetchDwellJob = null
        pendingPrefetchImdbId = null
        prefetchStreamsInBackground(imdbId, season, episode)
    }

    private fun scheduleStreamPrefetch(imdbId: String, season: Int?, episode: Int?) {
        prefetchDwellJob?.cancel()
        pendingPrefetchImdbId = imdbId
        pendingPrefetchSeason = season
        pendingPrefetchEpisode = episode
        val scheduledMediaId = currentMediaId
        val scheduledMediaType = currentMediaType
        val isTv = try {
            detectDeviceType(context) == DeviceType.TV
        } catch (_: Exception) {
            false
        }
        if (com.arflix.tv.BuildConfig.PERSONAL_BUILD && isTv) {
            // Personal build on TV: start the Usenet Ultimate lookup right away (no dwell),
            // so sources are ready when Play is pressed.
            prefetchDwellJob = null
            pendingPrefetchImdbId = null
            prefetchStreamsInBackground(imdbId, season, episode)
            return
        }
        val dwellMs = if (isTv) STREAM_PREFETCH_DWELL_TV_MS else STREAM_PREFETCH_DWELL_MOBILE_MS
        prefetchDwellJob = viewModelScope.launch {
            delay(dwellMs)
            // Similar-chain guard: a new loadDetails() supersedes this pending scrape.
            if (currentMediaId != scheduledMediaId || currentMediaType != scheduledMediaType) return@launch
            if (pendingPrefetchImdbId != imdbId) return@launch
            // Mobile metered-network gate: Cellular browsing must not trigger full
            // parallel addon scraping + TLS prewarming. Play still scrapes on demand.
            if (!isTv) {
                val networkType = runCatching { networkMonitor.getNetworkType() }.getOrNull()
                if (networkType == NetworkType.CELLULAR || networkType == NetworkType.NONE) return@launch
            }
            pendingPrefetchImdbId = null
            prefetchDwellJob = null
            prefetchStreamsInBackground(imdbId, season, episode)
        }
    }
    private fun prefetchStreamsInBackground(imdbId: String, season: Int?, episode: Int?) {
        sharedPrefetch?.release()
        sharedPrefetch = null
        sharedPrefetchTarget = null
        prefetchJob?.cancel()
        val requestMediaType = currentMediaType
        val requestMediaId = currentMediaId
        prefetchJob = viewModelScope.launch {
            runCatching {
                if ((requestMediaType == MediaType.MOVIE || (season != null && episode != null)) &&
                    com.arflix.tv.BuildConfig.PERSONAL_BUILD && streamRepository.installedAddons.first().any {
                        it.isInstalled && it.isEnabled && com.arflix.tv.data.model.isUsenetUltimateAddon(it)
                    }) {
                    val lease = streamRepository.acquireStreamSearch(
                        imdbId = imdbId, season = season, episode = episode,
                        tmdbId = requestMediaId, tvdbId = _uiState.value.tvdbId,
                        genreIds = _uiState.value.item?.genreIds ?: emptyList(),
                        originalLanguage = _uiState.value.item?.originalLanguage,
                        title = _uiState.value.item?.title.orEmpty(),
                        year = _uiState.value.item?.year?.toIntOrNull(),
                        airDate = _uiState.value.episodes.firstOrNull {
                            it.seasonNumber == season && it.episodeNumber == episode
                        }?.airDate?.takeIf { it.isNotBlank() },
                        nativeType = mediaRepository.addonNativeType(requestMediaId)
                    )
                    sharedPrefetch = lease
                    sharedPrefetchTarget = Triple(imdbId, season, episode)
                    try {
                        fun prewarm(progressive: com.arflix.tv.data.repository.ProgressiveStreamResult) {
                            prewarmVisibleStreams(
                                sortPlayableStreamsFirst(
                                    progressive.streams
                                        .filter { !it.url.isNullOrBlank() && !it.url.orEmpty().startsWith("magnet:", ignoreCase = true) }
                                        .distinctBy(::providerScopedStreamIdentity)
                                )
                            )
                        }
                        kotlinx.coroutines.coroutineScope {
                            val updates = launch {
                                lease.updates.collect { progressive ->
                                    if (progressive != null) prewarm(progressive)
                                }
                            }
                            try {
                                prewarm(lease.result.await())
                            } finally {
                                updates.cancel()
                            }
                        }
                    } finally {
                        lease.release()
                        if (sharedPrefetch === lease) {
                            sharedPrefetch = null
                            sharedPrefetchTarget = null
                        }
                    }
                } else if (requestMediaType == MediaType.MOVIE) {
                    streamRepository.resolveMovieStreamsProgressive(
                        imdbId = imdbId,
                        title = _uiState.value.item?.title.orEmpty(),
                        year = _uiState.value.item?.year?.toIntOrNull(),
                        nativeType = mediaRepository.addonNativeType(currentMediaId)
                    ).collect { progressive ->
                        prewarmVisibleStreams(
                            sortPlayableStreamsFirst(
                                progressive.streams
                                    .filter { !it.url.isNullOrBlank() && !it.url.orEmpty().startsWith("magnet:", ignoreCase = true) }
                                    .distinctBy(::providerScopedStreamIdentity)
                            )
                        )
                    }
                } else if (season != null && episode != null) {
                    val prefetchAirDate = _uiState.value.episodes
                        .firstOrNull { it.seasonNumber == season && it.episodeNumber == episode }
                        ?.airDate?.takeIf { it.isNotBlank() }
                    streamRepository.resolveEpisodeStreamsProgressive(
                        imdbId = imdbId,
                        season = season,
                        episode = episode,
                        tmdbId = currentMediaId,
                        tvdbId = _uiState.value.tvdbId,
                        genreIds = _uiState.value.item?.genreIds ?: emptyList(),
                        originalLanguage = _uiState.value.item?.originalLanguage,
                        title = _uiState.value.item?.title ?: "",
                        airDate = prefetchAirDate,
                        nativeType = mediaRepository.addonNativeType(currentMediaId)
                    ).collect { progressive ->
                        prewarmVisibleStreams(
                            sortPlayableStreamsFirst(
                                progressive.streams
                                    .filter { !it.url.isNullOrBlank() && !it.url.orEmpty().startsWith("magnet:", ignoreCase = true) }
                                    .distinctBy(::providerScopedStreamIdentity)
                            )
                        )
                    }
                } else {
                    null
                }
            }
        }
    }

    fun prewarmStream(stream: StreamSource) {
        focusedStreamPrewarmJob?.cancel()
        focusedStreamPrewarmJob = viewModelScope.launch {
            runCatching {
                streamRepository.prewarmStreamForPlayback(stream, allowNetworkWarmup = true)
            }
        }
    }

    fun prewarmStreamsAround(stream: StreamSource, streams: List<StreamSource>) {
        if (streams.isEmpty()) return
        focusedStreamPrewarmJob?.cancel()
        focusedStreamPrewarmJob = viewModelScope.launch {
            val index = streams.indexOf(stream).takeIf { it >= 0 } ?: 0
            val candidates = listOf(index, index + 1, index + 2)
                .mapNotNull { streams.getOrNull(it) }
                .distinctBy { "${it.addonId}:${it.source}:${it.url?.substringBefore('|')?.substringBefore('#')}" }
            runCatching {
                streamRepository.prewarmStreamsForPlayback(
                    streams = candidates,
                    limit = candidates.size,
                    allowNetworkWarmup = true
                )
            }
        }
    }

    private fun prewarmVisibleStreams(streams: List<StreamSource>) {
        if (streams.isEmpty()) return
        val topStreams = streams.take(3)
        val prewarmKey = topStreams.joinToString("|") { stream ->
            "${stream.addonId}:${stream.source}:${stream.url?.substringBefore('|')?.substringBefore('#')}"
        }
        if (prewarmKey == lastStreamListPrewarmKey) return
        lastStreamListPrewarmKey = prewarmKey
        streamListPrewarmJob?.cancel()
        streamListPrewarmJob = viewModelScope.launch {
            runCatching {
                streamRepository.prewarmStreamsForPlayback(
                    streams = topStreams,
                    limit = topStreams.size,
                    allowNetworkWarmup = true
                )
            }
        }
    }

    private fun diagSourceList(streams: List<StreamSource>, requestId: Long, isFinal: Boolean) {
        // One bounded line per source keeps metadata useful without building oversized JSON lines.
        runCatching {
            PtpDiag.i("src-list", "requestId" to requestId, "total" to streams.size, "shown" to minOf(20, streams.size), "final" to isFinal)
            if (!isFinal) return@runCatching
            streams.take(20).forEachIndexed { index, stream ->
                val traits = BestSourcePicker.parse(stream)
                PtpDiag.i("src-list", "requestId" to requestId, "total" to streams.size, "final" to isFinal,
                    "index" to index, "name" to diagSafeText(stream.source).take(120), "quality" to diagSafeText(stream.quality).take(60),
                    "sizeBytes" to traits.sizeBytes, "codec" to traits.codec.name, "hdr" to traits.isHdr,
                    "dolbyVision" to traits.dolbyVision, "hdr10" to traits.hdr10, "hdr10Plus" to traits.hdr10Plus,
                    "hlg" to traits.hlg, "tenBit" to traits.tenBit, "resolution" to traits.resolution,
                    "score" to null, "scoreReason" to "provider-order-no-numeric-score", "addon" to diagSafeText(stream.addonName).take(80))
            }
        }
    }

    fun loadStreams(imdbId: String?, identity: EpisodeIdentity? = null) {
        val requestId = ++loadStreamsRequestId
        loadStreamsJob?.cancel()
        focusedStreamPrewarmJob?.cancel()
        streamListPrewarmJob?.cancel()
        // Reset synchronously so the modal opens in loading state immediately,
        // before the coroutine below gets a chance to run.
        _uiState.value = _uiState.value.copy(
            isLoadingStreams = true,
            completedAddons = 0,
            totalAddons = 0,
            streams = emptyList(),
            streamsEpisodeIdentity = identity,
            recentSource = null,
            subtitles = emptyList(),
            streamSearchStartTime = System.currentTimeMillis(),
        )
        val requestMediaType = currentMediaType
        val requestMediaId = currentMediaId

        viewModelScope.launch {
            val recent = recentPlayedSourceRepository.get(
                requestMediaType, requestMediaId, identity?.tmdbSeason, identity?.tmdbEpisode
            ) ?: return@launch
            if (requestId == loadStreamsRequestId) {
                _uiState.value = _uiState.value.copy(recentSource = recent)
            }
        }

        // Register the job before it can synchronously finish or launch providers.
        loadStreamsJob = viewModelScope.launch(start = CoroutineStart.LAZY) {
            fun isCurrentRequest(): Boolean {
                return requestId == loadStreamsRequestId &&
                    currentMediaType == requestMediaType &&
                    currentMediaId == requestMediaId
            }
            if (!isCurrentRequest()) return@launch

            var resolvedImdbId: String? = null
            try {
                // If the user clicked Play/Sources very fast (e.g. from Search), the background
                // resolveExternalIds might still be running. Try to recover it from cache
                // or wait for the UI state to be updated by the background fetch.
                var currentImdbId = imdbId ?: _uiState.value.imdbId
                if (currentImdbId.isNullOrBlank()) {
                    currentImdbId = mediaRepository.getCachedImdbId(requestMediaType, requestMediaId)
                }
                if (currentImdbId.isNullOrBlank()) {
                    withTimeoutOrNull(3500) {
                        while (currentImdbId.isNullOrBlank() && isCurrentRequest()) {
                            delay(200)
                            currentImdbId = _uiState.value.imdbId
                        }
                    }
                }
                resolvedImdbId = currentImdbId
                val effectiveStreamId: String? = when {
                    !resolvedImdbId.isNullOrBlank() -> resolvedImdbId
                    requestMediaId > 0 -> "tmdb:$requestMediaId"
                    else -> null
                }

                val unifiedOrderedIds = streamIntegrationRepository.getUnifiedSourceOrderedIds().first()
                if (!isCurrentRequest()) return@launch
                _uiState.value = _uiState.value.copy(
                    isLoadingStreams = true,
                    completedAddons = 0,
                    totalAddons = 0,
                    streams = emptyList(),
                    streamsEpisodeIdentity = identity,
                    subtitles = emptyList(),
                    addonOrderedIds = unifiedOrderedIds,
                    streamSearchStartTime = System.currentTimeMillis()
                )

                if (requestMediaType == MediaType.MOVIE) {
                    val title = _uiState.value.item?.title.orEmpty()
                    Log.d(
                        TAG,
                        "[MovieSources] loadStreams start requestId=$requestId mediaId=$requestMediaId imdbId=${resolvedImdbId ?: "null"} title=$title"
                    )
                }

                // Get current item's genre IDs and language for anime detection
                val item = _uiState.value.item
                val genreIds = item?.genreIds ?: emptyList()
                val originalLanguage = item?.originalLanguage
                val canonicalSeason = identity?.tmdbSeason
                val canonicalEpisode = identity?.tmdbEpisode
                val animeQueryOverride = identity?.kitsuQuery

                if (!isCurrentRequest()) return@launch
            val result = if (currentMediaType == MediaType.MOVIE) {
                    val enabledAddons = streamRepository.installedAddons.first()
                        .filter { it.isVodStreamingAddon() }
                    val enabledStreamingAddons = enabledAddons.size
                    val stremioCount = enabledAddons.count { it.runtimeKind == com.arflix.tv.data.model.RuntimeKind.STREMIO }
                    val enabledAddonNames = enabledAddons.take(4).joinToString(",") { it.name }
                    Log.d(
                        TAG,
                        "[MovieSources] enabledStreamingAddons=$enabledStreamingAddons requestId=$requestId mediaId=$requestMediaId"
                    )
                    Log.d(
                        TAG,
                        "[MovieSources] addonBreakdown stremio=$stremioCount names=$enabledAddonNames"
                    )
                    if (enabledStreamingAddons == 0) {
                        Log.w(
                            TAG,
                            "[MovieSources] no streaming addons enabled. Install/enable sources in Settings > Addons."
                        )
                    }

                    if (effectiveStreamId.isNullOrBlank()) {
                        PtpDiag.w("src-empty", "reason" to "missing-media-id", "requestId" to requestId, "mediaId" to requestMediaId)
                        Log.w(
                            TAG,
                            "[MovieSources] loadStreams skipped (missing imdbId) requestId=$requestId mediaId=$requestMediaId"
                        )
                        _uiState.value = _uiState.value.copy(
                            isLoadingStreams = false,
                            streams = emptyList(),
                            subtitles = emptyList(),
                            hasStreamingAddons = hasAnyStreamProvider(
                                streamingAddonCount = streamRepository.installedAddons.first()
                                    .count { it.isVodStreamingAddon() },

                            )
                        )
                        return@launch
                    }
                    streamRepository.resolveMovieStreamsProgressive(
                        imdbId = effectiveStreamId,
                        title = item?.title.orEmpty(),
                        year = item?.year?.toIntOrNull(),
                        nativeType = mediaRepository.addonNativeType(currentMediaId)
                    ).collect { progressive ->
                        if (!isCurrentRequest()) return@collect
                        val existingVod = _uiState.value.streams.filter(::isSupplementalStream)
                        val mergedStreams = sortPlayableStreamsFirst(
                            (progressive.streams + existingVod)
                                .distinctBy(::providerScopedStreamIdentity)
                        )
                        Log.d(
                            TAG,
                            "[MovieSources] progressive requestId=$requestId final=${progressive.isFinal} " +
                                "incoming=${progressive.streams.size} merged=${mergedStreams.size} subtitles=${progressive.subtitles.size}"
                        )
                        diagSourceList(mergedStreams, requestId, progressive.isFinal)
                        val addonCount = streamRepository.installedAddons.first()
                            .count { it.isVodStreamingAddon() }
                        val supplementalSourcesStillLoading =
                            false
                        _uiState.value = _uiState.value.copy(
                            isLoadingStreams = mergedStreams.isEmpty() &&
                                (!progressive.isFinal || supplementalSourcesStillLoading),
                            completedAddons = progressive.completedAddons,
                            totalAddons = progressive.totalAddons,
                            streams = mergedStreams,
                            subtitles = progressive.subtitles,
                            hasStreamingAddons = hasAnyStreamProvider(
                                streamingAddonCount = addonCount,

                            )
                        )
                        prewarmVisibleStreams(mergedStreams)
                        if (progressive.isFinal) {
                            Log.d(
                                TAG,
                                "[MovieSources] loadStreams completed requestId=$requestId mediaId=$requestMediaId totalStreams=${mergedStreams.size}"
                            )
                        }
                    }
                    return@launch
                } else {
                    if (effectiveStreamId.isNullOrBlank()) {
                        _uiState.value = _uiState.value.copy(
                            isLoadingStreams = false,
                            streams = emptyList(),
                            subtitles = emptyList(),
                            hasStreamingAddons = hasAnyStreamProvider(
                                streamingAddonCount = streamRepository.installedAddons.first()
                                    .count { it.isVodStreamingAddon() },

                            )
                        )
                        return@launch
                    }
                    // Look up air date for daily show stream resolution fallback
                    val episodeAirDate = identity?.let { requested ->
                        _uiState.value.episodes.firstOrNull { it.identity == requested }?.airDate
                    }?.takeIf { it.isNotBlank() }

                    streamRepository.resolveEpisodeStreamsProgressive(
                        imdbId = effectiveStreamId,
                        season = canonicalSeason ?: 1,
                        episode = canonicalEpisode ?: 1,
                        tmdbId = currentMediaId,
                        tvdbId = _uiState.value.tvdbId,
                        genreIds = genreIds,
                        originalLanguage = originalLanguage,
                        title = item?.title ?: "",
                        animeQueryOverride = animeQueryOverride,
                        airDate = episodeAirDate,
                        nativeType = mediaRepository.addonNativeType(currentMediaId)
                    ).collect { progressive ->
                        if (!isCurrentRequest()) return@collect
                        val existingVod = _uiState.value.streams.filter(::isSupplementalStream)
                        val mergedStreams = sortPlayableStreamsFirst(
                            (progressive.streams + existingVod)
                                .distinctBy(::providerScopedStreamIdentity)
                        )
                        diagSourceList(mergedStreams, requestId, progressive.isFinal)
                        val addonCount = streamRepository.installedAddons.first()
                            .count { it.isVodStreamingAddon() }
                        val supplementalSourcesStillLoading =
                            false
                        _uiState.value = _uiState.value.copy(
                            isLoadingStreams = mergedStreams.isEmpty() &&
                                (!progressive.isFinal || supplementalSourcesStillLoading),
                            completedAddons = progressive.completedAddons,
                            totalAddons = progressive.totalAddons,
                            streams = mergedStreams,
                            subtitles = progressive.subtitles,
                            hasStreamingAddons = hasAnyStreamProvider(
                                streamingAddonCount = addonCount,

                            )
                        )
                        prewarmVisibleStreams(mergedStreams)
                    }
                    return@launch
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                if (!isCurrentRequest()) return@launch
                if (requestMediaType == MediaType.MOVIE) {
                    Log.e(
                        TAG,
                        "[MovieSources] loadStreams failed requestId=$requestId mediaId=$requestMediaId message=${e.message}",
                        e
                    )
                }
            } finally {
                if (isCurrentRequest()) {
                    stopStreamSpinnerIfNothingLeft(ignoring = coroutineContext[Job])
                }
            }
        }
        loadStreamsJob?.start()
    }

    private suspend fun persistNextEpisodePointer(
        item: MediaItem,
        progress: SeasonProgressResult,
    ) {
        if (!progress.isComplete) return
        val airedTarget = progress.nextUnwatched
        val deferredTarget = progress.nextUnaired
        val target = airedTarget ?: deferredTarget

        // Remove the previous show-level pointer first. This immediately removes a completed or
        // caught-up show from Home; an aired replacement below is then published as one update.
        localWatchRepository.removeFromContinueWatchingCache(currentMediaId, null, null, MediaType.TV)
        if (target == null) return

        val (season, episode) = target
        val displayIdentity = animeSeasonStructure?.identityForTmdb(season, episode)
        localWatchRepository.saveLocalContinueWatching(
            mediaType = MediaType.TV,
            tmdbId = currentMediaId,
            title = item.title,
            posterPath = item.image,
            backdropPath = item.backdrop,
            season = season,
            episode = episode,
            displaySeason = displayIdentity?.displaySeason ?: season,
            displayEpisode = displayIdentity?.displayEpisode ?: episode,
            episodeTitle = null,
            progress = 0,
            positionSeconds = 0L,
            durationSeconds = 0L,
            year = item.year,
            isUpNext = true,
            episodeAirDate = if (airedTarget == null) progress.nextUnairedAirDate else "",
            // Keep a future pointer on disk so it can reappear after its air date, but do not
            // publish it into the currently visible Home row before then.
            emitUpdate = airedTarget != null,
        )
    }

    fun markEpisodeWatched(season: Int, episode: Int, watched: Boolean) {
        viewModelScope.launch {
            try {
                val selectedEpisode = _uiState.value.episodes.firstOrNull {
                    it.seasonNumber == season && it.episodeNumber == episode
                }
                val canonicalSeason = selectedEpisode?.tmdbSeasonNumber ?: season
                val canonicalEpisode = selectedEpisode?.tmdbEpisodeNumber ?: episode
                if (watched) {
                    localWatchRepository.markEpisodeWatched(currentMediaId, canonicalSeason, canonicalEpisode)
                    watchHistoryRepository.removeFromHistory(currentMediaId, canonicalSeason, canonicalEpisode)
                    localWatchRepository.removeFromContinueWatchingCache(
                        currentMediaId,
                        canonicalSeason,
                        canonicalEpisode,
                        MediaType.TV,
                    )
                } else {
                    localWatchRepository.markEpisodeUnwatched(currentMediaId, canonicalSeason, canonicalEpisode)
                }

                // Update local state
                val updatedEpisodes = _uiState.value.episodes.map { ep ->
                    if (ep.seasonNumber == season && ep.episodeNumber == episode) {
                        ep.copy(isWatched = watched)
                    } else ep
                }
                val refreshedProgress = fetchSeasonProgress(currentMediaId)
                val resumeInfo = fetchResumeInfo(currentMediaId, MediaType.TV)
                val playTarget = buildPlayTarget(MediaType.TV, refreshedProgress, resumeInfo)
                if (resumeInfo == null) {
                    _uiState.value.item?.let { persistNextEpisodePointer(it, refreshedProgress) }
                }
                val displayPlayTarget = playTarget?.let { target ->
                    val targetSeason = target.season ?: return@let null
                    val targetEpisode = target.episode ?: return@let null
                    animeSeasonStructure?.identityForTmdb(targetSeason, targetEpisode)
                }
                _uiState.value = _uiState.value.copy(
                    item = _uiState.value.item?.copy(
                        isWatched = if (refreshedProgress.isComplete) {
                            refreshedProgress.hasWatched && refreshedProgress.nextUnwatched == null
                        } else _uiState.value.item?.isWatched ?: false
                    ),
                    episodes = updatedEpisodes,
                    episodePlaybackProgress = fetchEpisodePlaybackProgress(currentMediaId, updatedEpisodes),
                    seasonProgress = if (refreshedProgress.isComplete) refreshedProgress.progress else _uiState.value.seasonProgress,
                    playSeason = displayPlayTarget?.displaySeason ?: playTarget?.season,
                    playEpisode = displayPlayTarget?.displayEpisode ?: playTarget?.episode,
                    playTmdbSeason = playTarget?.season,
                    playTmdbEpisode = playTarget?.episode,
                    playLabel = displayPlayTarget?.let {
                        context.getString(R.string.continue_season_episode, it.displaySeason, it.displayEpisode)
                    } ?: playTarget?.label,
                    playPositionMs = playTarget?.positionMs,
                )
                runCatching { launcherContinueWatchingRepository.refreshForCurrentProfile() }
            } catch (e: Exception) {
                // Failed silently
            }
        }
    }

    fun markSeasonWatched(season: Int) {
        if (currentMediaType != MediaType.TV) return
        val currentItem = _uiState.value.item ?: return

        viewModelScope.launch {
            try {
                val seasonEpisodes = if (_uiState.value.currentSeason == season && _uiState.value.episodes.isNotEmpty()) {
                    _uiState.value.episodes
                } else {
                    animeSeasonStructure?.let { loadAnimeDisplaySeason(currentMediaId, season, it) }
                        ?: mediaRepository.getSeasonEpisodes(currentMediaId, season)
                }

                val airedEpisodes = seasonEpisodes.filter { episode ->
                    EpisodeAvailability.hasAired(episode.airDate)
                }
                if (airedEpisodes.isEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        toastMessage = context.getString(R.string.details_no_episodes_season, season),
                        toastType = ToastType.ERROR
                    )
                    return@launch
                }

                // OPTIMISTIC UPDATE: Update local state immediately so the UI responds instantly
                val updatedEpisodes = if (_uiState.value.currentSeason == season) {
                    _uiState.value.episodes.map { ep ->
                        if (ep.seasonNumber == season) {
                            EpisodeAvailability.markWatchedIfAired(ep)
                        } else ep
                    }
                } else {
                    _uiState.value.episodes
                }
                val optimisticProgress = _uiState.value.seasonProgress.toMutableMap().apply {
                    this[season] = Pair(airedEpisodes.size, airedEpisodes.size)
                }
                _uiState.value = _uiState.value.copy(
                    episodes = updatedEpisodes,
                    seasonProgress = optimisticProgress
                )

                val canonicalGroups = airedEpisodes.groupBy { it.tmdbSeasonNumber }

                if (animeSeasonStructure == null) {
                    // Preserve the existing single-season fast path for ordinary TV shows.
                    val episodeNumbers = airedEpisodes.map { it.episodeNumber }
                    runCatching {
                        localWatchRepository.markSeasonWatched(currentMediaId, season, episodeNumbers)
                    }
                    runCatching {
                        watchHistoryRepository.removeFromHistory(currentMediaId, season, null)
                    }
                    episodeNumbers.map { epNum ->
                        async {
                            runCatching {
                                localWatchRepository.markEpisodeWatched(currentMediaId, season, epNum)
                            }
                        }
                    }.forEach { it.await() }
                } else {
                    // A displayed anime season may be a cour inside a larger TMDB season. Never
                    // clear or mark the rest of that canonical season by accident.
                    canonicalGroups.forEach { (tmdbSeason, episodes) ->
                        val tmdbEpisodes = episodes.map { it.tmdbEpisodeNumber }
                        runCatching {
                            localWatchRepository.markSeasonWatched(currentMediaId, tmdbSeason, tmdbEpisodes)
                        }
                        episodes.map { ep ->
                            async {
                                runCatching {
                                    watchHistoryRepository.removeFromHistory(
                                        currentMediaId,
                                        ep.tmdbSeasonNumber,
                                        ep.tmdbEpisodeNumber
                                    )
                                    localWatchRepository.markEpisodeWatched(
                                        currentMediaId,
                                        ep.tmdbSeasonNumber,
                                        ep.tmdbEpisodeNumber
                                    )
                                }
                            }
                        }.forEach { it.await() }
                    }
                }

                val refreshedProgress = runCatching { fetchSeasonProgress(currentMediaId) }.getOrNull()
                val nextUnwatched = refreshedProgress?.nextUnwatched

                if (refreshedProgress != null) {
                    runCatching { persistNextEpisodePointer(currentItem, refreshedProgress) }
                }

                val playTarget = buildPlayTarget(currentMediaType, refreshedProgress, null)
                val displayPlayTarget = playTarget?.let { target ->
                    val targetSeason = target.season ?: return@let null
                    val targetEpisode = target.episode ?: return@let null
                    animeSeasonStructure?.identityForTmdb(targetSeason, targetEpisode)
                }

                _uiState.value = _uiState.value.copy(
                    item = if (refreshedProgress?.isComplete == true) {
                        currentItem.copy(isWatched = nextUnwatched == null)
                    } else currentItem,
                    episodes = updatedEpisodes,
                    seasonProgress = refreshedProgress?.takeIf { it.isComplete }?.progress ?: optimisticProgress,
                    episodePlaybackProgress = fetchEpisodePlaybackProgress(currentMediaId, updatedEpisodes),
                    playSeason = displayPlayTarget?.displaySeason ?: playTarget?.season,
                    playEpisode = displayPlayTarget?.displayEpisode ?: playTarget?.episode,
                    playTmdbSeason = playTarget?.season,
                    playTmdbEpisode = playTarget?.episode,
                    playLabel = displayPlayTarget?.let {
                        if (playTarget?.label == context.getString(R.string.play_start_s1e1)) {
                            context.getString(R.string.play_start_s1e1)
                        } else {
                            context.getString(R.string.continue_season_episode, it.displaySeason, it.displayEpisode)
                        }
                    } ?: playTarget?.label,
                    playPositionMs = playTarget?.positionMs,
                    toastMessage = context.getString(R.string.details_season_marked_watched, season),
                    toastType = ToastType.SUCCESS
                )
                runCatching { launcherContinueWatchingRepository.refreshForCurrentProfile() }
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(
                    toastMessage = context.getString(R.string.details_failed_mark_season_watched),
                    toastType = ToastType.ERROR
                )
            }
        }
    }

    fun markSeasonUnwatched(season: Int) {
        if (currentMediaType != MediaType.TV) return
        val currentItem = _uiState.value.item ?: return

        viewModelScope.launch {
            try {
                val seasonEpisodes = if (_uiState.value.currentSeason == season && _uiState.value.episodes.isNotEmpty()) {
                    _uiState.value.episodes
                } else {
                    animeSeasonStructure?.let { loadAnimeDisplaySeason(currentMediaId, season, it) }
                        ?: mediaRepository.getSeasonEpisodes(currentMediaId, season)
                }

                if (seasonEpisodes.isEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        toastMessage = context.getString(R.string.details_no_episodes_season, season),
                        toastType = ToastType.ERROR
                    )
                    return@launch
                }

                // OPTIMISTIC UPDATE: Update local state immediately
                val updatedEpisodes = if (_uiState.value.currentSeason == season) {
                    _uiState.value.episodes.map { ep ->
                        if (ep.seasonNumber == season) ep.copy(isWatched = false) else ep
                    }
                } else {
                    _uiState.value.episodes
                }
                val optimisticProgress = _uiState.value.seasonProgress.toMutableMap().apply {
                    this[season] = Pair(0, seasonEpisodes.size)
                }
                _uiState.value = _uiState.value.copy(
                    episodes = updatedEpisodes,
                    seasonProgress = optimisticProgress
                )

                val canonicalGroups = seasonEpisodes.groupBy { it.identity.tmdbSeason }
                val groupResults = canonicalGroups.map { (tmdbSeason, episodes) ->
                    async {
                        val tmdbEpisodes = episodes.map { it.identity.tmdbEpisode }
                        val batchRemoved = runCatching {
                            localWatchRepository.removeSeasonFromHistory(currentMediaId, tmdbSeason, tmdbEpisodes)
                        }.getOrDefault(false)
                        val perEpisode = episodes.map { ep ->
                            async {
                                runCatching {
                                    localWatchRepository.markEpisodeUnwatched(
                                        currentMediaId,
                                        ep.identity.tmdbSeason,
                                        ep.identity.tmdbEpisode
                                    )
                                }
                            }
                        }.map { it.await() }
                        batchRemoved || perEpisode.all { it.isSuccess }
                    }
                }.map { it.await() }

                if (groupResults.any { !it }) {
                    _uiState.value = _uiState.value.copy(
                        episodes = updatedEpisodes,
                        seasonProgress = optimisticProgress,
                        toastMessage = context.getString(R.string.details_failed_sync_season_unwatched, season),
                        toastType = ToastType.ERROR
                    )
                    return@launch
                }

                val refreshedProgress = runCatching { fetchSeasonProgress(currentMediaId) }.getOrNull()

                _uiState.value = _uiState.value.copy(
                    item = currentItem.copy(isWatched = false),
                    episodes = updatedEpisodes,
                    seasonProgress = refreshedProgress?.progress ?: optimisticProgress,
                    toastMessage = context.getString(R.string.details_season_marked_unwatched, season),
                    toastType = ToastType.SUCCESS
                )
                runCatching { launcherContinueWatchingRepository.refreshForCurrentProfile() }
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(
                    toastMessage = context.getString(R.string.details_failed_mark_season_unwatched),
                    toastType = ToastType.ERROR
                )
            }
        }
    }

    /**
     * Resolve real IMDB ID from TMDB using external_ids endpoint
     * This is required for addon stream resolution
     */
    /**
     * Fetch season progress for a TV show
     * Returns Map<seasonNumber, Pair<watchedCount, totalCount>>
     * Uses the local watched cache
     */
    private suspend fun fetchSeasonProgress(tmdbId: Int): SeasonProgressResult {
        return try {
            val cachedEpisodes = runCatching { localWatchRepository.getWatchedEpisodesFromCache() }.getOrDefault(emptySet())
            val cachedKeysForShow = cachedEpisodes.filter { it.startsWith("show_tmdb:$tmdbId:") }.toSet()

            val watchedKeys = if (cachedKeysForShow.isNotEmpty()) {
                cachedKeysForShow
            } else {
                runCatching { localWatchRepository.getWatchedEpisodesForShow(tmdbId) }.getOrDefault(emptySet())
            }

            val tvDetails = tmdbApi.getMergedTvDetails(tmdbId, Constants.TMDB_API_KEY)
            val seasonNumbers = tvDetails.seasons
                .asSequence()
                .filter { it.seasonNumber > 0 && it.episodeCount > 0 }
                .map { it.seasonNumber }
                .distinct()
                .sorted()
                .toList()
                .ifEmpty { (1..tvDetails.numberOfSeasons.coerceAtLeast(1)).toList() }

            val progressMap = mutableMapOf<Int, Pair<Int, Int>>()
            var nextUnwatched: Pair<Int, Int>? = null
            var nextUnaired: Pair<Int, Int>? = null
            var nextUnairedAirDate = ""
            var isComplete = true

            for (seasonNum in seasonNumbers) {
                try {
                    val seasonDetails = tmdbApi.getMergedTvSeason(tmdbId, seasonNum, Constants.TMDB_API_KEY)
                    val orderedEpisodes = seasonDetails.episodes.filter { it.seasonNumber == seasonNum }.sortedBy { it.episodeNumber }
                    if (orderedEpisodes.isEmpty()) isComplete = false
                    val airedEpisodes = orderedEpisodes.filter { episode ->
                        EpisodeAvailability.hasAired(episode.airDate)
                    }
                    val totalEpisodes = airedEpisodes.size

                    val watchedCount = airedEpisodes.count { episode ->
                        watchedKeys.contains("show_tmdb:$tmdbId:$seasonNum:${episode.episodeNumber}")
                    }
                    progressMap[seasonNum] = Pair(watchedCount, totalEpisodes)

                    if (nextUnwatched == null) {
                        val firstUnwatched = airedEpisodes.firstOrNull { episode ->
                            val key = "show_tmdb:$tmdbId:$seasonNum:${episode.episodeNumber}"
                            !watchedKeys.contains(key)
                        }
                        if (firstUnwatched != null) {
                            nextUnwatched = Pair(seasonNum, firstUnwatched.episodeNumber)
                        }
                    }
                    if (nextUnaired == null) {
                        val firstUnaired = orderedEpisodes.firstOrNull { episode ->
                            val key = "show_tmdb:$tmdbId:$seasonNum:${episode.episodeNumber}"
                            !EpisodeAvailability.hasAired(episode.airDate) && !watchedKeys.contains(key)
                        }
                        if (firstUnaired != null) {
                            nextUnaired = Pair(seasonNum, firstUnaired.episodeNumber)
                            nextUnairedAirDate = firstUnaired.airDate.orEmpty()
                        }
                    }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    isComplete = false
                }
            }

            val watchedCoordinates = watchedKeys.mapNotNull { key ->
                val parts = key.split(":")
                val season = parts.getOrNull(2)?.toIntOrNull() ?: return@mapNotNull null
                val episode = parts.getOrNull(3)?.toIntOrNull() ?: return@mapNotNull null
                season to episode
            }.toSet()
            val displayProgress = animeSeasonStructure
                ?.progressForCanonicalEpisodes(watchedCoordinates)
                ?: progressMap

            SeasonProgressResult(
                progress = displayProgress,
                hasWatched = progressMap.values.any { it.first > 0 },
                nextUnwatched = nextUnwatched,
                nextUnaired = nextUnaired,
                nextUnairedAirDate = nextUnairedAirDate,
                isComplete = isComplete,
            )
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            SeasonProgressResult(emptyMap(), false, null, isComplete = false)
        }
    }

    private suspend fun fetchResumeInfo(
        tmdbId: Int,
        mediaType: MediaType,
        preferredSeason: Int? = null,
        preferredEpisode: Int? = null
    ): ResumeInfo? {
        return try {
            val exactEntry = if (mediaType == MediaType.TV && preferredSeason != null && preferredEpisode != null) {
                watchHistoryRepository.getProgress(mediaType, tmdbId, preferredSeason, preferredEpisode)
            } else {
                null
            }
            val watchedEpisodeKeys = if (mediaType == MediaType.TV) {
                val prefix = "show_tmdb:$tmdbId:"
                val cached = runCatching {
                    localWatchRepository.getWatchedEpisodesFromCache()
                        .filter { it.startsWith(prefix) }
                        .toSet()
                }.getOrDefault(emptySet())
                if (cached.isNotEmpty()) {
                    cached
                } else {
                    runCatching { localWatchRepository.getWatchedEpisodesForShow(tmdbId) }.getOrDefault(emptySet())
                }
            } else {
                emptySet()
            }
            fun ResumeInfo?.dropIfWatchedEpisode(): ResumeInfo? {
                val info = this ?: return null
                if (mediaType != MediaType.TV) return info
                val s = info.season ?: return null
                val e = info.episode ?: return null
                val key = "show_tmdb:$tmdbId:$s:$e"
                return if (watchedEpisodeKeys.contains(key)) null else info
            }

            val entry = exactEntry ?: watchHistoryRepository.getLatestProgress(mediaType, tmdbId)
            val cloudResume = if (entry != null) {
                buildResumeFromProgress(
                    mediaType = mediaType,
                    tmdbId = tmdbId,
                    season = entry.season,
                    episode = entry.episode,
                    progress = entry.progress,
                    positionSeconds = entry.position_seconds,
                    durationSeconds = entry.duration_seconds
                ).dropIfWatchedEpisode()
            } else null

            val localItem = runCatching {
                localWatchRepository.getLocalContinueWatchingEntry(
                    mediaType = mediaType,
                    tmdbId = tmdbId,
                    season = preferredSeason ?: entry?.season,
                    episode = preferredEpisode ?: entry?.episode
                )
            }.getOrNull()
            val localFallbackItem = if (localItem == null && preferredSeason == null && preferredEpisode == null) {
                runCatching {
                    localWatchRepository.getBestLocalContinueWatchingEntry(
                        mediaType = mediaType,
                        tmdbId = tmdbId
                    )
                }.getOrNull()
            } else {
                null
            }

            // Which episode to resume is decided by the most recent saved activity.
            val localCandidates = listOfNotNull(localItem, localFallbackItem)
            val resumeCandidate = localCandidates
                .maxByOrNull { it.updatedAtMs }

            // Where to resume: a saved real position for the same episode wins.
            val exactSource = localCandidates.firstOrNull { local ->
                local.resumePositionSeconds > 0L &&
                    local.season == resumeCandidate?.season &&
                    local.episode == resumeCandidate?.episode
            }
            val localResume = if (resumeCandidate != null) {
                buildResumeFromProgress(
                    mediaType = mediaType,
                    tmdbId = tmdbId,
                    season = resumeCandidate.season,
                    episode = resumeCandidate.episode,
                    progress = resumeCandidate.progress / 100f,
                    positionSeconds = exactSource?.resumePositionSeconds
                        ?: resumeCandidate.resumePositionSeconds,
                    durationSeconds = maxOf(
                        exactSource?.durationSeconds ?: 0L,
                        resumeCandidate.durationSeconds
                    ),
                    allowProgressDerivedResume = !resumeCandidate.isUpNext
                ).dropIfWatchedEpisode()
            } else null

            when {
                preferredSeason != null && preferredEpisode != null -> {
                    // Only use resume data that matches the requested episode.
                    // Never let a stale entry from a different episode bleed through.
                    val matchLocal = localResume?.takeIf { it.season == preferredSeason && it.episode == preferredEpisode }
                    val matchCloud = cloudResume?.takeIf { it.season == preferredSeason && it.episode == preferredEpisode }
                    matchLocal ?: matchCloud
                }
                cloudResume == null -> localResume
                localResume == null -> cloudResume
                // When both exist, prefer the one for the later episode; if same episode, prefer higher position
                localResume.season != null && cloudResume.season != null && localResume.episode != null && cloudResume.episode != null -> {
                    val localEp = localResume.season!! * 10000 + localResume.episode!!
                    val cloudEp = cloudResume.season!! * 10000 + cloudResume.episode!!
                    if (localEp > cloudEp) localResume
                    else if (cloudEp > localEp) cloudResume
                    else if (localResume.positionMs > cloudResume.positionMs) localResume
                    else cloudResume
                }
                localResume.positionMs > cloudResume.positionMs -> localResume
                else -> cloudResume
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            null
        }
    }

    private suspend fun fetchResumeInfoFromHistoryOnly(tmdbId: Int, mediaType: MediaType): ResumeInfo? {
        return try {
            val entry = watchHistoryRepository.getLatestProgress(mediaType, tmdbId) ?: return null
            if (mediaType == MediaType.TV && entry.season != null && entry.episode != null) {
                val watchedKey = "show_tmdb:$tmdbId:${entry.season}:${entry.episode}"
                val isWatched = try {
                    localWatchRepository.getWatchedEpisodesFromCache().contains(watchedKey) ||
                        localWatchRepository.getWatchedEpisodesForShow(tmdbId).contains(watchedKey)
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    false
                }
                if (isWatched) return null
            }
            buildResumeFromProgress(
                mediaType = mediaType,
                tmdbId = tmdbId,
                season = entry.season,
                episode = entry.episode,
                progress = entry.progress,
                positionSeconds = entry.position_seconds,
                durationSeconds = entry.duration_seconds
            )
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            null
        }
    }

    private suspend fun buildResumeFromProgress(
        mediaType: MediaType,
        tmdbId: Int,
        season: Int?,
        episode: Int?,
        progress: Float,
        positionSeconds: Long,
        durationSeconds: Long,
        allowProgressDerivedResume: Boolean = true
    ): ResumeInfo? {
        val normalizedDuration = if (durationSeconds > 86_400L) durationSeconds / 1000L else durationSeconds
        val normalizedPosition = if (positionSeconds > 86_400L) positionSeconds / 1000L else positionSeconds
        val normalizedProgress = progress.coerceIn(0f, 1f)
        val minPlaybackProgress = Constants.MIN_PROGRESS_THRESHOLD / 100f

        // If both position and duration are 0, this entry has no real playback data.
        // The progress field may be a show-level synthetic value (% of episodes watched),
        // NOT an episode playback percentage. Don't derive a fake resume position from it.
        if (normalizedPosition <= 0L && normalizedDuration <= 0L) {
            return null
        }

        // TV "up next" placeholders intentionally point at the next episode with
        // little/no progress. Older builds could accidentally attach the previous
        // episode's position to that placeholder, causing labels like
        // "Continue S4E4 at 23:16" for a never-started episode.
        if (
            mediaType == MediaType.TV &&
            season != null &&
            episode != null &&
            normalizedPosition > 0L &&
            (
                (normalizedProgress <= minPlaybackProgress && normalizedDuration < 60L) ||
                    (normalizedDuration in 1L..59L && normalizedPosition >= 60L)
            )
        ) {
            return null
        }

        var seconds = when {
            normalizedPosition > 0 -> normalizedPosition
            allowProgressDerivedResume && normalizedDuration > 0 && normalizedProgress > 0f ->
                (normalizedDuration * normalizedProgress).toLong()
            else -> 0L
        }
        if (normalizedDuration > 0L && seconds > 0L) {
            seconds = seconds.coerceIn(1L, normalizedDuration.coerceAtLeast(1L))
        }
        if (seconds <= 0L) return null
        val timeLabel = formatResumeTime(seconds)
        if (timeLabel.isBlank()) return null

        return if (mediaType == MediaType.MOVIE) {
            ResumeInfo(
                label = context.getString(R.string.continue_at, timeLabel),
                positionMs = seconds * 1000L
            )
        } else {
            val s = season ?: return null
            val e = episode ?: return null
            ResumeInfo(
                season = s,
                episode = e,
                label = context.getString(R.string.continue_season_episode_at, s, e, timeLabel),
                positionMs = seconds * 1000L
            )
        }
    }

    private suspend fun resolveRuntimeSeconds(
        tmdbId: Int,
        mediaType: MediaType,
        season: Int?,
        episode: Int?
    ): Long {
        return try {
            if (mediaType == MediaType.MOVIE) {
                val details = tmdbApi.getMovieDetails(tmdbId, Constants.TMDB_API_KEY)
                (details.runtime ?: 0) * 60L
            } else {
                val details = tmdbApi.getMergedTvDetails(tmdbId, Constants.TMDB_API_KEY)
                val avgRuntime = details.episodeRunTime.firstOrNull() ?: 0
                if (avgRuntime > 0) {
                    avgRuntime * 60L
                } else {
                    val s = season ?: return 0L
                    val e = episode ?: return 0L
                    val seasonDetails = tmdbApi.getMergedTvSeason(tmdbId, s, Constants.TMDB_API_KEY)
                    val episodeRuntime = seasonDetails.episodes.firstOrNull { it.episodeNumber == e }?.runtime
                        ?: seasonDetails.episodes.firstOrNull { it.runtime != null }?.runtime
                        ?: 0
                    episodeRuntime * 60L
                }
            }
        } catch (_: Exception) {
            0L
        }
    }

    private fun formatResumeTime(seconds: Long): String {
        val total = seconds.coerceAtLeast(0)
        val hours = total / 3600
        val minutes = (total % 3600) / 60
        val secs = total % 60
        return if (hours > 0) {
            "%d:%02d:%02d".format(hours, minutes, secs)
        } else {
            "%d:%02d".format(minutes, secs)
        }
    }

    private fun buildPlayTarget(
        mediaType: MediaType,
        result: SeasonProgressResult?,
        resumeInfo: ResumeInfo?
    ): PlayTarget? {
        if (resumeInfo != null) {
            return PlayTarget(
                season = resumeInfo.season,
                episode = resumeInfo.episode,
                label = resumeInfo.label,
                positionMs = resumeInfo.positionMs
            )
        }
        if (mediaType == MediaType.MOVIE) return null
        if (result == null) return null
        if (!result.isComplete) {
            val state = _uiState.value
            return PlayTarget(
                season = state.playTmdbSeason ?: state.playSeason,
                episode = state.playTmdbEpisode ?: state.playEpisode,
                label = state.playLabel.orEmpty(),
                positionMs = state.playPositionMs,
            )
        }
        return if (!result.hasWatched) {
            val firstAired = result.nextUnwatched ?: return null
            PlayTarget(
                season = firstAired.first,
                episode = firstAired.second,
                label = if (firstAired == (1 to 1)) {
                    context.getString(R.string.play_start_s1e1)
                } else {
                    context.getString(R.string.play_season_episode, firstAired.first, firstAired.second)
                }
            )
        } else {
            val next = result.nextUnwatched
            if (next != null) {
                PlayTarget(
                    season = next.first,
                    episode = next.second,
                    label = context.getString(R.string.continue_season_episode, next.first, next.second)
                )
            } else {
                null
            }
        }
    }

    private data class ExternalIds(val imdbId: String?, val tvdbId: Int?)

    private suspend fun resolveExternalIds(mediaType: MediaType, mediaId: Int): ExternalIds {
        if (mediaType == MediaType.TV) ShowMergeTable.entry(mediaId)?.let { return ExternalIds(it.imdbId, null) }
        // A native addon item's own id stands in for the IMDb id: streams are requested by it.
        if (mediaId < 0) return ExternalIds(mediaRepository.getCachedImdbId(mediaType, mediaId), null)
        return try {
            val ids = when (mediaType) {
                MediaType.MOVIE -> tmdbApi.getMovieExternalIds(mediaId, Constants.TMDB_API_KEY)
                MediaType.TV -> tmdbApi.getTvExternalIds(mediaId, Constants.TMDB_API_KEY)
            }
            ExternalIds(imdbId = ids.imdbId, tvdbId = ids.tvdbId)
        } catch (_: Exception) {
            ExternalIds(null, null)
        }
    }















    /**
     * Ends the "searching sources" state once the last source job came back
     * empty-handed.
     *
     * The caller passes itself as [ignoring]: a job is still marked active
     * while its own last lines run, and it must not read itself as a reason to
     * keep waiting.
     */
    private fun stopStreamSpinnerIfNothingLeft(ignoring: kotlinx.coroutines.Job?) {
        val state = _uiState.value
        val stop = shouldStopStreamSpinner(
            isLoadingStreams = state.isLoadingStreams,
            hasStreams = state.streams.isNotEmpty(),
            otherSourceJobsActive = hasOtherActiveStreamJobs(
                listOf(loadStreamsJob), ignoring
            )
        )
        if (!stop) return
        _uiState.value = state.copy(isLoadingStreams = false)
    }
}

/**
 * Whether a source job that came back with nothing may switch the
 * "searching sources" spinner off.
 *
 * It may not do so on its own account: the other supplemental job, the addon
 * resolution can all still be working, and a spinner
 * taken away while sources are on their way reads as "nothing found". But
 * each completed source job checks whether the remaining work is finished.
 */
internal fun hasOtherActiveStreamJobs(jobs: List<Job?>, finishingJob: Job?): Boolean =
    jobs.any { job -> job !== finishingJob && job?.isActive == true }

internal fun shouldStopStreamSpinner(
    isLoadingStreams: Boolean,
    hasStreams: Boolean,
    otherSourceJobsActive: Boolean
): Boolean = isLoadingStreams &&
    !hasStreams &&
    !otherSourceJobsActive

/**
 * Whether this setup has any source of streams at all.
 *
 * Determines which empty state the source picker shows.
 */
internal fun hasAnyStreamProvider(
    streamingAddonCount: Int,
): Boolean = streamingAddonCount > 0
