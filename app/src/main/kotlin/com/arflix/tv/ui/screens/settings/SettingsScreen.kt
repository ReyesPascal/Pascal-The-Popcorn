package com.arflix.tv.ui.screens.settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.TableRows
import androidx.compose.ui.text.font.FontWeight
import com.arflix.tv.data.model.AnimeStructuringStyle

import androidx.activity.compose.BackHandler
import com.arflix.tv.ui.components.LocalBottomBarInset
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.InputType
import android.text.method.PasswordTransformationMethod
import android.os.SystemClock
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.arflix.tv.BuildConfig
import com.arflix.tv.util.Constants
import androidx.compose.foundation.background
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.SwitchAccount
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.filled.Archive
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.asImageBitmap
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.PaddingValues
import androidx.activity.compose.BackHandler
import androidx.compose.material3.Icon
import com.arflix.tv.ui.components.LoadingIndicator
import com.arflix.tv.ui.components.dragReorderItem
import com.arflix.tv.ui.components.rememberDragReorderState
import com.arflix.tv.ui.components.MobileSettingsCategory
import com.arflix.tv.ui.components.MobileSettingsRow
import com.arflix.tv.ui.components.QrCodeImage
import com.arflix.tv.ui.components.Toast
import com.arflix.tv.ui.components.ToastType as ComponentToastType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.arflix.tv.ui.components.SettingsRow
import com.arflix.tv.ui.components.SettingsToggleRow
import com.arflix.tv.ui.components.settingsFocusOutline
import com.arflix.tv.ui.components.localizeSettingValue
import androidx.core.widget.doAfterTextChanged
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.arflix.tv.data.model.CatalogConfig
import com.arflix.tv.data.model.CatalogDiscoveryResult
import com.arflix.tv.data.model.CatalogKind
import com.arflix.tv.data.model.CatalogPackManifest
import com.arflix.tv.data.model.effectivePackId
import com.arflix.tv.data.model.isRemovable
import com.arflix.tv.data.model.needsConfiguration
import com.arflix.tv.data.model.settingsPageUrl
import com.arflix.tv.data.model.effectivePackName
import com.arflix.tv.data.model.isBulkDeletablePack
import com.arflix.tv.data.model.CatalogSourceType
import com.arflix.tv.data.model.RuntimeKind
import com.arflix.tv.ui.components.AppTopBar
import com.arflix.tv.ui.components.AppTopBarContentTopInset
import com.arflix.tv.ui.components.CatalogueRowLayoutToggleButton
import com.arflix.tv.util.LocalDeviceType
import com.arflix.tv.util.tr
import com.arflix.tv.util.trUpper
import com.arflix.tv.ui.components.SidebarItem
import com.arflix.tv.ui.components.toggleCatalogueRowLayoutMode
import com.arflix.tv.ui.components.topBarFocusedItem
import com.arflix.tv.ui.components.topBarMaxIndex
import com.arflix.tv.ui.focus.arvioDpadFocusGroup
import com.arflix.tv.ui.focus.isArvioDpadNavigationKey
import com.arflix.tv.ui.focus.rememberArvioDpadRepeatGate
import com.arflix.tv.ui.skin.resolveAccentColor
import com.arflix.tv.ui.theme.ArflixTypography
import com.arflix.tv.ui.theme.appBackgroundDark
import com.arflix.tv.ui.theme.BackgroundElevated
import com.arflix.tv.ui.theme.contrastingContentColor
import com.arflix.tv.ui.theme.Pink
import com.arflix.tv.ui.theme.SuccessGreen
import com.arflix.tv.ui.theme.TextPrimary
import com.arflix.tv.ui.theme.TextSecondary
import kotlin.math.abs
import androidx.compose.ui.res.stringResource
import com.arflix.tv.R
import com.arflix.tv.network.OkHttpProvider

/**
 * Per-section registry of [BringIntoViewRequester]s keyed by the row's
 * `contentFocusIndex`. Rows register via [settingsFocusSlot]; the scroll
 * effect invokes `bringIntoView()` on the requester for the focused index.
 *
 * This is Compose's native mechanism for nested-scroll focus-follow; it
 * correctly handles variable-height rows and arbitrary nesting depth
 * between the slot and the scrollable ancestor, replacing the old ratio
 * heuristic which failed on variable heights (the root cause of the
 * "focus moves but screen doesn't scroll" bug, e.g. reaching "Account").
 */
@OptIn(ExperimentalFoundationApi::class)
class SettingsFocusTracker {
    // Registration is only consumed by the focus coroutine on the main thread.
    // Keeping this as a regular map avoids invalidating the whole settings tree
    // once for every row that registers during composition.
    val requesters = mutableMapOf<Int, BringIntoViewRequester>()
    val coordinates = mutableMapOf<Int, androidx.compose.ui.layout.LayoutCoordinates>()
    var viewport: androidx.compose.ui.layout.LayoutCoordinates? = null

    fun revealDelta(index: Int): Float? {
        val parent = viewport?.takeIf { it.isAttached } ?: return null
        val child = coordinates[index]?.takeIf { it.isAttached } ?: return null
        return runCatching {
            val bounds = parent.localBoundingBoxOf(child, clipBounds = false)
            com.arflix.tv.ui.focus.focusRevealDelta(bounds.top, bounds.bottom, parent.size.height.toFloat())
        }.getOrNull()
    }
}

val LocalSettingsFocusTracker = compositionLocalOf<SettingsFocusTracker?> { null }

private const val ACCOUNT_DELETION_URL = "https://auth.arvio.tv/delete"

private fun isRepeatedSettingsSelect(event: androidx.compose.ui.input.key.KeyEvent): Boolean =
    event.type == KeyEventType.KeyDown &&
        (event.key == Key.Enter || event.key == Key.DirectionCenter || event.key == Key.NumPadEnter) &&
        event.nativeKeyEvent.repeatCount > 0

private val tvGeneralSectionIds = setOf(
    "language",
    "subtitles",
    "playback",
    "advanced",
    "appearance",
    "network"
)

private fun tvGeneralRowsForSection(section: String): List<Int> {
    return when (section) {
        "advanced" -> listOf(51, 52, 15, 40, 27)
        "language" -> listOf(0, 3, 1, 2)
        "subtitles" -> listOf(4, 5, 6, 7, 42, 8, 39, 9, 45)
        // 51 (show all sources) and 52 (this TV / re-scan) exist only in the personal build.
        "playback" -> listOf(15, 40, 27)
        "appearance" -> listOf(18, 20, 21, 24, 23, 46, 36)
        "network" -> listOf(25, 26, 35)
        else -> emptyList()
    }
}

/**
 * Scroll offset that puts a row in the MIDDLE of a lazy list instead of flush
 * against its top edge.
 *
 * `animateScrollToItem(index)` aligns the row with the top, which leaves no
 * context above it - on the categories screen that means a group being moved
 * around is always the topmost visible row. A negative offset pushes it down by
 * half the remaining viewport. LazyColumn clamps at both ends by itself, so the
 * first and last rows still sit flush and nothing scrolls into empty space.
 *
 * Returns 0 while the list has not been measured yet ([viewportSize] or
 * [itemSize] still 0), which falls back to the old top alignment for one frame.
 */
internal fun centeredScrollOffset(viewportSize: Int, itemSize: Int): Int =
    if (viewportSize <= 0 || itemSize <= 0 || itemSize >= viewportSize) 0
    else -((viewportSize - itemSize) / 2)

/**
 * the code in `callbackURL`, so the user never has to type it. Falls back to the plain
 * verification URL when no code is available yet.
 */


private fun openExternalUrl(context: Context, url: String) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

/** Resolves a settings message and its nested arguments against the app language. */
@Composable
private fun SettingsMessage.localizedText(): String = when (this) {
    is SettingsMessage.Raw -> text
    is SettingsMessage.Res -> {
        val args = mutableListOf<Any>()
        formatArgs.forEach { arg ->
            args += if (arg is SettingsMessage) arg.localizedText() else arg
        }
        stringResource(resourceId, *args.toTypedArray())
    }
}

@Composable
private fun formatUserAgentPreview(value: String?, maxLength: Int): String {
    val safeValue = value.orEmpty()
    val displayValue = safeValue.ifBlank { stringResource(R.string.settings_ua_default) }
    val preview = displayValue.take(maxLength)
    return if (displayValue.length > maxLength) "$preview..." else preview
}

/**
 * Attach to the outermost modifier of a focusable settings row so that when
 * it gains focus the scroll container brings it fully into view. Pass the
 * same [index] the parent uses as its `focusedIndex == N` comparator.
 *
 * Sections that don't adopt this modifier fall back to the legacy ratio
 * scroll — this modifier is purely additive, non-regressive.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.settingsFocusSlot(index: Int): Modifier {
    val tracker = LocalSettingsFocusTracker.current ?: return this
    if (!LocalDeviceType.current.isTouchDevice()) {
        val owner = remember(tracker, index) { arrayOfNulls<androidx.compose.ui.layout.LayoutCoordinates>(1) }
        DisposableEffect(tracker, index) {
            onDispose { if (tracker.coordinates[index] === owner[0]) tracker.coordinates.remove(index) }
        }
        return this.testTag("settings-focus-$index").onGloballyPositioned {
            owner[0] = it
            tracker.coordinates[index] = it
        }
    }
    val requester = remember(index) { BringIntoViewRequester() }
    DisposableEffect(tracker, index, requester) {
        tracker.requesters[index] = requester
        onDispose {
            if (tracker.requesters[index] === requester) {
                tracker.requesters.remove(index)
            }
        }
    }
    return this.bringIntoViewRequester(requester)
}

/**
 * Settings screen
 */
@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    initialSection: String? = null,
    installPackUrl: String? = null,
    installAddonUrl: String? = null,
    onNavigateToHome: () -> Unit = {},
    onNavigateToSearch: () -> Unit = {},
    onNavigateToWatchlist: () -> Unit = {},
    onBack: () -> Unit = {},
    onSubPageChanged: (Boolean) -> Unit = {},
    onOpenTvCheck: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isTouchDevice = LocalDeviceType.current.isTouchDevice()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isRtlLayoutDirection = LocalLayoutDirection.current == LayoutDirection.Rtl

    val sections = remember {
        buildList {
            add(if (BuildConfig.PERSONAL_BUILD) "advanced" else "playback")
            if (!BuildConfig.PERSONAL_BUILD) add("language")
            if (!BuildConfig.PERSONAL_BUILD) add("subtitles")
            add("stremio")
            add("catalogs")
            add("appearance")
            add("network")
        }
    }

    val initialSectionIdx = remember(initialSection, sections) {
        initialSection?.let { sections.indexOf(if (BuildConfig.PERSONAL_BUILD && it == "playback") "advanced" else it) }?.takeIf { it >= 0 }
    }



    val arrivesAtTopLevel = initialSection == null
    var isSidebarFocused by remember { mutableStateOf(arrivesAtTopLevel) }
    val maxSidebarIndex = topBarMaxIndex()
    var sidebarFocusIndex by remember { mutableIntStateOf(topBarMaxIndex()) } // SETTINGS gear is always the last index
    var sectionIndex by remember { mutableIntStateOf(initialSectionIdx ?: 0) }
    var mobilePage by remember {
        mutableStateOf(
            when (initialSection) {
                "stremio" -> "Addons"
                else -> "MAIN"
            }
        )
    }
    val currentOnSubPageChanged by rememberUpdatedState(onSubPageChanged)
    LaunchedEffect(mobilePage) {
        currentOnSubPageChanged(mobilePage != "MAIN")
    }
    DisposableEffect(Unit) {
        onDispose {
            currentOnSubPageChanged(false)
        }
    }
    var contentFocusIndex by remember { mutableIntStateOf(0) }
    // Personal build: "Show all sources" and the saved TV capability summary.
    val showAllSources by remember(context) {
        com.arflix.tv.util.PersonalPrefs.showAllSourcesFlow(context)
    }.collectAsStateWithLifecycle(initialValue = false)
    // Personal 1.0.1: the summary follows the effective profile (scan + owner corrections), so it
    // updates after a scan, save or reset; the row opens the TV check screen.
    remember(context) { if (BuildConfig.PERSONAL_BUILD) com.arflix.tv.util.TvOverridesStore.effective(context) }
    val tvEffective by com.arflix.tv.util.TvOverridesStore.effectiveFlow.collectAsStateWithLifecycle()
    val tvSummary = if (BuildConfig.PERSONAL_BUILD) tvEffective?.summary else null
    val openTvCheck: () -> Unit = { onOpenTvCheck() }
    val toggleShowAllSources: (Boolean) -> Unit = { enabled ->
        scope.launch { com.arflix.tv.util.PersonalPrefs.setShowAllSources(context, enabled) }
    }
    var activeZone by remember { mutableStateOf(if (arrivesAtTopLevel) Zone.SIDEBAR else Zone.CONTENT) }

    // Sub-focus for addon rows: 0 = toggle, 1 = delete
    var addonActionIndex by remember { mutableIntStateOf(0) }
    // Sub-focus for catalog rows: 0 = edit, 1 = up, 2 = down, 3 = layout, 4 = delete
    var catalogActionIndex by remember { mutableIntStateOf(0) }
    // Rename dialog state
    var showCatalogRename by remember { mutableStateOf(false) }
    var renameCatalogId by remember { mutableStateOf("") }
    var renameCatalogTitle by remember { mutableStateOf("") }

    // Catalog Pack states
    var showCatalogPackInput by remember { mutableStateOf(false) }
    var catalogPackInputUrl by remember { mutableStateOf("") }
    var showDeletePackConfirm by remember { mutableStateOf(false) }
    var deletePackId by remember { mutableStateOf("") }
    var deletePackName by remember { mutableStateOf("") }

    LaunchedEffect(installPackUrl) {
        if (!installPackUrl.isNullOrBlank()) {
            viewModel.loadPackManifest(installPackUrl)
        }
    }

    // Saved so the link is not offered again when the user comes back to this screen.
    var handledInstallAddonUrl by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(installAddonUrl) {
        if (!installAddonUrl.isNullOrBlank() && installAddonUrl != handledInstallAddonUrl) {
            handledInstallAddonUrl = installAddonUrl
            viewModel.requestAddonInstall(installAddonUrl, fromLink = true)
        }
    }
    // TV only: the addon whose settings page is shown as a QR code.
    var addonConfigureTarget by remember { mutableStateOf<com.arflix.tv.data.model.Addon?>(null) }
    var showCommunityAddons by remember { mutableStateOf(false) }
    LaunchedEffect(showCommunityAddons) {
        if (showCommunityAddons) viewModel.loadCommunityAddons()
    }

    // Input modal states
    var showCustomAddonInput by remember { mutableStateOf(false) }
    var customAddonUrl by remember { mutableStateOf("") }
    var showCatalogInput by remember { mutableStateOf(false) }
    var catalogInputUrl by remember { mutableStateOf("") }
    var showSubtitlePicker by remember { mutableStateOf(false) }
    var subtitlePickerIndex by remember { mutableIntStateOf(0) }
    var showSecondarySubtitlePicker by remember { mutableStateOf(false) }
    var secondarySubtitlePickerIndex by remember { mutableIntStateOf(0) }
    var showAudioLanguagePicker by remember { mutableStateOf(false) }
    var audioLanguagePickerIndex by remember { mutableIntStateOf(0) }
    var showDnsProviderPicker by remember { mutableStateOf(false) }
    var dnsProviderPickerIndex by remember { mutableIntStateOf(0) }
    var showContentLanguagePicker by remember { mutableStateOf(false) }
    var contentLanguagePickerIndex by remember { mutableIntStateOf(0) }
    var showUiModeWarningDialog by remember { mutableStateOf(false) }
    var nextUiMode by remember { mutableStateOf("") }
    var showCustomUserAgentDialog by remember { mutableStateOf(false) }
    var qualityFilterRegexPattern by remember { mutableStateOf("") }

    val stremioAddons = remember(uiState.addons) {
        uiState.addons.filter { it.runtimeKind == RuntimeKind.STREMIO }
    }

    val sectionMaxIndex: (String) -> Int = { section ->
        when (section) {
            in tvGeneralSectionIds -> (tvGeneralRowsForSection(section).size - 1).coerceAtLeast(0)
            "catalogs" -> uiState.catalogs.size + 1 // Add + Import + catalogs
            "stremio" -> stremioAddons.size + 2 // rows + refresh + community + add button
            else -> 0
        }
    }

    val focusRequester = remember { FocusRequester() }
    val scrollState = key(sectionIndex) { rememberScrollState() }
    val sectionScrollState = rememberScrollState()
    val focusTracker = remember(sectionIndex) { SettingsFocusTracker() }
    val sectionFocusTracker = remember { SettingsFocusTracker() }
    val openSubtitlePicker = {
        viewModel.refreshSubtitleOptions()
        val options = uiState.subtitleOptions
        subtitlePickerIndex = options.indexOfFirst { it.equals(uiState.defaultSubtitle, ignoreCase = true) }
            .coerceAtLeast(0)
        showSubtitlePicker = true
    }
    val openSecondarySubtitlePicker = {
        viewModel.refreshSubtitleOptions()
        val options = uiState.subtitleOptions
        secondarySubtitlePickerIndex = options.indexOfFirst { it.equals(uiState.secondarySubtitle, ignoreCase = true) }
            .coerceAtLeast(0)
        showSecondarySubtitlePicker = true
    }
    val openAudioLanguagePicker = {
        viewModel.refreshAudioLanguageOptions()
        val options = uiState.audioLanguageOptions
        audioLanguagePickerIndex = options.indexOfFirst { it.equals(uiState.defaultAudioLanguage, ignoreCase = true) }
            .coerceAtLeast(0)
        showAudioLanguagePicker = true
    }
    val openDnsProviderPicker = {
        val options = uiState.dnsProviderOptions
        dnsProviderPickerIndex = options.indexOfFirst { it.equals(uiState.dnsProvider, ignoreCase = true) }
            .coerceAtLeast(0)
        showDnsProviderPicker = true
    }

    val openContentLanguagePicker = {
        contentLanguagePickerIndex = TMDB_LANGUAGES.indexOfFirst { it.first == uiState.contentLanguage }.coerceAtLeast(0)
        showContentLanguagePicker = true
    }
    val openUiModeWarningDialog = {
        nextUiMode = when (uiState.deviceModeOverride) {
            "auto" -> "tv"
            "tv" -> "tablet"
            "tablet" -> "phone"
            else -> "auto"
        }
        showUiModeWarningDialog = true
    }

    LaunchedEffect(sections.size) {
        if (sectionIndex > sections.lastIndex) {
            sectionIndex = sections.lastIndex.coerceAtLeast(0)
            contentFocusIndex = 0
        }
    }

    LaunchedEffect(showSubtitlePicker, uiState.subtitleOptions) {
        if (showSubtitlePicker) {
            val options = uiState.subtitleOptions
            val maxIndex = (options.size - 1).coerceAtLeast(0)
            val targetIndex = options.indexOfFirst { it.equals(uiState.defaultSubtitle, ignoreCase = true) }
            subtitlePickerIndex = if (targetIndex >= 0) targetIndex else subtitlePickerIndex.coerceIn(0, maxIndex)
        }
    }

    LaunchedEffect(showSecondarySubtitlePicker, uiState.subtitleOptions) {
        if (showSecondarySubtitlePicker) {
            val options = uiState.subtitleOptions
            val maxIndex = (options.size - 1).coerceAtLeast(0)
            val targetIndex = options.indexOfFirst { it.equals(uiState.secondarySubtitle, ignoreCase = true) }
            secondarySubtitlePickerIndex = if (targetIndex >= 0) targetIndex else secondarySubtitlePickerIndex.coerceIn(0, maxIndex)
        }
    }

    LaunchedEffect(showAudioLanguagePicker, uiState.audioLanguageOptions) {
        if (showAudioLanguagePicker) {
            val options = uiState.audioLanguageOptions
            val maxIndex = (options.size - 1).coerceAtLeast(0)
            val targetIndex = options.indexOfFirst { it.equals(uiState.defaultAudioLanguage, ignoreCase = true) }
            audioLanguagePickerIndex = if (targetIndex >= 0) targetIndex else audioLanguagePickerIndex.coerceIn(0, maxIndex)
        }
    }

    LaunchedEffect(showDnsProviderPicker, uiState.dnsProviderOptions) {
        if (showDnsProviderPicker) {
            val options = uiState.dnsProviderOptions
            val maxIndex = (options.size - 1).coerceAtLeast(0)
            val targetIndex = options.indexOfFirst { it.equals(uiState.dnsProvider, ignoreCase = true) }
            dnsProviderPickerIndex = if (targetIndex >= 0) targetIndex else dnsProviderPickerIndex.coerceIn(0, maxIndex)
        }
    }

    // Reset content scroll AND position cache when switching sections.
    LaunchedEffect(sectionIndex, uiState.catalogs.size) {
        if (sections[sectionIndex] == "catalogs") {
            contentFocusIndex = contentFocusIndex.coerceAtMost(uiState.catalogs.size + 2)
        }
    }

    LaunchedEffect(sectionIndex, activeZone, sections.size) {
        if (isTouchDevice || activeZone != Zone.SECTION) return@LaunchedEffect
        androidx.compose.runtime.withFrameNanos { }
        sectionFocusTracker.revealDelta(sectionIndex)?.let { delta ->
            val target = (sectionScrollState.value + delta).toInt().coerceIn(0, sectionScrollState.maxValue)
            if (target != sectionScrollState.value) sectionScrollState.animateScrollTo(target, tween(100, easing = FastOutSlowInEasing))
            return@LaunchedEffect
        }
        val maxScroll = sectionScrollState.maxValue
        if (maxScroll <= 0) return@LaunchedEffect
        val maxIndex = sections.lastIndex.coerceAtLeast(1)
        val ratio = sectionIndex.coerceIn(0, maxIndex).toFloat() / maxIndex.toFloat()
        val targetScroll = (maxScroll * ratio).toInt().coerceIn(0, maxScroll)
        if (abs(sectionScrollState.value - targetScroll) > 24) {
            // Keep the transition short and cancellable. A long default spring
            // makes rapid DPAD presses feel delayed and leaves the sidebar behind.
            sectionScrollState.animateScrollTo(
                targetScroll,
                animationSpec = tween(100, easing = FastOutSlowInEasing)
            )
        }
    }

    // TV reveals measured rows; touch uses native bring-into-view. A new
    // subpage gets its own tracker even if its first focused index is unchanged.
    LaunchedEffect(
        contentFocusIndex,
        focusTracker,
        sectionIndex,
        activeZone,
        uiState.catalogs.size,
        uiState.addons.size
    ) {
        if (activeZone != Zone.CONTENT) return@LaunchedEffect
        if (!isTouchDevice && sections[sectionIndex] == "catalogs") return@LaunchedEffect

        if (!isTouchDevice) {
            // Let newly selected sections attach; never retain detached coordinates.
            androidx.compose.runtime.withFrameNanos { }
            focusTracker.revealDelta(contentFocusIndex)?.let { delta ->
                val target = (scrollState.value + delta).toInt().coerceIn(0, scrollState.maxValue)
                if (target != scrollState.value) scrollState.animateScrollTo(target, tween(100, easing = FastOutSlowInEasing))
                return@LaunchedEffect
            }
        }

        val requester = focusTracker.requesters[contentFocusIndex]
        if (requester != null && isTouchDevice) {
            // Native branch — handles all geometry correctly.
            runCatching { requester.bringIntoView() }
            return@LaunchedEffect
        }

        // Fallback: legacy ratio heuristic, only when positions are unknown
        // and scrolling is actually possible.
        val maxScroll = scrollState.maxValue
        val currentScroll = scrollState.value
        if (maxScroll <= 0) return@LaunchedEffect
        val currentSection = sections.getOrNull(sectionIndex).orEmpty()
        val maxIndex = sectionMaxIndex(currentSection).coerceAtLeast(1)
        val clampedFocus = contentFocusIndex.coerceIn(0, maxIndex)
        val ratio = clampedFocus.toFloat() / maxIndex.toFloat()
        val targetScroll = (maxScroll * ratio).toInt().coerceIn(0, maxScroll)
        if (abs(currentScroll - targetScroll) > 24) {
            if (isTouchDevice) {
                scrollState.animateScrollTo(targetScroll)
            } else {
                // Settings uses a single, manually managed focus target on TV.
                // A short animation keeps larger row jumps readable without
                // building a long animation queue behind the remote input.
                scrollState.animateScrollTo(
                    targetScroll,
                    animationSpec = tween(100, easing = FastOutSlowInEasing)
                )
            }
        }
    }







    val hasBlockingModal by remember { derivedStateOf {
        showCustomAddonInput ||
        showCommunityAddons ||
        showCatalogInput ||
        showCatalogRename ||
        showSubtitlePicker ||
        showSecondarySubtitlePicker ||
        showAudioLanguagePicker ||
        showDnsProviderPicker ||
        showContentLanguagePicker ||
        showUiModeWarningDialog ||
        addonConfigureTarget != null ||
        showCustomUserAgentDialog ||
        showCatalogPackInput ||
        showDeletePackConfirm ||
        uiState.isPackLoading ||
        uiState.packError != null ||
        uiState.pendingPackManifest != null ||
        false
    } }

    val inputFocusModifier = rememberSettingsInputFocusModifier(focusRequester, !isTouchDevice, hasBlockingModal)

    // Same D-pad repeat throttle as Home and Details, so holding a direction
    // key walks the settings lists at a readable pace instead of blurring.
    val dpadRepeatGate = rememberArvioDpadRepeatGate(
        horizontalMinRepeatIntervalMs = 80L,
        verticalMinRepeatIntervalMs = 112L
    )

    // One press used to reach two receivers: this screen's key handling consumed
    // Key.Back on the way down, and the system's back dispatcher invoked
    // BackHandler on its own afterwards - so the categories screen closed and the
    // zone stepped back as well, two views for one press. Consuming the key event
    // cannot prevent that: the app sets android:enableOnBackInvokedCallback, so
    // the dispatcher hears about the press through its own channel and does not
    // care whether a view swallowed the key. Everywhere else the two receivers
    // happened to do the same thing, which is why the double step stayed
    // invisible until the categories screen made them differ.
    //
    // BackHandler is therefore the only owner of Back. It is also the one that
    // always arrives: on API 33+ through the back dispatcher, below that through
    // Activity.onKeyUp, which now reaches it because nothing consumes the key any
    // more. Key.Escape keeps its own branch in the key handling below - no
    // dispatcher speaks for it - and calls the same lambda, so the two can never
    // drift apart.
    val goBack: () -> Unit = {
        when (activeZone) {
            Zone.SIDEBAR -> onBack()
            Zone.SECTION -> {
                activeZone = Zone.SIDEBAR
                isSidebarFocused = true
            }
            Zone.CONTENT -> {
                activeZone = Zone.SECTION
            }
        }
    }

    BackHandler(enabled = !isTouchDevice && !hasBlockingModal) { goBack() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(appBackgroundDark())
            .then(inputFocusModifier)
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                    if (isTouchDevice) return@onPreviewKeyEvent false
                    if (hasBlockingModal) return@onPreviewKeyEvent false

                // One physical OK press is one action, even when a remote repeats it.
                if (isRepeatedSettingsSelect(event)) return@onPreviewKeyEvent true
                if (event.type == KeyEventType.KeyUp &&
                    (event.key == Key.Enter || event.key == Key.DirectionCenter || event.key == Key.NumPadEnter)) {
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

                if (event.type == KeyEventType.KeyDown) {
                    val currentSection = sections.getOrNull(sectionIndex).orEmpty()
                    val focusedStremioAddon = stremioAddons.getOrNull(contentFocusIndex)
                    val focusedStremioAddonMaxAction = focusedStremioAddon
                        ?.let { addonRowActions(it).size - 1 }
                        ?: 0

                    val isRtl = isRtlLayoutDirection
                    val actualKey = event.key
                    val logicalKey = if (isRtl) {
                        when (actualKey) {
                            Key.DirectionLeft -> Key.DirectionRight
                            Key.DirectionRight -> Key.DirectionLeft
                            else -> actualKey
                        }
                    } else actualKey

                    when (logicalKey) {
                        // Key.Back deliberately absent: BackHandler owns it, see goBack().
                        Key.Escape -> {
                            goBack()
                            true
                        }
                        Key.DirectionLeft -> {
                            when (activeZone) {
                                Zone.CONTENT -> {
                                    if (currentSection == "stremio" && contentFocusIndex < stremioAddons.size && addonActionIndex > 0) {
                                        addonActionIndex--
                                    } else if (currentSection == "catalogs" && contentFocusIndex > 2 && catalogActionIndex > 0) {
                                        catalogActionIndex--
                                    } else {
                                        activeZone = Zone.SECTION
                                        addonActionIndex = 0
                                        catalogActionIndex = 0
                                    }
                                }
                                Zone.SECTION -> {
                                    Unit
                                }
                                Zone.SIDEBAR -> {
                                    if (sidebarFocusIndex > 0) {
                                        sidebarFocusIndex = (sidebarFocusIndex - 1).coerceIn(0, maxSidebarIndex)
                                    }
                                }
                            }
                            true
                        }
                        Key.DirectionRight -> {
                            when (activeZone) {
                                Zone.SIDEBAR -> {
                                    if (sidebarFocusIndex < maxSidebarIndex) {
                                        sidebarFocusIndex = (sidebarFocusIndex + 1).coerceIn(0, maxSidebarIndex)
                                    }
                                }
                                Zone.SECTION -> {
                                    activeZone = Zone.CONTENT
                                    addonActionIndex = 0
                                    catalogActionIndex = 0
                                }
                                Zone.CONTENT -> {
                                    if (currentSection == "stremio" &&
                                        contentFocusIndex in 0 until stremioAddons.size &&
                                        addonActionIndex < focusedStremioAddonMaxAction
                                    ) {
                                        addonActionIndex++
                                    } else if (currentSection == "catalogs" && contentFocusIndex > 2 && catalogActionIndex < 5) {
                                        catalogActionIndex++
                                    }
                                }
                            }
                            true
                        }
                        Key.DirectionUp -> {
                            when (activeZone) {
                                Zone.SIDEBAR -> Unit
                                Zone.SECTION -> {
                                    if (sectionIndex > 0) {
                                        sectionIndex--
                                        contentFocusIndex = 0 // Reset content focus when changing section
                                        addonActionIndex = 0
                                        catalogActionIndex = 0
                                    } else {
                                        activeZone = Zone.SIDEBAR
                                        isSidebarFocused = true
                                    }
                                }
                                Zone.CONTENT -> {
                                    run {
                                        if (contentFocusIndex > 0) {
                                            contentFocusIndex--
                                            addonActionIndex = 0 // Reset to toggle when changing rows
                                            catalogActionIndex = 0
                                        } else {
                                            // The categories screen is the exception: it is a screen of
                                            // its own, reached from a row, and walking a group up the
                                            // list runs into its top edge all the time. Handing the
                                            // focus to the section strip there drops the user out of
                                            // the screen they are working in. Back leaves it, Up does
                                            // not.
                                            activeZone = Zone.SECTION
                                        }
                                    }
                                }
                            }
                            true
                        }
                        Key.DirectionDown -> {
                            when (activeZone) {
                                Zone.SIDEBAR -> {
                                    activeZone = Zone.SECTION
                                    isSidebarFocused = false
                                }
                                Zone.SECTION -> {
                                    if (sectionIndex < sections.size - 1) {
                                        sectionIndex++
                                        contentFocusIndex = 0 // Reset content focus when changing section
                                        addonActionIndex = 0
                                        catalogActionIndex = 0
                                    }
                                }
                                Zone.CONTENT -> {
                                    run {
                                        val maxIndex = sectionMaxIndex(currentSection)
                                        if (contentFocusIndex < maxIndex) {
                                            contentFocusIndex++
                                            addonActionIndex = 0 // Reset to toggle when changing rows
                                            catalogActionIndex = 0
                                        }
                                    }
                                }
                            }
                            true
                        }
                        Key.Enter, Key.DirectionCenter, Key.NumPadEnter -> {
                            when (activeZone) {
                                Zone.SIDEBAR -> {
                                    when (topBarFocusedItem(sidebarFocusIndex)) {
                                        SidebarItem.SEARCH -> onNavigateToSearch()
                                        SidebarItem.HOME -> onNavigateToHome()
                                        SidebarItem.WATCHLIST -> onNavigateToWatchlist()
                                        SidebarItem.SETTINGS -> { /* Already here */ }
                                        null -> Unit
                                    }
                                }
                                Zone.SECTION -> activeZone = Zone.CONTENT
                                Zone.CONTENT -> {
                                    when (currentSection) {
                                        in tvGeneralSectionIds -> {
                                            when (tvGeneralRowsForSection(currentSection).getOrNull(contentFocusIndex)) {
                                                0 -> openContentLanguagePicker()
                                                1 -> openSubtitlePicker()
                                                2 -> openSecondarySubtitlePicker()
                                                3 -> openAudioLanguagePicker()
                                                4 -> viewModel.cycleSubtitleSize()
                                                5 -> viewModel.cycleSubtitleColor()
                                                6 -> viewModel.cycleSubtitleOffset()
                                                7 -> viewModel.cycleSubtitleStyle()
                                                42 -> viewModel.cycleSubtitleFont()
                                                8 -> viewModel.toggleSubtitleStylized()
                                                9 -> viewModel.setFilterSubtitlesByLanguage(!uiState.filterSubtitlesByLanguage)
                                                45 -> viewModel.setUseForcedSubtitles(!uiState.useForcedSubtitles)
                                                15 -> viewModel.cycleFrameRateMatchingMode()
                                                18 -> openUiModeWarningDialog()
                                                20 -> viewModel.setOledBlackBackground(!uiState.oledBlackBackground)
                                                21 -> viewModel.cycleClockFormat()
                                                46 -> viewModel.cycleAnimeStructuringStyle()
                                                 36 -> viewModel.setSmoothScrolling(!uiState.smoothScrolling)
                                                23 -> viewModel.setSpoilerBlurEnabled(!uiState.spoilerBlurEnabled)
                                                24 -> viewModel.cycleAccentColor()
                                                25 -> openDnsProviderPicker()
                                                26 -> viewModel.setShowLoadingStats(!uiState.showLoadingStats)
                                                35 -> showCustomUserAgentDialog = true
                                                27 -> viewModel.cycleVolumeBoost()
                                                39 -> viewModel.setSubtitlePreloadEnabled(!uiState.subtitlePreloadEnabled)
                                                40 -> viewModel.setDolbyVisionCompatEnabled(!uiState.dolbyVisionCompatEnabled)
                                                51 -> toggleShowAllSources(!showAllSources)
                                                52 -> openTvCheck()
                                            }
                                        }
                                        "catalogs" -> {
                                            if (contentFocusIndex == 0) {
                                                showCatalogInput = true
                                            } else if (contentFocusIndex == 1) {
                                                showCatalogPackInput = true
                                            } else {
                                                val catalog = uiState.catalogs.getOrNull(contentFocusIndex - 2)
                                                if (catalog != null) {
                                                    when (catalogActionIndex) {
                                                        0 -> {
                                                            renameCatalogId = catalog.id
                                                            renameCatalogTitle = catalog.title
                                                            showCatalogRename = true
                                                        }
                                                        1 -> viewModel.moveCatalogUp(catalog.id)
                                                        2 -> viewModel.moveCatalogDown(catalog.id)
                                                        3 -> scope.launch {
                                                            toggleCatalogueRowLayoutMode(context, catalogueLayoutRowKey(catalog))
                                                        }
                                                        4 -> {
                                                            if (catalog.packId != null && catalog.isBulkDeletablePack) {
                                                                viewModel.unpackCatalog(catalog.id)
                                                            }
                                                        }
                                                        else -> {
                                                            if (catalog.isBulkDeletablePack) {
                                                                deletePackId = catalog.effectivePackId
                                                                deletePackName = catalog.effectivePackName
                                                                showDeletePackConfirm = true
                                                            } else {
                                                                viewModel.removeCatalog(catalog.id)
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                        "stremio" -> {
                                            when {
                                                contentFocusIndex in 0 until stremioAddons.size -> {
                                                    val addon = stremioAddons[contentFocusIndex]
                                                    when (addonRowActions(addon).getOrNull(addonActionIndex)) {
                                                        AddonRowAction.CONFIGURE -> addonConfigureTarget = addon
                                                        AddonRowAction.DELETE -> {
                                                            viewModel.removeAddon(addon.id)
                                                            addonActionIndex = 0
                                                            if (contentFocusIndex >= stremioAddons.size && contentFocusIndex > 0) {
                                                                contentFocusIndex--
                                                            }
                                                        }
                                                        else -> viewModel.toggleAddon(addon.id)
                                                    }
                                                }
                                                contentFocusIndex == stremioAddons.size -> {
                                                    viewModel.refreshAddons()
                                                }
                                                contentFocusIndex == stremioAddons.size + 1 -> {
                                                    showCommunityAddons = true
                                                }
                                                else -> {
                                                    showCustomAddonInput = true
                                                }
                                            }
                                        }
                                        else -> Unit
                                    }
                                }
                                else -> {}
                            }
                            true
                        }
                        else -> false
                    }
                } else false
            }
    ) {
        if (isTouchDevice) {
            MobileSettingsLayout(
                page = mobilePage,
                onNavigate = { mobilePage = it },
                uiState = uiState,
                viewModel = viewModel,
                stremioAddons = stremioAddons,

                openContentLanguagePicker = openContentLanguagePicker,
                openSubtitlePicker = openSubtitlePicker,
                openSecondarySubtitlePicker = openSecondarySubtitlePicker,
                openAudioLanguagePicker = openAudioLanguagePicker,
                openDnsProviderPicker = openDnsProviderPicker,
                openUiModeWarningDialog = openUiModeWarningDialog,
                onAddCatalogClick = { showCatalogInput = true },
                onImportCatalogPackClick = { showCatalogPackInput = true },
                onRenameCatalogClick = { catalog ->
                    renameCatalogId = catalog.id
                    renameCatalogTitle = catalog.title
                    showCatalogRename = true
                },
                onDeleteCatalogClick = { catalog ->
                    if (catalog.isBulkDeletablePack) {
                        deletePackId = catalog.effectivePackId
                        deletePackName = catalog.effectivePackName
                        showDeletePackConfirm = true
                    } else {
                        viewModel.removeCatalog(catalog.id)
                    }
                },
                onAddCustomAddonClick = { showCustomAddonInput = true },
                onBrowseCommunityAddonsClick = { showCommunityAddons = true },
                openCustomUserAgentDialog = { showCustomUserAgentDialog = true },
            )
        } else {
            AppTopBar(
                selectedItem = SidebarItem.SETTINGS,
                isFocused = activeZone == Zone.SIDEBAR,
                focusedIndex = sidebarFocusIndex,

            )

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = AppTopBarContentTopInset)
                    .padding(start = 34.dp, end = 42.dp, bottom = 28.dp)
            ) {
                // Settings internal sidebar
                Column(
                    modifier = Modifier
                        .width(204.dp)
                        .fillMaxSize()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF050505))
                        .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(16.dp))
                        .padding(vertical = 18.dp, horizontal = 10.dp)
                ) {
                    Text(
                        text = stringResource(R.string.settings),
                        style = ArflixTypography.cardTitle.copy(fontSize = 19.sp),
                        color = TextPrimary,
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .padding(bottom = 14.dp)
                    )

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .onGloballyPositioned { sectionFocusTracker.viewport = it }
                            .verticalScroll(sectionScrollState)
                    ) {
                        sections.forEachIndexed { index, section ->
                            val group = tvSettingsSidebarGroup(section)
                            val previousGroup = sections.getOrNull(index - 1)?.let { tvSettingsSidebarGroup(it) }
                            if (index == 0 || group != previousGroup) {
                                SettingsSectionGroupLabel(group)
                                Spacer(modifier = Modifier.height(6.dp))
                            }
                            SettingsSectionItem(
                                modifier = Modifier.onGloballyPositioned { sectionFocusTracker.coordinates[index] = it },
                                icon = when (section) {
                                    "language" -> Icons.Default.Language
                                    "subtitles" -> Icons.Default.Subtitles
                                    "advanced" -> Icons.Default.Settings
                                    "playback" -> Icons.Default.PlayArrow
                                    "appearance" -> Icons.Default.Palette
                                    "network" -> Icons.Default.Settings
                                    "catalogs" -> Icons.Default.Widgets
                                    "stremio" -> Icons.Default.Extension
                                    else -> Icons.Default.Settings
                                },
                                title = when (section) {
                                    "language" -> stringResource(R.string.language_and_audio)
                                    "subtitles" -> stringResource(R.string.subtitles)
                                    "advanced" -> stringResource(R.string.settings_advanced)
                                    "playback" -> stringResource(R.string.playback)
                                    "appearance" -> stringResource(R.string.interface_label)
                                    "network" -> stringResource(R.string.network)
                                    "catalogs" -> stringResource(R.string.catalogs)
                                    "stremio" -> stringResource(R.string.addons)
                                    else -> section.replaceFirstChar { it.uppercase() }
                                },
                                isSelected = sectionIndex == index,
                                isFocused = activeZone == Zone.SECTION && sectionIndex == index,
                                onClick = {
                                    sectionIndex = index
                                    contentFocusIndex = 0
                                    activeZone = Zone.SECTION
                                }
                            )

                            Spacer(modifier = Modifier.height(6.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = stringResource(R.string.settings_app_version_label, BuildConfig.VERSION_NAME),
                        style = ArflixTypography.caption,
                        color = TextSecondary.copy(alpha = 0.5f),
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }

                // Content area
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .testTag("settings-content")
                        .onGloballyPositioned { focusTracker.viewport = it }
                        .then(if (sections[sectionIndex] == "catalogs") Modifier else Modifier.verticalScroll(scrollState))
                        .padding(start = 28.dp)
                ) {
                  if (sections[sectionIndex] != "catalogs") {
                    TvSettingsSectionHeader(
                        section = sections[sectionIndex],
                        uiState = uiState,
                        addonCount = stremioAddons.size
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                  }
                  CompositionLocalProvider(LocalSettingsFocusTracker provides focusTracker) {
                    when (sections[sectionIndex]) {
                        in tvGeneralSectionIds -> {
                        TvGeneralSettingsRows(
                            section = sections[sectionIndex],
                            defaultSubtitle = uiState.defaultSubtitle,
                            secondarySubtitle = uiState.secondarySubtitle,
                            defaultAudioLanguage = uiState.defaultAudioLanguage,
                            dnsProvider = uiState.dnsProvider,
                            frameRateMatchingMode = uiState.frameRateMatchingMode,
                            contentLanguage = uiState.contentLanguage,
                            subtitleSize = uiState.subtitleSize,
                            subtitleColor = uiState.subtitleColor,
                            subtitleOffset = uiState.subtitleOffset,
                            subtitleStyle = uiState.subtitleStyle,
                            subtitleFont = uiState.subtitleFont,
                            subtitleStylized = uiState.subtitleStylized,
                            deviceModeOverride = uiState.deviceModeOverride,
                            oledBlackBackground = uiState.oledBlackBackground,
                            clockFormat = uiState.clockFormat,
                            volumeBoostDb = uiState.volumeBoostDb,
                            showAllSources = showAllSources,
                            onShowAllSourcesToggle = toggleShowAllSources,
                            tvSummary = tvSummary,
                            onRescanTvClick = openTvCheck,
                            focusedIndex = if (activeZone == Zone.CONTENT) contentFocusIndex else -1,
                            onSubtitleClick = openSubtitlePicker,
                            onSecondarySubtitleClick = openSecondarySubtitlePicker,
                            onAudioLanguageClick = openAudioLanguagePicker,
                            onFrameRateMatchingClick = { viewModel.cycleFrameRateMatchingMode() },
                            onDnsProviderClick = openDnsProviderPicker,
                            onDeviceModeClick = openUiModeWarningDialog,
                            onContentLanguageClick = openContentLanguagePicker,
                            onOledBlackBackgroundToggle = { viewModel.setOledBlackBackground(it) },
                            onClockFormatClick = { viewModel.cycleClockFormat() },
                            animeStructuringStyle = uiState.animeStructuringStyle,
                            onAnimeStructuringStyleToggle = { viewModel.cycleAnimeStructuringStyle() },
                            smoothScrolling = uiState.smoothScrolling,
                            onSmoothScrollingToggle = { viewModel.setSmoothScrolling(it) },
                            spoilerBlurEnabled = uiState.spoilerBlurEnabled,
                            onSpoilerBlurToggle = { viewModel.setSpoilerBlurEnabled(it) },
                            accentColor = uiState.accentColor,
                            onAccentColorClick = { viewModel.cycleAccentColor() },
                            showLoadingStats = uiState.showLoadingStats,
                            onShowLoadingStatsToggle = { viewModel.setShowLoadingStats(it) },
                            onVolumeBoostClick = { viewModel.cycleVolumeBoost() },
                            onSubtitleSizeClick = { viewModel.cycleSubtitleSize() },
                            onSubtitleColorClick = { viewModel.cycleSubtitleColor() },
                            onSubtitleOffsetClick = { viewModel.cycleSubtitleOffset() },
                            onSubtitleStyleClick = { viewModel.cycleSubtitleStyle() },
                            onSubtitleFontClick = { viewModel.cycleSubtitleFont() },
                            onSubtitleStylizedToggle = { viewModel.toggleSubtitleStylized() },
                            filterSubtitlesByLanguage = uiState.filterSubtitlesByLanguage,
                            onFilterSubtitlesByLanguageToggle = { viewModel.setFilterSubtitlesByLanguage(it) },
                            useForcedSubtitles = uiState.useForcedSubtitles,
                            onUseForcedSubtitlesToggle = { viewModel.setUseForcedSubtitles(it) },
                            subtitlePreloadEnabled = uiState.subtitlePreloadEnabled,
                            dolbyVisionCompatEnabled = uiState.dolbyVisionCompatEnabled,
                            onSubtitlePreloadToggle = { viewModel.setSubtitlePreloadEnabled(it) },
                            onDolbyVisionCompatToggle = { viewModel.setDolbyVisionCompatEnabled(it) },
                            customUserAgent = uiState.customUserAgent,
                            onCustomUserAgentClick = { showCustomUserAgentDialog = true }
                        )
                        if (showCustomUserAgentDialog) {
                            CustomUserAgentDialog(
                                currentValue = uiState.customUserAgent,
                                onSave = { value ->
                                    viewModel.setCustomUserAgent(value)
                                    showCustomUserAgentDialog = false
                                },
                                onDismiss = { showCustomUserAgentDialog = false }
                            )
                        }
                        } // end "general" block
                        "catalogs" -> CatalogsSettings(
                            catalogs = uiState.catalogs,
                            header = {
                                TvSettingsSectionHeader("catalogs", uiState, stremioAddons.size)
                                Spacer(Modifier.height(12.dp))
                            },
                            focusedIndex = if (activeZone == Zone.CONTENT) contentFocusIndex else -1,
                            focusedActionIndex = catalogActionIndex,
                            onAddCatalog = { showCatalogInput = true },
                            onImportCatalogPack = { showCatalogPackInput = true },
                            onRenameCatalog = { catalog ->
                                renameCatalogId = catalog.id
                                renameCatalogTitle = catalog.title
                                showCatalogRename = true
                            },
                            onMoveCatalogUp = { catalog -> viewModel.moveCatalogUp(catalog.id) },
                            onMoveCatalogDown = { catalog -> viewModel.moveCatalogDown(catalog.id) },
                            onDeleteCatalog = { catalog ->
                                if (catalog.isBulkDeletablePack) {
                                    deletePackId = catalog.effectivePackId
                                    deletePackName = catalog.effectivePackName
                                    showDeletePackConfirm = true
                                } else {
                                    viewModel.removeCatalog(catalog.id)
                                }
                            },
                            onUnpackCatalog = { catalog -> viewModel.unpackCatalog(catalog.id) }
                        )
                        "stremio" -> StremioAddonsSettings(
                            addons = stremioAddons,
                            isRefreshingAddons = uiState.isRefreshingAddons,
                            focusedIndex = if (activeZone == Zone.CONTENT) contentFocusIndex else -1,
                            focusedActionIndex = addonActionIndex,
                            onToggleAddon = { viewModel.toggleAddon(it) },
                            onDeleteAddon = { viewModel.removeAddon(it) },
                            onConfigureAddon = { addonConfigureTarget = it },
                            onAddCustomAddon = { showCustomAddonInput = true },
                            onBrowseCommunityAddons = { showCommunityAddons = true },
                            onRefreshAddons = { viewModel.refreshAddons() }
                        )
                    }
                  }
                }
            }
        }

        // Custom Addon Input Modal
        if (showCustomAddonInput) {
            InputModal(
                title = stringResource(R.string.add_addon),
                fields = listOf(
                    InputField(label = stringResource(R.string.settings_label_url), value = customAddonUrl, onValueChange = { customAddonUrl = it })
                ),
                onConfirm = {
                    if (customAddonUrl.isNotBlank()) {
                        viewModel.addCustomAddon(customAddonUrl.trim())
                        customAddonUrl = ""
                        showCustomAddonInput = false
                    }
                },
                onDismiss = {
                    customAddonUrl = ""
                    showCustomAddonInput = false
                }
            )
        }




        if (showCatalogInput) {
            CatalogDiscoveryModal(
                query = uiState.catalogSearchQuery,
                results = uiState.catalogSearchResults,
                isSearching = uiState.isCatalogSearching,
                error = uiState.catalogSearchError?.localizedText(),
                manualUrl = catalogInputUrl,
                addedCatalogUrls = uiState.catalogs.mapNotNull { it.sourceUrl }.toSet(),
                onQueryChange = viewModel::setCatalogSearchQuery,
                onSearch = { viewModel.searchCatalogLists() },
                onAddResult = viewModel::addDiscoveredCatalog,
                onManualUrlChange = { catalogInputUrl = it },
                onManualAdd = {
                    if (catalogInputUrl.isNotBlank()) {
                        viewModel.addCatalog(catalogInputUrl)
                        catalogInputUrl = ""
                        showCatalogInput = false
                    }
                },
                onDismiss = {
                    catalogInputUrl = ""
                    viewModel.clearCatalogDiscovery()
                    showCatalogInput = false
                }
            )
        }


        if (showCatalogRename) {
            InputModal(
                title = stringResource(R.string.rename_catalog),
                fields = listOf(
                    InputField(label = stringResource(R.string.settings_label_title), value = renameCatalogTitle, onValueChange = { renameCatalogTitle = it })
                ),
                onConfirm = {
                    if (renameCatalogTitle.isNotBlank()) {
                        viewModel.renameCatalog(renameCatalogId, renameCatalogTitle)
                        showCatalogRename = false
                    }
                },
                onDismiss = {
                    showCatalogRename = false
                }
            )
        }

        if (showCatalogPackInput) {
            InputModal(
                title = stringResource(R.string.settings_catalog_pack_import_title),
                fields = listOf(
                    InputField(label = stringResource(R.string.settings_catalog_pack_url_label), value = catalogPackInputUrl, onValueChange = { catalogPackInputUrl = it })
                ),
                onConfirm = {
                    if (catalogPackInputUrl.isNotBlank()) {
                        viewModel.loadPackManifest(catalogPackInputUrl)
                        catalogPackInputUrl = ""
                        showCatalogPackInput = false
                    }
                },
                onDismiss = {
                    catalogPackInputUrl = ""
                    showCatalogPackInput = false
                }
            )
        }

        if (uiState.pendingAddonInstall != null || uiState.isAddonInstallLoading) {
            AddonInstallDialog(
                pending = uiState.pendingAddonInstall,
                isLoading = uiState.isAddonInstallLoading,
                onInstall = { viewModel.confirmAddonInstall(replaceExisting = false) },
                onReplace = { viewModel.confirmAddonInstall(replaceExisting = true) },
                onKeepBoth = { viewModel.confirmAddonInstall(replaceExisting = false) },
                onDismiss = { viewModel.cancelAddonInstall() }
            )
        }

        if (showCommunityAddons) {
            CommunityAddonsBrowser(
                addons = uiState.communityAddons,
                installedAddons = stremioAddons,
                isLoading = uiState.isCommunityAddonsLoading,
                failed = uiState.communityAddonsFailed,
                busyUrls = uiState.communityAddonBusyUrls,
                onRetry = { viewModel.loadCommunityAddons(forceRefresh = true) },
                onInstall = viewModel::installCommunityAddon,
                onUninstall = viewModel::uninstallCommunityAddon,
                onOpenUrl = { openExternalUrl(context, it) },
                onDismiss = { showCommunityAddons = false },
                // The browser is its own window, so the screen's toast would be hidden behind it.
                overlay = {
                    uiState.toastMessage?.let { message ->
                        Toast(
                            message = message.localizedText(),
                            type = when (uiState.toastType) {
                                ToastType.SUCCESS -> ComponentToastType.SUCCESS
                                ToastType.ERROR -> ComponentToastType.ERROR
                                ToastType.INFO -> ComponentToastType.INFO
                            },
                            isVisible = true,
                            onDismiss = { viewModel.dismissToast() }
                        )
                    }
                }
            )
        }

        val configureTarget = addonConfigureTarget
        val configureTargetUrl = configureTarget?.settingsPageUrl
        if (configureTarget != null && configureTargetUrl != null) {
            AddonConfigureQrDialog(
                addonName = configureTarget.name,
                url = configureTargetUrl,
                onDismiss = { addonConfigureTarget = null }
            )
        }

        if (uiState.pendingPackManifest != null || uiState.isPackLoading || uiState.packError != null) {
            CatalogPackImportDialog(
                pendingPack = uiState.pendingPackManifest,
                isLoading = uiState.isPackLoading,
                error = uiState.packError?.localizedText(),
                onConfirm = { manifest ->
                    viewModel.confirmInstallPack(uiState.pendingPackUrl ?: "")
                },
                onDismiss = {
                    viewModel.clearPendingPack()
                }
            )
        }

        if (showDeletePackConfirm) {
            CatalogPackDeleteConfirmDialog(
                packName = deletePackName,
                onConfirm = {
                    viewModel.removeCatalogPack(deletePackId)
                    showDeletePackConfirm = false
                },
                onDismiss = {
                    showDeletePackConfirm = false
                }
            )
        }


        if (showSubtitlePicker) {
            SubtitlePickerModal(
                title = stringResource(R.string.default_subtitle),
                options = uiState.subtitleOptions,
                selected = uiState.defaultSubtitle,
                focusedIndex = subtitlePickerIndex,
                onFocusChange = { subtitlePickerIndex = it },
                onSelect = {
                    viewModel.setDefaultSubtitle(it)
                    showSubtitlePicker = false
                },
                onDismiss = { showSubtitlePicker = false }
            )
        }

        if (showSecondarySubtitlePicker) {
            SubtitlePickerModal(
                title = stringResource(R.string.secondary_subtitle),
                options = uiState.subtitleOptions,
                selected = uiState.secondarySubtitle,
                focusedIndex = secondarySubtitlePickerIndex,
                onFocusChange = { secondarySubtitlePickerIndex = it },
                onSelect = {
                    viewModel.setSecondarySubtitle(it)
                    showSecondarySubtitlePicker = false
                },
                onDismiss = { showSecondarySubtitlePicker = false }
            )
        }

        if (showAudioLanguagePicker) {
            SubtitlePickerModal(
                title = stringResource(R.string.default_audio),
                options = uiState.audioLanguageOptions,
                selected = uiState.defaultAudioLanguage,
                focusedIndex = audioLanguagePickerIndex,
                onFocusChange = { audioLanguagePickerIndex = it },
                onSelect = {
                    viewModel.setDefaultAudioLanguage(it)
                    showAudioLanguagePicker = false
                },
                onDismiss = { showAudioLanguagePicker = false }
            )
        }

        if (showDnsProviderPicker) {
            SubtitlePickerModal(
                title = stringResource(R.string.dns_provider),
                options = uiState.dnsProviderOptions,
                selected = uiState.dnsProvider,
                focusedIndex = dnsProviderPickerIndex,
                onFocusChange = { dnsProviderPickerIndex = it },
                onSelect = {
                    showDnsProviderPicker = false
                    viewModel.setDnsProvider(it)
                },
                onDismiss = { showDnsProviderPicker = false }
            )
        }

        if (showContentLanguagePicker) {
            SubtitlePickerModal(
                title = stringResource(R.string.content_language),
                options = TMDB_LANGUAGES.map { it.second },
                selected = TMDB_LANGUAGES.firstOrNull { it.first == uiState.contentLanguage }?.second ?: "English",
                focusedIndex = contentLanguagePickerIndex,
                onFocusChange = { contentLanguagePickerIndex = it },
                onSelect = { displayName ->
                    val code = TMDB_LANGUAGES.firstOrNull { it.second == displayName }?.first ?: "en-US"
                    viewModel.setContentLanguage(code)
                    showContentLanguagePicker = false
                },
                onDismiss = { showContentLanguagePicker = false }
            )
        }

        if (isTouchDevice && showCustomUserAgentDialog) {
            CustomUserAgentDialog(
                currentValue = uiState.customUserAgent,
                onSave = { value ->
                    viewModel.setCustomUserAgent(value)
                    showCustomUserAgentDialog = false
                },
                onDismiss = { showCustomUserAgentDialog = false }
            )
        }













        if (showUiModeWarningDialog) {
            UiModeWarningDialog(
                nextMode = nextUiMode,
                onConfirm = {
                    viewModel.setDeviceModeOverride(nextUiMode)
                    showUiModeWarningDialog = false
                },
                onDismiss = {
                    showUiModeWarningDialog = false
                }
            )
        }

        // Toast notification
        uiState.toastMessage?.let { message ->
            Toast(
                message = message.localizedText(),
                type = when (uiState.toastType) {
                    ToastType.SUCCESS -> ComponentToastType.SUCCESS
                    ToastType.ERROR -> ComponentToastType.ERROR
                    ToastType.INFO -> ComponentToastType.INFO
                },
                isVisible = true,
                onDismiss = { viewModel.dismissToast() }
            )
        }

    }
}

@Composable
internal fun ModalScrim(
    onDismiss: () -> Unit,
    content: @Composable BoxScope.() -> Unit
) {
    val scrimInteraction = remember { MutableInteractionSource() }
    val contentInteraction = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f))
            // An OK held while opening a dialog must not select its first option.
            .onPreviewKeyEvent(::isRepeatedSettingsSelect)
            .clickable(
                interactionSource = scrimInteraction,
                indication = null,
                onClick = onDismiss
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier.clickable(
                interactionSource = contentInteraction,
                indication = null,
                onClick = {}
            ),
            content = content
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun MobileSettingsLayout(
    page: String,
    onNavigate: (String) -> Unit,
    uiState: SettingsUiState,
    viewModel: SettingsViewModel,
    stremioAddons: List<com.arflix.tv.data.model.Addon>,

    openContentLanguagePicker: () -> Unit,
    openSubtitlePicker: () -> Unit,
    openSecondarySubtitlePicker: () -> Unit = {},
    openAudioLanguagePicker: () -> Unit,
    openDnsProviderPicker: () -> Unit,
    openUiModeWarningDialog: () -> Unit,
    onAddCatalogClick: () -> Unit,
    onImportCatalogPackClick: () -> Unit,
    onRenameCatalogClick: (CatalogConfig) -> Unit,
    onDeleteCatalogClick: (CatalogConfig) -> Unit,
    onAddCustomAddonClick: () -> Unit,
    onBrowseCommunityAddonsClick: () -> Unit,
    openCustomUserAgentDialog: () -> Unit = {},
) {
    // Every phone sub-page is opened from the main list and goes back to it. The
    // categories list is the one exception: it is opened from the TV sources page,
    // so sending Back to the main list skips a level and loses the page the user
    // was actually on.
    val progressAnimatable = remember { Animatable(0f) }
    val exitOffsetAnimatable = remember { Animatable(0f) }
    val exitAlphaAnimatable = remember { Animatable(1f) }
    var isPredictiveBackActive by remember { mutableStateOf(false) }
    var isPredictiveBackCommitted by remember { mutableStateOf(false) }
    var swipeEdge by remember { mutableIntStateOf(BackEventCompat.EDGE_LEFT) }

    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }

    val targetX = screenWidthPx

    PredictiveBackHandler(enabled = page != "MAIN") { progressFlow ->
        try {
            isPredictiveBackActive = true
            isPredictiveBackCommitted = false
            progressFlow.collect { backEvent ->
                swipeEdge = backEvent.swipeEdge
                progressAnimatable.snapTo(backEvent.progress)
            }
            // User completed back gesture (committed).
            // Smoothly glide off-screen to the right and fade out BEFORE calling onNavigate("MAIN"),
            // so the card never snaps back to full size / sticks to the screen!
            coroutineScope {
                launch {
                    exitOffsetAnimatable.animateTo(
                        targetValue = targetX,
                        animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing)
                    )
                }
                launch {
                    exitAlphaAnimatable.animateTo(
                        targetValue = 0f,
                        animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing)
                    )
                }
                launch {
                    progressAnimatable.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing)
                    )
                }
            }
            isPredictiveBackCommitted = true
            onNavigate("MAIN")
        } catch (e: CancellationException) {
            // User cancelled gesture by dragging back to screen edge.
            // Run spring reset inside NonCancellable so coroutine cancellation does not abort it.
            withContext(NonCancellable) {
                try {
                    progressAnimatable.animateTo(
                        targetValue = 0f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMedium
                        )
                    )
                } finally {
                    isPredictiveBackActive = false
                    isPredictiveBackCommitted = false
                    exitOffsetAnimatable.snapTo(0f)
                    exitAlphaAnimatable.snapTo(1f)
                }
            }
        } finally {
            isPredictiveBackActive = false
            progressAnimatable.snapTo(0f)
            if (!isPredictiveBackCommitted) {
                exitOffsetAnimatable.snapTo(0f)
                exitAlphaAnimatable.snapTo(1f)
            }
        }
    }

    val backProgress = progressAnimatable.value
    val subPageScale = 1f - (backProgress * 0.10f)
    val cornerRadius = (backProgress * 28).dp
    val direction = if (swipeEdge == BackEventCompat.EDGE_RIGHT) -1f else 1f
    val maxShiftDp = 8.dp
    val subPageXOffset = maxShiftDp * (direction * backProgress)

    var lastSubPage by remember { mutableStateOf(if (page != "MAIN") page else "") }
    val displayedSubPage = if (page != "MAIN") page else lastSubPage
    LaunchedEffect(page) {
        if (page != "MAIN") {
            lastSubPage = page
            isPredictiveBackActive = false
            isPredictiveBackCommitted = false
            progressAnimatable.snapTo(0f)
            exitOffsetAnimatable.snapTo(0f)
            exitAlphaAnimatable.snapTo(1f)
        } else {
            isPredictiveBackActive = false
            progressAnimatable.snapTo(0f)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(appBackgroundDark())
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    if (page != "MAIN" && isPredictiveBackActive && backProgress > 0f) {
                        val bgScale = 0.95f + (backProgress * 0.05f)
                        scaleX = bgScale
                        scaleY = bgScale
                        alpha = 0.7f + (backProgress * 0.3f)
                    }
                }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.settings),
                    style = ArflixTypography.heroTitle.copy(fontSize = 28.sp),
                    color = TextPrimary,
                    modifier = Modifier.weight(1f)
                )

            }
            MobileSettingsMainPage(
                uiState = uiState,
                viewModel = viewModel,
                onNavigate = onNavigate,
                openContentLanguagePicker = openContentLanguagePicker,
                openSubtitlePicker = openSubtitlePicker,
                openSecondarySubtitlePicker = openSecondarySubtitlePicker,
                openAudioLanguagePicker = openAudioLanguagePicker,
            )
        }

        if (page != "MAIN" && isPredictiveBackActive && backProgress > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f * (1f - backProgress)))
            )
        }

        AnimatedVisibility(
            visible = page != "MAIN",
            enter = fadeIn(tween(200)) + slideInHorizontally(tween(280, easing = FastOutSlowInEasing)) { it },
            exit = if (isPredictiveBackCommitted) {
                ExitTransition.None
            } else {
                fadeOut(tween(250, easing = FastOutSlowInEasing)) + slideOutHorizontally(tween(280, easing = FastOutSlowInEasing)) { it }
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        if (page == "MAIN" && isPredictiveBackCommitted) {
                            alpha = 0f
                            translationX = targetX
                        } else if (isPredictiveBackActive) {
                            scaleX = subPageScale
                            scaleY = subPageScale
                            translationX = subPageXOffset.toPx() + exitOffsetAnimatable.value
                            alpha = exitAlphaAnimatable.value
                            shape = RoundedCornerShape(cornerRadius)
                            clip = cornerRadius > 0.dp
                            shadowElevation = (backProgress * 16).dp.toPx()
                        }
                    }
                    .background(appBackgroundDark())
                    // The main settings list stays composed underneath this page,
                    // and an opaque background hides it without stopping a touch.
                    // A tap that lands anywhere this page has no control - the gap
                    // under a heading, the space beside a row, the empty area below
                    // a short list - therefore reached whatever sat at the same
                    // spot in the list below, which starts with the app language
                    // row. Opening a sub-page and tapping just under its heading
                    // opened the language picker. Swallow what no child of this
                    // page took; children are hit first, so nothing here loses a
                    // tap it would otherwise have received.
                    .pointerInput(Unit) { detectTapGestures { } }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .padding(horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                        tint = TextPrimary,
                        modifier = Modifier
                            .clickable { onNavigate("MAIN") }
                            .padding(end = 16.dp)
                            .size(24.dp)
                    )
                    Text(
                        text = mobileCategoryTitle(displayedSubPage),
                        style = ArflixTypography.heroTitle.copy(fontSize = 20.sp),
                        color = TextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                }
                MobileSettingsSubPage(
                    page = displayedSubPage,
                    onNavigate = onNavigate,
                    uiState = uiState,
                    viewModel = viewModel,
                    stremioAddons = stremioAddons,

                    openDnsProviderPicker = openDnsProviderPicker,
                    openUiModeWarningDialog = openUiModeWarningDialog,
                    onAddCatalogClick = onAddCatalogClick,
                    onImportCatalogPackClick = onImportCatalogPackClick,
                    onRenameCatalogClick = onRenameCatalogClick,
                    onDeleteCatalogClick = onDeleteCatalogClick,
                    onAddCustomAddonClick = onAddCustomAddonClick,
                    onBrowseCommunityAddonsClick = onBrowseCommunityAddonsClick,
                    openCustomUserAgentDialog = openCustomUserAgentDialog,
                )
            }
        }
    }
}

/**
 * Maps a stable mobile-settings page navigation key (kept in English so the
 * `when (page)` routing stays stable) to its localized display title.
 */
@Composable
private fun mobileCategoryTitle(page: String): String = when (page) {
    "Playback & Controls" -> stringResource(R.string.settings_cat_playback_controls)
    "Audio & Subtitles" -> stringResource(R.string.settings_cat_audio_subtitles)
    "Appearance" -> stringResource(R.string.interface_label)
    "Addons" -> stringResource(R.string.addons)
    "Catalogs" -> stringResource(R.string.catalogs)
    else -> page
}


@Composable
private fun MobileSettingsMainPage(
    uiState: SettingsUiState,
    viewModel: SettingsViewModel,
    onNavigate: (String) -> Unit,
    openContentLanguagePicker: () -> Unit,
    openSubtitlePicker: () -> Unit,
    openSecondarySubtitlePicker: () -> Unit = {},
    openAudioLanguagePicker: () -> Unit,
) {
    val context = LocalContext.current
    androidx.compose.foundation.lazy.LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 24.dp,
            end = 24.dp,
            top = 8.dp,
            bottom = 16.dp + LocalBottomBarInset.current
        ),
        verticalArrangement = Arrangement.spacedBy(32.dp)
    ) {
        if (!BuildConfig.PERSONAL_BUILD) item {
            MobileSettingsCategory(title = stringResource(R.string.settings_section_languages)) {
                MobileSettingsRow(
                    icon = Icons.Default.Language,
                    title = stringResource(R.string.content_language),
                    value = TMDB_LANGUAGES.firstOrNull { it.first == uiState.contentLanguage }?.second ?: uiState.contentLanguage,
                    isToggle = false,
                    isFocused = false,
                    onClick = openContentLanguagePicker
                )
                MobileSettingsRow(
                    icon = Icons.Default.VolumeUp,
                    title = stringResource(R.string.default_audio),
                    value = uiState.defaultAudioLanguage,
                    isToggle = false,
                    isFocused = false,
                    onClick = openAudioLanguagePicker
                )
                MobileSettingsRow(
                    icon = Icons.Default.Subtitles,
                    title = stringResource(R.string.default_subtitle),
                    value = uiState.defaultSubtitle,
                    isToggle = false,
                    isFocused = false,
                    onClick = openSubtitlePicker
                )
                MobileSettingsRow(
                    icon = Icons.Default.Subtitles,
                    title = stringResource(R.string.secondary_subtitle),
                    value = uiState.secondarySubtitle,
                    isToggle = false,
                    isFocused = false,
                    showDivider = false,
                    onClick = openSecondarySubtitlePicker
                )
            }
        }

        item {
            MobileSettingsCategory(title = stringResource(R.string.settings_section_categories)) {
                val categories = buildList {
                    add("Playback & Controls" to Icons.Default.PlayArrow)
                    add("Audio & Subtitles" to Icons.Default.Speaker)
                    add("Appearance" to Icons.Default.Palette)
                    add("Addons" to Icons.Default.Extension)

                    add("Catalogs" to Icons.Default.Widgets)
                }
                categories.forEachIndexed { index, (name, icon) ->
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onNavigate(name) }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(imageVector = icon, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(text = mobileCategoryTitle(name), style = ArflixTypography.cardTitle.copy(fontSize = 16.sp), color = TextPrimary, modifier = Modifier.weight(1f))
                            Icon(imageVector = Icons.Default.ChevronRight, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(20.dp))
                        }
                        if (index < categories.lastIndex) {
                            Box(modifier = Modifier.fillMaxWidth().height(1.dp).padding(horizontal = 16.dp).background(Color.White.copy(alpha = 0.05f)))
                        }
                    }
                }
            }
        }


    }
}

@Composable
private fun MobileSettingsSubPage(
    page: String,
    onNavigate: (String) -> Unit,
    uiState: SettingsUiState,
    viewModel: SettingsViewModel,
    stremioAddons: List<com.arflix.tv.data.model.Addon>,
    openDnsProviderPicker: () -> Unit,
    openUiModeWarningDialog: () -> Unit,
    onAddCatalogClick: () -> Unit,
    onImportCatalogPackClick: () -> Unit,
    onRenameCatalogClick: (CatalogConfig) -> Unit,
    onDeleteCatalogClick: (CatalogConfig) -> Unit,
    onAddCustomAddonClick: () -> Unit,
    onBrowseCommunityAddonsClick: () -> Unit,
    openCustomUserAgentDialog: () -> Unit = {},
) {

    val scrollState = rememberScrollState()
    LaunchedEffect(page) {
        scrollState.scrollTo(0)
    }

    // The categories page is caught before the shared scrolling column and gets the full height
    // to itself. Press-and-hold reordering needs a list that scrolls itself while a row is held
    // against an edge, and that is impossible inside a page that is already scrolling. Every other
    // sub-page carries on unchanged below; the back arrow and the title live in the caller.


    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(
                start = 24.dp,
                end = 24.dp,
                top = 8.dp,
                bottom = 16.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            ),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        when (page) {
            "Playback & Controls" -> {
                MobileSettingsCategory(title = stringResource(R.string.settings_section_playback)) {
                    MobileSettingsRow(
                        icon = Icons.Default.Settings,
                        title = stringResource(R.string.frame_rate),
                        value = uiState.frameRateMatchingMode,
                        isFocused = false,
                        onClick = { viewModel.cycleFrameRateMatchingMode() }
                    )
                    MobileSettingsRow(
                        icon = Icons.Default.Movie,
                        title = stringResource(R.string.dv_compat_title),
                        subtitle = stringResource(R.string.dv_compat_desc),
                        value = stringResource(if (uiState.dolbyVisionCompatEnabled) R.string.on else R.string.off),
                        toggleChecked = uiState.dolbyVisionCompatEnabled,
                        isFocused = false,
                        onClick = { viewModel.setDolbyVisionCompatEnabled(!uiState.dolbyVisionCompatEnabled) }
                    )

                }
                MobileSettingsCategory(title = stringResource(R.string.settings_section_controls)) {
                    MobileSettingsRow(
                        icon = Icons.Default.Language,
                        title = stringResource(R.string.dns_provider),
                        value = uiState.dnsProvider,
                        isFocused = false,
                        onClick = openDnsProviderPicker
                    )
                    MobileSettingsRow(
                        icon = Icons.Default.Language,
                        title = stringResource(R.string.custom_user_agent),
                        value = formatUserAgentPreview(uiState.customUserAgent, 20),
                        isFocused = false,
                        showDivider = false,
                        onClick = openCustomUserAgentDialog
                    )
                }
            }
            "Audio & Subtitles" -> {
                MobileSettingsCategory(title = stringResource(R.string.settings_section_subtitles)) {
                    MobileSettingsRow(
                        icon = Icons.Default.Subtitles,
                        title = stringResource(R.string.subtitle_size),
                        value = uiState.subtitleSize,
                        isFocused = false,
                        onClick = { viewModel.cycleSubtitleSize() }
                    )
                    MobileSettingsRow(
                        icon = Icons.Default.Subtitles,
                        title = stringResource(R.string.subtitle_color),
                        value = uiState.subtitleColor,
                        isFocused = false,
                        onClick = { viewModel.cycleSubtitleColor() }
                    )
                    MobileSettingsRow(
                        icon = Icons.Default.Subtitles,
                        title = stringResource(R.string.subtitle_offset),
                        value = uiState.subtitleOffset,
                        isFocused = false,
                        onClick = { viewModel.cycleSubtitleOffset() }
                    )
                    MobileSettingsRow(
                        icon = Icons.Default.Subtitles,
                        title = stringResource(R.string.subtitle_style),
                        value = uiState.subtitleStyle,
                        isFocused = false,
                        onClick = { viewModel.cycleSubtitleStyle() }
                    )
                    MobileSettingsRow(
                        icon = Icons.Default.Subtitles,
                        title = stringResource(R.string.subtitle_font),
                        subtitle = stringResource(R.string.subtitle_font_desc),
                        value = uiState.subtitleFont,
                        isFocused = false,
                        onClick = { viewModel.cycleSubtitleFont() }
                    )
                    MobileSettingsRow(
                        icon = Icons.Default.Subtitles,
                        title = stringResource(R.string.subtitle_stylized),
                        subtitle = stringResource(R.string.subtitle_stylized_desc),
                        value = stringResource(if (uiState.subtitleStylized) R.string.on else R.string.off),
                        toggleChecked = uiState.subtitleStylized,
                        isToggle = true,
                        isFocused = false,
                        onClick = { viewModel.toggleSubtitleStylized() }
                    )
                    MobileSettingsRow(
                        icon = Icons.Default.Subtitles,
                        title = stringResource(R.string.subtitle_preload_title),
                        subtitle = stringResource(R.string.subtitle_preload_desc),
                        value = stringResource(if (uiState.subtitlePreloadEnabled) R.string.on else R.string.off),
                        toggleChecked = uiState.subtitlePreloadEnabled,
                        isToggle = true,
                        isFocused = false,
                        onClick = { viewModel.setSubtitlePreloadEnabled(!uiState.subtitlePreloadEnabled) }
                    )
                    MobileSettingsRow(
                        icon = Icons.Default.Subtitles,
                        title = stringResource(R.string.filter_subtitles),
                        subtitle = stringResource(R.string.filter_subtitles_desc),
                        value = stringResource(if (uiState.filterSubtitlesByLanguage) R.string.on else R.string.off),
                        toggleChecked = uiState.filterSubtitlesByLanguage,
                        isToggle = true,
                        isFocused = false,
                        onClick = { viewModel.setFilterSubtitlesByLanguage(!uiState.filterSubtitlesByLanguage) }
                    )
                    MobileSettingsRow(
                        icon = Icons.Default.Subtitles,
                        title = stringResource(R.string.use_forced_subtitles),
                        subtitle = stringResource(R.string.use_forced_subtitles_desc),
                        value = stringResource(if (uiState.useForcedSubtitles) R.string.on else R.string.off),
                        toggleChecked = uiState.useForcedSubtitles,
                        isToggle = true,
                        isFocused = false,
                        showDivider = false,
                        onClick = { viewModel.setUseForcedSubtitles(!uiState.useForcedSubtitles) }
                    )
                }
            }
            "Appearance" -> {
                MobileSettingsCategory(title = stringResource(R.string.settings_section_appearance)) {
                    MobileSettingsRow(
                        icon = Icons.Default.Palette,
                        title = stringResource(R.string.ui_mode),
                        value = uiState.deviceModeOverride.replaceFirstChar { it.uppercase() },
                        isFocused = false,
                        onClick = openUiModeWarningDialog
                    )
                    MobileSettingsRow(
                        icon = Icons.Default.Palette,
                        title = stringResource(R.string.oled_black_background),
                        subtitle = stringResource(R.string.oled_black_background_desc),
                        value = stringResource(if (uiState.oledBlackBackground) R.string.on else R.string.off),
                        toggleChecked = uiState.oledBlackBackground,
                        isFocused = false,
                        onClick = { viewModel.setOledBlackBackground(!uiState.oledBlackBackground) }
                    )
                    MobileSettingsRow(
                        icon = Icons.Default.Schedule,
                        title = stringResource(R.string.clock_format),
                        value = uiState.clockFormat,
                        isFocused = false,
                        onClick = { viewModel.cycleClockFormat() }
                    )
                    MobileSettingsRow(
                        icon = Icons.Default.SwapHoriz,
                        title = stringResource(R.string.anime_episode_structuring),
                        subtitle = stringResource(R.string.anime_episode_structuring_desc),
                        value = stringResource(
                            if (uiState.animeStructuringStyle == AnimeStructuringStyle.BROADCAST)
                                R.string.anime_structuring_broadcast
                            else
                                R.string.anime_structuring_standard
                        ),
                        isFocused = false,
                        showDivider = true,
                        onClick = { viewModel.cycleAnimeStructuringStyle() }
                    )
                    MobileSettingsRow(
                        icon = Icons.Default.VisibilityOff,
                        title = stringResource(R.string.spoiler_blur),
                        value = stringResource(if (uiState.spoilerBlurEnabled) R.string.on else R.string.off),
                        toggleChecked = uiState.spoilerBlurEnabled,
                        isFocused = false,
                        showDivider = true,
                        onClick = { viewModel.setSpoilerBlurEnabled(!uiState.spoilerBlurEnabled) }
                    )
                    MobileSettingsRow(
                        icon = Icons.Default.Palette,
                        title = stringResource(R.string.accent_color),
                        value = uiState.accentColor,
                        isFocused = false,
                        showDivider = true,
                        onClick = { viewModel.cycleAccentColor() }
                    )
                    MobileSettingsRow(
                        icon = Icons.Default.Palette,
                        title = stringResource(R.string.smooth_scrolling),
                        subtitle = stringResource(R.string.smooth_scrolling_desc),
                        value = stringResource(if (uiState.smoothScrolling) R.string.on else R.string.off),
                        toggleChecked = uiState.smoothScrolling,
                        isFocused = false,
                        showDivider = false,
                        onClick = { viewModel.setSmoothScrolling(!uiState.smoothScrolling) }
                    )
                }
            }
            "Addons" -> {
                StremioAddonsSettings(
                    addons = stremioAddons,
                    isRefreshingAddons = uiState.isRefreshingAddons,
                    focusedIndex = -1,
                    focusedActionIndex = 0,
                    onToggleAddon = { viewModel.toggleAddon(it) },
                    onDeleteAddon = { viewModel.removeAddon(it) },
                    onAddCustomAddon = onAddCustomAddonClick,
                    onBrowseCommunityAddons = onBrowseCommunityAddonsClick,
                    onRefreshAddons = { viewModel.refreshAddons() }
                )
            }

            "Catalogs" -> {
                CatalogsSettings(
                    catalogs = uiState.catalogs,
                    focusedIndex = -1,
                    focusedActionIndex = 0,
                    onAddCatalog = onAddCatalogClick,
                    onImportCatalogPack = onImportCatalogPackClick,
                    onRenameCatalog = onRenameCatalogClick,
                    onMoveCatalogUp = { viewModel.moveCatalogUp(it.id) },
                    onMoveCatalogDown = { viewModel.moveCatalogDown(it.id) },
                    onDeleteCatalog = onDeleteCatalogClick,
                    onUnpackCatalog = { viewModel.unpackCatalog(it.id) }
                )
            }

        }
    }


}

private enum class Zone {
    SIDEBAR, SECTION, CONTENT
}

@Composable
private fun tvSettingsSidebarGroup(section: String): String {
    return when (section) {
        "advanced" -> stringResource(R.string.settings_advanced)
        "playback", "language", "subtitles" -> stringResource(R.string.playback)
        "stremio", "catalogs" -> stringResource(R.string.sources)
        else -> stringResource(R.string.settings_group_system)
    }
}

@Composable
private fun SettingsSectionGroupLabel(label: String) {
    Text(
        text = label.uppercase(),
        style = ArflixTypography.caption.copy(fontSize = 10.sp),
        color = TextSecondary.copy(alpha = 0.46f),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(start = 9.dp, top = 8.dp)
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SettingsSectionItem(
    icon: ImageVector,
    title: String,
    isSelected: Boolean,
    isFocused: Boolean,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val bgColor = when {
        isFocused -> Color.White.copy(alpha = 0.12f)
        isSelected -> Color.White.copy(alpha = 0.07f)
        else -> Color.Transparent
    }
    val textColor = when {
        isFocused -> TextPrimary
        isSelected -> TextPrimary
        else -> TextSecondary
    }
    val accentColor = resolveAccentColor(fallback = Pink)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer()
            .clickable { onClick() }
            .background(bgColor, RoundedCornerShape(12.dp))
            .settingsFocusOutline(isFocused, cornerRadius = 12.dp)
            .padding(horizontal = 9.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .background(
                    if (isSelected || isFocused) accentColor.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.05f),
                    RoundedCornerShape(9.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isFocused || isSelected) accentColor else textColor,
                modifier = Modifier.size(17.dp)
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = title,
            style = ArflixTypography.body.copy(fontSize = 16.sp),
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (isSelected) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(20.dp)
                    .background(accentColor, RoundedCornerShape(99.dp))
            )
        }
    }
}

@Composable
private fun TvSettingsSectionHeader(
    section: String,
    uiState: SettingsUiState,
    addonCount: Int
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth(),
            verticalAlignment = Alignment.Bottom
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = tvSettingsSectionTitle(section),
                    style = ArflixTypography.sectionTitle.copy(
                        fontSize = 24.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = tvSettingsSectionDescription(section),
                    style = ArflixTypography.caption.copy(
                        fontSize = 13.sp
                    ),
                    color = TextSecondary.copy(alpha = 0.74f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(18.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                tvSettingsSectionPills(section, uiState, addonCount).take(2).forEach { pill ->
                    TvSettingsStatusPill(label = pill)
                }
            }
        }
    }
}

@Composable
private fun TvSettingsStatusPill(label: String) {
    Row(
        modifier = Modifier
            .height(26.dp)
            .background(Color.White.copy(alpha = 0.065f), RoundedCornerShape(999.dp))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(resolveAccentColor(fallback = Pink), RoundedCornerShape(99.dp))
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            style = ArflixTypography.caption.copy(fontSize = 11.sp),
            color = TextSecondary.copy(alpha = 0.86f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun TvSettingsInsightPanel(
    section: String,
    focusedIndex: Int,
    uiState: SettingsUiState,
    addonCount: Int,
    modifier: Modifier = Modifier
) {
    val help = tvSettingsFocusedHelp(section, focusedIndex)
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .background(Color(0xFF050505))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(22.dp))
            .padding(22.dp)
    ) {
        Text(
            text = stringResource(R.string.settings_overview),
            style = ArflixTypography.sectionTitle.copy(fontSize = 22.sp),
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = tvSettingsSectionDescription(section),
            style = ArflixTypography.caption,
            color = TextSecondary.copy(alpha = 0.7f),
            lineHeight = 18.sp
        )

        Spacer(modifier = Modifier.height(22.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White.copy(alpha = 0.055f), RoundedCornerShape(16.dp))
                .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(16.dp))
                .padding(18.dp)
        ) {
            Column {
                Text(
                    text = if (focusedIndex >= 0) stringResource(R.string.settings_focused_setting) else stringResource(R.string.settings_section_label),
                    style = ArflixTypography.caption,
                    color = resolveAccentColor(fallback = Pink),
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = help.title,
                    style = ArflixTypography.cardTitle,
                    color = TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = help.description,
                    style = ArflixTypography.caption,
                    color = TextSecondary.copy(alpha = 0.76f),
                    lineHeight = 18.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(22.dp))

        Text(
            text = stringResource(R.string.settings_status),
            style = ArflixTypography.caption.copy(fontSize = 11.sp),
            color = TextSecondary.copy(alpha = 0.54f),
            maxLines = 1
        )
        Spacer(modifier = Modifier.height(10.dp))
        tvSettingsPanelFacts(section, uiState, addonCount).forEach { fact ->
            TvSettingsFactRow(fact.first, fact.second)
            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

@Composable
private fun TvSettingsFactRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = ArflixTypography.caption,
            color = TextSecondary.copy(alpha = 0.62f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = ArflixTypography.caption,
            color = TextPrimary.copy(alpha = 0.88f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
            modifier = Modifier.widthIn(max = 132.dp)
        )
    }
}

private data class TvSettingsHelp(
    val title: String,
    val description: String
)

@Composable
private fun tvSettingsSectionTitle(section: String): String {
    return when (section) {
        "language" -> stringResource(R.string.language_and_audio)
        "subtitles" -> stringResource(R.string.subtitles)
        "advanced" -> stringResource(R.string.settings_advanced)
        "playback" -> stringResource(R.string.playback)
        "appearance" -> stringResource(R.string.interface_label)
        "network" -> stringResource(R.string.network)
        "catalogs" -> stringResource(R.string.catalogs)
        "stremio" -> stringResource(R.string.addons)
        else -> section.replaceFirstChar { it.uppercase() }
    }
}

@Composable
private fun tvSettingsSectionDescription(section: String): String {
    return when (section) {
        "language" -> stringResource(R.string.settings_desc_language)
        "subtitles" -> stringResource(R.string.settings_desc_subtitles)
        "advanced" -> stringResource(R.string.settings_desc_advanced)
        "playback" -> stringResource(R.string.settings_desc_playback)
        "appearance" -> stringResource(R.string.settings_desc_appearance)
        "network" -> stringResource(R.string.settings_desc_network)
        "catalogs" -> stringResource(R.string.settings_desc_catalogs)
        "stremio" -> stringResource(R.string.settings_desc_stremio)
        else -> stringResource(R.string.settings_desc_default)
    }
}

@Composable
private fun tvSettingsSectionPills(
    section: String,
    uiState: SettingsUiState,
    addonCount: Int
): List<String> {
    return when (section) {
        "language" -> listOf(
            stringResource(R.string.settings_pill_app, uiState.contentLanguage.uppercase()),
            stringResource(R.string.settings_pill_audio, localizeSettingValue(uiState.defaultAudioLanguage))
        )
        "subtitles" -> listOf(
            stringResource(R.string.settings_pill_default, localizeSettingValue(uiState.defaultSubtitle)),
            stringResource(R.string.settings_pill_size, localizeSettingValue(uiState.subtitleSize))
        )
        "advanced", "playback" -> emptyList()
        "appearance" -> listOf(
            stringResource(R.string.settings_pill_oled, if (uiState.oledBlackBackground) stringResource(R.string.settings_inline_on) else stringResource(R.string.settings_inline_off))
        )
        "network" -> listOf(stringResource(R.string.settings_pill_dns, uiState.dnsProvider), if (uiState.showLoadingStats) stringResource(R.string.settings_pill_stats_on) else stringResource(R.string.settings_pill_stats_off))
        "catalogs" -> listOf(stringResource(R.string.settings_pill_catalogs, uiState.catalogs.size))
        "stremio" -> listOf(stringResource(R.string.settings_pill_installed, addonCount))
        else -> emptyList()
    }
}

@Composable
private fun tvSettingsPanelFacts(
    section: String,
    uiState: SettingsUiState,
    addonCount: Int
): List<Pair<String, String>> {
    return when (section) {
        "language" -> listOf(
            stringResource(R.string.settings_fact_app_language) to uiState.contentLanguage.uppercase(),
            stringResource(R.string.audio) to uiState.defaultAudioLanguage
        )
        "subtitles" -> listOf(
            stringResource(R.string.settings_fact_default) to uiState.defaultSubtitle,
            stringResource(R.string.settings_fact_secondary) to uiState.secondarySubtitle,
            stringResource(R.string.settings_fact_style) to uiState.subtitleStyle
        )
        "advanced", "playback" -> listOf(
            stringResource(R.string.settings_fact_frame_rate) to uiState.frameRateMatchingMode
        )
        "appearance" -> listOf(
            stringResource(R.string.settings_fact_oled) to if (uiState.oledBlackBackground) stringResource(R.string.on) else stringResource(R.string.off),
            stringResource(R.string.settings_fact_accent_color) to uiState.accentColor
        )
        "network" -> listOf(
            stringResource(R.string.settings_fact_dns) to uiState.dnsProvider,
            stringResource(R.string.settings_fact_loading_stats) to if (uiState.showLoadingStats) stringResource(R.string.on) else stringResource(R.string.off),
            stringResource(R.string.settings_fact_user_agent) to formatUserAgentPreview(uiState.customUserAgent, 40)
        )
        "catalogs" -> listOf(
            stringResource(R.string.catalogs) to uiState.catalogs.size.toString(),
            stringResource(R.string.settings_fact_discovery) to if (uiState.isCatalogSearching) stringResource(R.string.settings_searching) else stringResource(R.string.settings_ready)
        )
        "stremio" -> listOf(
            stringResource(R.string.addons) to addonCount.toString()
        )
        else -> emptyList()
    }
}

@Composable
private fun tvSettingsFocusedHelp(section: String, focusedIndex: Int): TvSettingsHelp {
    if (focusedIndex < 0) {
        return TvSettingsHelp(
            title = stringResource(R.string.settings_help_choose_title),
            description = stringResource(R.string.settings_help_choose_desc)
        )
    }
    return when (section) {
        "language" -> when (focusedIndex) {
            0 -> TvSettingsHelp(stringResource(R.string.settings_fact_app_language), stringResource(R.string.settings_help_app_language_desc))
            else -> TvSettingsHelp(stringResource(R.string.default_audio), stringResource(R.string.settings_help_default_audio_desc))
        }
        "subtitles" -> when (focusedIndex) {
            0 -> TvSettingsHelp(stringResource(R.string.default_subtitle), stringResource(R.string.settings_help_default_subtitle_desc))
            1 -> TvSettingsHelp(stringResource(R.string.settings_help_secondary_subtitle), stringResource(R.string.settings_help_secondary_subtitle_desc))
            else -> TvSettingsHelp(stringResource(R.string.settings_help_subtitle_pref), stringResource(R.string.settings_help_subtitle_pref_desc))
        }
        "advanced", "playback" -> TvSettingsHelp(stringResource(R.string.settings_advanced), stringResource(R.string.settings_desc_advanced))
        "appearance" -> TvSettingsHelp(stringResource(R.string.interface_label), stringResource(R.string.settings_help_interface_desc))
        "network" -> TvSettingsHelp(stringResource(R.string.network), stringResource(R.string.settings_desc_network))
        "catalogs" -> when (focusedIndex) {
            0 -> TvSettingsHelp(stringResource(R.string.settings_help_discover_catalog), stringResource(R.string.settings_help_discover_catalog_desc))
            else -> TvSettingsHelp(stringResource(R.string.settings_help_catalog_row), stringResource(R.string.settings_help_catalog_row_desc))
        }
        "stremio" -> TvSettingsHelp(stringResource(R.string.settings_help_addon), stringResource(R.string.settings_help_addon_desc))
        else -> TvSettingsHelp(stringResource(R.string.settings_help_setting), stringResource(R.string.settings_help_setting_desc))
    }
}

private fun formatCompactCount(value: Int): String {
    return when {
        value >= 1_000_000 -> "${value / 1_000_000}M"
        value >= 1_000 -> "${value / 1_000}k"
        else -> value.toString()
    }
}

@Composable
private fun TvGeneralSettingsRows(
    section: String,
    defaultSubtitle: String,
    secondarySubtitle: String = "Off",
    defaultAudioLanguage: String,
    contentLanguage: String = "en-US",
    dnsProvider: String,
    frameRateMatchingMode: String,
    subtitleSize: String = "Medium",
    subtitleColor: String = "White",
    subtitleOffset: String = "Low",
    subtitleStyle: String = "Bold",
    subtitleFont: String = "System",
    deviceModeOverride: String = "auto",
    oledBlackBackground: Boolean = false,
    clockFormat: String = "24h",
    animeStructuringStyle: AnimeStructuringStyle = AnimeStructuringStyle.BROADCAST,
    smoothScrolling: Boolean = true,
    spoilerBlurEnabled: Boolean = false,
    accentColor: String = "White",
    volumeBoostDb: Int = 0,
    focusedIndex: Int,
    onSubtitleClick: () -> Unit,
    onSecondarySubtitleClick: () -> Unit = {},
    onAudioLanguageClick: () -> Unit,
    onFrameRateMatchingClick: () -> Unit,
    onDnsProviderClick: () -> Unit,
    onDeviceModeClick: () -> Unit = {},
    onContentLanguageClick: () -> Unit = {},
    onOledBlackBackgroundToggle: (Boolean) -> Unit = {},
    onClockFormatClick: () -> Unit = {},
    onAnimeStructuringStyleToggle: () -> Unit = {},
    onSmoothScrollingToggle: (Boolean) -> Unit = {},
    onSpoilerBlurToggle: (Boolean) -> Unit = {},
    onAccentColorClick: () -> Unit = {},
    showLoadingStats: Boolean = true,
    onShowLoadingStatsToggle: (Boolean) -> Unit = {},
    onVolumeBoostClick: () -> Unit = {},
    onSubtitleSizeClick: () -> Unit = {},
    onSubtitleColorClick: () -> Unit = {},
    onSubtitleOffsetClick: () -> Unit = {},
    onSubtitleStyleClick: () -> Unit = {},
    onSubtitleFontClick: () -> Unit = {},
    subtitleStylized: Boolean = true,
    onSubtitleStylizedToggle: () -> Unit = {},
    filterSubtitlesByLanguage: Boolean = true,
    onFilterSubtitlesByLanguageToggle: (Boolean) -> Unit = {},
    useForcedSubtitles: Boolean = false,
    onUseForcedSubtitlesToggle: (Boolean) -> Unit = {},
    subtitlePreloadEnabled: Boolean = false,
    dolbyVisionCompatEnabled: Boolean = true,
    onSubtitlePreloadToggle: (Boolean) -> Unit = {},
    onDolbyVisionCompatToggle: (Boolean) -> Unit = {},
    customUserAgent: String = "",
    onCustomUserAgentClick: () -> Unit = {},
    showAllSources: Boolean = false,
    onShowAllSourcesToggle: (Boolean) -> Unit = {},
    tvSummary: String? = null,
    isTvRescanning: Boolean = false,
    onRescanTvClick: () -> Unit = {}
) {
    Column {
        tvGeneralRowsForSection(section).forEachIndexed { localIndex, rowId ->
            if (localIndex > 0) Spacer(modifier = Modifier.height(10.dp))
            when (rowId) {
                0 -> SettingsRow(
                    icon = Icons.Default.Language,
                    title = stringResource(R.string.app_language),
                    subtitle = stringResource(R.string.app_language_desc),
                    value = TMDB_LANGUAGES.firstOrNull { it.first == contentLanguage }?.second ?: contentLanguage,
                    isFocused = focusedIndex == localIndex,
                    onClick = onContentLanguageClick,
                    modifier = Modifier.settingsFocusSlot(localIndex)
                )
                1 -> SettingsRow(
                    icon = Icons.Default.Subtitles,
                    title = stringResource(R.string.default_subtitle),
                    subtitle = stringResource(R.string.subtitle_desc),
                    value = defaultSubtitle,
                    isFocused = focusedIndex == localIndex,
                    onClick = onSubtitleClick,
                    modifier = Modifier.settingsFocusSlot(localIndex)
                )
                2 -> SettingsRow(
                    icon = Icons.Default.Subtitles,
                    title = stringResource(R.string.secondary_subtitle),
                    subtitle = stringResource(R.string.secondary_subtitle_desc),
                    value = secondarySubtitle,
                    isFocused = focusedIndex == localIndex,
                    onClick = onSecondarySubtitleClick,
                    modifier = Modifier.settingsFocusSlot(localIndex)
                )
                3 -> SettingsRow(
                    icon = Icons.Default.VolumeUp,
                    title = stringResource(R.string.default_audio),
                    subtitle = stringResource(R.string.audio_desc),
                    value = defaultAudioLanguage,
                    isFocused = focusedIndex == localIndex,
                    onClick = onAudioLanguageClick,
                    modifier = Modifier.settingsFocusSlot(localIndex)
                )
                4 -> SettingsRow(Icons.Default.Subtitles, stringResource(R.string.subtitle_size), stringResource(R.string.subtitle_size_desc), subtitleSize, focusedIndex == localIndex, onSubtitleSizeClick, Modifier.settingsFocusSlot(localIndex))
                5 -> SettingsRow(Icons.Default.Subtitles, stringResource(R.string.subtitle_color), stringResource(R.string.subtitle_color_desc), subtitleColor, focusedIndex == localIndex, onSubtitleColorClick, Modifier.settingsFocusSlot(localIndex))
                6 -> SettingsRow(Icons.Default.Subtitles, stringResource(R.string.subtitle_offset), stringResource(R.string.subtitle_offset_desc), subtitleOffset, focusedIndex == localIndex, onSubtitleOffsetClick, Modifier.settingsFocusSlot(localIndex))
                7 -> SettingsRow(Icons.Default.Subtitles, stringResource(R.string.subtitle_style), stringResource(R.string.subtitle_style_desc), subtitleStyle, focusedIndex == localIndex, onSubtitleStyleClick, Modifier.settingsFocusSlot(localIndex))
                42 -> SettingsRow(Icons.Default.Subtitles, stringResource(R.string.subtitle_font), stringResource(R.string.subtitle_font_desc), subtitleFont, focusedIndex == localIndex, onSubtitleFontClick, Modifier.settingsFocusSlot(localIndex))
                8 -> SettingsToggleRow(stringResource(R.string.subtitle_stylized), stringResource(R.string.subtitle_stylized_desc), subtitleStylized, focusedIndex == localIndex, { onSubtitleStylizedToggle() }, Modifier.settingsFocusSlot(localIndex))
                9 -> SettingsToggleRow(stringResource(R.string.filter_subtitles), stringResource(R.string.filter_subtitles_desc), filterSubtitlesByLanguage, focusedIndex == localIndex, onFilterSubtitlesByLanguageToggle, Modifier.settingsFocusSlot(localIndex))
                45 -> SettingsToggleRow(stringResource(R.string.use_forced_subtitles), stringResource(R.string.use_forced_subtitles_desc), useForcedSubtitles, focusedIndex == localIndex, onUseForcedSubtitlesToggle, Modifier.settingsFocusSlot(localIndex))
                15 -> SettingsRow(Icons.Default.Movie, stringResource(R.string.frame_rate), stringResource(R.string.frame_rate_desc), frameRateMatchingMode, focusedIndex == localIndex, onFrameRateMatchingClick, Modifier.settingsFocusSlot(localIndex))
                18 -> SettingsRow(
                    icon = Icons.Default.Settings,
                    title = stringResource(R.string.ui_mode),
                    subtitle = stringResource(R.string.ui_mode_desc),
                    value = when (deviceModeOverride) {
                        "tv" -> "TV"
                        "tablet" -> stringResource(R.string.settings_mode_tablet)
                        "phone" -> stringResource(R.string.settings_mode_phone)
                        else -> stringResource(R.string.auto)
                    },
                    isFocused = focusedIndex == localIndex,
                    onClick = onDeviceModeClick,
                    modifier = Modifier.settingsFocusSlot(localIndex)
                )
                20 -> SettingsToggleRow(stringResource(R.string.oled_black_background), stringResource(R.string.oled_black_background_desc), oledBlackBackground, focusedIndex == localIndex, onOledBlackBackgroundToggle, Modifier.settingsFocusSlot(localIndex))
                21 -> SettingsRow(Icons.Default.Schedule, stringResource(R.string.clock_format), stringResource(R.string.clock_format_desc), if (clockFormat == "12h") "12-hour" else "24-hour", focusedIndex == localIndex, onClockFormatClick, Modifier.settingsFocusSlot(localIndex))
                46 -> SettingsRow(
                    icon = Icons.Default.SwapHoriz,
                    title = stringResource(R.string.anime_episode_structuring),
                    subtitle = stringResource(R.string.anime_episode_structuring_desc),
                    value = stringResource(
                        if (animeStructuringStyle == AnimeStructuringStyle.BROADCAST)
                            R.string.anime_structuring_broadcast
                        else
                            R.string.anime_structuring_standard
                    ),
                    isFocused = focusedIndex == localIndex,
                    onClick = onAnimeStructuringStyleToggle,
                    modifier = Modifier.settingsFocusSlot(localIndex)
                )
                36 -> SettingsToggleRow(stringResource(R.string.smooth_scrolling), stringResource(R.string.smooth_scrolling_desc), smoothScrolling, focusedIndex == localIndex, onSmoothScrollingToggle, Modifier.settingsFocusSlot(localIndex))
                23 -> SettingsToggleRow(stringResource(R.string.spoiler_blur), stringResource(R.string.spoiler_blur_desc), spoilerBlurEnabled, focusedIndex == localIndex, onSpoilerBlurToggle, Modifier.settingsFocusSlot(localIndex))
                24 -> SettingsRow(Icons.Default.Palette, stringResource(R.string.accent_color), stringResource(R.string.accent_color_desc), accentColor, focusedIndex == localIndex, onAccentColorClick, Modifier.settingsFocusSlot(localIndex))
                25 -> SettingsRow(Icons.Default.Language, stringResource(R.string.dns_provider), stringResource(R.string.dns_desc), dnsProvider, focusedIndex == localIndex, onDnsProviderClick, Modifier.settingsFocusSlot(localIndex))
                26 -> SettingsToggleRow(stringResource(R.string.show_loading_stats), stringResource(R.string.show_loading_stats_desc), showLoadingStats, focusedIndex == localIndex, onShowLoadingStatsToggle, Modifier.settingsFocusSlot(localIndex))
                27 -> SettingsRow(
                    icon = Icons.Default.VolumeUp,
                    title = stringResource(R.string.volume_boost),
                    subtitle = stringResource(R.string.volume_boost_desc),
                    value = if (volumeBoostDb == 0) "0 dB" else "+${volumeBoostDb} dB",
                    isFocused = focusedIndex == localIndex,
                    onClick = onVolumeBoostClick,
                    modifier = Modifier.settingsFocusSlot(localIndex)
                )
                39 -> SettingsToggleRow(stringResource(R.string.subtitle_preload_title), stringResource(R.string.subtitle_preload_desc), subtitlePreloadEnabled, focusedIndex == localIndex, onSubtitlePreloadToggle, Modifier.settingsFocusSlot(localIndex))
                40 -> SettingsToggleRow(stringResource(R.string.dv_compat_title), stringResource(R.string.dv_compat_desc), dolbyVisionCompatEnabled, focusedIndex == localIndex, onDolbyVisionCompatToggle, Modifier.settingsFocusSlot(localIndex))
                35 -> SettingsRow(Icons.Default.Language, stringResource(R.string.custom_user_agent), stringResource(R.string.custom_user_agent_desc), formatUserAgentPreview(customUserAgent, 30), focusedIndex == localIndex, onCustomUserAgentClick, Modifier.settingsFocusSlot(localIndex))
                51 -> SettingsToggleRow(stringResource(R.string.personal_show_all_sources), stringResource(R.string.personal_show_all_sources_desc), showAllSources, focusedIndex == localIndex, onShowAllSourcesToggle, Modifier.settingsFocusSlot(localIndex))
                52 -> SettingsRow(
                    icon = Icons.Default.LiveTv,
                    title = stringResource(R.string.personal_this_tv),
                    subtitle = tvSummary ?: stringResource(R.string.personal_this_tv_not_scanned),
                    value = stringResource(if (isTvRescanning) R.string.personal_tv_scanning else R.string.personal_tv_check_open),
                    isFocused = focusedIndex == localIndex,
                    onClick = onRescanTvClick,
                    modifier = Modifier.settingsFocusSlot(localIndex)
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun GeneralSettings(
    defaultSubtitle: String,
    secondarySubtitle: String = "Off",
    defaultAudioLanguage: String,
    contentLanguage: String = "en-US",
    dnsProvider: String,
    frameRateMatchingMode: String,
    subtitleSize: String = "Medium",
    subtitleColor: String = "White",
    subtitleOffset: String = "Low",
    subtitleStyle: String = "Bold",
    deviceModeOverride: String = "auto",
    oledBlackBackground: Boolean = false,
    clockFormat: String = "24h",
    animeStructuringStyle: AnimeStructuringStyle = AnimeStructuringStyle.BROADCAST,
    spoilerBlurEnabled: Boolean = false,
    accentColor: String = "White",
    volumeBoostDb: Int = 0,
    focusedIndex: Int,
    onSubtitleClick: () -> Unit,
    onSecondarySubtitleClick: () -> Unit = {},
    onAudioLanguageClick: () -> Unit,
    onFrameRateMatchingClick: () -> Unit,
    onDnsProviderClick: () -> Unit,
    onDeviceModeClick: () -> Unit = {},
    onContentLanguageClick: () -> Unit = {},
    onOledBlackBackgroundToggle: (Boolean) -> Unit = {},
    onClockFormatClick: () -> Unit = {},
    onAnimeStructuringStyleToggle: () -> Unit = {},
    onSpoilerBlurToggle: (Boolean) -> Unit = {},
    onAccentColorClick: () -> Unit = {},
    showLoadingStats: Boolean = true,
    onShowLoadingStatsToggle: (Boolean) -> Unit = {},
    onVolumeBoostClick: () -> Unit = {},
    onSubtitleSizeClick: () -> Unit = {},
    onSubtitleColorClick: () -> Unit = {},
    onSubtitleOffsetClick: () -> Unit = {},
    onSubtitleStyleClick: () -> Unit = {},
    onSubtitleFontClick: () -> Unit = {},
    subtitleStylized: Boolean = true,
    onSubtitleStylizedToggle: () -> Unit = {},
    filterSubtitlesByLanguage: Boolean = true,
    onFilterSubtitlesByLanguageToggle: (Boolean) -> Unit = {},
    customUserAgent: String = "",
    onCustomUserAgentClick: () -> Unit = {}
) {
    Column {
        // ── Language & Subtitles ──
        Text(
            text = stringResource(R.string.language_and_subtitles),
            style = ArflixTypography.caption.copy(fontSize = 11.sp, letterSpacing = 0.8.sp),
            color = TextSecondary.copy(alpha = 0.5f),
            modifier = Modifier.padding(start = 4.dp, bottom = 12.dp)
        )

        SettingsRow(
            icon = Icons.Default.Subtitles,
            title = stringResource(R.string.app_language),
            subtitle = stringResource(R.string.app_language_desc),
            value = TMDB_LANGUAGES.firstOrNull { it.first == contentLanguage }?.second ?: contentLanguage,
            isFocused = focusedIndex == 0,
            onClick = onContentLanguageClick,
            modifier = Modifier.settingsFocusSlot(0)
        )
        Spacer(modifier = Modifier.height(10.dp))
        SettingsRow(
            icon = Icons.Default.Subtitles,
            title = stringResource(R.string.default_subtitle),
            subtitle = stringResource(R.string.subtitle_desc),
            value = defaultSubtitle,
            isFocused = focusedIndex == 1,
            onClick = onSubtitleClick,
            modifier = Modifier.settingsFocusSlot(1)
        )
        Spacer(modifier = Modifier.height(10.dp))
        SettingsRow(
            icon = Icons.Default.Subtitles,
            title = stringResource(R.string.secondary_subtitle),
            subtitle = stringResource(R.string.secondary_subtitle_desc),
            value = secondarySubtitle,
            isFocused = focusedIndex == 2,
            onClick = onSecondarySubtitleClick,
            modifier = Modifier.settingsFocusSlot(2)
        )
        Spacer(modifier = Modifier.height(10.dp))
        SettingsRow(
            icon = Icons.Default.VolumeUp,
            title = stringResource(R.string.default_audio),
            subtitle = stringResource(R.string.audio_desc),
            value = defaultAudioLanguage,
            isFocused = focusedIndex == 3,
            onClick = onAudioLanguageClick,
            modifier = Modifier.settingsFocusSlot(3)
        )
        Spacer(modifier = Modifier.height(10.dp))
        SettingsRow(
            icon = Icons.Default.Subtitles,
            title = stringResource(R.string.subtitle_size),
            subtitle = stringResource(R.string.subtitle_size_desc),
            value = subtitleSize,
            isFocused = focusedIndex == 4,
            onClick = onSubtitleSizeClick,
            modifier = Modifier.settingsFocusSlot(4)
        )
        Spacer(modifier = Modifier.height(10.dp))
        SettingsRow(
            icon = Icons.Default.Subtitles,
            title = stringResource(R.string.subtitle_color),
            subtitle = stringResource(R.string.subtitle_color_desc),
            value = subtitleColor,
            isFocused = focusedIndex == 5,
            onClick = onSubtitleColorClick,
            modifier = Modifier.settingsFocusSlot(5)
        )
        Spacer(modifier = Modifier.height(10.dp))
        SettingsRow(
            icon = Icons.Default.Subtitles,
            title = stringResource(R.string.subtitle_offset),
            subtitle = stringResource(R.string.subtitle_offset_desc),
            value = subtitleOffset,
            isFocused = focusedIndex == 6,
            onClick = onSubtitleOffsetClick,
            modifier = Modifier.settingsFocusSlot(6)
        )
        Spacer(modifier = Modifier.height(10.dp))
        SettingsRow(
            icon = Icons.Default.Subtitles,
            title = stringResource(R.string.subtitle_style),
            subtitle = stringResource(R.string.subtitle_style_desc),
            value = subtitleStyle,
            isFocused = focusedIndex == 7,
            onClick = onSubtitleStyleClick,
            modifier = Modifier.settingsFocusSlot(7)
        )
        Spacer(modifier = Modifier.height(10.dp))
        SettingsToggleRow(
            title = stringResource(R.string.subtitle_stylized),
            subtitle = stringResource(R.string.subtitle_stylized_desc),
            isEnabled = subtitleStylized,
            isFocused = focusedIndex == 8,
            onToggle = { onSubtitleStylizedToggle() },
            modifier = Modifier.settingsFocusSlot(8)
        )
        Spacer(modifier = Modifier.height(10.dp))
        SettingsToggleRow(
            title = stringResource(R.string.filter_subtitles),
            subtitle = stringResource(R.string.filter_subtitles_desc),
            isEnabled = filterSubtitlesByLanguage,
            isFocused = focusedIndex == 9,
            onToggle = onFilterSubtitlesByLanguageToggle,
            modifier = Modifier.settingsFocusSlot(9)
        )

        // ── Playback ──
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.playback),
            style = ArflixTypography.caption.copy(fontSize = 11.sp, letterSpacing = 0.8.sp),
            color = TextSecondary.copy(alpha = 0.5f),
            modifier = Modifier.padding(start = 4.dp, bottom = 12.dp)
        )


        Spacer(modifier = Modifier.height(10.dp))

        Spacer(modifier = Modifier.height(10.dp))

        Spacer(modifier = Modifier.height(10.dp))

        Spacer(modifier = Modifier.height(10.dp))

        Spacer(modifier = Modifier.height(10.dp))
        SettingsRow(
            icon = Icons.Default.Movie,
            title = stringResource(R.string.frame_rate),
            subtitle = stringResource(R.string.frame_rate_desc),
            value = frameRateMatchingMode,
            isFocused = focusedIndex == 15,
            onClick = onFrameRateMatchingClick,
            modifier = Modifier.settingsFocusSlot(15)
        )
        Spacer(modifier = Modifier.height(10.dp))


        // ── Interface ──
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.interface_label),
            style = ArflixTypography.caption.copy(fontSize = 11.sp, letterSpacing = 0.8.sp),
            color = TextSecondary.copy(alpha = 0.5f),
            modifier = Modifier.padding(start = 4.dp, bottom = 12.dp)
        )

        Spacer(modifier = Modifier.height(10.dp))
        SettingsRow(
            icon = Icons.Default.Settings,
            title = stringResource(R.string.ui_mode),
            subtitle = stringResource(R.string.ui_mode_desc),
            value = when (deviceModeOverride) {
                "tv" -> "TV"
                "tablet" -> "Tablet"
                "phone" -> "Phone"
                else -> "Auto"
            },
            isFocused = focusedIndex == 18,
            onClick = onDeviceModeClick,
            modifier = Modifier.settingsFocusSlot(18)
        )
        Spacer(modifier = Modifier.height(10.dp))
        Spacer(modifier = Modifier.height(10.dp))
        SettingsToggleRow(
            title = stringResource(R.string.oled_black_background),
            subtitle = stringResource(R.string.oled_black_background_desc),
            isEnabled = oledBlackBackground,
            isFocused = focusedIndex == 20,
            onToggle = onOledBlackBackgroundToggle,
            modifier = Modifier.settingsFocusSlot(20)
        )
        Spacer(modifier = Modifier.height(10.dp))
        SettingsRow(
            icon = Icons.Default.Schedule,
            title = stringResource(R.string.clock_format),
            subtitle = stringResource(R.string.clock_format_desc),
            value = if (clockFormat == "12h") stringResource(R.string.settings_clock_12h) else stringResource(R.string.settings_clock_24h),
            isFocused = focusedIndex == 21,
            onClick = onClockFormatClick,
            modifier = Modifier.settingsFocusSlot(21)
        )
        Spacer(modifier = Modifier.height(10.dp))
        SettingsRow(
            icon = Icons.Default.SwapHoriz,
            title = stringResource(R.string.anime_episode_structuring),
            subtitle = stringResource(R.string.anime_episode_structuring_desc),
            value = stringResource(
                if (animeStructuringStyle == AnimeStructuringStyle.BROADCAST)
                    R.string.anime_structuring_broadcast
                else
                    R.string.anime_structuring_standard
            ),
            isFocused = focusedIndex == 46,
            onClick = onAnimeStructuringStyleToggle,
            modifier = Modifier.settingsFocusSlot(46)
        )
        Spacer(modifier = Modifier.height(10.dp))
        SettingsToggleRow(
            title = stringResource(R.string.spoiler_blur),
            subtitle = stringResource(R.string.spoiler_blur_desc),
            isEnabled = spoilerBlurEnabled,
            isFocused = focusedIndex == 23,
            onToggle = onSpoilerBlurToggle,
            modifier = Modifier.settingsFocusSlot(23)
        )
        Spacer(modifier = Modifier.height(10.dp))
        SettingsRow(
            icon = Icons.Default.Palette,
            title = stringResource(R.string.accent_color),
            subtitle = stringResource(R.string.accent_color_desc),
            value = accentColor,
            isFocused = focusedIndex == 24,
            onClick = onAccentColorClick,
            modifier = Modifier.settingsFocusSlot(24)
        )

        // ── Network ──
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.network),
            style = ArflixTypography.caption.copy(fontSize = 11.sp, letterSpacing = 0.8.sp),
            color = TextSecondary.copy(alpha = 0.5f),
            modifier = Modifier.padding(start = 4.dp, bottom = 12.dp)
        )

        SettingsRow(
            icon = Icons.Default.Language,
            title = stringResource(R.string.dns_provider),
            subtitle = stringResource(R.string.dns_desc),
            value = dnsProvider,
            isFocused = focusedIndex == 25,
            onClick = onDnsProviderClick,
            modifier = Modifier.settingsFocusSlot(25)
        )
        Spacer(modifier = Modifier.height(10.dp))
        SettingsToggleRow(
            title = stringResource(R.string.show_loading_stats),
            subtitle = stringResource(R.string.show_loading_stats_desc),
            isEnabled = showLoadingStats,
            isFocused = focusedIndex == 26,
            onToggle = onShowLoadingStatsToggle,
            modifier = Modifier.settingsFocusSlot(26)
        )
        Spacer(modifier = Modifier.height(10.dp))
        SettingsRow(
            icon = Icons.Default.Language,
            title = stringResource(R.string.custom_user_agent),
            subtitle = stringResource(R.string.custom_user_agent_desc),
            value = formatUserAgentPreview(customUserAgent, 30),
            isFocused = focusedIndex == 35,
            onClick = onCustomUserAgentClick,
            modifier = Modifier.settingsFocusSlot(35)
        )

        // ── Audio ──
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.audio),
            style = ArflixTypography.caption.copy(fontSize = 11.sp, letterSpacing = 0.8.sp),
            color = TextSecondary.copy(alpha = 0.5f),
            modifier = Modifier.padding(start = 4.dp, bottom = 12.dp)
        )

        SettingsRow(
            icon = Icons.Default.VolumeUp,
            title = stringResource(R.string.volume_boost),
            subtitle = stringResource(R.string.volume_boost_desc),
            value = when (volumeBoostDb) {
                0 -> stringResource(R.string.off)
                else -> "+${volumeBoostDb} dB"
            },
            isFocused = focusedIndex == 27,
            onClick = onVolumeBoostClick,
            modifier = Modifier.settingsFocusSlot(27)
        )
    }
}

@Composable
private fun CustomUserAgentDialog(
    currentValue: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val isMobile = LocalDeviceType.current.isTouchDevice()
    var value by remember(currentValue) { mutableStateOf(currentValue) }
    val inputFocusRequester = remember { FocusRequester() }
    BackHandler { onDismiss() }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(100)
        inputFocusRequester.requestFocus()
    }
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .then(
                    if (isMobile) Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                    else Modifier.width(520.dp)
                )
                .clip(RoundedCornerShape(16.dp))
                .background(BackgroundElevated)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
                Text(text = stringResource(R.string.custom_user_agent), style = ArflixTypography.sectionTitle, color = TextPrimary)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.custom_user_agent_desc),
                    style = ArflixTypography.caption,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(16.dp))
                androidx.compose.material3.OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    placeholder = { Text(OkHttpProvider.DEFAULT_USER_AGENT, color = TextSecondary.copy(alpha = 0.4f)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().focusRequester(inputFocusRequester),
                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = Pink,
                        unfocusedBorderColor = TextSecondary.copy(alpha = 0.3f)
                    )
                )
                Spacer(modifier = Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val cancelFocus = remember { FocusRequester() }
                    val saveFocus = remember { FocusRequester() }
                    Surface(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f).focusRequester(cancelFocus).pointerInput(Unit) {
                            detectTapGestures(onTap = { onDismiss() })
                        },
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = BackgroundElevated,
                            focusedContainerColor = BackgroundElevated.copy(alpha = 0.8f)
                        ),
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                        border = ClickableSurfaceDefaults.border(
                            border = androidx.tv.material3.Border(
                                border = androidx.compose.foundation.BorderStroke(1.dp, TextSecondary.copy(alpha = 0.3f))
                            )
                        )
                    ) {
                        Text(
                            text = stringResource(R.string.cancel),
                            modifier = Modifier.padding(vertical = 12.dp).fillMaxWidth(),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            color = TextSecondary
                        )
                    }
                    Surface(
                        onClick = { onSave(value) },
                        modifier = Modifier.weight(1f).focusRequester(saveFocus).pointerInput(Unit) {
                            detectTapGestures(onTap = { onSave(value) })
                        },
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = Pink.copy(alpha = 0.15f),
                            focusedContainerColor = Pink.copy(alpha = 0.25f)
                        ),
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                        border = ClickableSurfaceDefaults.border(
                            border = androidx.tv.material3.Border(
                                border = androidx.compose.foundation.BorderStroke(1.dp, Pink.copy(alpha = 0.4f))
                            )
                        )
                    ) {
                        Text(
                            text = stringResource(R.string.save),
                            modifier = Modifier.padding(vertical = 12.dp).fillMaxWidth(),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            color = Pink
                        )
                    }
                    LaunchedEffect(Unit) {
                        kotlinx.coroutines.delay(150)
                        inputFocusRequester.requestFocus()
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun CatalogDiscoveryModal(
    query: String,
    results: List<CatalogDiscoveryResult>,
    isSearching: Boolean,
    error: String?,
    manualUrl: String,
    addedCatalogUrls: Set<String>,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onAddResult: (CatalogDiscoveryResult) -> Unit,
    onManualUrlChange: (String) -> Unit,
    onManualAdd: () -> Unit,
    onDismiss: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val view = LocalView.current
    var editingInput by remember { mutableStateOf<CatalogDiscoveryInputTarget?>(null) }
    val normalizedAddedCatalogUrls = remember(addedCatalogUrls) {
        addedCatalogUrls.map { normalizeCatalogDiscoveryUrl(it) }.toSet()
    }
    fun addResult(result: CatalogDiscoveryResult) {
        val normalizedUrl = normalizeCatalogDiscoveryUrl(result.sourceUrl)
        if (normalizedUrl in normalizedAddedCatalogUrls) return
        onAddResult(result)
    }
    fun submitSearch() {
        focusManager.clearFocus()
        (view.context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
            ?.hideSoftInputFromWindow(view.windowToken, 0)
        onSearch()
    }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isCompact = maxWidth < 720.dp
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.86f))
                    .padding(
                        horizontal = if (isCompact) 10.dp else 48.dp,
                        vertical = if (isCompact) 10.dp else 34.dp
                    ),
                contentAlignment = Alignment.TopCenter
            ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(if (isCompact) 12.dp else 18.dp))
                    .background(Color.Black.copy(alpha = 0.34f), RoundedCornerShape(if (isCompact) 12.dp else 18.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(if (isCompact) 12.dp else 18.dp))
                    .padding(if (isCompact) 10.dp else 18.dp)
            ) {
                Text("Catalogs", color = TextSecondary)

                if (isCompact) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.Black.copy(alpha = 0.32f))
                            .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(14.dp))
                            .padding(10.dp)
                    ) {
                        CatalogDiscoveryInputButton(
                            label = stringResource(R.string.settings_search_lists),
                            value = query,
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = stringResource(R.string.settings_search_lists),
                            onClick = { editingInput = CatalogDiscoveryInputTarget.Search }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            DiscoveryActionButton(
                                label = if (isSearching) stringResource(R.string.settings_searching) else stringResource(R.string.search),
                                onClick = { submitSearch() },
                                highlighted = true,
                                enabled = !isSearching && query.isNotBlank(),
                                modifier = Modifier.weight(1f)
                            )
                            DiscoveryActionButton(
                                label = stringResource(R.string.close),
                                onClick = onDismiss,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        CatalogDiscoveryInputButton(
                            label = stringResource(R.string.settings_paste_url),
                            value = manualUrl,
                            placeholder = stringResource(R.string.settings_paste_catalog_url),
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { editingInput = CatalogDiscoveryInputTarget.ManualUrl }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        DiscoveryActionButton(
                            label = stringResource(R.string.settings_add_url),
                            onClick = onManualAdd,
                            enabled = manualUrl.isNotBlank(),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.Black.copy(alpha = 0.32f))
                            .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(16.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CatalogDiscoveryInputButton(
                            label = stringResource(R.string.settings_search_lists),
                            value = query,
                            modifier = Modifier.weight(1f),
                            placeholder = stringResource(R.string.settings_search_lists),
                            onClick = { editingInput = CatalogDiscoveryInputTarget.Search }
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        DiscoveryActionButton(
                            label = if (isSearching) stringResource(R.string.settings_searching) else stringResource(R.string.search),
                            onClick = { submitSearch() },
                            highlighted = true,
                            enabled = !isSearching && query.isNotBlank()
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        CatalogDiscoveryInputButton(
                            label = stringResource(R.string.settings_paste_url),
                            value = manualUrl,
                            placeholder = stringResource(R.string.settings_paste_catalog_url),
                            modifier = Modifier.weight(1f),
                            onClick = { editingInput = CatalogDiscoveryInputTarget.ManualUrl }
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        DiscoveryActionButton(
                            label = stringResource(R.string.settings_add_url),
                            onClick = onManualAdd,
                            enabled = manualUrl.isNotBlank()
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        DiscoveryActionButton(
                            label = stringResource(R.string.close),
                            onClick = onDismiss
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (isCompact) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = when {
                                isSearching -> stringResource(R.string.settings_searching_lists)
                                else -> stringResource(R.string.settings_lists_found, results.size)
                            },
                            style = ArflixTypography.caption,
                            color = TextSecondary
                        )
                        Text(
                            text = stringResource(R.string.settings_tap_field_to_edit),
                            style = ArflixTypography.caption,
                            color = TextSecondary.copy(alpha = 0.75f)
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                    Text(
                        text = when {
                            isSearching -> stringResource(R.string.settings_searching_lists)
                            else -> stringResource(R.string.settings_lists_found, results.size)
                        },
                        style = ArflixTypography.caption,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = stringResource(R.string.settings_press_select_to_edit),
                        style = ArflixTypography.caption,
                        color = TextSecondary.copy(alpha = 0.75f)
                    )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Black.copy(alpha = 0.20f))
                        .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
                        .padding(if (isCompact) 8.dp else 12.dp)
                ) {
                    when {
                        isSearching -> {
                            Column(
                                modifier = Modifier.align(Alignment.Center),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                LoadingIndicator(size = 30.dp)
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(stringResource(R.string.settings_searching_lists), color = TextSecondary, style = ArflixTypography.body)
                            }
                        }
                        error != null -> {
                            Text(
                                text = error,
                                color = TextSecondary,
                                style = ArflixTypography.body,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }
                        results.isEmpty() -> {
                            Column(
                                modifier = Modifier.align(Alignment.Center),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = stringResource(R.string.settings_start_with_search),
                                    color = TextPrimary,
                                    style = ArflixTypography.bodyLarge
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = stringResource(R.string.settings_search_examples),
                                    color = TextSecondary,
                                    style = ArflixTypography.body,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 28.dp)
                                )
                            }
                        }
                        else -> {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                itemsIndexed(results, key = { _, item -> item.id }) { _, item ->
                                    val isAdded = normalizeCatalogDiscoveryUrl(item.sourceUrl) in normalizedAddedCatalogUrls
                                    CatalogDiscoveryResultRow(
                                        result = item,
                                        isAdded = isAdded,
                                        onAdd = { addResult(item) },
                                        compact = isCompact
                                    )
                                }
                            }
                        }
                    }
                }
            }
            }
        }
    }

    editingInput?.let { target ->
        CatalogDiscoveryTextInputDialog(
            title = if (target == CatalogDiscoveryInputTarget.Search) stringResource(R.string.settings_search_lists) else stringResource(R.string.settings_paste_catalog_url),
            initialValue = if (target == CatalogDiscoveryInputTarget.Search) query else manualUrl,
            placeholder = stringResource(if (target == CatalogDiscoveryInputTarget.Search) R.string.settings_search_lists else R.string.settings_paste_catalog_url),
            confirmLabel = if (target == CatalogDiscoveryInputTarget.Search) stringResource(R.string.settings_use_search) else stringResource(R.string.settings_use_url),
            onConfirm = { value ->
                if (target == CatalogDiscoveryInputTarget.Search) {
                    onQueryChange(value)
                } else {
                    onManualUrlChange(value)
                }
                editingInput = null
            },
            onDismiss = { editingInput = null }
        )
    }
}

private enum class CatalogDiscoveryInputTarget {
    Search,
    ManualUrl
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun CatalogDiscoveryInputButton(
    label: String,
    value: String,
    placeholder: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val inputFocusColor = resolveAccentColor(fallback = Color.White)
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (isFocused) Color.White else Color.Black.copy(alpha = 0.36f))
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) inputFocusColor else Color.White.copy(alpha = 0.55f),
                shape = RoundedCornerShape(12.dp)
            )
            .onFocusChanged { isFocused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Text(
            text = label,
            style = ArflixTypography.caption,
            color = if (isFocused) Color.Black.copy(alpha = 0.68f) else TextSecondary,
            maxLines = 1
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value.ifBlank { placeholder },
            style = ArflixTypography.body,
            color = when {
                isFocused -> Color.Black
                value.isBlank() -> TextSecondary.copy(alpha = 0.7f)
                else -> TextPrimary
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun CatalogDiscoveryTextInputDialog(
    title: String,
    initialValue: String,
    placeholder: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var value by remember(initialValue) { mutableStateOf(initialValue) }
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            val isCompact = maxWidth < 720.dp
            Column(
                modifier = Modifier
                    .widthIn(max = 680.dp)
                    .fillMaxWidth(if (isCompact) 0.92f else 0.76f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF181818), RoundedCornerShape(16.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(16.dp))
                    .padding(if (isCompact) 16.dp else 20.dp)
            ) {
            Text(title, style = ArflixTypography.sectionTitle, color = TextPrimary)
            Spacer(modifier = Modifier.height(14.dp))
            androidx.compose.material3.TextField(
                value = value,
                onValueChange = { value = it },
                placeholder = { androidx.compose.material3.Text(placeholder) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = androidx.compose.material3.TextFieldDefaults.colors(
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedContainerColor = Color.White.copy(alpha = 0.08f),
                    unfocusedContainerColor = Color.White.copy(alpha = 0.06f),
                    focusedIndicatorColor = Color.White,
                    unfocusedIndicatorColor = Color.White.copy(alpha = 0.18f),
                    focusedPlaceholderColor = TextSecondary,
                    unfocusedPlaceholderColor = TextSecondary
                )
            )
            Spacer(modifier = Modifier.height(18.dp))
            if (isCompact) {
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DiscoveryActionButton(
                        label = confirmLabel,
                        onClick = { onConfirm(value.trim()) },
                        highlighted = true,
                        enabled = value.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    DiscoveryActionButton(
                        label = stringResource(R.string.cancel),
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    DiscoveryActionButton(label = stringResource(R.string.cancel), onClick = onDismiss)
                    Spacer(modifier = Modifier.width(10.dp))
                    DiscoveryActionButton(
                        label = confirmLabel,
                        onClick = { onConfirm(value.trim()) },
                        highlighted = true,
                        enabled = value.isNotBlank()
                    )
                }
            }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun CatalogDiscoveryResultRow(
    result: CatalogDiscoveryResult,
    isAdded: Boolean,
    onAdd: () -> Unit,
    compact: Boolean = false
) {
    var isFocused by remember { mutableStateOf(false) }
    val compactFocusColor = resolveAccentColor(fallback = Color.White)
    val creator = result.creatorName ?: result.creatorHandle
    val creatorMeta = creator?.let { stringResource(R.string.settings_by, it) }
    val itemCountMeta = result.itemCount?.let { stringResource(R.string.settings_items_count, it.toString()) }
    val likesMeta = result.likes?.let { stringResource(R.string.settings_likes_count, it.toString()) } ?: stringResource(R.string.settings_likes_count, "0")
    val updatedMeta = result.updatedAt?.let { stringResource(R.string.settings_updated_meta, formatCatalogDiscoveryDate(it)) }

    if (compact) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .onFocusChanged { isFocused = it.isFocused || it.hasFocus }
                .clickable(onClick = { if (!isAdded) onAdd() })
                .background(
                    if (isFocused) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.38f),
                    RoundedCornerShape(14.dp)
                )
                .border(
                    width = if (isFocused) 3.dp else 1.dp,
                    color = if (isFocused) compactFocusColor else Color.White.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(14.dp)
                )
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val posters = result.previewPosterUrls.take(5)
                if (posters.isEmpty()) {
                    repeat(5) {
                        Box(
                            modifier = Modifier
                                .size(width = 52.dp, height = 78.dp)
                                .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(7.dp))
                        )
                    }
                } else {
                    posters.forEach { posterUrl ->
                        AsyncImage(
                            model = posterUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(width = 52.dp, height = 78.dp)
                                .clip(RoundedCornerShape(7.dp))
                                .background(Color.White.copy(alpha = 0.08f))
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = result.title,
                style = ArflixTypography.bodyLarge,
                color = TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(5.dp))

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                SourceChip(label = sourceLabel(result.sourceType), active = true, compact = true)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = listOfNotNull(itemCountMeta, likesMeta).joinToString("  |  "),
                    style = ArflixTypography.caption,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }

            if (!creatorMeta.isNullOrBlank()) {
                Text(
                    text = creatorMeta,
                    style = ArflixTypography.caption,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            if (!updatedMeta.isNullOrBlank()) {
                Text(
                    text = updatedMeta,
                    style = ArflixTypography.caption,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            if (!result.description.isNullOrBlank()) {
                Text(
                    text = result.description,
                    style = ArflixTypography.caption,
                    color = TextSecondary.copy(alpha = 0.82f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            CatalogDiscoveryAddPill(
                isAdded = isAdded,
                modifier = Modifier.fillMaxWidth()
            )
        }
        return
    }

    val resultFocusColor = resolveAccentColor(fallback = Color.White)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .onFocusChanged { isFocused = it.isFocused || it.hasFocus }
            .clickable(onClick = { if (!isAdded) onAdd() })
            .background(
                if (isFocused) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.38f),
                RoundedCornerShape(14.dp)
            )
            .border(
                width = if (isFocused) 3.dp else 1.dp,
                color = if (isFocused) resultFocusColor else Color.White.copy(alpha = 0.08f),
                shape = RoundedCornerShape(14.dp)
            )
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .width(330.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            val posters = result.previewPosterUrls.take(5)
            if (posters.isEmpty()) {
                repeat(5) {
                    Box(
                        modifier = Modifier
                            .size(width = 60.dp, height = 90.dp)
                            .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(7.dp))
                    )
                }
            } else {
                posters.forEach { posterUrl ->
                    AsyncImage(
                        model = posterUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(width = 60.dp, height = 90.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .background(Color.White.copy(alpha = 0.08f))
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(18.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = result.title,
                style = ArflixTypography.bodyLarge,
                color = TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                SourceChip(label = sourceLabel(result.sourceType), active = true, compact = true)
                if (!creatorMeta.isNullOrBlank()) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = creatorMeta,
                        style = ArflixTypography.caption,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (!itemCountMeta.isNullOrBlank()) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "|",
                        style = ArflixTypography.caption,
                        color = TextSecondary.copy(alpha = 0.65f),
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = itemCountMeta,
                        style = ArflixTypography.caption,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 112.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "|",
                    style = ArflixTypography.caption,
                    color = TextSecondary.copy(alpha = 0.65f),
                    maxLines = 1
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = likesMeta,
                    style = ArflixTypography.caption,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(min = 64.dp, max = 86.dp)
                )
            }

            if (!updatedMeta.isNullOrBlank()) {
                Text(
                    text = updatedMeta,
                    style = ArflixTypography.caption,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            if (!result.description.isNullOrBlank()) {
                Text(
                    text = result.description,
                    style = ArflixTypography.caption,
                    color = TextSecondary.copy(alpha = 0.82f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(18.dp))
        CatalogDiscoveryAddPill(isAdded = isAdded)
    }
}

@Composable
private fun CatalogDiscoveryAddPill(
    isAdded: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .widthIn(min = 112.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color.Black.copy(alpha = 0.46f), RoundedCornerShape(10.dp))
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.74f),
                shape = RoundedCornerShape(10.dp)
            )
            .padding(horizontal = 22.dp, vertical = 13.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (isAdded) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = TextPrimary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = if (isAdded) stringResource(R.string.settings_added) else stringResource(R.string.add),
                style = ArflixTypography.button,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SourceChip(
    label: String,
    active: Boolean,
    compact: Boolean = false
) {
    Text(
        text = label,
        style = if (compact) ArflixTypography.label else ArflixTypography.body,
        color = if (active) TextPrimary else TextSecondary,
        modifier = Modifier
            .background(
                if (active) Color.Black.copy(alpha = 0.30f) else Color.White.copy(alpha = 0.06f),
                RoundedCornerShape(999.dp)
            )
            .border(
                width = 1.dp,
                color = if (active) Color.White.copy(alpha = 0.48f) else Color.White.copy(alpha = 0.12f),
                shape = RoundedCornerShape(999.dp)
            )
            .padding(horizontal = if (compact) 8.dp else 12.dp, vertical = if (compact) 3.dp else 5.dp)
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun DiscoveryActionButton(
    label: String,
    onClick: () -> Unit,
    icon: ImageVector? = null,
    highlighted: Boolean = false,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .widthIn(min = if (label.length <= 5) 112.dp else 132.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(
                when {
                    !enabled -> Color.Black.copy(alpha = 0.18f)
                    isFocused -> Color.White
                    highlighted -> Color.Black.copy(alpha = 0.46f)
                    else -> Color.Black.copy(alpha = 0.36f)
                },
                RoundedCornerShape(10.dp)
            )
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = when {
                    !enabled -> Color.White.copy(alpha = 0.18f)
                    isFocused -> Color.White
                    highlighted -> Color.White.copy(alpha = 0.74f)
                    else -> Color.White.copy(alpha = 0.55f)
                },
                shape = RoundedCornerShape(10.dp)
            )
            .onFocusChanged { isFocused = it.isFocused }
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 13.dp),
        contentAlignment = Alignment.Center
    ) {
        val contentColor = when {
            !enabled -> TextSecondary.copy(alpha = 0.72f)
            isFocused -> Color.Black
            else -> TextPrimary
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = label,
                style = ArflixTypography.button,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun normalizeCatalogDiscoveryUrl(url: String): String {
    return url.trim().trimEnd('/').lowercase()
}

@Composable
private fun sourceLabel(sourceType: CatalogSourceType): String {
    return when (sourceType) {
        CatalogSourceType.TMDB -> "TMDB"
        CatalogSourceType.PREINSTALLED -> stringResource(R.string.settings_source_builtin)
        CatalogSourceType.ADDON -> stringResource(R.string.settings_source_addon)
    }
}

private fun formatCatalogDiscoveryDate(raw: String): String {
    return raw.substringBefore('T')
        .takeIf { it.length == 10 }
        ?: raw.replace('T', ' ').substringBefore('.').take(16)
}

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun CatalogsSettings(
    catalogs: List<CatalogConfig>,
    focusedIndex: Int,
    focusedActionIndex: Int,
    onAddCatalog: () -> Unit,
    onImportCatalogPack: () -> Unit,
    onRenameCatalog: (CatalogConfig) -> Unit,
    onMoveCatalogUp: (CatalogConfig) -> Unit,
    onMoveCatalogDown: (CatalogConfig) -> Unit,
    onDeleteCatalog: (CatalogConfig) -> Unit,
    onUnpackCatalog: (CatalogConfig) -> Unit,
    header: @Composable () -> Unit = {}
) {
    val isMobile = LocalDeviceType.current.isTouchDevice()
    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<String>()) }

    if (isMobile) {
        Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
            if (selectionMode) {
                Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.settings_n_selected, selectedIds.size), style = ArflixTypography.sectionTitle, color = TextPrimary)
                    Spacer(modifier = Modifier.weight(1f))
                    if (selectedIds.isNotEmpty()) {
                        Box(modifier = Modifier.size(36.dp).clickable { selectedIds.forEach { id -> val cat = catalogs.find { it.id == id }; if (cat != null) onDeleteCatalog(cat) }; selectionMode = false; selectedIds = emptySet() }.background(Color(0xFFDC2626), RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete), tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                    }
                    Box(modifier = Modifier.size(36.dp).clickable { selectionMode = false; selectedIds = emptySet() }.background(Color.White.copy(alpha = 0.15f), RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close), tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                }
            }
            MobileSettingsCategory(title = stringResource(R.string.settings_section_add_catalog)) {
                MobileSettingsRow(icon = Icons.Default.Add, title = stringResource(R.string.add_catalog), subtitle = stringResource(R.string.add_catalog_desc), value = "", isFocused = false, showDivider = true, onClick = onAddCatalog)
                MobileSettingsRow(icon = Icons.Default.Widgets, title = stringResource(R.string.settings_catalog_pack_import_title), subtitle = stringResource(R.string.settings_catalog_pack_import_desc), value = "", isFocused = false, showDivider = true, onClick = onImportCatalogPack)
            }
            if (catalogs.isNotEmpty()) {
                MobileSettingsCategory(title = stringResource(R.string.settings_section_my_catalogs)) {
                    catalogs.forEachIndexed { index, catalog ->
                        val title = if (catalog.isPreinstalled) stringResource(R.string.settings_title_builtin, catalog.title) else catalog.title
                        val currentPackId = catalog.packId
                        val prevPackId = if (index > 0) catalogs[index - 1].packId else null
                        val showPackHeader = currentPackId != null && currentPackId != prevPackId && catalog.isBulkDeletablePack
                        val addonFallback = stringResource(R.string.settings_source_addon)
                        val subtitle = run {
                            val baseSubtitle = when {
                                catalog.sourceType == CatalogSourceType.PREINSTALLED -> stringResource(R.string.settings_preinstalled_catalog)
                                catalog.sourceType == CatalogSourceType.ADDON -> {
                                    val addonLabel = catalog.addonName?.takeIf { it.isNotBlank() } ?: addonFallback
                                    stringResource(R.string.settings_from_source, addonLabel)
                                }
                                else -> catalog.sourceUrl ?: stringResource(R.string.settings_custom_catalog)
                            }
                            "Pack: ${catalog.effectivePackName} • $baseSubtitle"
                        }
                        val isSelected = selectedIds.contains(catalog.id)
                        val layoutRowKey = remember(catalog.id, catalog.kind) { catalogueLayoutRowKey(catalog) }
                        Column {
                            if (showPackHeader) {
                                if (index > 0) {
                                    Spacer(modifier = Modifier.height(16.dp))
                                }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Archive,
                                        contentDescription = null,
                                        tint = Pink,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = catalog.effectivePackName.uppercase(),
                                        style = ArflixTypography.caption.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, letterSpacing = androidx.compose.ui.unit.TextUnit.Unspecified),
                                        color = Pink
                                    )
                                }
                                Box(modifier = Modifier.fillMaxWidth().height(1.dp).padding(horizontal = 16.dp).background(Pink.copy(alpha = 0.2f)))
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth()
                                    .background(if (isSelected) Pink.copy(alpha = 0.15f) else Color.Transparent)
                                    .combinedClickable(
                                        onClick = { if (selectionMode) { selectedIds = if (isSelected) selectedIds - catalog.id else selectedIds + catalog.id; if (selectedIds.isEmpty()) selectionMode = false } else onRenameCatalog(catalog) },
                                        onLongClick = { if (!selectionMode) { selectionMode = true; selectedIds = setOf(catalog.id) } }
                                    )
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (selectionMode) {
                                    Icon(if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked, contentDescription = null, tint = if (isSelected) Pink else TextSecondary, modifier = Modifier.size(24.dp))
                                    Spacer(modifier = Modifier.width(16.dp))
                                } else {
                                    Icon(Icons.Default.Widgets, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(24.dp))
                                    Spacer(modifier = Modifier.width(16.dp))
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(title, style = ArflixTypography.cardTitle.copy(fontSize = 16.sp), color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(subtitle, style = ArflixTypography.caption.copy(fontSize = 13.sp), color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                if (!selectionMode) {
                                    CatalogueRowLayoutToggleButton(rowKey = layoutRowKey, enabled = true)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    if (catalog.packId != null && catalog.isBulkDeletablePack) {
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clickable { onUnpackCatalog(catalog) }
                                                .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(8.dp)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Unarchive,
                                                contentDescription = stringResource(R.string.settings_cd_unpack_catalog_row),
                                                tint = Color.White.copy(alpha = 0.7f),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                    }
                                }
                                if (selectionMode && selectedIds.size == 1 && isSelected) {
                                    Icon(imageVector = Icons.Default.DragHandle, contentDescription = stringResource(R.string.settings_cd_drag_reorder), tint = TextSecondary, modifier = Modifier.size(24.dp).pointerInput(catalog.id) {
                                        var dragOffset = 0f; val itemHeight = 64.dp.toPx()
                                        detectVerticalDragGestures(onDragEnd = { dragOffset = 0f }, onDragCancel = { dragOffset = 0f }) { change, dragAmount -> change.consume(); dragOffset += dragAmount; if (dragOffset > itemHeight) { onMoveCatalogDown(catalog); dragOffset -= itemHeight } else if (dragOffset < -itemHeight) { onMoveCatalogUp(catalog); dragOffset += itemHeight } }
                                    })
                                }
                            }
                            if (index < catalogs.size - 1) {
                                Box(modifier = Modifier.fillMaxWidth().height(1.dp).padding(horizontal = 16.dp).background(Color.White.copy(alpha = 0.05f)))
                            }
                        }
                    }
                }
            }
        }
    } else {
        // Keep off-screen catalog controls and their preference observers out of composition.
        val layoutRowKeys = remember(catalogs) { catalogs.map(::catalogueLayoutRowKey) }
        val layoutModes = com.arflix.tv.ui.components.rememberCatalogueRowLayoutModes(layoutRowKeys)
        val listState = rememberLazyListState()
        LaunchedEffect(focusedIndex, catalogs.size) {
            if (focusedIndex < 0) return@LaunchedEffect
            androidx.compose.runtime.withFrameNanos { }
            val target = (focusedIndex + 1).coerceAtMost(catalogs.size + 2) // Heading is item zero.
            val info = listState.layoutInfo
            val item = info.visibleItemsInfo.firstOrNull { it.index == target }
            if (item == null) {
                listState.animateScrollToItem(target)
            } else {
                val delta = settingsCategoryScrollDelta(
                    item.offset, item.size, info.viewportStartOffset, info.viewportEndOffset,
                    center = false
                )
                if (delta != 0f) listState.animateScrollBy(delta, tween(100, easing = FastOutSlowInEasing))
            }
        }
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
          item(key = "heading") {
            header()
            if (selectionMode) {
                Row(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.settings_n_selected, selectedIds.size), style = ArflixTypography.sectionTitle, color = TextPrimary)
                    Spacer(modifier = Modifier.weight(1f))
                    if (selectedIds.isNotEmpty()) { Box(modifier = Modifier.size(36.dp).clickable { selectedIds.forEach { id -> val cat = catalogs.find { it.id == id }; if (cat != null) onDeleteCatalog(cat) }; selectionMode = false; selectedIds = emptySet() }.background(Color(0xFFDC2626), RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) { Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete), tint = Color.White, modifier = Modifier.size(20.dp)) }; Spacer(modifier = Modifier.width(12.dp)) }
                    Box(modifier = Modifier.size(36.dp).clickable { selectionMode = false; selectedIds = emptySet() }.background(Color.White.copy(alpha = 0.15f), RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) { Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close), tint = Color.White, modifier = Modifier.size(20.dp)) }
                }
            }
            Text(text = stringResource(R.string.catalogs), style = ArflixTypography.caption, color = TextSecondary.copy(alpha = 0.65f), modifier = Modifier.padding(bottom = 20.dp))
          }
          item(key = "add") {
            SettingsRow(icon = Icons.Default.Add, title = stringResource(R.string.add_catalog), subtitle = stringResource(R.string.add_catalog_desc), value = stringResource(R.string.settings_badge_add), isFocused = focusedIndex == 0, onClick = onAddCatalog, modifier = Modifier.settingsFocusSlot(0))
            Spacer(modifier = Modifier.height(16.dp))
          }
          item(key = "import") {
            SettingsRow(icon = Icons.Default.Widgets, title = stringResource(R.string.settings_catalog_pack_import_title), subtitle = stringResource(R.string.settings_catalog_pack_import_desc), value = stringResource(R.string.settings_catalog_pack_import_badge), isFocused = focusedIndex == 1, onClick = onImportCatalogPack, modifier = Modifier.settingsFocusSlot(1))
            Spacer(modifier = Modifier.height(16.dp))
          }

            itemsIndexed(catalogs, key = { _, catalog -> "catalog:${catalog.id}" }, contentType = { _, _ -> "catalog" }) { index, catalog ->
                val rowFocusIndex = index + 2; val isRowFocused = focusedIndex == rowFocusIndex
                val currentPackId = catalog.packId
                val prevPackId = if (index > 0) catalogs[index - 1].packId else null
                val showPackHeader = currentPackId != null && currentPackId != prevPackId && catalog.isBulkDeletablePack
                TvCatalogSettingsRow(
                    catalog = catalog,
                    rowFocusIndex = rowFocusIndex,
                    isRowFocused = isRowFocused,
                    focusedActionIndex = if (isRowFocused) focusedActionIndex else -1,
                    isSelected = selectedIds.contains(catalog.id),
                    showPackHeader = showPackHeader,
                    layoutMode = layoutModes[catalogueLayoutRowKey(catalog)] ?: com.arflix.tv.ui.components.CardLayoutMode.LANDSCAPE,
                    onRenameCatalog = onRenameCatalog,
                    onMoveCatalogUp = onMoveCatalogUp,
                    onMoveCatalogDown = onMoveCatalogDown,
                    onUnpackCatalog = onUnpackCatalog,
                    onDeleteCatalog = onDeleteCatalog
                )
            }
        }
    }
}

// A restartable row boundary lets Compose skip unchanged neighbours on DPAD moves.
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TvCatalogSettingsRow(
    catalog: CatalogConfig,
    rowFocusIndex: Int,
    isRowFocused: Boolean,
    focusedActionIndex: Int,
    isSelected: Boolean,
    showPackHeader: Boolean,
    layoutMode: com.arflix.tv.ui.components.CardLayoutMode,
    onRenameCatalog: (CatalogConfig) -> Unit,
    onMoveCatalogUp: (CatalogConfig) -> Unit,
    onMoveCatalogDown: (CatalogConfig) -> Unit,
    onUnpackCatalog: (CatalogConfig) -> Unit,
    onDeleteCatalog: (CatalogConfig) -> Unit
) {

                if (showPackHeader) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Archive,
                            contentDescription = null,
                            tint = Pink,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = catalog.effectivePackName.uppercase(),
                            style = ArflixTypography.caption.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, letterSpacing = androidx.compose.ui.unit.TextUnit.Unspecified),
                            color = Pink
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                val title = if (catalog.isPreinstalled) stringResource(R.string.settings_title_builtin, catalog.title) else catalog.title
                val addonFallback = stringResource(R.string.settings_source_addon)
                val subtitle = run {
                    val baseSubtitle = when {
                        catalog.sourceType == CatalogSourceType.PREINSTALLED -> stringResource(R.string.settings_preinstalled_catalog)
                        catalog.sourceType == CatalogSourceType.ADDON -> {
                            val addonLabel = catalog.addonName?.takeIf { it.isNotBlank() } ?: addonFallback
                            stringResource(R.string.settings_from_source, addonLabel)
                        }
                        else -> catalog.sourceUrl ?: stringResource(R.string.settings_custom_catalog)
                    }
                    "Pack: ${catalog.effectivePackName} • $baseSubtitle"
                }
                val layoutRowKey = remember(catalog.id, catalog.kind) { catalogueLayoutRowKey(catalog) }
                val focusRingColor = resolveAccentColor(fallback = Pink)
                // Moving a row can reuse its pixels instead of rasterizing six
                // outlined action chips and both text lines on every scroll frame.
                Row(modifier = Modifier.settingsFocusSlot(rowFocusIndex).fillMaxWidth().graphicsLayer {
                    compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen
                }.background(if (isSelected) Pink.copy(alpha = 0.2f) else if (isRowFocused) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp)).border(width = if (isRowFocused) 2.dp else 0.dp, color = if (isRowFocused) focusRingColor else Color.Transparent, shape = RoundedCornerShape(12.dp)).clickable { onRenameCatalog(catalog) }.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(title, style = ArflixTypography.cardTitle.copy(fontSize = 16.sp), color = if (isRowFocused || isSelected) TextPrimary else TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(subtitle, style = ArflixTypography.caption.copy(fontSize = 13.sp), color = TextSecondary.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    CatalogActionChip(icon = Icons.Default.Edit, isFocused = isRowFocused && focusedActionIndex == 0, onClick = { onRenameCatalog(catalog) })
                    Spacer(modifier = Modifier.width(6.dp))
                    CatalogActionChip(icon = Icons.Default.ArrowUpward, isFocused = isRowFocused && focusedActionIndex == 1, onClick = { onMoveCatalogUp(catalog) })
                    Spacer(modifier = Modifier.width(6.dp))
                    CatalogActionChip(icon = Icons.Default.ArrowDownward, isFocused = isRowFocused && focusedActionIndex == 2, onClick = { onMoveCatalogDown(catalog) })
                    Spacer(modifier = Modifier.width(6.dp))
                    CatalogueRowLayoutToggleButton(
                        rowKey = layoutRowKey,
                        enabled = true,
                        forceFocused = isRowFocused && focusedActionIndex == 3,
                        layoutMode = layoutMode
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    CatalogActionChip(icon = Icons.Default.Unarchive, isFocused = isRowFocused && focusedActionIndex == 4, enabled = catalog.packId != null && catalog.isBulkDeletablePack, onClick = { onUnpackCatalog(catalog) })
                    Spacer(modifier = Modifier.width(6.dp))
                    CatalogActionChip(icon = Icons.Default.Delete, isFocused = isRowFocused && focusedActionIndex == 5, isDestructive = true, enabled = true, onClick = { onDeleteCatalog(catalog) })
                }
                Spacer(modifier = Modifier.height(10.dp))
}


@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun CatalogActionChip(
    icon: ImageVector,
    isFocused: Boolean,
    isDestructive: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit = {}
) {
    // Support both D-pad focus AND touch pressed state
    val accent = resolveAccentColor(fallback = Color.White)
    var isPressed by remember { mutableStateOf(false) }
    val visualActive = isFocused || isPressed
    val bgColor = when {
        !enabled -> Color.Black.copy(alpha = 0.4f)
        visualActive && isDestructive -> Color(0xFFDC2626)
        visualActive -> accent
        else -> Color.White.copy(alpha = 0.08f)
    }
    // Choose foreground (icon) color based on accent luminance for contrast
    val accentFg = if (visualActive) {
        val l = 0.299f * accent.red + 0.587f * accent.green + 0.114f * accent.blue
        if (l > 0.5f) Color.Black else Color.White
    } else Color.White
    val fgColor = when {
        !enabled -> Color.White.copy(alpha = 0.5f)
        visualActive && isDestructive -> Color.White
        visualActive -> accentFg
        else -> Color.White.copy(alpha = 0.7f)
    }
    Box(
        modifier = Modifier
            .size(36.dp)
            .onPreviewKeyEvent { event ->
                if (!enabled || !isFocused || event.type != KeyEventType.KeyDown) {
                    return@onPreviewKeyEvent false
                }
                when (event.key) {
                    Key.Enter, Key.DirectionCenter -> {
                        onClick()
                        true
                    }
                    else -> false
                }
            }
            .clickable(enabled = enabled, onClick = onClick)
            .background(bgColor, RoundedCornerShape(8.dp))
            .border(
                width = if (visualActive) 1.5.dp else 1.dp,
                color = if (visualActive) accent else Color.White.copy(alpha = 0.15f),
                shape = RoundedCornerShape(8.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = fgColor,
            modifier = Modifier.size(16.dp)
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun StremioAddonsSettings(
    addons: List<com.arflix.tv.data.model.Addon> = emptyList(),
    isRefreshingAddons: Boolean = false,
    focusedIndex: Int = -1,
    focusedActionIndex: Int = 0,
    onToggleAddon: (String) -> Unit = {},
    onDeleteAddon: (String) -> Unit = {},
    onConfigureAddon: (com.arflix.tv.data.model.Addon) -> Unit = {},
    onAddCustomAddon: () -> Unit = {},
    onBrowseCommunityAddons: () -> Unit = {},
    onRefreshAddons: () -> Unit = {}
) {
    val isMobile = LocalDeviceType.current.isTouchDevice()
    val context = LocalContext.current

    if (isMobile) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            MobileSettingsCategory(title = stringResource(R.string.settings_section_add_addon)) {
                MobileSettingsRow(
                    icon = Icons.Default.Refresh,
                    title = stringResource(R.string.refresh_addons),
                    subtitle = stringResource(if (BuildConfig.PERSONAL_BUILD) R.string.personal_settings_refresh_addons_desc else R.string.settings_refresh_addons_desc),
                    value = if (isRefreshingAddons) {
                        stringResource(R.string.settings_pulling_latest_addon)
                    } else {
                        ""
                    },
                    isFocused = false,
                    onClick = {
                        if (!isRefreshingAddons) onRefreshAddons()
                    }
                )
                MobileSettingsRow(
                    icon = Icons.Default.Explore,
                    title = stringResource(R.string.settings_community_addons),
                    subtitle = stringResource(R.string.settings_community_addons_desc),
                    value = "",
                    isFocused = false,
                    onClick = onBrowseCommunityAddons
                )
                MobileSettingsRow(
                    icon = Icons.Default.Add,
                    title = stringResource(R.string.add_addon),
                    subtitle = stringResource(R.string.settings_install_custom_addon),
                    value = "",
                    isFocused = false,
                    showDivider = false,
                    onClick = onAddCustomAddon
                )
            }
            MobileSettingsCategory(title = stringResource(R.string.settings_section_my_addons)) {
                if (addons.isEmpty()) {
                    MobileSettingsRow(icon = Icons.Default.Extension, title = stringResource(R.string.settings_no_addons_installed), value = "", isFocused = false, showDivider = false, onClick = {})
                } else {
                    addons.forEachIndexed { index, addon ->
                        val canDelete = addon.isRemovable
                        val settingsUrl = addon.settingsPageUrl
                        val needsSetup = addon.needsConfiguration && !addon.isEnabled
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { onToggleAddon(addon.id) }.padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AddonLogo(addon = addon, size = 32.dp, fallbackTint = TextSecondary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(addon.name, style = ArflixTypography.cardTitle.copy(fontSize = 16.sp), color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (needsSetup) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    AddonSetupRequiredChip()
                                } else {
                                    Text(addon.description, style = ArflixTypography.caption.copy(fontSize = 13.sp), color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            if (needsSetup && settingsUrl != null) {
                                // Replaces the toggle: it cannot be switched on before setup anyway,
                                // and the row is too narrow on phones for both.
                                Box(modifier = Modifier.clickable { openExternalUrl(context, settingsUrl) }.background(AddonSetupRequiredColor, RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 7.dp), contentAlignment = Alignment.Center) {
                                    Text(stringResource(R.string.settings_addon_setup), style = ArflixTypography.button.copy(fontSize = 13.sp), color = Color.Black, maxLines = 1)
                                }
                            } else {
                                // Toggle switch
                                Box(modifier = Modifier.width(44.dp).height(24.dp).background(color = if (addon.isEnabled) SuccessGreen else Color.White.copy(alpha = 0.2f), shape = RoundedCornerShape(13.dp)).padding(3.dp), contentAlignment = if (addon.isEnabled) Alignment.CenterEnd else Alignment.CenterStart) {
                                    Box(modifier = Modifier.size(18.dp).background(color = Color.White, shape = RoundedCornerShape(10.dp)))
                                }
                            }
                            if (settingsUrl != null && !needsSetup) {
                                Spacer(modifier = Modifier.width(12.dp))
                                Box(modifier = Modifier.size(32.dp).clickable { openExternalUrl(context, settingsUrl) }.background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings_addon_configure), tint = TextSecondary, modifier = Modifier.size(18.dp))
                                }
                            }
                            if (canDelete) {
                                Spacer(modifier = Modifier.width(12.dp))
                                Box(modifier = Modifier.size(32.dp).clickable { onDeleteAddon(addon.id) }.background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.settings_cd_delete_addon), tint = TextSecondary, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                        if (index < addons.size - 1) {
                            Box(modifier = Modifier.fillMaxWidth().height(1.dp).padding(horizontal = 16.dp).background(Color.White.copy(alpha = 0.05f)))
                        }
                    }
                }
            }
        }
    } else {
        // TV UI
        Column {
            Text(stringResource(R.string.settings_section_stremio_addons), style = ArflixTypography.caption.copy(fontSize = 12.sp, letterSpacing = 1.sp), color = TextSecondary, modifier = Modifier.padding(bottom = 10.dp))
            if (addons.isEmpty()) {
                Text(stringResource(R.string.settings_no_addons_installed), style = ArflixTypography.body, color = TextSecondary)
            } else {
                addons.forEachIndexed { index, addon ->
                    AddonRow(
                        addon = addon,
                        isFocused = focusedIndex == index,
                        focusedAction = if (focusedIndex == index) focusedActionIndex else -1,
                        onToggle = { onToggleAddon(addon.id) },
                        onConfigure = { onConfigureAddon(addon) },
                        onDelete = { onDeleteAddon(addon.id) },
                        modifier = Modifier.settingsFocusSlot(index)
                    )
                    if (index < addons.size - 1) { Spacer(modifier = Modifier.height(12.dp)) }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            Row(
                modifier = Modifier
                    .settingsFocusSlot(addons.size)
                    .fillMaxWidth()
                    .clickable(enabled = !isRefreshingAddons, onClick = onRefreshAddons)
                    .background(if (focusedIndex == addons.size) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                    .border(width = if (focusedIndex == addons.size) 2.dp else 0.dp, color = if (focusedIndex == addons.size) Pink else Color.Transparent, shape = RoundedCornerShape(12.dp))
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, tint = Pink, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = stringResource(
                        if (isRefreshingAddons) {
                            R.string.settings_pulling_latest_addon
                        } else {
                            R.string.refresh_addons
                        }
                    ),
                    style = ArflixTypography.button,
                    color = Pink
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .settingsFocusSlot(addons.size + 1)
                    .fillMaxWidth()
                    .clickable(onClick = onBrowseCommunityAddons)
                    .background(if (focusedIndex == addons.size + 1) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                    .border(width = if (focusedIndex == addons.size + 1) 2.dp else 0.dp, color = if (focusedIndex == addons.size + 1) Pink else Color.Transparent, shape = RoundedCornerShape(12.dp))
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Default.Explore, contentDescription = null, tint = Pink, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text(stringResource(R.string.settings_community_addons), style = ArflixTypography.button, color = Pink)
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .settingsFocusSlot(addons.size + 2)
                    .fillMaxWidth()
                    .clickable(onClick = onAddCustomAddon)
                    .background(if (focusedIndex == addons.size + 2) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                    .border(width = if (focusedIndex == addons.size + 2) 2.dp else 0.dp, color = if (focusedIndex == addons.size + 2) Pink else Color.Transparent, shape = RoundedCornerShape(12.dp))
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Default.Widgets, contentDescription = null, tint = Pink, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text(stringResource(R.string.add_addon), style = ArflixTypography.button, color = Pink)
            }
        }
    }
}

@Composable
private fun AddonStatusChip(
    text: String,
    background: Color,
    textColor: Color
) {
    Box(
        modifier = Modifier
            .background(background, RoundedCornerShape(999.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = ArflixTypography.caption.copy(fontSize = 11.sp),
            color = textColor,
            maxLines = 1
        )
    }
}

@Composable
private fun AddonRow(
    addon: com.arflix.tv.data.model.Addon,
    isFocused: Boolean,
    focusedAction: Int = -1,
    onToggle: () -> Unit,
    onConfigure: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val actions = addonRowActions(addon)
    val focusedRowAction = if (isFocused) actions.getOrNull(focusedAction) else null
    val isToggleFocused = focusedRowAction == AddonRowAction.TOGGLE
    val isConfigureFocused = focusedRowAction == AddonRowAction.CONFIGURE
    val isDeleteFocused = focusedRowAction == AddonRowAction.DELETE
    val isEnabled = addon.isEnabled
    val needsSetup = addon.needsConfiguration && !isEnabled
    val focusRingColor = resolveAccentColor(fallback = Pink)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .background(
                if (isFocused) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.05f),
                RoundedCornerShape(12.dp)
            )
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = if (isFocused) focusRingColor else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(Pink.copy(alpha = 0.2f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                AddonLogo(addon = addon, size = 32.dp, fallbackTint = Pink)
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column {
                Text(
                    text = addon.name,
                    style = ArflixTypography.cardTitle.copy(fontSize = 16.sp),
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = addon.description,
                    style = ArflixTypography.caption.copy(fontSize = 13.sp),
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AddonStatusChip(
                        text = stringResource(R.string.settings_status_installed),
                        background = Color(0xFF2563EB).copy(alpha = 0.18f),
                        textColor = Color(0xFF93C5FD)
                    )
                    AddonStatusChip(
                        text = if (isEnabled) stringResource(R.string.settings_enabled) else stringResource(R.string.settings_disabled),
                        background = if (isEnabled) SuccessGreen.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.08f),
                        textColor = if (isEnabled) SuccessGreen else TextSecondary
                    )
                    if (needsSetup) {
                        AddonStatusChip(
                            text = stringResource(R.string.settings_addon_setup_required),
                            background = AddonSetupRequiredColor,
                            textColor = Color.Black
                        )
                    }
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .border(
                        width = if (isToggleFocused) 2.dp else 0.dp,
                        color = if (isToggleFocused) Color.White else Color.Transparent,
                        shape = RoundedCornerShape(13.dp)
                    )
                    .padding(2.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(44.dp)
                        .height(24.dp)
                        .background(
                            color = if (isEnabled) SuccessGreen else Color.White.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(13.dp)
                        )
                        .padding(3.dp),
                    contentAlignment = if (isEnabled) Alignment.CenterEnd else Alignment.CenterStart
                ) {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .background(
                                color = Color.White,
                                shape = RoundedCornerShape(10.dp)
                            )
                    )
                }
            }

            if (AddonRowAction.CONFIGURE in actions) {
                CatalogActionChip(
                    icon = Icons.Default.Settings,
                    isFocused = isConfigureFocused,
                    onClick = onConfigure
                )
            }

            if (AddonRowAction.DELETE in actions) {
                CatalogActionChip(
                    icon = Icons.Default.Delete,
                    isFocused = isDeleteFocused,
                    isDestructive = true,
                    onClick = onDelete
                )
            }
        }
    }
}




@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun AccountActionRow(
    title: String,
    description: String,
    actionLabel: String,
    isEnabled: Boolean,
    isFocused: Boolean
) {
    val focusRingColor = resolveAccentColor(fallback = Pink)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isFocused) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.05f),
                RoundedCornerShape(12.dp)
            )
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = if (isFocused) focusRingColor else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = ArflixTypography.cardTitle.copy(fontSize = 16.sp),
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (description.isNotEmpty()) {
                Text(
                    text = description,
                    style = ArflixTypography.caption.copy(fontSize = 13.sp),
                    color = TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Box(
            modifier = Modifier
                .background(
                    if (isEnabled) Pink.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.05f),
                    RoundedCornerShape(999.dp)
                )
                .border(
                    1.dp,
                    if (isEnabled) Pink.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.15f),
                    RoundedCornerShape(999.dp)
                )
                .padding(horizontal = 12.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = actionLabel.uppercase(),
                style = ArflixTypography.label.copy(fontSize = 11.sp, letterSpacing = 0.5.sp),
                color = if (isEnabled) Pink else TextSecondary,
                maxLines = 1
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SettingsActionRow(
    title: String,
    description: String,
    actionLabel: String,
    isFocused: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    SettingsRow(
        title = title,
        subtitle = description,
        value = actionLabel,
        isFocused = isFocused,
        onClick = onClick,
        modifier = modifier
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun AccountDisconnectConfirmDialog(
    title: String,
    description: String,
    confirmLabel: String = stringResource(R.string.settings_disconnect),
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    var focusedButton by remember { mutableIntStateOf(0) } // 0 = cancel, 1 = disconnect
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl

    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }

    // onPreviewKeyEvent on the root Box fires for ANY focused child (Cancel button,
    // Disconnect button, or the Box itself). This is intentional: it intercepts key
    // events BEFORE clickable children can handle them, preventing the Cancel button
    // from firing via its own clickable when the user presses OK/Enter to open the dialog.
    BackHandler {
        onDismiss()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.65f))
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.Back, Key.Escape -> { onDismiss(); true }
                    Key.DirectionLeft  -> { focusedButton = if (isRtl) 1 else 0; true }
                    Key.DirectionRight -> { focusedButton = if (isRtl) 0 else 1; true }
                    Key.Enter, Key.DirectionCenter -> {
                        if (focusedButton == 0) onDismiss() else onConfirm()
                        true
                    }
                    else -> false
                }
            }
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 360.dp)
                .background(BackgroundElevated, RoundedCornerShape(16.dp))
                .clickable(enabled = false) {}
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                style = ArflixTypography.cardTitle.copy(fontSize = 18.sp),
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = description,
                style = ArflixTypography.caption.copy(fontSize = 13.sp),
                color = TextSecondary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            if (focusedButton == 0) Color.White.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.06f),
                            RoundedCornerShape(8.dp)
                        )
                        .border(
                            width = if (focusedButton == 0) 2.dp else 0.dp,
                            color = if (focusedButton == 0) Color.White.copy(alpha = 0.5f) else Color.Transparent,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clickable { onDismiss() }
                        .padding(vertical = 13.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.cancel).uppercase(),
                        style = ArflixTypography.label.copy(fontSize = 12.sp),
                        color = TextPrimary
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            if (focusedButton == 1) Pink.copy(alpha = 0.3f) else Pink.copy(alpha = 0.12f),
                            RoundedCornerShape(8.dp)
                        )
                        .border(
                            width = if (focusedButton == 1) 2.dp else 1.dp,
                            color = if (focusedButton == 1) Pink else Pink.copy(alpha = 0.35f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clickable { onConfirm() }
                        .padding(vertical = 13.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = confirmLabel.uppercase(),
                        style = ArflixTypography.label.copy(fontSize = 12.sp),
                        color = Pink
                    )
                }
            }
        }
    }
}

/**
 * Data class for input field
 */
data class InputField(
    val label: String,
    val value: String,
    val placeholder: String = "",
    val helper: String = "",
    val isSecret: Boolean = false,
    val singleLine: Boolean = true,
    val onValueChange: (String) -> Unit
)

data class ToggleField(
    val label: String,
    val value: Boolean,
    val onValueChange: (Boolean) -> Unit
)

/** Input modal for text entry (custom addon URL, API keys, etc.). */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun InputModalLegacy(
    title: String,
    fields: List<InputField>,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    // Track which element is focused: 0 to fields.size-1 = text fields, fields.size = paste button, fields.size+1 = cancel, fields.size+2 = confirm
    var focusedIndex by remember { mutableIntStateOf(0) } // Start on first text field
    val totalItems = fields.size + 3 // fields + paste + cancel + confirm

    // Create focus requesters for each text field
    val fieldFocusRequesters = remember { fields.map { FocusRequester() } }

    // Clipboard manager for paste functionality
    val clipboardManager = LocalClipboardManager.current

    // Request focus on first field when modal opens
    LaunchedEffect(Unit) {
        if (fieldFocusRequesters.isNotEmpty()) {
            fieldFocusRequesters[0].requestFocus()
        }
    }

    // Request focus when focusedIndex changes to a text field
    LaunchedEffect(focusedIndex) {
        if (focusedIndex < fields.size && focusedIndex >= 0) {
            fieldFocusRequesters[focusedIndex].requestFocus()
        }
    }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        BackHandler {
            onDismiss()
        }
        ModalScrim(onDismiss = onDismiss) {
            Column(
                modifier = Modifier
                    .width(550.dp)
                    .background(BackgroundElevated, RoundedCornerShape(16.dp))
                    .padding(32.dp)
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown) {
                            when (event.key) {
                                Key.Back, Key.Escape -> {
                                    onDismiss()
                                    true
                                }
                                Key.DirectionUp -> {
                                    if (focusedIndex > 0) {
                                        focusedIndex--
                                    }
                                    true
                                }
                                Key.DirectionDown -> {
                                    if (focusedIndex < totalItems - 1) {
                                        focusedIndex++
                                    }
                                    true
                                }
                                Key.DirectionLeft -> {
                                    if (focusedIndex == fields.size + 2) {
                                        focusedIndex = fields.size + 1
                                    }
                                    true
                                }
                                Key.DirectionRight -> {
                                    if (focusedIndex == fields.size + 1) {
                                        focusedIndex = fields.size + 2
                                    }
                                    true
                                }
                                Key.Enter, Key.DirectionCenter -> {
                                    when {
                                        focusedIndex == fields.size -> {
                                            val clipboardText = clipboardManager.getText()?.text
                                            // Paste into URL field (index 1) when available
                                            val targetIndex = if (fields.size > 1) 1 else 0
                                            if (clipboardText != null && fields.size > targetIndex) {
                                                fields[targetIndex].onValueChange(clipboardText)
                                            }
                                            true
                                        }
                                        focusedIndex == fields.size + 1 -> {
                                            onDismiss()
                                            true
                                        }
                                        focusedIndex == fields.size + 2 -> {
                                            onConfirm()
                                            true
                                        }
                                        else -> false
                                    }
                                }
                                else -> false
                            }
                        } else false
                    },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
            Text(
                text = title,
                style = ArflixTypography.sectionTitle,
                color = TextPrimary,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            // Input fields
            fields.forEachIndexed { index, field ->
                val isFocused = focusedIndex == index

                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = field.label,
                        style = ArflixTypography.caption,
                        color = if (isFocused) Pink else TextSecondary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    androidx.compose.material3.TextField(
                        value = field.value,
                        onValueChange = field.onValueChange,
                        singleLine = true,
                        placeholder = {
                            Text(
                                text = stringResource(R.string.settings_enter_field, field.label.lowercase()),
                                color = TextSecondary.copy(alpha = 0.5f)
                            )
                        },
                        textStyle = ArflixTypography.body.copy(color = TextPrimary),
                        colors = androidx.compose.material3.TextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedContainerColor = Color.White.copy(alpha = 0.1f),
                            unfocusedContainerColor = Color.White.copy(alpha = 0.05f),
                            focusedIndicatorColor = Pink,
                            unfocusedIndicatorColor = Color.Transparent,
                            cursorColor = Pink
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(fieldFocusRequesters[index])
                            .border(
                                width = if (isFocused) 2.dp else 1.dp,
                                color = if (isFocused) resolveAccentColor(fallback = Pink) else Color.White.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(8.dp)
                            )
                    )
                }

                if (index < fields.size - 1) {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Paste button
            val isPasteFocused = focusedIndex == fields.size
            val pasteFocusRingColor = resolveAccentColor(fallback = Pink)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = if (isPasteFocused) Color.White.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.08f),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .border(
                        width = if (isPasteFocused) 2.dp else 0.dp,
                        color = if (isPasteFocused) pasteFocusRingColor else Color.Transparent,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(vertical = 12.dp, horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.ContentPaste,
                    contentDescription = stringResource(R.string.settings_cd_paste),
                    tint = if (isPasteFocused) Pink else TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.settings_paste_from_clipboard),
                    style = ArflixTypography.button,
                    color = if (isPasteFocused) Pink else TextSecondary
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Buttons row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Cancel button
                val isCancelFocused = focusedIndex == fields.size + 1
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            color = if (isCancelFocused) Color.White.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .border(
                            width = if (isCancelFocused) 2.dp else 0.dp,
                            color = if (isCancelFocused) Pink else Color.Transparent,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = tr("Cancel"),
                        style = ArflixTypography.button,
                        color = if (isCancelFocused) TextPrimary else TextSecondary
                    )
                }

                // Confirm button
                val isConfirmFocused = focusedIndex == fields.size + 2
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            color = if (isConfirmFocused) SuccessGreen else Pink.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .border(
                            width = if (isConfirmFocused) 2.dp else 0.dp,
                            color = if (isConfirmFocused) SuccessGreen.copy(alpha = 0.5f) else Color.Transparent,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = tr("Confirm"),
                        style = ArflixTypography.button,
                        color = Color.White
                    )
                }
            }

            // Hint text
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.settings_hint_enter_dpad),
                style = ArflixTypography.caption,
                color = TextSecondary.copy(alpha = 0.5f)
            )
            }
        }
    }
}

/**
 * TV IME-safe input modal.
 * - D-pad navigation is handled by the dialog container
 * - Keyboard opens only on OK/Click on a field
 * - Back closes the keyboard first (doesn't dismiss), second Back dismisses
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun InputModal(
    title: String,
    supportingText: String? = null,
    fields: List<InputField>,
    toggleFields: List<ToggleField> = emptyList(),
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    var focusedIndex by remember(title, fields.size) { mutableIntStateOf(0) }
    var lastFocusedFieldIndex by remember(title, fields.size) { mutableStateOf<Int?>(null) }
    val totalItems = fields.size + toggleFields.size + 3 // inputs + toggles + paste + cancel + confirm
    val isTouchDevice = LocalDeviceType.current.isTouchDevice()
    val screenHeightDp = LocalConfiguration.current.screenHeightDp.dp
    val maxDialogHeight = (screenHeightDp * 0.88f).coerceAtMost(if (isTouchDevice) 620.dp else 660.dp)

    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val view = LocalView.current
    val modalFocusRequester = remember { FocusRequester() }
    val formScrollState = rememberScrollState()

    val editTextRefs = remember { MutableList<EditText?>(fields.size) { null } }

    fun anyEditTextFocused(): Boolean = editTextRefs.any { it?.hasFocus() == true }

    fun pasteTargetIndex(): Int {
        if (focusedIndex in 0 until fields.size) return focusedIndex
        lastFocusedFieldIndex?.takeIf { it in 0 until fields.size }?.let { return it }
        return fields.indexOfFirst { field ->
            field.label.contains("url", ignoreCase = true) ||
                field.label.contains("server", ignoreCase = true) ||
                field.label.contains("host", ignoreCase = true)
        }.takeIf { it >= 0 } ?: if (fields.size > 1) 1 else 0
    }

    fun pasteClipboardIntoTarget() {
        val clipboardText = clipboardManager.getText()?.text ?: return
        val targetIndex = pasteTargetIndex()
        val target = fields.getOrNull(targetIndex) ?: return
        target.onValueChange(clipboardText)
        editTextRefs.getOrNull(targetIndex)?.let { edit ->
            edit.setText(clipboardText)
            edit.setSelection(edit.text?.length ?: 0)
            edit.clearFocus()
        }
        modalFocusRequester.requestFocus()
    }

    fun hideKeyboardAll() {
        val imm = context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        editTextRefs.forEach { edit ->
            if (edit != null) {
                imm?.hideSoftInputFromWindow(edit.windowToken, 0)
                edit.clearFocus()
                runCatching { imm?.restartInput(edit) }
            }
        }
        view.requestFocus()
    }

    fun showKeyboardFor(index: Int) {
        val edit = editTextRefs.getOrNull(index) ?: return
        val imm = context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        edit.post {
            edit.requestFocus()
            val shown = imm?.showSoftInput(edit, InputMethodManager.SHOW_IMPLICIT) ?: false
            if (!shown) imm?.showSoftInput(edit, InputMethodManager.SHOW_FORCED)
        }
    }

    LaunchedEffect(title, fields.size) {
        // Ensure D-pad events always start from the dialog on all TV devices.
        modalFocusRequester.requestFocus()
    }

    // Auto-scroll the form so the focused field stays in view when D-pad navigates.
    // Each field is ~86dp tall (label 22dp + spacer 6dp + edittext 48dp + spacing 12dp).
    // Scroll so the focused field stays in view. Distribute proportionally across the actual
    // scroll range so the calculation works at any screen density and form height.
    LaunchedEffect(focusedIndex) {
        if (focusedIndex in 0 until fields.size) {
            lastFocusedFieldIndex = focusedIndex
            val maxScroll = formScrollState.maxValue
            if (maxScroll > 0 && fields.size > 1) {
                val targetScroll = (focusedIndex.toFloat() / (fields.size - 1) * maxScroll).toInt()
                runCatching { formScrollState.animateScrollTo(targetScroll) }
            }
        } else if (focusedIndex >= fields.size) {
            // Focused on paste/cancel/confirm — scroll form to end so it's not blocking
            runCatching { formScrollState.animateScrollTo(formScrollState.maxValue) }
        }
    }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = {
            hideKeyboardAll()
            onDismiss()
        },
        properties = androidx.compose.ui.window.DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        BackHandler {
            hideKeyboardAll()
            onDismiss()
        }
        ModalScrim(
            onDismiss = {
                hideKeyboardAll()
                onDismiss()
            }
        ) {
            Column(
                modifier = Modifier
                    .then(
                        if (isTouchDevice) Modifier.fillMaxWidth(0.92f).widthIn(max = 640.dp)
                        else Modifier.width(620.dp)
                    )
                    .navigationBarsPadding()
                    .imePadding()
                    .heightIn(max = maxDialogHeight)
                    .background(BackgroundElevated, RoundedCornerShape(14.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(14.dp))
                    .padding(horizontal = if (isTouchDevice) 16.dp else 20.dp, vertical = 18.dp)
                    .focusRequester(modalFocusRequester)
                    .focusable()
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown) {
                            when (event.key) {
                                Key.Back, Key.Escape -> {
                                    if (anyEditTextFocused()) {
                                        hideKeyboardAll()
                                    } else {
                                        hideKeyboardAll()
                                        onDismiss()
                                    }
                                    true
                                }
                                Key.DirectionUp -> {
                                    if (focusedIndex > 0) {
                                        if (focusedIndex < fields.size) hideKeyboardAll()
                                        focusedIndex--
                                    }
                                    true
                                }
                                Key.DirectionDown -> {
                                    if (focusedIndex < totalItems - 1) {
                                        if (focusedIndex < fields.size) hideKeyboardAll()
                                        focusedIndex++
                                    }
                                    true
                                }
                                Key.DirectionLeft -> {
                                    if (focusedIndex == fields.size + toggleFields.size + 2) focusedIndex = fields.size + toggleFields.size + 1
                                    true
                                }
                                Key.DirectionRight -> {
                                    if (focusedIndex == fields.size + toggleFields.size + 1) focusedIndex = fields.size + toggleFields.size + 2
                                    true
                                }
                                Key.Enter, Key.DirectionCenter -> {
                                    when {
                                        focusedIndex in 0 until fields.size -> {
                                            showKeyboardFor(focusedIndex)
                                            true
                                        }
                                        focusedIndex in fields.size until (fields.size + toggleFields.size) -> {
                                            val toggle = toggleFields[focusedIndex - fields.size]
                                            toggle.onValueChange(!toggle.value)
                                            true
                                        }
                                        focusedIndex == fields.size + toggleFields.size -> {
                                            pasteClipboardIntoTarget()
                                            true
                                        }
                                        focusedIndex == fields.size + toggleFields.size + 1 -> {
                                            hideKeyboardAll()
                                            onDismiss()
                                            true
                                        }
                                        focusedIndex == fields.size + toggleFields.size + 2 -> {
                                            hideKeyboardAll()
                                            onConfirm()
                                            true
                                        }
                                        else -> false
                                    }
                                }
                                else -> false
                            }
                        } else false
                    },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = title,
                    style = ArflixTypography.sectionTitle,
                    color = TextPrimary
                )
                Text(
                    text = if (LocalDeviceType.current.isTouchDevice()) {
                        stringResource(R.string.settings_modal_hint_touch)
                    } else {
                        stringResource(R.string.settings_modal_hint_tv)
                    },
                    style = ArflixTypography.caption,
                    color = TextSecondary.copy(alpha = 0.75f),
                    modifier = Modifier.padding(top = 2.dp, bottom = if (supportingText == null) 12.dp else 4.dp)
                )
                if (supportingText != null) {
                    Text(
                        text = supportingText,
                        style = ArflixTypography.caption,
                        color = TextSecondary.copy(alpha = 0.68f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(formScrollState)
                ) {
                    fields.forEachIndexed { index, field ->
                        val isFocused = focusedIndex == index

                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(bottom = 6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .background(
                                            if (isFocused) Pink.copy(alpha = 0.20f) else Color.White.copy(alpha = 0.08f),
                                            RoundedCornerShape(11.dp)
                                        )
                                        .border(
                                            1.dp,
                                            if (isFocused) Pink else Color.White.copy(alpha = 0.12f),
                                            RoundedCornerShape(11.dp)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "${index + 1}",
                                        style = ArflixTypography.caption,
                                        color = if (isFocused) Pink else TextSecondary
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = field.label,
                                    style = ArflixTypography.caption,
                                    color = if (isFocused) Pink else TextSecondary
                                )
                            }
                            if (field.helper.isNotBlank()) {
                                Text(
                                    text = field.helper,
                                    style = ArflixTypography.caption.copy(fontSize = 12.sp),
                                    color = TextSecondary.copy(alpha = 0.68f),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(start = 30.dp, bottom = 6.dp)
                                )
                            }

                            val enterFieldHint = stringResource(R.string.settings_input_hint_enter, field.label)
                            val regexFieldFocusColor = resolveAccentColor(fallback = Pink)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color.White.copy(alpha = if (isFocused) 0.12f else 0.05f), RoundedCornerShape(10.dp))
                                    .border(
                                        width = if (isFocused) 2.dp else 1.dp,
                                        color = if (isFocused) regexFieldFocusColor else Color.White.copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .padding(2.dp)
                            ) {
                                AndroidView(
                                    factory = { ctx ->
                                        EditText(ctx).apply {
                                            editTextRefs[index] = this
                                            setText(field.value)
                                            setTextColor(android.graphics.Color.WHITE)
                                            setHintTextColor(android.graphics.Color.GRAY)
                                            hint = field.placeholder.ifBlank { enterFieldHint }
                                            textSize = 16f
                                            background = null
                                            setPadding(20, 14, 20, 14)
                                            isSingleLine = field.singleLine
                                            setHorizontallyScrolling(field.singleLine)
                                            minLines = if (field.singleLine) 1 else 3
                                            maxLines = if (field.singleLine) 1 else 5
                                            isFocusable = true
                                            isFocusableInTouchMode = true

                                            val isPasswordField = field.isSecret || field.label.contains("password", ignoreCase = true)
                                            val isLikelyUrlField =
                                                field.label.contains("url", ignoreCase = true) ||
                                                    field.label.contains("server", ignoreCase = true)
                                            inputType = if (isPasswordField) {
                                                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                                            } else if (isLikelyUrlField) {
                                                (InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI) or
                                                    if (field.singleLine) 0 else InputType.TYPE_TEXT_FLAG_MULTI_LINE
                                            } else {
                                                InputType.TYPE_CLASS_TEXT or
                                                    if (field.singleLine) 0 else InputType.TYPE_TEXT_FLAG_MULTI_LINE
                                            }
                                            if (isPasswordField) {
                                                transformationMethod = PasswordTransformationMethod.getInstance()
                                            }

                                            doAfterTextChanged { editable ->
                                                field.onValueChange(editable?.toString() ?: "")
                                            }

                                            // Sync Compose focusedIndex when this EditText gains native focus
                                            // (prevents Bug 25: clicking one field opens another)
                                            setOnFocusChangeListener { _, hasFocus ->
                                                if (hasFocus && focusedIndex != index) {
                                                    focusedIndex = index
                                                }
                                                if (hasFocus) {
                                                    lastFocusedFieldIndex = index
                                                }
                                            }

                                            // Forward D-pad events to Compose navigation instead of letting EditText consume them
                                            setOnKeyListener { v, keyCode, event ->
                                                if (event.action == android.view.KeyEvent.ACTION_DOWN) {
                                                    when (keyCode) {
                                                        android.view.KeyEvent.KEYCODE_DPAD_DOWN -> {
                                                            val imm = ctx.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                                                            imm?.hideSoftInputFromWindow(windowToken, 0)
                                                            clearFocus()
                                                            focusedIndex = (index + 1).coerceAtMost(totalItems - 1)
                                                            modalFocusRequester.requestFocus()
                                                            true
                                                        }
                                                        android.view.KeyEvent.KEYCODE_DPAD_UP -> {
                                                            if (index > 0) {
                                                                val imm = ctx.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                                                                imm?.hideSoftInputFromWindow(windowToken, 0)
                                                                clearFocus()
                                                                focusedIndex = index - 1
                                                                modalFocusRequester.requestFocus()
                                                            }
                                                            true
                                                        }
                                                        android.view.KeyEvent.KEYCODE_BACK -> {
                                                            val imm = ctx.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                                                            imm?.hideSoftInputFromWindow(windowToken, 0)
                                                            clearFocus()
                                                            modalFocusRequester.requestFocus()
                                                            true
                                                        }
                                                        else -> false
                                                    }
                                                } else false
                                            }

                                            setOnEditorActionListener { _, actionId, _ ->
                                                if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                                                    val imm = ctx.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                                                    imm?.hideSoftInputFromWindow(windowToken, 0)
                                                    clearFocus()
                                                    // Return focus to Compose so D-pad navigation works
                                                    modalFocusRequester.requestFocus()
                                                    true
                                                } else false
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    update = { editText ->
                                        editText.hint = field.placeholder.ifBlank { enterFieldHint }
                                        val current = editText.text?.toString().orEmpty()
                                        if (current != field.value) {
                                            editText.setText(field.value)
                                            editText.setSelection(field.value.length)
                                        }
                                    }
                                )
                            }

                            if (index < fields.size - 1) {
                                Spacer(modifier = Modifier.height(12.dp))
                            }
                        }
                    }
                }
                if (toggleFields.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    toggleFields.forEachIndexed { toggleIndex, toggle ->
                        val absoluteIndex = fields.size + toggleIndex
                        val isFocused = focusedIndex == absoluteIndex
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.White.copy(alpha = if (isFocused) 0.12f else 0.05f), RoundedCornerShape(10.dp))
                                .border(
                                    width = if (isFocused) 2.dp else 1.dp,
                                    color = if (isFocused) resolveAccentColor(fallback = Pink) else Color.White.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .clickable { toggle.onValueChange(!toggle.value) }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = toggle.label,
                                style = ArflixTypography.caption,
                                color = if (isFocused) Pink else TextSecondary,
                                modifier = Modifier.weight(1f)
                            )
                            Box(
                                modifier = Modifier
                                    .width(44.dp)
                                    .height(24.dp)
                                    .background(
                                        color = if (toggle.value) SuccessGreen else Color.White.copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(13.dp)
                                    )
                                    .padding(3.dp),
                                contentAlignment = if (toggle.value) Alignment.CenterEnd else Alignment.CenterStart
                            ) {
                                Box(modifier = Modifier.size(18.dp).background(color = Color.White, shape = RoundedCornerShape(10.dp)))
                            }
                        }
                        if (toggleIndex < toggleFields.size - 1) {
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))

                val isPasteFocused = focusedIndex == fields.size + toggleFields.size
                val fieldFallbackLabel = stringResource(R.string.settings_field_fallback)
                val pasteTargetLabel = fields.getOrNull(pasteTargetIndex())
                    ?.label
                    ?.substringBefore("(")
                    ?.trim()
                    ?.ifBlank { fieldFallbackLabel }
                    ?: fieldFallbackLabel
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            color = if (isPasteFocused) Color.White else Color.Black.copy(alpha = 0.82f),
                            shape = RoundedCornerShape(10.dp)
                        )
                        .border(
                            width = 1.dp,
                            color = if (isPasteFocused) Color.White else Color.White.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(10.dp)
                        )
                        .clickable {
                            pasteClipboardIntoTarget()
                        }
                        .padding(vertical = 11.dp, horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentPaste,
                        contentDescription = stringResource(R.string.settings_cd_paste),
                        tint = if (isPasteFocused) Color.Black else Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.settings_paste_into, pasteTargetLabel),
                        style = ArflixTypography.button,
                        color = if (isPasteFocused) Color.Black else Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val isCancelFocused = focusedIndex == fields.size + toggleFields.size + 1
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                color = if (isCancelFocused) Color.White else Color.Black.copy(alpha = 0.82f),
                                shape = RoundedCornerShape(10.dp)
                            )
                            .border(
                                width = 1.dp,
                                color = if (isCancelFocused) Color.White else Color.White.copy(alpha = 0.14f),
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable {
                                hideKeyboardAll()
                                onDismiss()
                            }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = tr("Cancel"),
                            style = ArflixTypography.button,
                            color = if (isCancelFocused) Color.Black else Color.White
                        )
                    }

                    val isConfirmFocused = focusedIndex == fields.size + toggleFields.size + 2
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                color = if (isConfirmFocused) Color.White else Color.Black.copy(alpha = 0.82f),
                                shape = RoundedCornerShape(10.dp)
                            )
                            .border(
                                width = 1.dp,
                                color = if (isConfirmFocused) Color.White else Color.White.copy(alpha = 0.14f),
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable {
                                hideKeyboardAll()
                                onConfirm()
                            }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = tr("Confirm"),
                            style = ArflixTypography.button,
                            color = if (isConfirmFocused) Color.Black else Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = if (LocalDeviceType.current.isTouchDevice()) stringResource(R.string.settings_modal_footer_touch) else stringResource(R.string.settings_modal_footer_tv),
                    style = ArflixTypography.caption,
                    color = TextSecondary.copy(alpha = 0.56f)
                    )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SubtitlePickerModal(
    title: String,
    options: List<String>,
    selected: String,
    focusedIndex: Int,
    onFocusChange: (Int) -> Unit,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()
    val safeIndex = focusedIndex.coerceIn(0, (options.size - 1).coerceAtLeast(0))

    LaunchedEffect(safeIndex) {
        if (options.isNotEmpty()) {
            listState.animateScrollToItem(safeIndex)
        }
    }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        LaunchedEffect(focusRequester) {
            focusRequester.requestFocus()
        }
        BackHandler {
            onDismiss()
        }
        ModalScrim(onDismiss = onDismiss) {
            Column(
                modifier = Modifier
                    .then(
                        if (LocalDeviceType.current.isTouchDevice()) Modifier.fillMaxWidth(0.92f).widthIn(max = 520.dp)
                        else Modifier.width(520.dp)
                    )
                    .background(BackgroundElevated, RoundedCornerShape(16.dp))
                    .padding(if (LocalDeviceType.current.isTouchDevice()) 20.dp else 28.dp)
                    .focusRequester(focusRequester)
                    .focusable()
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown) {
                            when (event.key) {
                                Key.Back, Key.Escape -> {
                                    onDismiss()
                                    true
                                }
                                Key.DirectionUp -> {
                                    if (safeIndex > 0) onFocusChange(safeIndex - 1)
                                    true
                                }
                                Key.DirectionDown -> {
                                    if (safeIndex < options.size - 1) onFocusChange(safeIndex + 1)
                                    true
                                }
                                Key.Enter, Key.DirectionCenter -> {
                                    if (options.isNotEmpty()) {
                                        onSelect(options[safeIndex])
                                    }
                                    true
                                }
                                else -> false
                            }
                        } else false
                    }
            ) {
                Text(
                    text = title,
                    style = ArflixTypography.sectionTitle,
                    color = TextPrimary
                )

                Spacer(modifier = Modifier.height(16.dp))

                LazyColumn(
                    state = listState,
                    modifier = Modifier.heightIn(max = 360.dp).arvioDpadFocusGroup()
                ) {
                    itemsIndexed(options) { index, option ->
                        val isFocused = index == safeIndex
                        val isSelected = option.equals(selected, ignoreCase = true)
                        val optionFocusColor = resolveAccentColor(fallback = Pink)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (isFocused) Color.White.copy(alpha = 0.12f) else Color.Transparent,
                                    RoundedCornerShape(10.dp)
                                )
                                .border(
                                    width = if (isFocused) 2.dp else 1.dp,
                                    color = if (isFocused) optionFocusColor else Color.White.copy(alpha = 0.1f),
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .clickable { onSelect(option) }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = option,
                                style = ArflixTypography.body,
                                color = if (isFocused) TextPrimary else TextSecondary,
                                modifier = Modifier.weight(1f)
                            )
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = SuccessGreen,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.settings_press_enter_to_select),
                    style = ArflixTypography.caption,
                    color = TextSecondary.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/** All TMDB-supported languages as (code, displayName) pairs. */
@Composable
private fun UiModeWarningDialog(
    nextMode: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    var focusedIndex by remember { mutableIntStateOf(0) } // 0 = Confirm, 1 = Cancel
    val focusRequester = remember { FocusRequester() }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        LaunchedEffect(focusRequester) { focusRequester.requestFocus() }
        BackHandler {
            onDismiss()
        }
        ModalScrim(onDismiss = onDismiss) {
            Column(
                modifier = Modifier
                    .then(
                        if (LocalDeviceType.current.isTouchDevice()) Modifier.fillMaxWidth(0.92f).widthIn(max = 400.dp)
                        else Modifier.width(400.dp)
                    )
                    .background(BackgroundElevated, RoundedCornerShape(16.dp))
                    .padding(if (LocalDeviceType.current.isTouchDevice()) 20.dp else 28.dp)
                    .focusRequester(focusRequester)
                    .focusable()
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown) {
                            when (event.key) {
                                Key.Back, Key.Escape -> {
                                    onDismiss()
                                    true
                                }
                                Key.DirectionLeft -> {
                                    if (focusedIndex > 0) focusedIndex--
                                    true
                                }
                                Key.DirectionRight -> {
                                    if (focusedIndex < 1) focusedIndex++
                                    true
                                }
                                Key.Enter, Key.DirectionCenter -> {
                                    if (focusedIndex == 0) onConfirm() else onDismiss()
                                    true
                                }
                                else -> false
                            }
                        } else false
                    }
            ) {
                Text(
                    text = stringResource(R.string.settings_change_ui_mode),
                    style = ArflixTypography.sectionTitle,
                    color = TextPrimary
                )

                Spacer(modifier = Modifier.height(16.dp))

                val modeString = when (nextMode) {
                    "tv" -> stringResource(R.string.settings_mode_tv)
                    "tablet" -> stringResource(R.string.settings_mode_tablet)
                    "phone" -> stringResource(R.string.settings_mode_phone)
                    else -> stringResource(R.string.auto)
                }

                Text(
                    text = stringResource(R.string.settings_change_ui_mode_confirm, modeString),
                    style = ArflixTypography.body,
                    color = TextSecondary
                )

                Spacer(modifier = Modifier.height(32.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    val isConfirmFocused = focusedIndex == 0
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                color = if (isConfirmFocused) SuccessGreen else SuccessGreen.copy(alpha = 0.6f)
                            )
                            .clickable { onConfirm() }
                            .border(
                                width = if (isConfirmFocused) 2.dp else 0.dp,
                                color = if (isConfirmFocused) Color.White else Color.Transparent,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = tr("Confirm"),
                            style = ArflixTypography.button,
                            color = Color.White
                        )
                    }

                    val isCancelFocused = focusedIndex == 1
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                color = if (isCancelFocused) Color.White.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.1f)
                            )
                            .clickable { onDismiss() }
                            .border(
                                width = if (isCancelFocused) 2.dp else 0.dp,
                                color = if (isCancelFocused) Pink else Color.Transparent,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = tr("Cancel"),
                            style = ArflixTypography.button,
                            color = if (isCancelFocused) TextPrimary else TextSecondary
                        )
                    }
                }
            }
        }
    }
}

val TMDB_LANGUAGES = listOf(
    "af-ZA" to "Afrikaans",
    "sq-AL" to "Albanian (Shqip)",
    "ar-SA" to "Arabic",
    "eu-ES" to "Basque (Euskara)",
    "bn-BD" to "Bengali",
    "bg-BG" to "Bulgarian",
    "ca-ES" to "Catalan",
    "zh-CN" to "Chinese (Simplified)",
    "zh-TW" to "Chinese (Traditional)",
    "hr-HR" to "Croatian (Hrvatski)",
    "cs-CZ" to "Czech (Cesky)",
    "da-DK" to "Danish (Dansk)",
    "nl-NL" to "Dutch (Nederlands)",
    "en-US" to "English",
    "et-EE" to "Estonian",
    "tl-PH" to "Filipino/Tagalog",
    "fi-FI" to "Finnish (Suomi)",
    "fr-FR" to "French (Francais)",
    "gl-ES" to "Galician (Galego)",
    "de-DE" to "German (Deutsch)",
    "el-GR" to "Greek",
    "gu-IN" to "Gujarati",
    "he-IL" to "Hebrew",
    "hi-IN" to "Hindi",
    "hu-HU" to "Hungarian (Magyar)",
    "id-ID" to "Indonesian",
    "it-IT" to "Italian (Italiano)",
    "ja-JP" to "Japanese",
    "kn-IN" to "Kannada",
    "ko-KR" to "Korean",
    "lv-LV" to "Latvian",
    "lt-LT" to "Lithuanian",
    "ms-MY" to "Malay",
    "ml-IN" to "Malayalam",
    "mr-IN" to "Marathi",
    "no-NO" to "Norwegian (Norsk)",
    "fa-IR" to "Persian (Farsi)",
    "pl-PL" to "Polish (Polski)",
    "pt-PT" to "Portuguese (Portugues)",
    "pt-BR" to "Português (Brasil)",
    "pa-IN" to "Punjabi",
    "ro-RO" to "Romanian (Romana)",
    "ru-RU" to "Russian",
    "sr-RS" to "Serbian (Srpski)",
    "sk-SK" to "Slovak (Slovensky)",
    "sl-SI" to "Slovenian (Slovenscina)",
    "es-ES" to "Spanish (Espanol)",
    "sw-KE" to "Swahili",
    "sv-SE" to "Swedish (Svenska)",
    "ta-IN" to "Tamil",
    "te-IN" to "Telugu",
    "th-TH" to "Thai",
    "tr-TR" to "Turkish (Turkce)",
    "uk-UA" to "Ukrainian",
    "ur-PK" to "Urdu",
    "vi-VN" to "Vietnamese"
)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun CatalogPackImportDialog(
    pendingPack: CatalogPackManifest?,
    isLoading: Boolean,
    error: String?,
    onConfirm: (CatalogPackManifest) -> Unit,
    onDismiss: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    var focusedIndex by remember(pendingPack) { mutableIntStateOf(0) } // 0 = Confirm, 1 = Cancel/Dismiss
    val isTouchDevice = LocalDeviceType.current.isTouchDevice()

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        LaunchedEffect(pendingPack, isLoading, error) { focusRequester.requestFocus() }
        BackHandler {
            onDismiss()
        }
        ModalScrim(onDismiss = onDismiss) {
            Column(
                modifier = Modifier
                    .then(
                        if (isTouchDevice) Modifier.fillMaxWidth(0.92f).widthIn(max = 480.dp)
                        else Modifier.width(480.dp)
                    )
                    .background(BackgroundElevated, RoundedCornerShape(16.dp))
                    .padding(if (isTouchDevice) 20.dp else 28.dp)
                    .focusRequester(focusRequester)
                    .focusable()
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown) {
                            when (event.key) {
                                Key.Back, Key.Escape -> {
                                    onDismiss()
                                    true
                                }
                                Key.DirectionLeft -> {
                                    if (focusedIndex > 0) focusedIndex--
                                    true
                                }
                                Key.DirectionRight -> {
                                    if (focusedIndex < 1) focusedIndex++
                                    true
                                }
                                Key.Enter, Key.DirectionCenter -> {
                                    if (isLoading) {
                                        // Ignore clicks while loading
                                    } else if (error != null) {
                                        onDismiss()
                                    } else if (pendingPack != null) {
                                        if (focusedIndex == 0) {
                                            onConfirm(pendingPack)
                                        } else {
                                            onDismiss()
                                        }
                                    }
                                    true
                                }
                                else -> false
                            }
                        } else false
                    }
            ) {
                if (isLoading) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        LoadingIndicator(size = 40.dp)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.settings_catalog_pack_loading),
                            style = ArflixTypography.body,
                            color = TextPrimary
                        )
                    }
                } else if (error != null) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = stringResource(R.string.settings_catalog_pack_import_failed),
                            style = ArflixTypography.sectionTitle,
                            color = Color(0xFFDC2626)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = error,
                            style = ArflixTypography.body,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.White)
                                .clickable { onDismiss() }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.close),
                                style = ArflixTypography.button,
                                color = Color.Black
                            )
                        }
                    }
                } else if (pendingPack != null) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = stringResource(R.string.settings_catalog_pack_import_title),
                            style = ArflixTypography.sectionTitle,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = pendingPack.name ?: "",
                            style = ArflixTypography.cardTitle.copy(fontSize = 18.sp),
                            color = resolveAccentColor(fallback = Pink)
                        )
                        if (!pendingPack.author.isNullOrBlank()) {
                            Text(
                                text = stringResource(R.string.settings_catalog_pack_author, pendingPack.author.orEmpty(), pendingPack.version ?: "1.0.0"),
                                style = ArflixTypography.caption,
                                color = TextSecondary.copy(alpha = 0.8f)
                            )
                        }
                        if (!pendingPack.description.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = pendingPack.description,
                                style = ArflixTypography.body.copy(fontSize = 14.sp),
                                color = TextSecondary
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.settings_catalog_pack_included, pendingPack.catalogs?.size ?: 0),
                            style = ArflixTypography.caption,
                            color = TextSecondary.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        // Display a preview list of catalogs (scrollable if many)
                        val previewScrollState = rememberScrollState()
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 160.dp)
                                .verticalScroll(previewScrollState)
                                .background(Color.Black.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                                .padding(8.dp)
                        ) {
                            pendingPack.catalogs?.forEach { cat ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Widgets,
                                        contentDescription = null,
                                        tint = TextSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = cat.name ?: "",
                                        style = ArflixTypography.body.copy(fontSize = 14.sp),
                                        color = TextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                        // Action buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            val isConfirmFocused = focusedIndex == 0
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        color = if (isConfirmFocused) Color.White else Color.Black.copy(alpha = 0.82f),
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (isConfirmFocused) Color.White else Color.White.copy(alpha = 0.14f),
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .clickable { onConfirm(pendingPack) }
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(R.string.settings_catalog_pack_install),
                                    style = ArflixTypography.button,
                                    color = if (isConfirmFocused) Color.Black else Color.White
                                )
                            }

                            val isCancelFocused = focusedIndex == 1
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        color = if (isCancelFocused) Color.White else Color.Black.copy(alpha = 0.82f),
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (isCancelFocused) Color.White else Color.White.copy(alpha = 0.14f),
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .clickable { onDismiss() }
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(R.string.cancel),
                                    style = ArflixTypography.button,
                                    color = if (isCancelFocused) Color.Black else Color.White
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun CatalogPackDeleteConfirmDialog(
    packName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    var focusedIndex by remember { mutableIntStateOf(0) } // 0 = Confirm, 1 = Cancel
    val isTouchDevice = LocalDeviceType.current.isTouchDevice()

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        LaunchedEffect(focusRequester) { focusRequester.requestFocus() }
        BackHandler {
            onDismiss()
        }
        ModalScrim(onDismiss = onDismiss) {
            Column(
                modifier = Modifier
                    .then(
                        if (isTouchDevice) Modifier.fillMaxWidth(0.92f).widthIn(max = 400.dp)
                        else Modifier.width(400.dp)
                    )
                    .background(BackgroundElevated, RoundedCornerShape(16.dp))
                    .padding(if (isTouchDevice) 20.dp else 28.dp)
                    .focusRequester(focusRequester)
                    .focusable()
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown) {
                            when (event.key) {
                                Key.Back, Key.Escape -> {
                                    onDismiss()
                                    true
                                }
                                Key.DirectionLeft -> {
                                    if (focusedIndex > 0) focusedIndex--
                                    true
                                }
                                Key.DirectionRight -> {
                                    if (focusedIndex < 1) focusedIndex++
                                    true
                                }
                                Key.Enter, Key.DirectionCenter -> {
                                    if (focusedIndex == 0) onConfirm() else onDismiss()
                                    true
                                }
                                else -> false
                            }
                        } else false
                    }
            ) {
                Text(
                    text = stringResource(R.string.settings_catalog_pack_delete_title),
                    style = ArflixTypography.sectionTitle,
                    color = Color(0xFFDC2626)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.settings_catalog_pack_delete_message, packName),
                    style = ArflixTypography.body,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val isConfirmFocused = focusedIndex == 0
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                color = if (isConfirmFocused) Color.White else Color.Black.copy(alpha = 0.82f),
                                shape = RoundedCornerShape(10.dp)
                            )
                            .border(
                                width = 1.dp,
                                color = if (isConfirmFocused) Color.White else Color.White.copy(alpha = 0.14f),
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable { onConfirm() }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.settings_catalog_pack_delete_confirm),
                            style = ArflixTypography.button,
                            color = if (isConfirmFocused) Color.Black else Color.White
                        )
                    }

                    val isCancelFocused = focusedIndex == 1
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                color = if (isCancelFocused) Color.White else Color.Black.copy(alpha = 0.82f),
                                shape = RoundedCornerShape(10.dp)
                            )
                            .border(
                                width = 1.dp,
                                color = if (isCancelFocused) Color.White else Color.White.copy(alpha = 0.14f),
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable { onDismiss() }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.cancel),
                            style = ArflixTypography.button,
                            color = if (isCancelFocused) Color.Black else Color.White
                        )
                    }
                }
            }
        }
    }
}



private fun catalogueLayoutRowKey(catalog: CatalogConfig): String = "home:${catalog.id}"
