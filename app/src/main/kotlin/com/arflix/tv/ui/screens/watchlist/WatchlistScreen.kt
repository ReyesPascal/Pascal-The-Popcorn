package com.arflix.tv.ui.screens.watchlist

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.gestures.animateScrollBy
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.mapSaver
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.LayoutDirection
import com.arflix.tv.ui.focus.mirrorHorizontalForRtl
import com.arflix.tv.ui.focus.arvioDpadFocusGroup
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import com.arflix.tv.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arflix.tv.data.model.*
import com.arflix.tv.data.repository.*
import com.arflix.tv.ui.components.*
import com.arflix.tv.ui.skin.resolveAccentColor
import com.arflix.tv.util.LocalDeviceType
import com.arflix.tv.util.tr
import com.arflix.tv.ui.screens.watchlist.calendar.*
import com.arflix.tv.ui.screens.home.CategoryBackdrop
import com.arflix.tv.ui.screens.home.CategorySpotlight
import com.arflix.tv.ui.screens.home.categorySpotlightHeight
import kotlinx.coroutines.delay
import java.time.LocalDate

internal enum class LibrarySection(val label: String) { WATCHLISTS("Watchlist"), CALENDAR("Calendar") }
internal val LibraryTabsTopInset = AppTopBarHeight - 8.dp
@OptIn(ExperimentalFoundationApi::class)
private object LibraryTvBringIntoViewSpec : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float = 0f
}
internal fun libraryColumns(width: Int, poster: Boolean): Int =
    if (poster) (width / 115).coerceIn(2, 8) else (width / 180).coerceIn(2, 4)
internal fun librarySources(sources: List<WatchlistSourceItem>, section: LibrarySection): List<WatchlistSourceItem> =
    sources.filter { when (section) {
        LibrarySection.WATCHLISTS -> it is WatchlistSourceItem.MyWatchlist
        LibrarySection.CALENDAR -> false
    } }

@OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun WatchlistScreen(
    viewModel: WatchlistViewModel = hiltViewModel(),
    onNavigateToDetails: (MediaType, Int) -> Unit = { _, _ -> },
    onNavigateToHome: () -> Unit = {}, onNavigateToSearch: () -> Unit = {},
    onNavigateToSettings: (String?) -> Unit = {},
    onBack: () -> Unit = {},
    calendarStateOverride: ReleaseCalendarUiState? = null,
    onCalendarSelectDate: (LocalDate) -> Unit = {},
    onCalendarChangeMonth: (Long) -> Unit = {},
    onCalendarSelectSource: (String) -> Unit = {},
    onCalendarRefresh: () -> Unit = {},
    calendarViewModel: ReleaseCalendarViewModel? = null,
    onCalendarOpenRelease: ((com.arflix.tv.data.model.CalendarRelease) -> Unit)? = null
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val logos by viewModel.logoUrls.collectAsStateWithLifecycle()
    val touch = LocalDeviceType.current.isTouchDevice()
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val poster = rememberCardLayoutMode() == CardLayoutMode.POSTER
    val scrollScope = rememberCoroutineScope()
    var section by rememberSaveable { mutableStateOf(LibrarySection.WATCHLISTS) }
    val calendarMode = section == LibrarySection.CALENDAR
    val calendarTab = remember { FocusRequester() }
    var query by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable { mutableStateOf(LibrarySort.RECENTLY_ADDED) }
    var mediaFilter by rememberSaveable { mutableStateOf<MediaType?>(null) }
    var filters by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf(false) }
    var sourcesOpen by remember { mutableStateOf(false) }
    var topFocused by remember { mutableStateOf(false) }
    var topIndex by remember { mutableIntStateOf(2) }
    val firstTab = remember { FocusRequester() }
    var initialFocusPlaced by remember { mutableStateOf(false) }
    val filterButton = remember { FocusRequester() }
    val topFocus = remember { FocusRequester() }
    val scopeSources = remember(state.sources, section) { librarySources(state.sources, section) }
    val selectedId = state.selectedSourceId
    val savedItems = remember(state.movies, state.series, state.selectedSourceId) {
        val combined = state.movies + state.series
        if(state.selectedSourceId == WatchlistSourceItem.MyWatchlist.id) combined.sortedByDescending { it.addedAt } else combined
    }
    val rawItems = savedItems
    val items = remember(rawItems, query, sort, mediaFilter) {
        val filtered = rawItems.filter { (mediaFilter == null || it.mediaType == mediaFilter) && it.title.contains(query, true) }
        sortLibraryItems(filtered, sort)
    }
    val loading = state.isLoading
    val error = state.error
    val sourceKey = "$section:$selectedId:$query:$sort:$mediaFilter"
    val viewports = rememberSaveable(saver = mapSaver(
        save = { states: MutableMap<String, LazyGridState> -> states.mapValues { (_, grid) -> arrayListOf(grid.firstVisibleItemIndex, grid.firstVisibleItemScrollOffset) } },
        restore = { saved -> saved.mapValues { (_, value) -> val position = value as List<*>; LazyGridState(position[0] as Int, position[1] as Int) }.toMutableMap() }
    )) { mutableMapOf<String, LazyGridState>() }
    val grid = remember(sourceKey) { viewports.getOrPut(sourceKey) { LazyGridState() } }
    var revealJob by remember(grid) { mutableStateOf<Job?>(null) }
    var focusedGridRow by remember(grid) { mutableStateOf<Pair<Int, Int>?>(null) }
    DisposableEffect(grid) {
        onDispose { revealJob?.cancel() }
    }
    fun revealCard(index: Int, columns: Int) {
        if (touch) return
        val row = columns to index / columns
        // Sideways focus must not cancel or restart the current row's reveal.
        if (focusedGridRow == row) return
        focusedGridRow = row
        revealJob?.cancel()
        revealJob = scrollScope.launch {
            withFrameNanos { }
            val layout = grid.layoutInfo
            val card = layout.visibleItemsInfo.firstOrNull { it.index == index }
            if (card == null) grid.animateScrollToItem(index / columns * columns) else {
                val delta = libraryRowAnchorScrollDelta(card.offset.y, card.size.height,
                    layout.viewportStartOffset + layout.beforeContentPadding,
                    layout.viewportEndOffset - layout.afterContentPadding)
                if (delta != 0) grid.animateScrollBy(delta.toFloat(), tween(160))
            }
        }
    }
    var focusedPreviewKey by rememberSaveable(sourceKey) { mutableStateOf<String?>(null) }
    val touchPreviewIndex by remember(grid) { derivedStateOf { grid.firstVisibleItemIndex } }
    val previewCandidate = if (touch) items.getOrNull(touchPreviewIndex.coerceAtMost(items.lastIndex.coerceAtLeast(0)))
        else items.firstOrNull { libraryPreviewKey(it) == focusedPreviewKey } ?: items.firstOrNull()
    var readyPreview by remember(sourceKey) { mutableStateOf<LibraryPreview?>(null) }
    val activePreview = readyPreview ?: previewCandidate?.let { LibraryPreview(it) }
    val previewItem = activePreview?.item
    LaunchedEffect(sourceKey, previewCandidate?.let(::libraryPreviewKey), calendarMode) {
        if (!calendarMode) {
            val item = previewCandidate
            if (item == null) {
                readyPreview = null
                return@LaunchedEffect
            }
            delay(100)
            viewModel.ensureLogo(item)
            // Swap complete previews together; never clear the description between cards.
            val preview = viewModel.loadLibraryPreview(item)
            readyPreview = preview
        }
    }
    fun selectSource(source: WatchlistSourceItem) {
        viewModel.selectSource(source.id)
        sourcesOpen = false
    }
    fun selectSection(next: LibrarySection) {
        section = next; query = ""; mediaFilter = null
        if (next == LibrarySection.WATCHLISTS) viewModel.selectSource(WatchlistSourceItem.MyWatchlist.id)
    }
    BackHandler(!search && !filters && !sourcesOpen) {
        when { !touch && !topFocused -> topFocus.requestFocus(); else -> onBack() }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, viewModel) {
        val observer = LifecycleEventObserver { _, event -> if(event == Lifecycle.Event.ON_RESUME) viewModel.refreshAfterResume() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val loadingMore = state.isLoadingMore
    val hasMore = state.hasMore
    LaunchedEffect(grid, sourceKey, items.size, rawItems.size, error, loading, loadingMore, hasMore) {
        snapshotFlow { grid.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }.collect { last ->
            if (!calendarMode && error == null && !loading && !loadingMore && hasMore && (items.isEmpty() || last >= items.size - 16)) {
                viewModel.loadMoreActiveSource()
            }
        }
    }
    LaunchedEffect(grid, sourceKey, items, poster) {
        if (!calendarMode && !poster) snapshotFlow { grid.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }.collect { last ->
            val first = grid.firstVisibleItemIndex
            viewModel.prefetchLogos(items.subList(first.coerceAtMost(items.size), (last + 17).coerceAtMost(items.size)))
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black).testTag("oled-library")) {
        val compact = touch && maxWidth < 700.dp
        val sideWidth = if (touch) 160.dp else 126.dp
            if (!touch) {
                Box(Modifier.fillMaxWidth().height(AppTopBarContentTopInset).focusRequester(topFocus).onFocusChanged { topFocused = it.isFocused }
                    .onKeyEvent { event ->
                        // The top bar's index runs left-to-right, but Compose mirrors
                        // the row in an RTL locale, so the physical key has to be
                        // mirrored back before it reaches index arithmetic written
                        // against an LTR layout — otherwise the menu walks the wrong way.
                        if (event.type != KeyEventType.KeyDown) false else when(event.key.mirrorHorizontalForRtl(isRtl)) {
                            Key.DirectionLeft -> { topIndex = (topIndex - 1).coerceAtLeast(0); true }
                            Key.DirectionRight -> { topIndex = (topIndex + 1).coerceAtMost(topBarMaxIndex()); true }
                            Key.DirectionDown -> { firstTab.requestFocus(); true }
                            Key.Enter, Key.DirectionCenter, Key.NumPadEnter -> {
                                when(topBarFocusedItem(topIndex)) {
                                    SidebarItem.HOME -> onNavigateToHome(); SidebarItem.SEARCH -> onNavigateToSearch()
                                    SidebarItem.SETTINGS -> onNavigateToSettings(null)
                                    SidebarItem.WATCHLIST -> firstTab.requestFocus(); null -> Unit
                                }; true
                            }
                            else -> false
                        }
                    }.focusable()) {
                    AppTopBar(SidebarItem.WATCHLIST, topFocused, topIndex)
                }
            }
        Column(Modifier.fillMaxSize().padding(top = if (touch) 0.dp else LibraryTabsTopInset)
            .padding(horizontal = if (compact) 16.dp else 26.dp)) {
            if (touch) Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth().testTag("library-section-tabs").then(if (compact) Modifier.horizontalScroll(rememberScrollState()) else Modifier)
                .heightIn(min = 48.dp)
                .padding(bottom = if(compact) 10.dp else 6.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 8.dp)) {
                LibrarySection.entries.forEachIndexed { index, entry ->
                    OledControl(tr(entry.label), selected = section == entry,
                        modifier = (if(index == 0) Modifier.focusRequester(firstTab).onGloballyPositioned {
                            if(!touch && !initialFocusPlaced) { initialFocusPlaced = true; firstTab.requestFocus() }
                        } else if (entry == LibrarySection.CALENDAR) Modifier.focusRequester(calendarTab) else Modifier), compact = compact,
                        onClick = { selectSection(entry) })
                }
                if (!compact && !calendarMode) {
                    Spacer(Modifier.weight(1f))
                    Text("${items.size}${if(hasMore) "+" else ""} ${tr("titles")}", color = Color.LightGray, fontSize = 13.sp)
                    OledControl("⌕", onClick = { sourcesOpen = false; filters = false; search = true })
                    OledControl(tr("Filters"), modifier = Modifier.focusRequester(filterButton), onClick = { sourcesOpen = false; filters = true })
                }
            }
            if (calendarMode) {
                if (calendarStateOverride != null) LibraryCalendarPane(
                    state = calendarStateOverride, onSelectDate = onCalendarSelectDate,
                    onChangeMonth = onCalendarChangeMonth, onSelectSource = onCalendarSelectSource,
                    onRefresh = onCalendarRefresh, onOpenDetails = onNavigateToDetails, onOpenRelease = onCalendarOpenRelease,
                    modifier = Modifier.weight(1f), onExitUp = { calendarTab.requestFocus() })
                else LibraryCalendarRoute(onOpenDetails = onNavigateToDetails, onOpenRelease = onCalendarOpenRelease,
                    modifier = Modifier.weight(1f), onExitUp = { calendarTab.requestFocus() },
                    viewModel = calendarViewModel ?: hiltViewModel())
            } else {
            if (compact) Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OledControl(scopeSources.firstOrNull { it.id == selectedId }?.title ?: tr("Choose library"),
                    modifier = Modifier.weight(1f).testTag("library-source-picker"), onClick = { sourcesOpen = true })
                OledControl("⌕", onClick = { sourcesOpen = false; filters = false; search = true })
                OledControl(tr("Filters"), modifier = Modifier.focusRequester(filterButton), onClick = { sourcesOpen = false; filters = true })
            }
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                if (!compact) {
                    OledSources(scopeSources, selectedId, Modifier.width(sideWidth), ::selectSource, onNavigateToSettings)
                }
                BoxWithConstraints(Modifier.weight(1f).fillMaxHeight()) {
                    val columns = libraryColumns(maxWidth.value.toInt(), poster)
                    val width = (maxWidth - 12.dp * (columns - 1)) / columns
                    val previewCatalog = remember(sourceKey, state.selectedSource.title, scopeSources, selectedId) {
                        CatalogConfig(sourceKey, scopeSources.firstOrNull { it.id == selectedId }?.title
                            ?: state.selectedSource.title, CatalogSourceType.PREINSTALLED)
                    }
                    val spotlightItem = previewItem
                    val compactSpotlight = compact || maxHeight < 440.dp
                    val showSpotlight = items.isNotEmpty()
                    if (showSpotlight) CategoryBackdrop(previewCatalog, spotlightItem, touch,
                        categorySpotlightHeight(touch, compactSpotlight) + 150.dp,
                        background = Color.Black)
                    Column(Modifier.fillMaxSize()) {
                    if (showSpotlight) CategorySpotlight(previewCatalog, spotlightItem, touch, compactSpotlight, {},
                        providerLogoUrl = activePreview?.providerLogoUrl ?: spotlightItem?.primaryNetworkLogo,
                        clearLogoUrl = previewItem?.let { logos[watchlistLogoKey(it)] },
                        horizontalInset = 6.dp, showBackButton = false, topInset = 16.dp)
                    // Row anchoring keeps the previous row entirely outside this viewport.
                    BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().clipToBounds()) {
                    val rowHeight by remember(grid) { derivedStateOf {
                        grid.layoutInfo.visibleItemsInfo.maxOfOrNull { it.size.height } ?: 0
                    } }
                    val minimumBottomPadding = 16.dp + LocalBottomBarInset.current
                    val bottomPadding = if (touch) minimumBottomPadding else with(LocalDensity.current) {
                        libraryGridBottomPadding(maxHeight.roundToPx(), rowHeight,
                            8.dp.roundToPx(), minimumBottomPadding.roundToPx()).toDp()
                    }
                    // Touch keeps native scrolling; TV moves once per row, never per column.
                    CompositionLocalProvider(LocalBringIntoViewSpec provides
                        if (touch) LocalBringIntoViewSpec.current else LibraryTvBringIntoViewSpec) {
                    if (loading && items.isEmpty()) {
                        CircularProgressIndicator(Modifier.align(Alignment.Center), color = resolveAccentColor(Color.White))
                    } else if (items.isEmpty()) {
                        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                            OledMessage(if(error != null) tr("Library unavailable") else tr("No titles found"), error ?: tr("Choose a source or add titles to your watchlist."))
                            if(error != null) OledControl(tr("Retry"), onClick = { viewModel.refresh() })
                        }
                    } else LazyVerticalGrid(GridCells.Fixed(columns), state = grid,
                        modifier = Modifier.fillMaxSize().arvioDpadFocusGroup(enableFocusRestorer = false)
                            .clipToBounds().testTag("library-grid"),
                        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(18.dp),
                        contentPadding = PaddingValues(top = 8.dp, bottom = bottomPadding)) {
                        itemsIndexed(items, key = { index, item -> watchlistItemKey(item, index) }) { index, item ->
                            LaunchedEffect(watchlistLogoKey(item), poster) { if(!poster) viewModel.ensureLogo(item) }
                            Box(Modifier.padding(6.dp).testTag("library-card-frame-$index")) {
                            MediaCard(item, width = width - 12.dp, isLandscape = !poster, logoImageUrl = logos[watchlistLogoKey(item)],
                                focusedScale = 1.025f, titleMaxLines = 1, showTitle = true,
                                modifier = Modifier.testTag("library-card-$index"),
                                onFocused = { focusedPreviewKey = libraryPreviewKey(item); viewModel.saveFocusState(0, index); revealCard(index, columns) },
                                onClick = { onNavigateToDetails(item.mediaType, item.id) },
                                onLongClick = if(state.selectedSourceId == WatchlistSourceItem.MyWatchlist.id) ({ viewModel.removeFromWatchlist(item) }) else null)
                            }
                        }
                        if (state.isLoadingMore) item("loading-more", span = { GridItemSpan(maxLineSpan) }) {
                            Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(Modifier.size(24.dp), color = resolveAccentColor(Color.White))
                            }
                        }
                        if(error != null) item("retry-page", span = { GridItemSpan(maxLineSpan) }) {
                            OledControl(tr("Could not update this source. Retry"), onClick = { viewModel.refresh() })
                        }
                    }
                    }
                    }
                    }
                }
            }
            }
        }
        state.toastMessage?.let { message ->
            Toast(message = message, isVisible = true, onDismiss = viewModel::dismissToast)
        }
    }
    if(sourcesOpen) OledDrawer(tr("Sources"), { sourcesOpen = false }) {
        OledSources(scopeSources, selectedId, Modifier.fillMaxWidth().heightIn(max = 500.dp), ::selectSource, onNavigateToSettings)
    }
    if(filters) OledDrawer(tr("Filters"), { filters = false; if(!touch) filterButton.requestFocus() }) {
        Text(tr("Sort"), color = Color.LightGray, modifier = Modifier.padding(vertical = 12.dp))
        listOf("Recently added" to LibrarySort.RECENTLY_ADDED, "Title A-Z" to LibrarySort.TITLE,
            "Highest rated" to LibrarySort.RATING, "Newest release" to LibrarySort.RELEASE_DATE_NEWEST,
            "Oldest release" to LibrarySort.RELEASE_DATE_OLDEST).forEach { (label, value) ->
            OledControl(tr(label), selected = sort == value, modifier = Modifier.fillMaxWidth(), onClick = { sort = value })
        }
        run {
            Text(tr("Type"), color = Color.LightGray, modifier = Modifier.padding(vertical = 12.dp))
            listOf("All" to null, "Movies" to MediaType.MOVIE, "Series" to MediaType.TV).forEach { (label, value) ->
                OledControl(tr(label), selected = mediaFilter == value, modifier = Modifier.fillMaxWidth(), onClick = { mediaFilter = value })
            }
            OledControl(tr("Refresh"), onClick = { viewModel.refresh() })
        }
    }
    TextInputModal(search, tr("Search library"), initialValue = query,
        onConfirm = { query = it.trim(); search = false }, onCancel = { search = false })
}

@Composable
internal fun OledControl(label: String, modifier: Modifier = Modifier, selected: Boolean = false, compact: Boolean = false, icon: ImageVector? = null, maxLines: Int = 1, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val accent = resolveAccentColor(Color.White)
    val foreground = if(focused) { if(accent.luminance() > .4f) Color.Black else Color.White } else Color.White
    val background by animateColorAsState(if(focused) accent else if(selected) Color(0xFF242426) else Color.Transparent, tween(120), label = "library-control")
    Box(modifier.onFocusChanged { focused = it.isFocused }.clip(RoundedCornerShape(7.dp))
        .background(background)
        .clickable(onClick = onClick).padding(horizontal = if(compact) 8.dp else 12.dp, vertical = 11.dp), contentAlignment = Alignment.CenterStart) {
        if(label == "⌕") Icon(Icons.Outlined.Search, contentDescription = tr("Search library"), tint = foreground, modifier = Modifier.size(20.dp))
        else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        if(icon != null) Icon(icon, contentDescription = null, tint = foreground, modifier = Modifier.size(20.dp))
        Text(label, color = foreground, fontSize = if(compact) 13.sp else 14.sp, fontWeight = if(selected || focused) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = maxLines, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun OledSources(sources: List<WatchlistSourceItem>, selectedId: String, modifier: Modifier,
    select: (WatchlistSourceItem) -> Unit, settings: (String) -> Unit) {
    val groups = sources.groupBy { when(it) {
        is WatchlistSourceItem.MyWatchlist -> tr("Saved")
        else -> it.subtitle ?: tr("Lists")
    } }
    LazyColumn(modifier.testTag("library-sources"), verticalArrangement = Arrangement.spacedBy(4.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
        groups.forEach { (group, entries) ->
            item("group:$group") {
                Column(Modifier.padding(top = 24.dp, bottom = 10.dp, start = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {

                        Text(group.uppercase(), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = .6.sp, maxLines = 1)
                    }
                }
            }
            items(entries, key = { it.id }) { source -> OledControl(tr(source.title), selected = source.id == selectedId, modifier = Modifier.fillMaxWidth().testTag("library-source-${source.id}"), onClick = { select(source) }) }
        }
    }
}

@Composable private fun OledMessage(title: String, detail: String) {
    Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, color = Color.White, fontSize = 19.sp)
        Text(detail, color = Color.Gray, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun OledDrawer(title: String, close: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val initial = remember { FocusRequester() }
    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().testTag("library-drawer-root").background(Color.Black.copy(alpha = .55f))) {
            Column(Modifier.align(Alignment.CenterEnd).fillMaxHeight().widthIn(max = 340.dp).fillMaxWidth()
                .background(Color(0xFF0C0C0E)).padding(24.dp).verticalScroll(rememberScrollState())) {
                OledControl("${tr("Close")} ×", modifier = Modifier.focusRequester(initial), onClick = close)
                Text(title, color = Color.White, fontSize = 24.sp, modifier = Modifier.padding(vertical = 12.dp))
                content()
            }
        }
        LaunchedEffect(Unit) { initial.requestFocus() }
    }
}
