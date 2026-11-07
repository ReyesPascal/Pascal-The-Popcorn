package com.arflix.tv.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.tv.foundation.lazy.grid.TvGridCells
import androidx.tv.foundation.PivotOffsets
import androidx.tv.foundation.lazy.grid.TvLazyVerticalGrid
import androidx.tv.foundation.lazy.grid.itemsIndexed
import com.arflix.tv.R
import com.arflix.tv.data.model.CatalogConfig
import com.arflix.tv.data.model.MediaItem
import com.arflix.tv.data.model.MediaType
import com.arflix.tv.ui.components.LocalBottomBarInset
import com.arflix.tv.ui.components.MediaCard
import com.arflix.tv.ui.focus.arvioDpadFocusGroup
import com.arflix.tv.ui.screens.home.stableHomeRowItemKeys
import com.arflix.tv.ui.theme.ArflixTypography
import com.arflix.tv.ui.theme.appBackgroundDark
import com.arflix.tv.ui.theme.TextPrimary
import com.arflix.tv.ui.theme.TextSecondary
import com.arflix.tv.util.LocalDeviceType
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.imageLoader
import com.arflix.tv.ui.components.ImdbSvgRatingBadge
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale


enum class CategoryTab { MOVIES, SERIES }

internal data class CategoryGridLayout(val columns: Int, val cardWidthDp: Int)

internal fun categoryGridLayout(containerWidthDp: Int, mobile: Boolean, posters: Boolean): CategoryGridLayout {
    val available = (containerWidthDp - if (mobile) 40 else 84).coerceAtLeast(1)
    val width = when {
        posters && mobile -> HOME_MOBILE_POSTER_CARD_WIDTH_DP
        posters -> HOME_TV_POSTER_CARD_WIDTH_DP
        mobile -> HOME_MOBILE_LANDSCAPE_CARD_WIDTH_DP
        else -> HOME_TV_LANDSCAPE_CARD_WIDTH_DP
    }.coerceAtMost(available)
    if (mobile) {
        // Touch grids use the available width; a phone must not become a half-empty single column.
        val minimumWidth = if (posters) 110 else 150
        val columns = ((available + 12) / (minimumWidth + 12)).coerceAtLeast(1)
        return CategoryGridLayout(columns, (available - (columns - 1) * 12) / columns)
    }
    return CategoryGridLayout(((available + 12) / (width + 12)).coerceAtLeast(1), width)
}

@Composable
private fun CategoryTabBar(
    hasMovies: Boolean,
    hasSeries: Boolean,
    selectedTab: CategoryTab,
    moviesTabFocusRequester: FocusRequester,
    seriesTabFocusRequester: FocusRequester,
    onTabSelected: (CategoryTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val showMovies = hasMovies || !hasSeries
    val showSeries = hasSeries || !hasMovies
    val onlyOne = showMovies xor showSeries
    Row(
        modifier = modifier
            .fillMaxWidth()
            .arvioDpadFocusGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {

        if (showMovies) {
            CategoryTabChip(
                label = stringResource(R.string.movies),
                isSelected = selectedTab == CategoryTab.MOVIES || onlyOne,
                focusRequester = moviesTabFocusRequester,
                onClick = { onTabSelected(CategoryTab.MOVIES) }
            )
        }
        if (showSeries) {
            CategoryTabChip(
                label = stringResource(R.string.series),
                isSelected = selectedTab == CategoryTab.SERIES || onlyOne,
                focusRequester = seriesTabFocusRequester,
                onClick = { onTabSelected(CategoryTab.SERIES) }
            )
        }
    }
}

@Composable
private fun CategoryTabChip(
    label: String,
    isSelected: Boolean,
    focusRequester: FocusRequester,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(6.dp)
    val bg = when {
        isSelected -> Color.White
        isFocused -> Color.Transparent
        else -> Color.Transparent
    }
    val fg = when {
        isSelected -> appBackgroundDark()
        isFocused -> Color.White
        else -> TextPrimary.copy(alpha = 0.75f)
    }
    val borderColor = when {
        isSelected && isFocused -> Color(0xFF4F7FB0)
        isFocused -> Color.White
        else -> Color.Transparent
    }
    val borderWidth = when {
        isSelected && isFocused -> 1.dp
        isFocused -> 2.dp
        else -> 0.dp
    }
    Box(
        modifier = Modifier
            .clip(shape)
            .background(bg)
            .border(width = borderWidth, color = borderColor, shape = shape)
            .focusRequester(focusRequester)
            .onFocusChanged { isFocused = it.isFocused }
            .onPreviewKeyEvent { event ->
                event.type == KeyEventType.KeyDown && event.key == Key.DirectionUp
            }
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 7.dp)
    ) {
        androidx.tv.material3.Text(
            text = label,
            style = ArflixTypography.sectionTitle.copy(
                fontSize = 13.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                letterSpacing = 0.sp
            ),
            color = fg
        )
    }
}

@Composable
internal fun CategoryItemsGrid(
    catalog: CatalogConfig?,
    onBack: () -> Unit,
    items: List<MediaItem>,
    usePosterCards: Boolean,
    gridState: androidx.tv.foundation.lazy.grid.TvLazyGridState,
    pendingFocusIndex: Int,
    onClearPendingFocus: () -> Unit,
    hasMovies: Boolean,
    hasSeries: Boolean,
    cardLogoUrls: Map<String, String>,
    selectedTab: CategoryTab,
    moviesTabFocusRequester: FocusRequester,
    seriesTabFocusRequester: FocusRequester,
    onTabSelected: (CategoryTab) -> Unit,
    onItemClick: (MediaItem) -> Unit,
    onItemFocused: (MediaItem, Int) -> Unit,
    onVisibleItemsChanged: (List<MediaItem>) -> Unit,
    onVisibleDetailsChanged: (List<MediaItem>) -> Unit,
    onNearEnd: () -> Unit,
    isLoading: Boolean,
    isLoadingMore: Boolean,
    emptyMessage: String,
    topContentPadding: androidx.compose.ui.unit.Dp,
    onPreviewItemChanged: (MediaItem) -> Unit = {},
    providerLogos: Map<String, String?> = emptyMap(),
    showTabs: Boolean = true,
    previewItemOverride: MediaItem? = null,
    onVisiblePositionChanged: (Int, Int) -> Unit = { _, _ -> }
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
    val isMobile = LocalDeviceType.current.isTouchDevice()
    val layout = categoryGridLayout(maxWidth.value.toInt(), isMobile, usePosterCards)
    val gridColumns = layout.columns
    val cardWidth = layout.cardWidthDp.dp
    val compact = LocalConfiguration.current.screenHeightDp < 480
    val headerItems = if (showTabs) 1 else 0
    val itemKeys = remember(catalog?.id, items) { stableHomeRowItemKeys(catalog?.id.orEmpty(), items) }
    var focusedKey by rememberSaveable(catalog?.id, selectedTab) { mutableStateOf<String?>(null) }
    var previewKey by remember(catalog?.id, selectedTab) { mutableStateOf<String?>(null) }
    LaunchedEffect(focusedKey) {
        // Focus feedback stays immediate; only expensive artwork waits for rapid navigation to settle.
        delay(100)
        previewKey = focusedKey
    }
    val focusedItem = remember(items, itemKeys, previewKey) {
        items.getOrNull(itemKeys.indexOf(previewKey)) ?: items.firstOrNull()
    }
    val previewItem = previewItemOverride?.takeIf {
        it.id == focusedItem?.id && it.mediaType == focusedItem?.mediaType
    } ?: focusedItem
    val latestPreviewChanged by rememberUpdatedState(onPreviewItemChanged)
    LaunchedEffect(focusedItem?.mediaType, focusedItem?.id) {
        focusedItem?.let { latestPreviewChanged(it) }
    }
    val cardContentType = if (usePosterCards) "poster_card" else "landscape_card"
    val focusBleedPadding = if (usePosterCards) 10.dp else 6.dp
    val scrollScope = rememberCoroutineScope()
    var revealJob by remember(gridState) { mutableStateOf<Job?>(null) }
    var focusedRow by remember(gridState) { mutableStateOf(-1) }
    val focusBleedPx = with(androidx.compose.ui.platform.LocalDensity.current) { focusBleedPadding.toPx() }
    val latestItems by rememberUpdatedState(items)
    val latestGridColumns by rememberUpdatedState(gridColumns)
    val latestOnVisibleItemsChanged by rememberUpdatedState(onVisibleItemsChanged)
    val latestOnVisibleDetailsChanged by rememberUpdatedState(onVisibleDetailsChanged)
    val latestOnNearEnd by rememberUpdatedState(onNearEnd)
    val latestOnVisiblePositionChanged by rememberUpdatedState(onVisiblePositionChanged)
    LaunchedEffect(gridState, headerItems) {
        try {
            snapshotFlow {
                gridState.layoutInfo.visibleItemsInfo.mapNotNull { latestItems.getOrNull(it.index - headerItems) }
            }.distinctUntilChanged().collect { latestOnVisibleDetailsChanged(it) }
        } finally {
            latestOnVisibleDetailsChanged(emptyList())
        }
    }
    // Collect scroll position without restarting on page-load-size changes —
    // items.size used to live in the key, which relaunched the snapshotFlow on
    // every page append and caused a stutter frame during scroll.
    LaunchedEffect(gridState, headerItems) {
        snapshotFlow {
            val layout = gridState.layoutInfo
            val last = layout.visibleItemsInfo.lastOrNull()?.index ?: 0
            val mediaIndexes = layout.visibleItemsInfo
                .asSequence()
                .map { it.index - headerItems }
                .filter { it >= 0 }
                .toList()
            Triple(last, layout.totalItemsCount, mediaIndexes)
        }.distinctUntilChanged().collect { (last, total, mediaIndexes) ->
            latestOnVisiblePositionChanged(last - headerItems, latestGridColumns)
            if (total > 12 && last >= total - 3) latestOnNearEnd()
            if (mediaIndexes.isNotEmpty()) {
                val start = (mediaIndexes.minOrNull() ?: 0).coerceAtLeast(0)
                val currentItems = latestItems
                val end = ((mediaIndexes.maxOrNull() ?: start) + latestGridColumns)
                    .coerceAtMost(currentItems.lastIndex)
                if (start <= end) {
                    latestOnVisibleItemsChanged(currentItems.subList(start, end + 1))
                }
            }
        }
    }

    Box(Modifier.fillMaxSize().background(appBackgroundDark())) {
        CategoryBackdrop(catalog, previewItem, isMobile,
            categorySpotlightHeight(isMobile, compact) + 150.dp)
        Column(Modifier.fillMaxSize().padding(top = topContentPadding)) {
        CategorySpotlight(
            catalog, previewItem, isMobile, compact, onBack,
            providerLogoUrl = providerLogos["${previewItem?.mediaType}_${previewItem?.id}"]
                ?: previewItem?.primaryNetworkLogo,
            clearLogoUrl = cardLogoUrls["${previewItem?.mediaType}_${previewItem?.id}"]
        )
        TvLazyVerticalGrid(
        columns = TvGridCells.Fixed(gridColumns),
        state = gridState,
        // On TV, reveal whole rows ourselves; automatic pivot scrolling follows
        // the focus scale and fights the row animation. Touch retains native scrolling.
        userScrollEnabled = isMobile,
        pivotOffsets = PivotOffsets(parentFraction = 0f),
        modifier = Modifier.weight(1f).fillMaxWidth().arvioDpadFocusGroup().clipToBounds()
            .testTag("category_grid"),
        contentPadding = PaddingValues(
            start = if (isMobile) 20.dp else 42.dp,
            top = focusBleedPadding,
            end = if (isMobile) 20.dp else 42.dp,
            bottom = 48.dp + focusBleedPadding + LocalBottomBarInset.current
        ),
        verticalArrangement = Arrangement.spacedBy(if (usePosterCards) 18.dp else 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (showTabs) item(
            span = { androidx.tv.foundation.lazy.grid.TvGridItemSpan(maxLineSpan) },
            contentType = "header"
        ) {
            CategoryTabBar(
                hasMovies = hasMovies,
                hasSeries = hasSeries,
                selectedTab = selectedTab,
                moviesTabFocusRequester = moviesTabFocusRequester,
                seriesTabFocusRequester = seriesTabFocusRequester,
                onTabSelected = onTabSelected,
                modifier = Modifier.onFocusChanged { state ->
                    if (!isMobile && state.hasFocus) {
                        focusedRow = -1
                        revealJob?.cancel()
                        revealJob = scrollScope.launch { gridState.animateScrollToItem(0) }
                    }
                }
            )
        }
        if (isLoading && items.isEmpty()) {
            val cardHeight = if (usePosterCards) cardWidth * 1.5f else cardWidth * 9f / 16f
            itemsIndexed((1..gridColumns * 3).toList(), contentType = { _, _ -> "skeleton" }) { _, _ ->
                Box(Modifier.fillMaxWidth()) {
                Box(
                    modifier = (if (isMobile) Modifier.fillMaxWidth() else Modifier)
                        .width(cardWidth)
                        .height(cardHeight)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                )
                }
            }
        } else if (items.isEmpty() && !isLoadingMore) {
            item(
                span = { androidx.tv.foundation.lazy.grid.TvGridItemSpan(maxLineSpan) },
                contentType = "empty"
            ) {
                CategoryEmptyState(message = emptyMessage)
            }
        } else {
            itemsIndexed(
                items,
                key = { index, _ -> itemKeys[index] },
                contentType = { _, _ -> cardContentType }
            ) { index, item ->
                val cardLogoUrl = cardLogoUrls["${item.mediaType}_${item.id}"]
                val itemFocusRequester = remember { FocusRequester() }

                // Fires when scrollToItem brings this card into composition on back-navigation.
                // pendingFocusIndex is set by requestTabFocus() after scrolling to this item.
                LaunchedEffect(pendingFocusIndex) {
                    if (pendingFocusIndex == index) {
                        delay(50)
                        runCatching { itemFocusRequester.requestFocus() }
                        onClearPendingFocus()
                    }
                }

                // Touch cards fill their measured cell, including after a window resize.
                // TV cards keep Home's fixed width inside the wider grid slot.
                Box(Modifier.fillMaxWidth()) {
                MediaCard(
                    item = item,
                    width = cardWidth,
                    isLandscape = !usePosterCards,
                    logoImageUrl = cardLogoUrl,
                    showTitle = true,
                    titleMaxLines = if (usePosterCards) 2 else 1,
                    titleMinLines = if (usePosterCards) 2 else 1,
                    onFocused = {
                        // Reveal unscaled rows only. TV's automatic pivot follows the animated
                        // card bounds and otherwise nudges the grid on every sideways move.
                        val row = index / gridColumns
                        if (!isMobile && focusedRow != row) {
                            focusedRow = row
                            revealJob?.cancel()
                            revealJob = scrollScope.launch {
                                val layout = gridState.layoutInfo
                                val cell = layout.visibleItemsInfo.firstOrNull { it.index == index + headerItems }
                                if (cell != null) {
                                    val top = cell.offset.y.toFloat()
                                    val bottom = top + cell.size.height
                                    val minTop = layout.viewportStartOffset + focusBleedPx
                                    val maxBottom = layout.viewportEndOffset - focusBleedPx
                                    val delta = when {
                                        top < minTop -> top - minTop
                                        bottom > maxBottom -> bottom - maxBottom
                                        else -> 0f
                                    }
                                    if (delta != 0f) gridState.animateScrollBy(delta, tween(160))
                                } else {
                                    gridState.animateScrollToItem(row * gridColumns + headerItems, -focusBleedPx.toInt())
                                }
                            }
                        }
                        focusedKey = itemKeys[index]
                        onItemFocused(item, index)
                        if (items.size > 10 && index >= items.size - 2) onNearEnd()
                    },
                    onClick = { onItemClick(item) },
                    modifier = (if (isMobile) Modifier.fillMaxWidth() else Modifier)
                        .focusRequester(itemFocusRequester).testTag("category_item_$index")
                )
                }
            }
        }

        if (isLoadingMore) {
            item(
                span = { androidx.tv.foundation.lazy.grid.TvGridItemSpan(maxLineSpan) },
                contentType = "loading_more"
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.material3.CircularProgressIndicator(
                        color = Color(0xFF4F7FB0),
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
    }
    }
}

}

@Composable
private fun CategoryEmptyState(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(320.dp),
        contentAlignment = Alignment.Center
    ) {
        androidx.tv.material3.Text(
            text = message,
            color = TextSecondary,
            style = ArflixTypography.body
        )
    }
}

internal fun categoryBudget(item: MediaItem?, locale: Locale = Locale.getDefault()): String? {
    val budget = item?.budget?.takeIf { it > 0 && item.mediaType == MediaType.MOVIE } ?: return null
    return NumberFormat.getCurrencyInstance(locale).apply {
        currency = Currency.getInstance("USD")
        maximumFractionDigits = 0
    }.format(budget)
}

internal fun categoryRating(item: MediaItem?): String? {
    if (item == null) return null
    fun valid(value: String) = value.toDoubleOrNull()?.let { it.isFinite() && it > 0 && it <= 10 } == true
    return when {
        valid(item.imdbRating) -> "IMDb ${item.imdbRating}"
        valid(item.tmdbRating) -> "TMDB ${item.tmdbRating}"
        else -> null
    }
}

internal fun categoryCompactBudget(item: MediaItem?, locale: Locale = Locale.getDefault()): String? {
    val value = item?.budget?.takeIf { it > 0 && item.mediaType == MediaType.MOVIE } ?: return null
    val divisor = when {
        value >= 1_000_000_000L -> 1_000_000_000L
        value >= 1_000_000L -> 1_000_000L
        else -> return categoryBudget(item, locale)
    }
    return NumberFormat.getCurrencyInstance(locale).apply {
        currency = Currency.getInstance("USD")
        minimumFractionDigits = 0
        maximumFractionDigits = 1
    }.format(value.toDouble() / divisor) + if (divisor == 1_000_000L) "M" else "B"
}

internal fun categorySpotlightHeight(isMobile: Boolean, compact: Boolean): Dp = when {
    compact -> 174.dp
    isMobile -> 258.dp
    else -> 198.dp
}

/** One backdrop continues behind the header and grid, rather than ending at their boundary. */
@Composable
internal fun CategoryBackdrop(
    catalog: CatalogConfig?,
    item: MediaItem?,
    isMobile: Boolean,
    height: Dp,
    background: Color = appBackgroundDark()
) {
    val context = LocalContext.current
    val backdrop = item?.backdrop?.takeIf(String::isNotBlank)
        
    val request = remember(backdrop, context) {
        ImageRequest.Builder(context).data(backdrop).size(1280, 720).crossfade(160).build()
    }
    Box(Modifier.fillMaxWidth().height(height).testTag("category_backdrop")) {
        if (backdrop != null) {
            AsyncImage(request, null,
                Modifier.fillMaxHeight().fillMaxWidth(if (isMobile) 1f else 0.78f).align(Alignment.CenterEnd),
                contentScale = ContentScale.Crop, alignment = BiasAlignment(1f, -0.5f))
        }
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(
            0f to background, 0.25f to background, 0.40f to background.copy(alpha = 0.88f),
            0.65f to background.copy(alpha = 0.45f), 0.92f to Color.Transparent,
            1f to background
        )))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
            0f to background, 0.12f to background.copy(alpha = 0.35f), 0.25f to Color.Transparent,
            0.55f to background.copy(alpha = 0.18f), 0.78f to background.copy(alpha = 0.72f),
            1f to background
        )))
    }
}

/** Persistent preview above the grid, with artwork drawn separately behind the cards. */
@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun CategorySpotlight(
    catalog: CatalogConfig?,
    item: MediaItem?,
    isMobile: Boolean,
    compact: Boolean,
    onBack: () -> Unit,
    providerLogoUrl: String? = item?.primaryNetworkLogo,
    clearLogoUrl: String? = null,
    horizontalInset: Dp = if (isMobile) 20.dp else 42.dp,
    showBackButton: Boolean = isMobile,
    topInset: Dp = 10.dp
) {
    val context = LocalContext.current
    val inset = horizontalInset
    val height = categorySpotlightHeight(isMobile, compact)
    Box(Modifier.fillMaxWidth().height(height).testTag("category_spotlight")) {
        Column(
            Modifier.fillMaxSize().padding(start = inset, end = inset, top = topInset, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 5.dp else 7.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (showBackButton) {
                    IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), tint = Color.White)
                    }
                }
                Text(catalog?.title.orEmpty(), color = Color(0xFFB6BBC2), fontSize = 12.sp,
                    fontWeight = FontWeight.Medium, letterSpacing = 0.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            val textWidth = if (isMobile) 1f else 0.62f
            CategoryTitle(
                title = item?.title ?: catalog?.title.orEmpty(),
                logoUrl = clearLogoUrl,
                isMobile = isMobile,
                compact = compact,
                modifier = Modifier.fillMaxWidth(textWidth)
            )
            val facts = listOfNotNull(
                item?.year?.takeIf(String::isNotBlank),
                item?.duration?.takeIf(String::isNotBlank),
                item?.contentRating?.takeIf(String::isNotBlank)
            ).joinToString("  ·  ")
            Text(facts, modifier = Modifier.fillMaxWidth(textWidth).testTag("category_spotlight_facts"),
                color = Color(0xFFD7DBDF), fontSize = 12.sp, letterSpacing = 0.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            val rating = categoryRating(item)
            val budget = remember(item?.budget, item?.mediaType) { categoryCompactBudget(item) }
            FlowRow(Modifier.fillMaxWidth(textWidth).testTag("category_spotlight_brands"),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)) {
                if (rating?.startsWith("IMDb ") == true) {
                    ImdbSvgRatingBadge(rating.removePrefix("IMDb "), context.imageLoader,
                        ratingFontSize = 13, logoWidth = 30.dp, logoHeight = 16.dp)
                } else if (rating != null) {
                    Text(rating, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
                if (!providerLogoUrl.isNullOrBlank()) {
                    val logo = remember(providerLogoUrl, context) {
                        ImageRequest.Builder(context).data(providerLogoUrl)
                            .bitmapConfig(android.graphics.Bitmap.Config.ARGB_8888)
                            .allowRgb565(false).size(156, 54).build()
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (rating != null) MetadataDivider()
                        AsyncImage(logo, stringResource(R.string.home_cd_primary_provider),
                            contentScale = ContentScale.Fit, alignment = Alignment.CenterStart,
                            modifier = Modifier.width(52.dp).height(18.dp).testTag("category_provider_logo"))
                    }
                }
                if (budget != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (rating != null || !providerLogoUrl.isNullOrBlank()) MetadataDivider()
                        Text("${stringResource(R.string.budget)} $budget", color = Color(0xFFB6BBC2),
                            fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            Text(item?.overview?.takeIf(String::isNotBlank) ?: "",
                modifier = Modifier.fillMaxWidth(textWidth).testTag("category_spotlight_overview"),
                color = Color(0xFFC4C8CE), fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 0.sp,
                maxLines = if (isMobile && !compact) 3 else 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
internal fun CategoryTitle(
    title: String,
    logoUrl: String?,
    isMobile: Boolean,
    compact: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val height = if (compact) 38.dp else 60.dp
    val url = logoUrl?.takeIf(String::isNotBlank)
    var logoReady by remember(title, url) { mutableStateOf(false) }
    val request = remember(url, context, density, height) {
        ImageRequest.Builder(context).data(url)
            .size(with(density) { 280.dp.roundToPx() }, with(density) { height.roundToPx() })
            .bitmapConfig(android.graphics.Bitmap.Config.ARGB_8888)
            .allowRgb565(false).crossfade(false).build()
    }
    // Keep the same slot during loading/failure so artwork cannot move the cards.
    Box(modifier.height(height), contentAlignment = Alignment.CenterStart) {
        if (url != null) {
            AsyncImage(
                model = request,
                contentDescription = title,
                contentScale = ContentScale.Fit,
                alignment = Alignment.CenterStart,
                modifier = Modifier.width(280.dp).fillMaxHeight().testTag("category_clearlogo"),
                onSuccess = { logoReady = true },
                onError = { logoReady = false }
            )
        }
        if (!logoReady) {
            Text(title, modifier = Modifier.fillMaxWidth().testTag("category_spotlight_title"),
                color = Color.White, fontSize = if (isMobile || compact) 23.sp else 28.sp,
                fontWeight = FontWeight.Bold, letterSpacing = 0.sp, lineHeight = 30.sp,
                maxLines = if (compact) 1 else 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun MetadataDivider() {
    Box(Modifier.width(1.dp).height(12.dp).background(Color.White.copy(alpha = 0.3f)))
}
