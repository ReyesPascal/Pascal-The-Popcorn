package com.arflix.tv.navigation

import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.runtime.SideEffect
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.arflix.tv.BuildConfig
import com.arflix.tv.data.model.Category
import com.arflix.tv.data.model.MediaItem
import com.arflix.tv.data.model.MediaType
import com.arflix.tv.ui.screens.details.DetailsScreen
import com.arflix.tv.ui.screens.home.CategoryViewAllScreen
import com.arflix.tv.ui.screens.home.HomeScreen
import com.arflix.tv.ui.screens.player.PlayerScreen
import com.arflix.tv.ui.screens.search.SearchScreen
import com.arflix.tv.ui.screens.settings.SettingsScreen
import com.arflix.tv.ui.screens.watchlist.WatchlistScreen
import com.arflix.tv.ui.screens.watchlist.calendar.ReleaseCalendarViewModel
import com.arflix.tv.util.LocalDeviceType
import com.arflix.tv.ui.diag.UiDiag
import kotlinx.coroutines.delay

@Composable
private fun CalendarIdlePreload(viewModel: ReleaseCalendarViewModel, content: @Composable () -> Unit) {
    var lastInteraction by remember { mutableLongStateOf(0L) }
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    LaunchedEffect(lifecycle, lastInteraction) {
        if (lifecycle == Lifecycle.State.RESUMED) {
            delay(6_000)
            viewModel.preloadIdle()
        }
    }
    Box(Modifier.fillMaxSize().onPreviewKeyEvent {
        UiDiag.key(it.nativeKeyEvent, Screen.Home.route)
        lastInteraction = android.os.SystemClock.uptimeMillis()
        false
    }.pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                awaitPointerEvent(PointerEventPass.Initial)
                lastInteraction = android.os.SystemClock.uptimeMillis()
            }
        }
    }) { content() }
}

/**
 * Navigation destinations
 */
sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Search : Screen("search")
    data object Watchlist : Screen("watchlist")

    data object CategoryViewAll : Screen("home_category/{categoryId}") {
        fun createRoute(categoryId: String): String {
            return "home_category/${android.net.Uri.encode(categoryId)}"
        }
    }
    data object Settings : Screen("settings") {
        fun createRoute(initialSection: String? = null): String {
            val base = "settings"
            val params = mutableListOf<String>()
            initialSection?.let {
                val section = if (BuildConfig.PERSONAL_BUILD && it == "playback") "advanced" else it
                params.add("initialSection=$section")
            }
            return if (params.isNotEmpty()) "$base?${params.joinToString("&")}" else base
        }
    }
    /** Personal build 1.0.1: first-run TV check (also Settings -> Playback -> This TV, with fromSettings=true). */
    data object TvCheck : Screen("tv_check?fromSettings={fromSettings}") {
        fun createRoute(fromSettings: Boolean = false): String = "tv_check?fromSettings=$fromSettings"
    }

    data object Details : Screen("details/{mediaType}/{mediaId}?initialSeason={initialSeason}&initialEpisode={initialEpisode}") {
        fun createRoute(
            mediaType: MediaType,
            mediaId: Int,
            initialSeason: Int? = null,
            initialEpisode: Int? = null
        ): String {
            val base = "details/${mediaType.name.lowercase()}/$mediaId"
            val params = mutableListOf<String>()
            initialSeason?.let { params.add("initialSeason=$it") }
            initialEpisode?.let { params.add("initialEpisode=$it") }
            return if (params.isNotEmpty()) "$base?${params.joinToString("&")}" else base
        }
    }

    data object Player : Screen("player/{mediaType}/{mediaId}?seasonNumber={seasonNumber}&episodeNumber={episodeNumber}&tmdbSeasonNumber={tmdbSeasonNumber}&tmdbEpisodeNumber={tmdbEpisodeNumber}&kitsuId={kitsuId}&kitsuEpisodeNumber={kitsuEpisodeNumber}&imdbId={imdbId}&streamUrl={streamUrl}&preferredAddonId={preferredAddonId}&preferredSourceName={preferredSourceName}&preferredBingeGroup={preferredBingeGroup}&startPositionMs={startPositionMs}&isLiveStream={isLiveStream}") {
        fun createRoute(
            mediaType: MediaType,
            mediaId: Int,
            seasonNumber: Int? = null,
            episodeNumber: Int? = null,
            tmdbSeasonNumber: Int? = seasonNumber,
            tmdbEpisodeNumber: Int? = episodeNumber,
            kitsuId: Int? = null,
            kitsuEpisodeNumber: Int? = null,
            imdbId: String? = null,
            streamUrl: String? = null,
            preferredAddonId: String? = null,
            preferredSourceName: String? = null,
            preferredBingeGroup: String? = null,
            startPositionMs: Long? = null,
            isLiveStream: Boolean = false
        ): String {
            val base = "player/${mediaType.name.lowercase()}/$mediaId"
            val params = mutableListOf<String>()
            seasonNumber?.let { params.add("seasonNumber=$it") }
            episodeNumber?.let { params.add("episodeNumber=$it") }
            tmdbSeasonNumber?.let { params.add("tmdbSeasonNumber=$it") }
            tmdbEpisodeNumber?.let { params.add("tmdbEpisodeNumber=$it") }
            kitsuId?.let { params.add("kitsuId=$it") }
            kitsuEpisodeNumber?.let { params.add("kitsuEpisodeNumber=$it") }
            imdbId?.let { params.add("imdbId=${android.net.Uri.encode(it)}") }
            streamUrl?.let { params.add("streamUrl=${android.net.Uri.encode(it)}") }
            preferredAddonId?.let { params.add("preferredAddonId=${android.net.Uri.encode(it)}") }
            preferredSourceName?.let { params.add("preferredSourceName=${android.net.Uri.encode(it)}") }
            preferredBingeGroup?.let { params.add("preferredBingeGroup=${android.net.Uri.encode(it)}") }
            startPositionMs?.let { params.add("startPositionMs=$it") }
            if (isLiveStream) params.add("isLiveStream=true")
            return if (params.isNotEmpty()) "$base?${params.joinToString("&")}" else base
        }
    }
}



/**
 * Main navigation graph
 */
@Composable
fun AppNavigation(
    navController: NavHostController = rememberNavController(),
    startDestination: String = Screen.Home.route,
    onSettingsSubPageChanged: (Boolean) -> Unit = {},
    onExitApp: () -> Unit = {}
) {
    // Activity-scoped results survive Home/Library navigation and remain profile-scoped internally.
    val calendarViewModel: ReleaseCalendarViewModel = hiltViewModel()
    DisposableEffect(navController) {
        val listener = UiDiag.destinationListener()
        navController.addOnDestinationChangedListener(listener)
        onDispose { navController.removeOnDestinationChangedListener(listener) }
    }
    val navigateTopLevel: (String) -> Unit = navigate@{ route ->
        navController.navigate(route) {
            popUpTo(Screen.Home.route) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    val navigateHome: () -> Unit = {
        // Pop back to the EXISTING Home entry rather than replacing it.
        //
        // navigateTopLevel uses popUpTo(Home) with inclusive = false, so Home is still on the back
        // stack while the user is on Watchlist/TV/Search. Re-navigating with inclusive = true
        // destroyed that entry, and with it the HomeViewModel that hiltViewModel() scopes to it —
        // so every return to Home rebuilt the whole screen from scratch (~550 requests, ~5s).
        //
        // popBackStack also clears everything stacked above Home, which is what the previous
        // comment here wanted (no stale Details pages); the fallback covers the case it worried
        // about, Home not being on the stack at all.
        if (!navController.popBackStack(Screen.Home.route, inclusive = false, saveState = true)) {
            navController.navigate(Screen.Home.route) {
                popUpTo(Screen.Home.route) { inclusive = true; saveState = false }
                launchSingleTop = true
                restoreState = false
            }
        }
    }

    NavHost(
        navController = navController,
        modifier = Modifier.onPreviewKeyEvent {
            // Home already logs in CalendarIdlePreload; do not emit duplicate key events.
            if (navController.currentDestination?.route != Screen.Home.route) UiDiag.key(it.nativeKeyEvent, navController.currentDestination?.route)
            false
        },
        startDestination = startDestination,
        // Premium screen transitions — subtle fade + slight depth push.
        // Netflix TV uses ~250ms fade; this is tuned for Android TV's 60fps.
        // Pure crossfade — no horizontal slides (those feel mobile, not TV).
        // Netflix TV uses ~250ms crossfade for all screen transitions.
        enterTransition = { fadeIn(androidx.compose.animation.core.tween(180, easing = androidx.compose.animation.core.FastOutSlowInEasing)) },
        exitTransition = { fadeOut(androidx.compose.animation.core.tween(140, easing = androidx.compose.animation.core.FastOutSlowInEasing)) },
        popEnterTransition = { fadeIn(androidx.compose.animation.core.tween(180, easing = androidx.compose.animation.core.FastOutSlowInEasing)) },
        popExitTransition = { fadeOut(androidx.compose.animation.core.tween(140, easing = androidx.compose.animation.core.FastOutSlowInEasing)) }
    ) {


        // Home screen
        composable(Screen.Home.route) {
          CalendarIdlePreload(calendarViewModel) {
            HomeScreen(
                onNavigateToDetails = { mediaType, mediaId, initialSeason, initialEpisode ->
                    navController.navigate(Screen.Details.createRoute(mediaType, mediaId, initialSeason, initialEpisode))
                },

                onNavigateToCategory = { categoryId ->
                    navController.navigate(Screen.CategoryViewAll.createRoute(categoryId))
                },
                onNavigateToSearch = {
                    navigateTopLevel(Screen.Search.route)
                },
                onNavigateToWatchlist = {
                    navigateTopLevel(Screen.Watchlist.route)
                },
                onNavigateToSettings = {
                    navigateTopLevel(Screen.Settings.route)
                },

                onExitApp = onExitApp
            )
          }
        }

        // Search screen
        composable(Screen.Search.route) {
            SearchScreen(

                onNavigateToDetails = { mediaType, mediaId ->
                    navController.navigate(Screen.Details.createRoute(mediaType, mediaId))
                },
                onNavigateToHome = { navigateHome() },
                onNavigateToWatchlist = { navigateTopLevel(Screen.Watchlist.route) },
                onNavigateToSettings = { navigateTopLevel(Screen.Settings.route) },

                onBack = { navigateHome() }
            )
        }

        // Watchlist screen
        composable(Screen.Watchlist.route) {
            DisposableEffect(calendarViewModel) {
                calendarViewModel.onVisible()
                onDispose { calendarViewModel.onHidden() }
            }
            WatchlistScreen(
                calendarViewModel = calendarViewModel,
                onCalendarOpenRelease = { release ->
                    navController.navigate(Screen.Details.createRoute(release.media.mediaType, release.media.id,
                        release.seasonNumber, release.episodeNumber))
                },

                onNavigateToDetails = { mediaType, mediaId ->
                    navController.navigate(Screen.Details.createRoute(mediaType, mediaId))
                },
                onNavigateToHome = { navigateHome() },
                onNavigateToSearch = { navigateTopLevel(Screen.Search.route) },
                onNavigateToSettings = { section ->
                    navigateTopLevel(Screen.Settings.createRoute(initialSection = section))
                },

                onBack = { navigateHome() }
            )
        }

        // Settings screen
        composable(
            route = "settings?initialSection={initialSection}&installPackUrl={installPackUrl}&installAddonUrl={installAddonUrl}",
            arguments = listOf(

                navArgument("initialSection") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("installPackUrl") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("installAddonUrl") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val initialSection = backStackEntry.arguments?.getString("initialSection")
            val installPackUrl = backStackEntry.arguments?.getString("installPackUrl")
            val installAddonUrl = backStackEntry.arguments?.getString("installAddonUrl")
            SettingsScreen(

                initialSection = initialSection,
                installPackUrl = installPackUrl,
                installAddonUrl = installAddonUrl,
                onNavigateToHome = { navigateHome() },
                onNavigateToSearch = { navigateTopLevel(Screen.Search.route) },
                onNavigateToWatchlist = { navigateTopLevel(Screen.Watchlist.route) },
                onBack = { navController.popBackStack() },
                onSubPageChanged = onSettingsSubPageChanged,
                onOpenTvCheck = {
                    navController.navigate(Screen.TvCheck.createRoute(fromSettings = true)) { launchSingleTop = true }
                }
            )
        }

        // Personal build 1.0.1: first-run TV check. First run: start destination, then Home replaces it.
        // From Settings: Results first; Back / "Looks right" return to Settings.
        composable(
            route = Screen.TvCheck.route,
            arguments = listOf(
                navArgument("fromSettings") {
                    type = NavType.BoolType
                    defaultValue = false
                }
            )
        ) { backStackEntry ->
            val fromSettings = backStackEntry.arguments?.getBoolean("fromSettings") ?: false
            com.arflix.tv.ui.screens.tvcheck.TvCheckScreen(
                fromSettings = fromSettings,
                onDone = {
                    if (fromSettings) {
                        navController.popBackStack()
                    } else {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.TvCheck.route) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }


        // Details screen

        composable(
            route = Screen.CategoryViewAll.route,
            arguments = listOf(navArgument("categoryId") { type = NavType.StringType })
        ) { backStackEntry ->
            val categoryId = backStackEntry.arguments?.getString("categoryId").orEmpty()
            val homeEntry = remember(backStackEntry) {
                runCatching { navController.getBackStackEntry(Screen.Home.route) }.getOrNull()
            }
            if (categoryId.isBlank() || homeEntry == null) {
                LaunchedEffect(Unit) { navController.popBackStack() }
                return@composable
            }
            CategoryViewAllScreen(
                categoryId = categoryId,
                viewModel = hiltViewModel(homeEntry),
                onNavigateToDetails = { mediaType, mediaId ->
                    navController.navigate(Screen.Details.createRoute(mediaType, mediaId))
                },
                onBack = { navController.popBackStack() }
            )
        }

        // Details screen
        composable(
            route = Screen.Details.route,
            arguments = listOf(
                navArgument("mediaType") { type = NavType.StringType },
                navArgument("mediaId") { type = NavType.IntType },
                navArgument("initialSeason") {
                    type = NavType.IntType
                    defaultValue = -1
                },
                navArgument("initialEpisode") {
                    type = NavType.IntType
                    defaultValue = -1
                }
            )
        ) { backStackEntry ->
            val mediaTypeStr = backStackEntry.arguments?.getString("mediaType") ?: "movie"
            val mediaId = backStackEntry.arguments?.getInt("mediaId") ?: 0
            // Negative ids are native addon items (see AddonNativeCatalog).
            if (mediaId == 0) {
                navigateHome()
                return@composable
            }
            val initialSeason = backStackEntry.arguments?.getInt("initialSeason")?.takeIf { it >= 0 }
            val initialEpisode = backStackEntry.arguments?.getInt("initialEpisode")?.takeIf { it >= 0 }
            val mediaType = if (mediaTypeStr == "tv") MediaType.TV else MediaType.MOVIE

            DetailsScreen(
                mediaType = mediaType,
                mediaId = mediaId,
                initialSeason = initialSeason,
                initialEpisode = initialEpisode,

                onNavigateToPlayer = { type, id, identity, imdbId, url, preferredAddonId, preferredSourceName, startPositionMs ->
                    UiDiag.playPressed(id, imdbId, identity?.displaySeason, identity?.displayEpisode, startPositionMs)
                    navController.navigate(
                        Screen.Player.createRoute(
                            mediaType = type,
                            mediaId = id,
                            seasonNumber = identity?.displaySeason,
                            episodeNumber = identity?.displayEpisode,
                            tmdbSeasonNumber = identity?.tmdbSeason,
                            tmdbEpisodeNumber = identity?.tmdbEpisode,
                            kitsuId = identity?.kitsuId,
                            kitsuEpisodeNumber = identity?.kitsuEpisode,
                            imdbId = imdbId,
                            streamUrl = url,
                            preferredAddonId = preferredAddonId,
                            preferredSourceName = preferredSourceName,
                            startPositionMs = startPositionMs
                        )
                    )
                },
                onNavigateToDetails = { type, id ->
                    navController.navigate(Screen.Details.createRoute(type, id))
                },

                onNavigateToHome = {
                    navigateHome()
                },
                onNavigateToSearch = {
                    navigateTopLevel(Screen.Search.route)
                },
                onNavigateToWatchlist = {
                    navigateTopLevel(Screen.Watchlist.route)
                },
                onNavigateToSettings = {
                    navigateTopLevel(Screen.Settings.route)
                },

                onBack = { navController.popBackStack() }
            )
        }

        // Player screen
        composable(
            route = Screen.Player.route,
            arguments = listOf(
                navArgument("mediaType") { type = NavType.StringType },
                navArgument("mediaId") { type = NavType.IntType },
                navArgument("seasonNumber") {
                    type = NavType.IntType
                    defaultValue = -1
                },
                navArgument("episodeNumber") {
                    type = NavType.IntType
                    defaultValue = -1
                },
                navArgument("tmdbSeasonNumber") {
                    type = NavType.IntType
                    defaultValue = -1
                },
                navArgument("tmdbEpisodeNumber") {
                    type = NavType.IntType
                    defaultValue = -1
                },
                navArgument("kitsuId") {
                    type = NavType.IntType
                    defaultValue = -1
                },
                navArgument("kitsuEpisodeNumber") {
                    type = NavType.IntType
                    defaultValue = -1
                },
                navArgument("imdbId") {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument("streamUrl") {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument("preferredAddonId") {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument("preferredSourceName") {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument("preferredBingeGroup") {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument("startPositionMs") {
                    type = NavType.LongType
                    defaultValue = -1L
                },
                navArgument("isLiveStream") {
                    type = NavType.BoolType
                    defaultValue = false
                }
            ),
            exitTransition = { ExitTransition.None },
            popExitTransition = { ExitTransition.None }
        ) { backStackEntry ->
            val mediaTypeStr = backStackEntry.arguments?.getString("mediaType") ?: "movie"
            val mediaId = backStackEntry.arguments?.getInt("mediaId") ?: 0
            val seasonNumber = backStackEntry.arguments?.getInt("seasonNumber")?.takeIf { it >= 0 }
            val episodeNumber = backStackEntry.arguments?.getInt("episodeNumber")?.takeIf { it >= 0 }
            val tmdbSeasonNumber = backStackEntry.arguments?.getInt("tmdbSeasonNumber")?.takeIf { it >= 0 }
                ?: seasonNumber
            val tmdbEpisodeNumber = backStackEntry.arguments?.getInt("tmdbEpisodeNumber")?.takeIf { it >= 0 }
                ?: episodeNumber
            val kitsuId = backStackEntry.arguments?.getInt("kitsuId")?.takeIf { it > 0 }
            val kitsuEpisodeNumber = backStackEntry.arguments?.getInt("kitsuEpisodeNumber")?.takeIf { it > 0 }
            val imdbId = backStackEntry.arguments?.getString("imdbId")?.takeIf { it.isNotBlank() }
            val streamUrl = backStackEntry.arguments?.getString("streamUrl")?.takeIf { it.isNotEmpty() }
            val preferredAddonId = backStackEntry.arguments?.getString("preferredAddonId")?.takeIf { it.isNotBlank() }
            val preferredSourceName = backStackEntry.arguments?.getString("preferredSourceName")?.takeIf { it.isNotBlank() }
            val preferredBingeGroup = backStackEntry.arguments?.getString("preferredBingeGroup")?.takeIf { it.isNotBlank() }
            val startPositionMs = backStackEntry.arguments?.getLong("startPositionMs")?.takeIf { it >= 0L }
            val isLiveStream = backStackEntry.arguments?.getBoolean("isLiveStream") ?: false
            val mediaType = if (mediaTypeStr == "tv") MediaType.TV else MediaType.MOVIE

            PlayerScreen(
                mediaType = mediaType,
                mediaId = mediaId,
                seasonNumber = seasonNumber,
                episodeNumber = episodeNumber,
                tmdbSeasonNumber = tmdbSeasonNumber,
                tmdbEpisodeNumber = tmdbEpisodeNumber,
                kitsuId = kitsuId,
                kitsuEpisodeNumber = kitsuEpisodeNumber,
                imdbId = imdbId,
                streamUrl = streamUrl,
                preferredAddonId = preferredAddonId,
                preferredSourceName = preferredSourceName,
                preferredBingeGroup = preferredBingeGroup,
                startPositionMs = startPositionMs,
                isLiveStream = isLiveStream,
                onBack = { navController.popBackStack() },
                onPlayNext = { nextIdentity, nextPreferredAddonId, nextPreferredSourceName, nextPreferredBingeGroup ->
                    // Navigate to next episode
                    navController.navigate(
                        Screen.Player.createRoute(
                            mediaType = mediaType,
                            mediaId = mediaId,
                            seasonNumber = nextIdentity.displaySeason,
                            episodeNumber = nextIdentity.displayEpisode,
                            tmdbSeasonNumber = nextIdentity.tmdbSeason,
                            tmdbEpisodeNumber = nextIdentity.tmdbEpisode,
                            kitsuId = nextIdentity.kitsuId,
                            kitsuEpisodeNumber = nextIdentity.kitsuEpisode,
                            preferredAddonId = nextPreferredAddonId,
                            preferredSourceName = nextPreferredSourceName,
                            preferredBingeGroup = nextPreferredBingeGroup
                        )
                    ) {
                        popUpTo(Screen.Player.route) { inclusive = true }
                    }
                }
            )
        }
    }
}
