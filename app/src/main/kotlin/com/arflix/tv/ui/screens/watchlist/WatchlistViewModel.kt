package com.arflix.tv.ui.screens.watchlist

import android.content.Context
import coil.imageLoader
import coil.request.ImageRequest
import coil.size.Precision
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arflix.tv.R
import com.arflix.tv.data.model.CatalogConfig
import com.arflix.tv.data.model.CatalogKind
import com.arflix.tv.data.model.CatalogSourceType
import com.arflix.tv.data.model.MediaItem
import com.arflix.tv.data.model.MediaType.MOVIE
import com.arflix.tv.data.model.MediaType.TV
import com.arflix.tv.data.repository.CatalogRepository
import com.arflix.tv.data.repository.MediaRepository
import com.arflix.tv.data.repository.ProfileManager
import com.arflix.tv.data.repository.LocalWatchRepository
import com.arflix.tv.data.repository.WatchHistoryEntry
import com.arflix.tv.data.repository.WatchHistoryRepository
import com.arflix.tv.data.repository.WatchlistRepository
import com.arflix.tv.util.AppLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import java.util.Locale

enum class ToastType {
    SUCCESS, ERROR, INFO
}

sealed interface WatchlistSourceItem {
    val id: String
    val title: String
    val subtitle: String?
    val displayLabel: String

    data object MyWatchlist : WatchlistSourceItem {
        override val id: String = "my_watchlist"
        override val title: String = "My watchlist"
        override val subtitle: String? = null
        override val displayLabel: String = "My watchlist"
    }

    data class Catalog(
        val config: CatalogConfig
    ) : WatchlistSourceItem {
        override val id: String = "catalog_${config.id}"
        override val title: String = config.title
        override val subtitle: String? = when {
            config.sourceType == CatalogSourceType.TMDB -> "TMDB"
            config.sourceType == CatalogSourceType.ADDON -> config.addonName ?: "Addon"
            else -> null
        }
        override val displayLabel: String = if (subtitle != null && !title.startsWith(subtitle, ignoreCase = true)) {
            "$subtitle / $title"
        } else {
            title
        }
    }



}

data class WatchlistUiState(
    val sources: List<WatchlistSourceItem> = listOf(WatchlistSourceItem.MyWatchlist),
    val selectedSourceId: String = WatchlistSourceItem.MyWatchlist.id,
    val isLoading: Boolean = true,
    val movies: List<MediaItem> = emptyList(),
    val series: List<MediaItem> = emptyList(),
    val error: String? = null,
    val toastMessage: String? = null,
    val toastType: ToastType = ToastType.INFO,
    val lastFocusedSectionIndex: Int = 0,
    val lastFocusedItemIndex: Int = 0,
    val hasMore: Boolean = false,
    val isLoadingMore: Boolean = false
) {
    val isEmpty: Boolean get() = movies.isEmpty() && series.isEmpty()
    val allItems: List<MediaItem> get() = movies + series
    val selectedSource: WatchlistSourceItem get() = sources.firstOrNull { it.id == selectedSourceId } ?: WatchlistSourceItem.MyWatchlist
}


internal fun watchlistLogoKey(item: MediaItem): String =
    if (item.isAddonNative) "addon:${item.addonNativeAddonId}:${item.addonNativeId}:${item.mediaType.name}"
    else "tmdb:${item.mediaType.name}:${item.id}"

internal fun watchlistItemKey(item: MediaItem, index: Int): String = "${watchlistLogoKey(item)}:$index"

private data class SourcePageState(
    val hasMore: Boolean = false,
    val isLoadingMore: Boolean = false,
    val nextOffset: Int = 0
)

internal fun buildWatchlistSources(
    catalogs: List<CatalogConfig>,
 ): List<WatchlistSourceItem> {
    val customCatalogs = catalogs.filter { config ->
        !config.isPreinstalled && config.sourceType != CatalogSourceType.PREINSTALLED
    }.map { WatchlistSourceItem.Catalog(it) }
    return listOf<WatchlistSourceItem>(WatchlistSourceItem.MyWatchlist) + customCatalogs
        .distinctBy(WatchlistSourceItem::id)
}

@HiltViewModel
class WatchlistViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val watchlistRepository: WatchlistRepository,
    private val localWatchRepository: LocalWatchRepository,
    private val mediaRepository: MediaRepository,
    private val catalogRepository: CatalogRepository,
    private val watchHistoryRepository: WatchHistoryRepository,
    private val profileManager: ProfileManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(WatchlistUiState())
    val uiState: StateFlow<WatchlistUiState> = _uiState.asStateFlow()

    private val _logoUrls = MutableStateFlow<Map<String, String>>(emptyMap())
    val logoUrls: StateFlow<Map<String, String>> = _logoUrls.asStateFlow()

    private val sourceItemsCache = linkedMapOf<String, List<MediaItem>>()
    private val sourcePageStates = mutableMapOf<String, SourcePageState>()

    private var currentCatalogs: List<CatalogConfig> = emptyList()
    private var sourceLoadJob: Job? = null
    private var sourceLoadMoreJob: Job? = null
    private var initialLoadComplete = false
    private var enrichmentInFlight = false
    private var enrichmentRequested = false
    private val logoRequestsInFlight = mutableSetOf<String>()
    private val logoPermits = Semaphore(5)

    private fun watchlistDiagnosticContext(
        phase: String,
        extra: Map<String, String> = emptyMap()
    ): Map<String, String> = mutableMapOf(
        "error_area" to "Watchlist",
        "watchlist_phase" to phase,
        "visible_count" to _uiState.value.allItems.size.toString()
    ).apply { putAll(extra) }

    private fun List<MediaItem>.watchlistDisplayOrder(): List<MediaItem> {
        return sortedWith(
            compareBy<MediaItem> { it.sourceOrder }
                .thenByDescending { it.addedAt }
        )
    }

    private fun List<MediaItem>.needsArtworkEnrichment(): Boolean {
        return any { item -> item.image.isBlank() && item.backdrop.isNullOrBlank() }
    }

    init {
        observeWatchlistChanges()
        observeCatalogs()
        loadWatchlistInstant()
    }

    private fun observeWatchlistChanges() {
        viewModelScope.launch {
            watchlistRepository.watchlistItems.collect { items ->
                if (_uiState.value.selectedSourceId == WatchlistSourceItem.MyWatchlist.id) {
                    val orderedItems = items.watchlistDisplayOrder().enrichWithPlaybackProgress()
                    sourceItemsCache[WatchlistSourceItem.MyWatchlist.id] = orderedItems
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        movies = orderedItems.filter { it.mediaType == MOVIE },
                        series = orderedItems.filter { it.mediaType == TV },
                        error = null
                    )
                    fetchLogos(orderedItems)
                }
            }
        }
    }

    private fun observeCatalogs() {
        viewModelScope.launch {
            catalogRepository.observeCatalogs().collect { catalogs ->
                updateAvailableSources(catalogs = catalogs)
            }
        }

    }


    private fun updateAvailableSources(
        catalogs: List<CatalogConfig>? = null
    ) {
        if (catalogs != null) currentCatalogs = catalogs

        val allSources = buildWatchlistSources(
            catalogs = currentCatalogs,
        )
        val previousSources = _uiState.value.sources.associateBy { it.id }
        val changedSourceIds = allSources.mapNotNull { source ->
            source.id.takeIf { previousSources[source.id] != source }
        }.toSet()
        changedSourceIds.forEach { sourceId ->
            sourceItemsCache.remove(sourceId)
            sourcePageStates.remove(sourceId)
        }
        val currentSelectedId = _uiState.value.selectedSourceId
        val wasValid = allSources.any { it.id == currentSelectedId }
        val validSelectedId = if (wasValid) currentSelectedId else WatchlistSourceItem.MyWatchlist.id

        _uiState.value = _uiState.value.copy(
            sources = allSources,
            selectedSourceId = validSelectedId
        )

        if ((!wasValid && currentSelectedId != WatchlistSourceItem.MyWatchlist.id) || validSelectedId in changedSourceIds) {
            loadActiveSourceItems()
        }
    }

    fun selectSource(sourceId: String) {
        if (_uiState.value.selectedSourceId == sourceId) return
        sourceLoadJob?.cancel()
        sourceLoadMoreJob?.cancel()
        val cached = sourceItemsCache[sourceId]
        val pageState = sourcePageStates[sourceId] ?: SourcePageState()
        _uiState.value = _uiState.value.copy(
            selectedSourceId = sourceId,
            isLoading = cached == null,
            movies = cached?.filter { it.mediaType == MOVIE } ?: emptyList(),
            series = cached?.filter { it.mediaType == TV } ?: emptyList(),
            error = null,
            lastFocusedSectionIndex = 0,
            lastFocusedItemIndex = 0,
            hasMore = pageState.hasMore,
            isLoadingMore = false
        )
        loadActiveSourceItems()
    }

    fun saveFocusState(sectionIndex: Int, itemIndex: Int) {
        _uiState.value = _uiState.value.copy(
            lastFocusedSectionIndex = sectionIndex,
            lastFocusedItemIndex = itemIndex
        )
    }

    private fun loadActiveSourceItems(forceRefresh: Boolean = false) {
        val activeSource = _uiState.value.selectedSource
        val cacheKey = activeSource.id
        val cached = sourceItemsCache[cacheKey]
        if (forceRefresh) {
            sourceLoadMoreJob?.cancel()
            sourcePageStates[cacheKey] = sourcePageStates[cacheKey]?.copy(isLoadingMore = false)
                ?: SourcePageState()
        }

        if (cached != null && !forceRefresh) {
            val pageState = sourcePageStates[cacheKey] ?: SourcePageState()
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                movies = cached.filter { it.mediaType == MOVIE },
                series = cached.filter { it.mediaType == TV },
                error = null,
                hasMore = pageState.hasMore,
                isLoadingMore = false
            )
            fetchLogos(cached)
            return
        }

        when (activeSource) {
            is WatchlistSourceItem.MyWatchlist -> {
                _uiState.value = _uiState.value.copy(
                    isLoading = cached == null,
                    error = null,
                    hasMore = false,
                    isLoadingMore = false
                )
                sourceLoadJob = viewModelScope.launch {
                    val local = watchlistRepository.getLocalWatchlistItems().watchlistDisplayOrder().enrichWithPlaybackProgress()
                    sourceItemsCache[cacheKey] = local
                    if (_uiState.value.selectedSourceId == WatchlistSourceItem.MyWatchlist.id) {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            movies = local.filter { it.mediaType == MOVIE },
                            series = local.filter { it.mediaType == TV },
                            error = null
                        )
                        fetchLogos(local)
                        if (local.needsArtworkEnrichment()) enrichLocalWatchlistInBackground()
                    }
                }
            }
            is WatchlistSourceItem.Catalog -> {
                _uiState.value = _uiState.value.copy(
                    isLoading = cached == null,
                    error = null,
                    hasMore = false,
                    isLoadingMore = false
                )
                sourceLoadJob = viewModelScope.launch {
                    runCatching {
                        mediaRepository.loadCustomCatalogPage(activeSource.config, offset = 0, limit = LIBRARY_PAGE_SIZE)
                    }.onSuccess { page ->
                        val items = page.items.enrichWithPlaybackProgress()
                        sourcePageStates[cacheKey] = SourcePageState(hasMore = page.hasMore, nextOffset = page.nextOffset ?: page.items.size)
                        sourceItemsCache[cacheKey] = items
                        if (_uiState.value.selectedSourceId == activeSource.id) {
                            _uiState.value = _uiState.value.copy(
                                isLoading = false,
                                hasMore = page.hasMore,
                                movies = items.filter { it.mediaType == MOVIE },
                                series = items.filter { it.mediaType == TV },
                                error = null
                            )
                            fetchLogos(items)
                        }
                    }.onFailure { error ->
                        if (_uiState.value.selectedSourceId == activeSource.id) {
                            _uiState.value = _uiState.value.copy(
                                isLoading = false,
                                error = error.message ?: context.getString(R.string.watchlist_error_refresh)
                            )
                        }
                    }
                }
            }

        }
    }

    fun loadMoreActiveSource() {
        val activeSource = _uiState.value.selectedSource
        if (activeSource !is WatchlistSourceItem.Catalog) return
        val cacheKey = activeSource.id
        val pageState = sourcePageStates[cacheKey] ?: return
        val currentItems = sourceItemsCache[cacheKey].orEmpty()
        if (!pageState.hasMore || pageState.isLoadingMore) return

        sourcePageStates[cacheKey] = pageState.copy(isLoadingMore = true)
        if (_uiState.value.selectedSourceId == cacheKey) {
            _uiState.value = _uiState.value.copy(isLoadingMore = true)
        }
        sourceLoadMoreJob?.cancel()
        sourceLoadMoreJob = viewModelScope.launch {
            runCatching {
                mediaRepository.loadCustomCatalogPage(activeSource.config, pageState.nextOffset, LIBRARY_PAGE_SIZE)
            }.onSuccess { page ->
                val existingKeys = currentItems.mapTo(HashSet(), ::watchlistLogoKey)
                val freshItems = page.items.filter { item ->
                    watchlistLogoKey(item) !in existingKeys
                }.enrichWithPlaybackProgress()
                val mergedItems = currentItems + freshItems
                sourceItemsCache[cacheKey] = mergedItems
                sourcePageStates[cacheKey] = SourcePageState(
                    hasMore = page.hasMore && (page.nextOffset ?: (pageState.nextOffset + page.items.size)) > pageState.nextOffset,
                    nextOffset = page.nextOffset ?: (pageState.nextOffset + page.items.size)
                )
                if (_uiState.value.selectedSourceId == cacheKey) {
                    _uiState.value = _uiState.value.copy(
                        movies = mergedItems.filter { it.mediaType == MOVIE },
                        series = mergedItems.filter { it.mediaType == TV },
                        hasMore = page.hasMore && (page.nextOffset ?: (pageState.nextOffset + page.items.size)) > pageState.nextOffset,
                        isLoadingMore = false,
                        error = null
                    )
                    fetchLogos(freshItems.take(LIBRARY_LOGO_INITIAL_PREFETCH))
                }
            }.onFailure { error ->
                if (error is CancellationException) throw error
                sourcePageStates[cacheKey] = pageState.copy(isLoadingMore = false)
                if (_uiState.value.selectedSourceId == cacheKey) {
                    _uiState.value = _uiState.value.copy(
                        isLoadingMore = false,
                        error = error.message ?: context.getString(R.string.watchlist_error_refresh),
                        toastMessage = error.message ?: context.getString(R.string.watchlist_error_refresh),
                        toastType = ToastType.ERROR
                    )
                }
            }
        }
    }

    private suspend fun List<MediaItem>.enrichWithPlaybackProgress(): List<MediaItem> {
        return runCatching {
            val watchHistory = watchHistoryRepository.getWatchHistory()
            if (watchHistory.isEmpty()) return@runCatching this

            val historyByKey = mutableMapOf<String, WatchHistoryEntry>()
            for (entry in watchHistory) {
                val key = "${entry.media_type}:${entry.show_tmdb_id}"
                if (!historyByKey.containsKey(key)) {
                    historyByKey[key] = entry
                }
            }

            map { item ->
                val typeStr = if (item.mediaType == TV) "tv" else "movie"
                val history = historyByKey["$typeStr:${item.id}"]
                if (history != null) {
                    val progressPercent = (history.progress * 100).toInt().coerceIn(0, 100)
                    val isWatched = progressPercent >= 90 || history.progress >= 0.9f
                    val showProgress = progressPercent in 1..89 && !isWatched
                    item.copy(
                        progress = progressPercent,
                        isWatched = isWatched,
                        showPlaybackProgress = showProgress
                    )
                } else {
                    item
                }
            }
        }.getOrDefault(this)
    }

    private fun fetchLogos(items: List<MediaItem>) = prefetchLogos(items.take(LIBRARY_LOGO_INITIAL_PREFETCH))

    fun prefetchLogos(items: List<MediaItem>) {
        items.distinctBy(::watchlistLogoKey).forEach { item ->
            val key = watchlistLogoKey(item)
            if (key in _logoUrls.value || !logoRequestsInFlight.add(key)) return@forEach
            viewModelScope.launch {
                try {
                    val url = logoPermits.withPermit { mediaRepository.getLogoUrl(item) }
                    if (url != null) {
                        _logoUrls.value = _logoUrls.value + (key to url)
                        val density = context.resources.displayMetrics.density
                        val width = (220 * density).toInt().coerceAtLeast(1)
                        val height = (64 * density).toInt().coerceAtLeast(1)
                        context.imageLoader.enqueue(ImageRequest.Builder(context).data(url)
                            .size(width, height).precision(Precision.INEXACT).allowHardware(true)
                            .memoryCacheKey("$url|${width}x$height").build())
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    // Transient failures remain retryable on the next viewport visit.
                } finally {
                    logoRequestsInFlight.remove(key)
                }
            }
        }
    }

    fun ensureLogo(item: MediaItem) = prefetchLogos(listOf(item))

    private val libraryPreviews = linkedMapOf<String, LibraryPreview>()


    internal suspend fun loadLibraryPreview(item: MediaItem): LibraryPreview {
        val profile = profileManager.getProfileIdSync()
        val key = "$profile:${libraryPreviewKey(item)}"
        libraryPreviews[key]?.let { return it.copy(item = mergeLibraryPreview(item, it.item)) }
        val details = if (item.id > 0) try {
            when (item.mediaType) {
                MOVIE -> mediaRepository.getMovieDetails(item.id)
                TV -> mediaRepository.getTvDetails(item.id)
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { null } else null
        val preview = mergeLibraryPreview(item, details)
        val provider = if (item.id > 0) try {
            mediaRepository.getStreamingServices(item.mediaType, item.id,
                preferredRegion = java.util.Locale.getDefault().country)?.services?.firstOrNull()?.logoUrl
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { null } else item.primaryNetworkLogo
        val result = LibraryPreview(preview, provider)
        if (profileManager.getProfileIdSync() == profile && (details != null || item.id <= 0)) {
            if (libraryPreviews.size >= 80) libraryPreviews.remove(libraryPreviews.keys.first())
            libraryPreviews[key] = result
        }
        return result
    }

    private val previewPermits = Semaphore(2)

    /** A visible collection requests one cover without changing the active list. */
    suspend fun collectionCover(source: WatchlistSourceItem): String? = previewPermits.withPermit {
        sourceItemsCache[source.id]?.firstOrNull()?.let { it.backdrop ?: it.image }
            ?: try {
                val first = when(source) {
                    is WatchlistSourceItem.Catalog -> mediaRepository.loadCustomCatalog(source.config, maxItems = 1)?.items?.firstOrNull()
                    else -> null
                }
                first?.let { it.backdrop ?: it.image }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { null }
    }

    private fun loadWatchlistInstant() {
        sourceLoadJob = viewModelScope.launch {
            val items = watchlistRepository.getLocalWatchlistItems().watchlistDisplayOrder().enrichWithPlaybackProgress()
            sourceItemsCache[WatchlistSourceItem.MyWatchlist.id] = items
            _uiState.value = _uiState.value.copy(isLoading = false,
                movies = items.filter { it.mediaType == MOVIE }, series = items.filter { it.mediaType == TV })
            fetchLogos(items)
            initialLoadComplete = true
            enrichLocalWatchlistInBackground()
        }
    }

    private fun enrichLocalWatchlistInBackground() {
        if (enrichmentInFlight) {
            enrichmentRequested = true
            return
        }
        enrichmentInFlight = true
        viewModelScope.launch {
            try {
                do {
                    enrichmentRequested = false
                    val enrichedItems = watchlistRepository.refreshWatchlistItems().watchlistDisplayOrder().enrichWithPlaybackProgress()
                    if (_uiState.value.selectedSourceId == WatchlistSourceItem.MyWatchlist.id) {
                        sourceItemsCache[WatchlistSourceItem.MyWatchlist.id] = enrichedItems
                        if (enrichedItems.isNotEmpty()) {
                            _uiState.value = _uiState.value.copy(
                                isLoading = false,
                                movies = enrichedItems.filter { it.mediaType == MOVIE },
                                series = enrichedItems.filter { it.mediaType == TV }
                            )
                            fetchLogos(enrichedItems)
                        } else if (_uiState.value.isLoading) {
                            _uiState.value = _uiState.value.copy(isLoading = false)
                        }
                    }
                } while (enrichmentRequested)
            } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                AppLogger.recordException(
                    throwable = error,
                    context = watchlistDiagnosticContext("background_enrich")
                )
                if (_uiState.value.isLoading && _uiState.value.selectedSourceId == WatchlistSourceItem.MyWatchlist.id) {
                    val fallbackItems = watchlistRepository.getLocalWatchlistItems().watchlistDisplayOrder().enrichWithPlaybackProgress()
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        movies = fallbackItems.filter { it.mediaType == MOVIE },
                        series = fallbackItems.filter { it.mediaType == TV }
                    )
                }
            } finally {
                enrichmentInFlight = false
            }
        }
    }

    fun refresh() {
        if (_uiState.value.selectedSourceId != WatchlistSourceItem.MyWatchlist.id) {
            loadActiveSourceItems(forceRefresh = true)
        } else enrichLocalWatchlistInBackground()
    }

    fun refreshAfterResume() {
        if (initialLoadComplete) refresh()
    }

    fun removeFromWatchlist(item: MediaItem) {
        if (_uiState.value.selectedSourceId != WatchlistSourceItem.MyWatchlist.id) return
        viewModelScope.launch {
            try {
                watchlistRepository.removeFromWatchlist(item.mediaType, item.id)

                val current = _uiState.value
                val updatedMovies = current.movies.filter { it.id != item.id || it.mediaType != item.mediaType }
                val updatedSeries = current.series.filter { it.id != item.id || it.mediaType != item.mediaType }
                sourceItemsCache[WatchlistSourceItem.MyWatchlist.id] = updatedMovies + updatedSeries

                _uiState.value = current.copy(
                    movies = updatedMovies,
                    series = updatedSeries,
                    toastMessage = context.getString(R.string.watchlist_toast_removed),
                    toastType = ToastType.SUCCESS
                )
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e

                AppLogger.recordException(
                    throwable = e,
                    context = watchlistDiagnosticContext(
                        phase = "remove",
                        extra = mapOf("media_type" to item.mediaType.name.lowercase())
                    )
                )
                _uiState.value = _uiState.value.copy(
                    toastMessage = context.getString(R.string.watchlist_failed_remove),
                    toastType = ToastType.ERROR
                )
            }
        }
    }

    fun dismissToast() {
        _uiState.value = _uiState.value.copy(toastMessage = null)
    }

    companion object {
        private const val LIBRARY_PAGE_SIZE = 60
        private const val LIBRARY_LOGO_INITIAL_PREFETCH = 12
        private const val LIBRARY_CACHE_ENTRY_LIMIT = 12
        private val BROWSABLE_LIBRARY_TYPES = setOf(
            "",
            "movie",
            "movies",
            "show",
            "shows",
            "series",
            "tvshows",
            "mixed"
        )
    }
}
