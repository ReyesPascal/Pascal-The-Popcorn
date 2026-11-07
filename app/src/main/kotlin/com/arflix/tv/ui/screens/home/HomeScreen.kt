@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.arflix.tv.ui.screens.home

import androidx.activity.compose.BackHandler
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import com.arflix.tv.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import android.os.SystemClock
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.arflix.tv.data.model.Category
import com.arflix.tv.data.model.MediaItem
import com.arflix.tv.data.model.MediaType
import com.arflix.tv.ui.components.movieGenreNameRes
import com.arflix.tv.ui.components.tvGenreNameRes
import com.arflix.tv.ui.components.MediaCard as ArvioMediaCard
import com.arflix.tv.ui.components.AppTopBar
import com.arflix.tv.ui.components.AppTopBarContentTopInset
import com.arflix.tv.util.LocalDeviceType
import com.arflix.tv.ui.components.MediaContextMenu
import com.arflix.tv.ui.components.Toast
import com.arflix.tv.ui.components.ToastType as ComponentToastType
import com.arflix.tv.ui.components.SidebarItem
import com.arflix.tv.ui.components.topBarSelectedIndex
import com.arflix.tv.ui.components.topBarFocusedItem
import com.arflix.tv.ui.components.topBarMaxIndex
import com.arflix.tv.ui.focus.isArvioDpadNavigationKey
import com.arflix.tv.ui.focus.rememberArvioDpadRepeatGate
import com.arflix.tv.ui.skin.ArvioFocusableSurface
import com.arflix.tv.ui.skin.ArvioSkin
import com.arflix.tv.ui.skin.rememberArvioCardShape
import com.arflix.tv.ui.theme.ArflixTypography
import com.arflix.tv.ui.theme.appBackgroundDark
import com.arflix.tv.ui.theme.TextPrimary
import com.arflix.tv.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource


private object HomeRegexes {
    val HTML_TAG = Regex("<[^>]*>")
    val NON_BREAKING_SPACE = Regex("[\u00A0\u2007\u202F]")
    val UNICODE_SPACE = Regex("\\p{Z}+")
    val WHITESPACE = Regex("\\s+")
}

internal fun Context.cleanOverviewText(value: String): String {
    return value
        .replace(HomeRegexes.HTML_TAG, " ")
        .replace(HomeRegexes.NON_BREAKING_SPACE, " ")
        .replace(HomeRegexes.UNICODE_SPACE, " ")
        .replace(HomeRegexes.WHITESPACE, " ")
        .trim()
        .ifBlank { getString(R.string.home_no_description) }
}

// Genre ID to display name (TMDB standard). The numeric id stays the key;
// only the rendered label is localized — see `TmdbGenreNames.kt`.
internal fun Context.genreNames(mediaType: MediaType, genreIds: List<Int>): List<String> =
    genreIds.mapNotNull { id ->
        val res = if (mediaType == MediaType.TV) tvGenreNameRes(id) else movieGenreNameRes(id)
        res?.let { getString(it) }
    }

@Stable
internal class HomeFocusState(
    initialRowIndex: Int = 0,
    initialItemIndex: Int = 0,
    initialSidebarIndex: Int = 1
) {
    var isSidebarFocused by mutableStateOf(false)
    var sidebarFocusIndex by mutableIntStateOf(initialSidebarIndex)
    var currentRowIndex by mutableIntStateOf(initialRowIndex)
    var currentItemIndex by mutableIntStateOf(initialItemIndex)
    var lastNavEventTime by mutableLongStateOf(0L)
    var userHasNavigated by mutableStateOf(false)
    var scrollResetRequest by mutableIntStateOf(0)
    var preferredCategoryId: String? = null
    private var reconciledCategories: List<Category>? = null
    // Per-row item indices — when pressing D-pad Down, we save the current item
    // index for the current row so pressing Up later returns to the same position.
    // Netflix preserves horizontal scroll position across rows; without this,
    // every Down press resets to item 0 which is jarring.
    // Catalog IDs remain stable while background loads insert or reorder rows.
    val rowItemIndicesByCategoryId = mutableMapOf<String, Int>()
    // Keep the exact focused title as well as its index. Catalog refreshes can
    // insert or reorder items, so the numeric index alone is not a stable anchor.
    val rowItemKeysByCategoryId = mutableMapOf<String, String>()
    private var categoryHasMoreMap: Map<String, Boolean> = emptyMap()

    fun returnToStart(categories: List<Category>, now: Long) {
        rowItemIndicesByCategoryId.clear()
        rowItemKeysByCategoryId.clear()
        currentRowIndex = 0
        currentItemIndex = 0
        preferredCategoryId = categories.firstOrNull()?.id
        isSidebarFocused = categories.isEmpty()
        userHasNavigated = true
        lastNavEventTime = now
        scrollResetRequest++
        recordSelection(categories)
    }

    private fun selectionKeys(category: Category): List<String> {
        val keys = stableHomeRowItemKeys(category.id, navigableHomeItems(category.items))
        return if (homeRowSupportsViewAll(category, categoryHasMoreMap[category.id] == true)) {
            keys + HOME_VIEW_ALL_FOCUS_KEY
        } else keys
    }

    fun restoredItemIndex(category: Category, fallback: Int): Int = resolveHomeItemIndex(
        selectionKeys(category), rowItemKeysByCategoryId[category.id], fallback,
    )

    fun recordSelection(categories: List<Category>) {
        val category = categories.getOrNull(currentRowIndex) ?: return
        preferredCategoryId = category.id
        val items = navigableHomeItems(category.items)
        if (currentItemIndex == homeRowViewAllIndex(category, categoryHasMoreMap[category.id] == true)) {
            rowItemIndicesByCategoryId[category.id] = currentItemIndex
            rowItemKeysByCategoryId[category.id] = HOME_VIEW_ALL_FOCUS_KEY
            return
        }
        val item = items.getOrNull(currentItemIndex) ?: return
        rowItemIndicesByCategoryId[category.id] = currentItemIndex
        if (!item.isPlaceholder) {
            rowItemKeysByCategoryId[category.id] = stableHomeRowItemKeys(category.id, items)[currentItemIndex]
        }
    }

    fun reconcile(categories: List<Category>, hasMoreMap: Map<String, Boolean> = emptyMap()) {
        if (categories.isEmpty()) return
        if (categories === reconciledCategories && categoryHasMoreMap == hasMoreMap) return
        reconciledCategories = categories
        categoryHasMoreMap = hasMoreMap
        currentRowIndex = resolveHomeCategoryIndex(categories.map { it.id }, preferredCategoryId, currentRowIndex)
        val category = categories[currentRowIndex]
        preferredCategoryId = category.id
        val items = navigableHomeItems(category.items)
        // A transient empty/skeleton response is not a user selection or deletion.
        if (items.isEmpty() || items.all { it.isPlaceholder }) return
        val keys = selectionKeys(category)
        val anchor = rowItemKeysByCategoryId[category.id]
        val fallback = rowItemIndicesByCategoryId[category.id] ?: currentItemIndex
        currentItemIndex = resolveHomeItemIndex(keys, anchor, fallback)
        if (anchor == null || anchor in keys) recordSelection(categories)
    }

    companion object {
        // `userHasNavigated` is saved as the 4th element (0/1). Without it,
        // returning from a Details screen caused the "preferred
        // start row" reset to fire (since the field defaulted back to false),
        // which snapped the scroll position to Trending Movies — losing the
        // user's place in Franchises or wherever they were.
        val Saver: androidx.compose.runtime.saveable.Saver<HomeFocusState, List<Any>> =
            androidx.compose.runtime.saveable.Saver(
                save = {
                    listOf(
                        it.currentRowIndex,
                        it.currentItemIndex,
                        it.sidebarFocusIndex,
                        if (it.userHasNavigated) 1 else 0,
                        it.preferredCategoryId.orEmpty(),
                        HashMap(it.rowItemIndicesByCategoryId),
                        HashMap(it.rowItemKeysByCategoryId),
                        it.isSidebarFocused,
                        false, // Reserved slot in previously saved focus state.
                        it.scrollResetRequest
                    )
                },
                restore = {
                    HomeFocusState(it[0] as Int, it[1] as Int, it[2] as Int).apply {
                        userHasNavigated = (it.getOrNull(3) ?: 0) == 1
                        preferredCategoryId = (it.getOrNull(4) as? String)?.takeIf(String::isNotBlank)
                        (it.getOrNull(5) as? Map<*, *>)?.forEach { (key, value) ->
                            if (key is String && value is Int) rowItemIndicesByCategoryId[key] = value
                        }
                        (it.getOrNull(6) as? Map<*, *>)?.forEach { (key, value) ->
                            if (key is String && value is String) rowItemKeysByCategoryId[key] = value
                        }
                        isSidebarFocused = it.getOrNull(7) as? Boolean ?: false
                        scrollResetRequest = it.getOrNull(9) as? Int ?: 0
                    }
                }
            )
    }
}

@Composable
internal fun localizedCategoryTitle(category: Category): String =
    if (category.id == "continue_watching") stringResource(R.string.continue_watching) else category.title


private fun deduplicateHomeCategories(categories: List<Category>): List<Category> {
    if (categories.size < 2) return categories
    val byId = LinkedHashMap<String, Category>(categories.size)
    categories.forEach { category ->
        val existing = byId[category.id]
        byId[category.id] = when {
            existing == null -> category
            category.id == "continue_watching" -> chooseContinueWatchingCategory(existing, category)
            existing.items.isEmpty() && category.items.isNotEmpty() -> category
            else -> existing
        }
    }
    return byId.values.toList()
}

private fun chooseContinueWatchingCategory(first: Category, second: Category): Category {
    val firstHasRealItems = first.items.any { !it.isPlaceholder }
    val secondHasRealItems = second.items.any { !it.isPlaceholder }
    return when {
        secondHasRealItems && !firstHasRealItems -> second
        firstHasRealItems && !secondHasRealItems -> first
        second.items.size > first.items.size -> second
        else -> first
    }
}

private fun getFocusedItem(categories: List<Category>, rowIndex: Int, itemIndex: Int): MediaItem? {
    val row = categories.getOrNull(rowIndex)
    return row?.items?.let(::navigableHomeItems)?.getOrNull(itemIndex)
        ?: row?.items?.firstOrNull()
        ?: categories.firstOrNull()?.items?.firstOrNull()
}

internal fun navigableHomeItems(items: List<MediaItem>): List<MediaItem> =
    if (items.any { it.isPlaceholder } && items.any { !it.isPlaceholder }) items.filterNot { it.isPlaceholder } else items

internal fun homeRowItemKey(item: MediaItem): String {
    val episodeSuffix = item.nextEpisode?.let { "_S${it.seasonNumber}E${it.episodeNumber}" }.orEmpty()
    return "${item.mediaType.name}-${item.id}$episodeSuffix"
}

internal fun stableHomeRowItemKeys(categoryId: String, items: List<MediaItem>): List<String> {
    val occurrences = HashMap<String, Int>()
    return items.map { item ->
        val baseKey = if (item.isPlaceholder) {
            "placeholder_${categoryId}_${item.id}"
        } else {
            homeRowItemKey(item)
        }
        val occurrence = occurrences[baseKey] ?: 0
        occurrences[baseKey] = occurrence + 1
        if (occurrence == 0) baseKey else "$baseKey#duplicate$occurrence"
    }
}

internal fun stableHomeRowKey(layout: String, categoryId: String): String =
    "${layout}_home_row_$categoryId"

internal fun resolveHomeCategoryIndex(
    categoryIds: List<String>,
    preferredCategoryId: String?,
    fallbackIndex: Int
): Int {
    if (categoryIds.isEmpty()) return 0
    val preferredIndex = preferredCategoryId?.let(categoryIds::indexOf) ?: -1
    return if (preferredIndex >= 0) {
        preferredIndex
    } else {
        fallbackIndex.coerceIn(0, categoryIds.lastIndex)
    }
}

internal fun resolveHomeItemIndex(
    itemKeys: List<String>,
    preferredItemKey: String?,
    fallbackIndex: Int
): Int {
    val preferredIndex = preferredItemKey?.let(itemKeys::indexOf) ?: -1
    if (preferredIndex >= 0) return preferredIndex
    if (itemKeys.isEmpty()) return 0
    return fallbackIndex.coerceIn(0, itemKeys.lastIndex)
}

internal fun clampHomeItemIndex(items: List<MediaItem>, index: Int): Int {
    val realItemCount = items.count { !it.isPlaceholder }
    val navigableItemCount = if (realItemCount > 0) realItemCount else items.size
    return if (navigableItemCount == 0) 0 else index.coerceIn(0, navigableItemCount - 1)
}

/** A fully loaded row shorter than this already shows everything, so it gets no "View all". */
private const val HOME_VIEW_ALL_MIN_ITEMS = 15

/** Focus-anchor key recorded while a row's trailing "View all" card is focused. */
private const val HOME_VIEW_ALL_FOCUS_KEY = "__view_all__"

/**
 * Whether a home row ends with a "View all" card that opens its full paginated grid.
 * The grid only opens title details, so rows of non-title items are excluded.
 */
internal fun homeRowSupportsViewAll(category: Category, hasMore: Boolean): Boolean {
    val realItems = category.items.filterNot { it.isPlaceholder }
    if (realItems.isEmpty()) return false
    val hasNonTitleItems = realItems.any { item ->
        val status = item.status
        false
    }
    if (hasNonTitleItems) return false
    return hasMore || realItems.size >= HOME_VIEW_ALL_MIN_ITEMS
}

/** Index of a row's trailing "View all" slot (one past its last title), or -1 when it has none. */
internal fun homeRowViewAllIndex(category: Category, hasMore: Boolean): Int =
    if (homeRowSupportsViewAll(category, hasMore)) category.items.count { !it.isPlaceholder } else -1

private fun preferredHomeStartRowIndex(categories: List<Category>): Int =
    categories.indexOfFirst { it.id == "continue_watching" }.coerceAtLeast(0)

internal fun isActionableHomeItem(item: MediaItem?): Boolean =
    item != null && item.hasOpenableId && !item.isPlaceholder

@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    onNavigateToDetails: (MediaType, Int, Int?, Int?) -> Unit = { _, _, _, _ -> },
    onNavigateToCategory: (String) -> Unit = {},
    onNavigateToSearch: () -> Unit = {},
    onNavigateToWatchlist: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onExitApp: () -> Unit = {}
) {
    val isMobile = LocalDeviceType.current.isTouchDevice()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val focusState = rememberSaveable(saver = HomeFocusState.Saver) { HomeFocusState() }
    val lifecycleOwner = LocalLifecycleOwner.current
    var suppressSelectUntilMs by remember { mutableLongStateOf(SystemClock.elapsedRealtime() + 150L) }
    var contextMenuItem by remember { mutableStateOf<MediaItem?>(null) }
    var contextMenuIsInWatchlist by remember { mutableStateOf(false) }
    val navigate: (MediaType, Int, Int?, Int?) -> Unit = { type, id, season, episode ->
        uiState.categories.flatMap { it.items }.firstOrNull { it.mediaType == type && it.id == id }
            ?.let(viewModel::cacheItem)
        onNavigateToDetails(type, id, season, episode)
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshContinueWatchingOnly()
                viewModel.refreshWatchedBadgesOnResume()
                suppressSelectUntilMs = SystemClock.elapsedRealtime() + 150L
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(contextMenuItem) {
        contextMenuIsInWatchlist = contextMenuItem?.let { viewModel.isInWatchlist(it) } ?: false
    }
    BackHandler(enabled = contextMenuItem != null) { contextMenuItem = null }
    Box(Modifier.fillMaxSize().background(appBackgroundDark())) {
        HomeInputLayer(
            categories = uiState.categories,
            cardLogoUrls = viewModel.cardLogoUrls,
            focusState = focusState,
            limitRowsDuringStartup = false,
            suppressSelectUntilMs = suppressSelectUntilMs,
            contentStartPadding = if (isMobile) 16.dp else 36.dp,
            fastScrollThresholdMs = 650L,
            usePosterCards = false,
            isContextMenuOpen = contextMenuItem != null,
            isMobile = isMobile,
            clockFormat = uiState.clockFormat,
            categoryHasMoreMap = uiState.categoryHasMoreMap,
            smoothScrolling = uiState.smoothScrolling,
            onNavigateToDetails = navigate,
            onNavigateToCategory = onNavigateToCategory,
            onNavigateToSearch = onNavigateToSearch,
            onNavigateToWatchlist = onNavigateToWatchlist,
            onNavigateToSettings = onNavigateToSettings,
            onExitApp = onExitApp,
            onOpenContextMenu = { item, _ -> contextMenuItem = item }
        )
        contextMenuItem?.let { item ->
            Box(Modifier.fillMaxSize().zIndex(120f)) {
                MediaContextMenu(
                    isVisible = true,
                    title = item.title,
                    isInWatchlist = contextMenuIsInWatchlist,
                    isWatched = item.isWatched,
                    isContinueWatching = true,
                    onPlay = { navigate(item.mediaType, item.id, item.nextEpisode?.seasonNumber, item.nextEpisode?.episodeNumber) },
                    onViewDetails = { navigate(item.mediaType, item.id, item.nextEpisode?.seasonNumber, item.nextEpisode?.episodeNumber) },
                    onToggleWatchlist = { viewModel.toggleWatchlist(item) },
                    onToggleWatched = { viewModel.toggleWatched(item) },
                    onRemoveFromContinueWatching = { viewModel.removeFromContinueWatching(item) },
                    onDismiss = { contextMenuItem = null }
                )
            }
        }
        uiState.toastMessage?.let { message ->
            Box(Modifier.fillMaxSize().zIndex(122f)) {
                Toast(message = message, type = when (uiState.toastType) {
                    ToastType.SUCCESS -> ComponentToastType.SUCCESS
                    ToastType.ERROR -> ComponentToastType.ERROR
                    ToastType.INFO -> ComponentToastType.INFO
                }, isVisible = true, onDismiss = viewModel::dismissToast)
            }
        }
    }
}

@Composable
internal fun HomeInputLayer(
    categories: List<Category>,
    cardLogoUrls: Map<String, String>,
    focusState: HomeFocusState,
    limitRowsDuringStartup: Boolean,
    suppressSelectUntilMs: Long,
    contentStartPadding: androidx.compose.ui.unit.Dp,
    fastScrollThresholdMs: Long,
    usePosterCards: Boolean,
    isContextMenuOpen: Boolean,
    isMobile: Boolean = false,
    clockFormat: String = "24h",
    categoryHasMoreMap: Map<String, Boolean> = emptyMap(),
    smoothScrolling: Boolean = true,
    isSlowLoading: Boolean = false,
    onRetry: () -> Unit = {},
    onLoadMoreCategory: (String) -> Unit = {},
    onItemFocusedPrefetch: (MediaItem) -> Unit = {},
    onMobileCategoryVisiblePosition: (String, Int) -> Unit = { _, _ -> },
    onNavigateToDetails: (MediaType, Int, Int?, Int?) -> Unit,
    onNavigateToCategory: (String) -> Unit = {},
    onNavigateToSearch: () -> Unit,
    onNavigateToWatchlist: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onExitApp: () -> Unit,
    onOpenContextMenu: (MediaItem, Boolean) -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val homeView = androidx.compose.ui.platform.LocalView.current
    val isRtl = androidx.compose.ui.platform.LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Rtl
    var selectPressedInHome by remember { mutableStateOf(false) }
    var selectDownAtMs by remember { mutableLongStateOf(0L) }
    var rootHasFocus by remember { mutableStateOf(false) }
    val backPress = remember { HomeBackPress() }
    val backScope = rememberCoroutineScope()
    var backHoldJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var suppressNativeBackUntil by remember { mutableLongStateOf(0L) }
    val returnHomeToStart by rememberUpdatedState {
        selectPressedInHome = false
        selectDownAtMs = 0L
        focusState.returnToStart(categories, SystemClock.elapsedRealtime())
    }

    fun shortBack() {
        selectPressedInHome = false
        selectDownAtMs = 0L
        if (focusState.isSidebarFocused) {
            onExitApp()
        } else {
            focusState.recordSelection(categories)
            focusState.isSidebarFocused = true
        }
    }

    fun finishBackPress() {
        backHoldJob?.cancel()
        backHoldJob = null
        when (backPress.up(SystemClock.elapsedRealtime())) {
            HomeBackAction.RETURN_TO_START -> returnHomeToStart()
            HomeBackAction.BACK -> shortBack()
            HomeBackAction.NONE -> Unit
        }
        suppressNativeBackUntil = SystemClock.elapsedRealtime() + 200L
    }

    DisposableEffect(Unit) {
        onDispose { backHoldJob?.cancel(); backPress.cancel() }
    }
    LaunchedEffect(isContextMenuOpen, isMobile) {
        if (isContextMenuOpen || isMobile) {
            backHoldJob?.cancel()
            backPress.cancel()
        }
    }
    val focusRecoveryDelayMs = 180L
    val dpadRepeatGate = rememberArvioDpadRepeatGate(
        horizontalMinRepeatIntervalMs = 80L,
        verticalMinRepeatIntervalMs = 112L
    )
    val maxSidebarIndex = topBarMaxIndex()

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
    LaunchedEffect(rootHasFocus, isContextMenuOpen, isMobile) {
        if (isMobile || isContextMenuOpen || rootHasFocus) return@LaunchedEffect
        delay(focusRecoveryDelayMs)
        if (!rootHasFocus && !isContextMenuOpen && homeView.findFocus() !is android.webkit.WebView) {
            runCatching { focusRequester.requestFocus() }
        }
    }
    // Personal build 1.0.1: an empty Home (nothing watched yet) has no row to focus, so focus starts on the
    // top bar where Left/Right reach Search and Settings. When rows arrive before the user moved, focus
    // goes back down to them.
    var topBarFocusedForEmptyHome by remember { mutableStateOf(false) }
    LaunchedEffect(categories.isEmpty()) {
        if (categories.isEmpty()) {
            if (!focusState.isSidebarFocused) {
                focusState.isSidebarFocused = true
                focusState.sidebarFocusIndex = topBarSelectedIndex(SidebarItem.HOME)
                topBarFocusedForEmptyHome = true
            }
        } else if (topBarFocusedForEmptyHome && !focusState.userHasNavigated) {
            focusState.isSidebarFocused = false
            topBarFocusedForEmptyHome = false
        }
    }

    // One owner reconciles both row and title identity. Input records anchors
    // synchronously, so a late refresh cannot restore the previous key press.
    LaunchedEffect(categories, categoryHasMoreMap) {
        if (categories.isEmpty()) return@LaunchedEffect

        if (!focusState.userHasNavigated && !focusState.isSidebarFocused) {
            val preferredStartRow = preferredHomeStartRowIndex(categories)
            if (focusState.currentRowIndex != preferredStartRow) {
                focusState.currentRowIndex = preferredStartRow
                focusState.currentItemIndex = 0
                focusState.preferredCategoryId = categories.getOrNull(preferredStartRow)?.id
            }
        }

        // If the preferred category still exists, restore the row index to it.
        // Otherwise keep the current index but clamp to valid range.
        focusState.reconcile(categories, categoryHasMoreMap)
    }

    BackHandler(enabled = !isContextMenuOpen) {
        selectPressedInHome = false
        selectDownAtMs = 0L
        val embeddedFocus = homeView.findFocus() as? android.webkit.WebView
        if (embeddedFocus != null) {
            embeddedFocus.clearFocus()
            focusRequester.requestFocus()
        } else if (backPress.isPressed) finishBackPress()
        else if (SystemClock.elapsedRealtime() >= suppressNativeBackUntil) shortBack()
    }

    val keyEventModifier = if (isMobile) {
        Modifier // No D-pad key handling on mobile
    } else {
        Modifier.onPreviewKeyEvent { event ->
            if (isContextMenuOpen || homeView.findFocus() is android.webkit.WebView) {
                return@onPreviewKeyEvent false
            }

            if (event.key == Key.Back) {
                if (event.type == KeyEventType.KeyDown) {
                    val now = SystemClock.elapsedRealtime()
                    if (backPress.down(now)) {
                        backHoldJob = backScope.launch {
                            delay(HOME_BACK_HOLD_MS)
                            if (backPress.hold(SystemClock.elapsedRealtime())) returnHomeToStart()
                        }
                    } else if (backPress.hold(now)) returnHomeToStart()
                } else if (event.type == KeyEventType.KeyUp) {
                    if (event.nativeKeyEvent.isCanceled) backPress.cancel()
                    finishBackPress()
                }
                return@onPreviewKeyEvent true
            }
            if (event.type == KeyEventType.KeyUp && isArvioDpadNavigationKey(event.key)) {
                dpadRepeatGate.reset()
            }
            if (
                event.type == KeyEventType.KeyDown &&
                isArvioDpadNavigationKey(event.key) &&
                dpadRepeatGate.shouldSkip(
                    keyCode = event.nativeKeyEvent.keyCode,
                    repeatCount = event.nativeKeyEvent.repeatCount,
                    nowMs = SystemClock.elapsedRealtime()
                )
            ) {
                return@onPreviewKeyEvent true
            }

            // The trailing "View all" card is a virtual slot one past the row's last title.
            // getFocusedItem() falls back to the first title for that index, so Enter/Menu
            // must check this before resolving an item.
            val isOnViewAllSlot = {
                val row = categories.getOrNull(focusState.currentRowIndex)
                row != null &&
                    focusState.currentItemIndex == homeRowViewAllIndex(row, categoryHasMoreMap[row.id] == true)
            }

            val moveNext = {
                if (focusState.isSidebarFocused) {
                    if (focusState.sidebarFocusIndex < maxSidebarIndex) {
                        focusState.sidebarFocusIndex++
                        focusState.lastNavEventTime = SystemClock.elapsedRealtime()
                    }
                    true
                } else {
                    val row = categories.getOrNull(focusState.currentRowIndex)
                    val maxItems = row?.items?.let(::navigableHomeItems)?.size ?: 0
                    val viewAllSlots = if (row != null && homeRowSupportsViewAll(row, categoryHasMoreMap[row.id] == true)) 1 else 0
                    if (focusState.currentItemIndex < maxItems - 1 + viewAllSlots) {
                        focusState.currentItemIndex++
                        focusState.lastNavEventTime = SystemClock.elapsedRealtime()
                    }
                    true
                }
            }

            val movePrev = {
                if (!focusState.isSidebarFocused) {
                    if (focusState.currentItemIndex == 0) {
                        true
                    } else {
                        focusState.currentItemIndex--
                        focusState.lastNavEventTime = SystemClock.elapsedRealtime()
                        true
                    }
                } else {
                    if (focusState.sidebarFocusIndex > 0) {
                        focusState.sidebarFocusIndex--
                        focusState.lastNavEventTime = SystemClock.elapsedRealtime()
                    }
                    true
                }
            }

            // Reconcile against this render's data before applying new input, not in
            // an asynchronous effect after a background update and key press race.
            focusState.reconcile(categories, categoryHasMoreMap)

            val handled = when (event.type) {
                KeyEventType.KeyDown -> when (event.key) {
                    Key.Enter, Key.DirectionCenter -> {
                        // Track KeyDown time for long-press detection.
                        // Sidebar actions fire immediately; content items wait for KeyUp
                        // to distinguish tap (navigate) from long-press (context menu).
                        if (focusState.isSidebarFocused) {
                            when (topBarFocusedItem(focusState.sidebarFocusIndex)) {
                                SidebarItem.SEARCH -> onNavigateToSearch()
                                SidebarItem.HOME -> Unit
                                SidebarItem.WATCHLIST -> onNavigateToWatchlist()
                                SidebarItem.SETTINGS -> onNavigateToSettings()
                                null -> Unit
                            }

                        } else {
                            if (!selectPressedInHome) {
                                selectPressedInHome = true
                                selectDownAtMs = SystemClock.elapsedRealtime()
                            }
                        }
                        true
                    }

                    Key.DirectionLeft -> {
                        selectPressedInHome = false
                        selectDownAtMs = 0L
                        focusState.userHasNavigated = true
                        if (isRtl) moveNext() else movePrev()
                    }
                    Key.DirectionRight -> {
                        selectPressedInHome = false
                        selectDownAtMs = 0L
                        focusState.userHasNavigated = true
                        if (isRtl) movePrev() else moveNext()
                    }
                    Key.DirectionUp -> {
                        selectPressedInHome = false
                        selectDownAtMs = 0L
                        focusState.userHasNavigated = true
                        if (focusState.isSidebarFocused) {
                            true
                        } else if (focusState.currentRowIndex > 0) {
                            // Save current item position before leaving this row
                            categories.getOrNull(focusState.currentRowIndex)?.id?.let { categoryId ->
                                focusState.rowItemIndicesByCategoryId[categoryId] = focusState.currentItemIndex
                            }
                            focusState.currentRowIndex--
                            // Restore saved position for the target row (or 0 if never visited)
                            val targetCategory = categories.getOrNull(focusState.currentRowIndex)
                            val restoredIndex = targetCategory?.id
                                ?.let(focusState.rowItemIndicesByCategoryId::get)
                                ?: 0
                            focusState.currentItemIndex = targetCategory
                                ?.let { focusState.restoredItemIndex(it, restoredIndex) }
                                ?: 0
                            focusState.lastNavEventTime = SystemClock.elapsedRealtime()
                            true
                        } else {
                            categories.getOrNull(focusState.currentRowIndex)?.id?.let { categoryId ->
                                focusState.rowItemIndicesByCategoryId[categoryId] = focusState.currentItemIndex
                            }
                            focusState.isSidebarFocused = true
                            true
                        }
                    }
                    Key.DirectionDown -> {
                        selectPressedInHome = false
                        selectDownAtMs = 0L
                        focusState.userHasNavigated = true
                        if (focusState.isSidebarFocused && categories.isEmpty()) {
                            // Nothing below the top bar on an empty Home: stay put instead of focusing nothing.
                            true
                        } else if (focusState.isSidebarFocused) {
                            focusState.isSidebarFocused = false
                            val targetCategory = categories.getOrNull(focusState.currentRowIndex)
                            val restoredIndex = targetCategory?.id
                                ?.let(focusState.rowItemIndicesByCategoryId::get)
                                ?: focusState.currentItemIndex
                            focusState.currentItemIndex = targetCategory
                                ?.let { focusState.restoredItemIndex(it, restoredIndex) }
                                ?: 0
                            focusState.lastNavEventTime = SystemClock.elapsedRealtime()
                            true
                        } else if (!focusState.isSidebarFocused && focusState.currentRowIndex < categories.size - 1) {
                            // Save current item position before leaving this row
                            categories.getOrNull(focusState.currentRowIndex)?.id?.let { categoryId ->
                                focusState.rowItemIndicesByCategoryId[categoryId] = focusState.currentItemIndex
                            }
                            focusState.currentRowIndex++
                            // Restore saved position for the target row (or 0 if never visited)
                            val targetCategory = categories.getOrNull(focusState.currentRowIndex)
                            val restoredIndex = targetCategory?.id
                                ?.let(focusState.rowItemIndicesByCategoryId::get)
                                ?: 0
                            focusState.currentItemIndex = targetCategory
                                ?.let { focusState.restoredItemIndex(it, restoredIndex) }
                                ?: 0
                            focusState.lastNavEventTime = SystemClock.elapsedRealtime()
                            true
                        } else {
                            true
                        }
                    }
                        Key.Escape -> {
                            selectPressedInHome = false
                            selectDownAtMs = 0L
                            if (focusState.isSidebarFocused) {
                                onExitApp()
                            } else {
                                categories.getOrNull(focusState.currentRowIndex)?.id?.let { categoryId ->
                                    focusState.rowItemIndicesByCategoryId[categoryId] = focusState.currentItemIndex
                                }
                                focusState.isSidebarFocused = true
                            }
                            true
                        }
                        Key.Menu, Key.Info -> {
                            selectPressedInHome = false
                            selectDownAtMs = 0L
                            if (!focusState.isSidebarFocused && !isOnViewAllSlot()) {
                                val currentItem = getFocusedItem(
                                    categories,
                                    focusState.currentRowIndex,
                                    focusState.currentItemIndex
                                )
                                currentItem?.takeIf { isActionableHomeItem(it) }?.let { item ->

                                        val currentCategory = categories.getOrNull(focusState.currentRowIndex)
                                        val isContinue = currentCategory?.id == "continue_watching"
                                        onOpenContextMenu(item, isContinue)

                                }
                            }
                            true
                        }
                        else -> false
                    }
                    KeyEventType.KeyUp -> when (event.key) {
                        Key.Enter, Key.DirectionCenter -> {
                            if (selectPressedInHome && !focusState.isSidebarFocused && isOnViewAllSlot()) {
                                categories.getOrNull(focusState.currentRowIndex)?.id?.let(onNavigateToCategory)
                            } else if (selectPressedInHome && !focusState.isSidebarFocused) {
                                val holdMs = SystemClock.elapsedRealtime() - selectDownAtMs
                                val currentItem = getFocusedItem(
                                    categories,
                                    focusState.currentRowIndex,
                                    focusState.currentItemIndex
                                )
                                currentItem?.takeIf { isActionableHomeItem(it) }?.let { item ->

                                    if (holdMs >= 500L) {
                                        // Long-press: open context menu
                                        val currentCategory = categories.getOrNull(focusState.currentRowIndex)
                                        val isContinue = currentCategory?.id == "continue_watching"
                                        onOpenContextMenu(item, isContinue)
                                    } else {
                                        onNavigateToDetails(item.mediaType, item.id, item.nextEpisode?.seasonNumber, item.nextEpisode?.episodeNumber)
                                    }
                                }
                            }
                            selectPressedInHome = false
                            selectDownAtMs = 0L
                            true
                        }
                        else -> false
                    }
                    else -> false
                }
            if (event.type == KeyEventType.KeyDown && isArvioDpadNavigationKey(event.key)) {
                focusState.recordSelection(categories)
            }
            handled
            }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .onFocusChanged {
                rootHasFocus = it.hasFocus
                if (!it.hasFocus) {
                    selectPressedInHome = false
                    selectDownAtMs = 0L
                    backHoldJob?.cancel()
                    backPress.cancel()
                }
            }
            .focusable()
            .then(keyEventModifier)
    ) {
        if (!isMobile) {
            AppTopBar(
                selectedItem = SidebarItem.HOME,
                isFocused = focusState.isSidebarFocused,
                focusedIndex = focusState.sidebarFocusIndex,
                clockFormat = clockFormat,
            )
        }

        HomeRowsLayer(
            categories = categories,
            cardLogoUrls = cardLogoUrls,
            focusState = focusState,
            contentStartPadding = contentStartPadding,
            isMobile = isMobile,
            smoothScrolling = smoothScrolling,
            onViewAllCategory = onNavigateToCategory,
            onItemClick = { item ->
                if (isActionableHomeItem(item)) onNavigateToDetails(item.mediaType, item.id,
                    item.nextEpisode?.seasonNumber, item.nextEpisode?.episodeNumber)
            },
            onOpenContextMenu = onOpenContextMenu
        )
    }
}

@Composable
private fun HomeRowsLayer(
    categories: List<Category>,
    cardLogoUrls: Map<String, String>,
    focusState: HomeFocusState,
    contentStartPadding: Dp,
    isMobile: Boolean,
    smoothScrolling: Boolean,
    onViewAllCategory: (String) -> Unit,
    onItemClick: (MediaItem) -> Unit,
    onOpenContextMenu: (MediaItem, Boolean) -> Unit
) {
    Column(Modifier.fillMaxSize().padding(top = if (isMobile) 24.dp else AppTopBarContentTopInset)) {
        categories.forEach { category ->
            ContentRow(
                category = category,
                cardLogoUrls = cardLogoUrls,
                isCurrentRow = !focusState.isSidebarFocused,
                startPadding = contentStartPadding,
                smoothScrolling = smoothScrolling,
                showViewAll = homeRowSupportsViewAll(category, false),
                onViewAll = { onViewAllCategory(category.id) },
                focusedItemIndex = focusState.currentItemIndex,
                isFastScrolling = false,
                enableSystemFocus = isMobile,
                onItemClick = onItemClick,
                onItemLongClick = { onOpenContextMenu(it, true) },
                onItemFocused = { _, index ->
                    focusState.currentItemIndex = index
                    focusState.recordSelection(categories)
                }
            )
        }
    }
}

@Composable
internal fun ContentRow(
    category: Category,
    cardLogoUrls: Map<String, String>,
    isCurrentRow: Boolean,
    startPadding: Dp = 12.dp,
    smoothScrolling: Boolean = true,
    showViewAll: Boolean = false,
    onViewAll: () -> Unit = {},
    focusedItemIndex: Int,
    isFastScrolling: Boolean,
    enableSystemFocus: Boolean = false,
    onItemClick: (MediaItem) -> Unit,
    onItemLongClick: ((MediaItem) -> Unit)? = null,
    onItemFocused: (MediaItem, Int) -> Unit
) {
    val rowState = rememberLazyListState()
    val itemsToRender = remember(category.items) { navigableHomeItems(category.items) }
    val itemKeys = remember(category.id, itemsToRender) { stableHomeRowItemKeys(category.id, itemsToRender) }
    val targetIndex = focusedItemIndex.coerceIn(0, (itemsToRender.size - 1 + if (showViewAll) 1 else 0).coerceAtLeast(0))
    LaunchedEffect(targetIndex, isCurrentRow) {
        if (isCurrentRow && itemsToRender.isNotEmpty()) {
            if (smoothScrolling) rowState.animateScrollToItem(targetIndex) else rowState.scrollToItem(targetIndex)
        }
    }
    Column(Modifier.fillMaxWidth().testTag("home_row_${category.id}")) {
        Text(text = localizedCategoryTitle(category), style = ArflixTypography.sectionTitle,
            color = TextPrimary, modifier = Modifier.padding(start = startPadding, bottom = 12.dp))
        LazyRow(state = rowState, contentPadding = PaddingValues(start = startPadding, end = 36.dp, top = 8.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth().testTag("home_rail_${category.id}")) {
            itemsIndexed(itemsToRender, key = { index, _ -> itemKeys[index] }) { index, item ->
                ArvioMediaCard(
                    item = item,
                    width = HOME_TV_LANDSCAPE_CARD_WIDTH_DP.dp,
                    isLandscape = true,
                    logoImageUrl = cardLogoUrls["${item.mediaType}_${item.id}"],
                    showLogoImage = true,
                    showProgress = true,
                    isFocusedOverride = !enableSystemFocus && isCurrentRow && index == focusedItemIndex,
                    enableSystemFocus = enableSystemFocus,
                    onFocused = { onItemFocused(item, index) },
                    onClick = { onItemClick(item) },
                    onLongClick = onItemLongClick?.let { callback -> { callback(item) } }
                )
            }
            if (showViewAll) item(key = "${category.id}_view_all") {
                HomeViewAllCard(width = HOME_TV_LANDSCAPE_CARD_WIDTH_DP.dp, isLandscape = true,
                    isFocused = isCurrentRow && focusedItemIndex == itemsToRender.size,
                    enableSystemFocus = enableSystemFocus, onClick = onViewAll)
            }
        }
    }
}

@Composable
internal fun HomeViewAllCard(
    width: Dp,
    isLandscape: Boolean,
    isFocused: Boolean,
    enableSystemFocus: Boolean,
    onClick: () -> Unit
) {
    val shape = rememberArvioCardShape(ArvioSkin.radius.md)
    ArvioFocusableSurface(
        modifier = Modifier
            .width(width)
            .aspectRatio(if (isLandscape) 16f / 9f else 2f / 3f),
        shape = shape,
        // Dark enough to stay readable over bright hero backdrops.
        backgroundColor = Color.Black.copy(alpha = 0.6f),
        outlineColor = ArvioSkin.colors.focusOutline,
        outlineWidth = 2.5.dp,
        focusedScale = 1f,
        animateFocus = false,
        enableSystemFocus = enableSystemFocus,
        isFocusedOverride = isFocused,
        onClick = onClick
    ) { focused ->
        val contentColor = if (focused) Color.White else TextSecondary
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(if (isLandscape) 28.dp else 24.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.home_view_all),
                style = ArflixTypography.label,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
