package com.arflix.tv.util

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.res.stringResource
import androidx.core.os.ConfigurationCompat
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.arflix.tv.R
import java.util.Locale

val LocalAppLanguage = staticCompositionLocalOf { "en-US" }
val LAST_APP_LANGUAGE_KEY = stringPreferencesKey("last_app_language")

fun defaultAppLanguage(): String {
    if (com.arflix.tv.BuildConfig.PERSONAL_BUILD) return PersonalPrefs.APP_LANGUAGE
    // Locale.getDefault() is changed by the app's own language override.
    val systemLocale = ConfigurationCompat.getLocales(Resources.getSystem().configuration)[0]
    return defaultAppLanguage(systemLocale ?: Locale.US)
}

internal fun defaultAppLanguage(systemLocale: Locale): String {
    if (com.arflix.tv.BuildConfig.PERSONAL_BUILD) return PersonalPrefs.APP_LANGUAGE
    val language = systemLocale.language.lowercase(Locale.ROOT)
    val country = systemLocale.country.uppercase(Locale.ROOT)
    return when {
        language == "pt" && country == "PT" -> "pt-PT"
        language == "pt" -> "pt-BR"
        language == "nl" -> "nl-NL"
        language == "fr" -> "fr-FR"
        language == "es" -> "es-ES"
        language == "de" -> "de-DE"
        language == "it" -> "it-IT"
        language == "ru" -> "ru-RU"
        language == "tr" -> "tr-TR"
        language == "ar" -> "ar-SA"
        language == "hi" -> "hi-IN"
        language == "zh" && systemLocale.script == "Hans" -> "zh-CN"
        language == "zh" && (systemLocale.script == "Hant" || country in setOf("TW", "HK", "MO")) -> "zh-TW"
        language == "zh" -> "zh-CN"
        language == "ja" -> "ja-JP"
        language == "ko" -> "ko-KR"
        language == "pl" -> "pl-PL"
        else -> "en-US"
    }
}

fun resolveAppLanguage(
    preferences: Preferences,
    profileId: String?,
    systemDefault: () -> String = ::defaultAppLanguage
): String {
    if (com.arflix.tv.BuildConfig.PERSONAL_BUILD) return PersonalPrefs.APP_LANGUAGE
    val profileLanguage = profileId?.takeIf { it.isNotBlank() }?.let {
        preferences[stringPreferencesKey("profile_${it}_content_language")]
    }
    return profileLanguage?.takeIf { it.isNotBlank() }
        ?: preferences[LAST_APP_LANGUAGE_KEY]?.takeIf { it.isNotBlank() }
        ?: systemDefault()
}

fun appLocale(languageTag: String): Locale {
    val normalized = languageTag.replace('_', '-')
    return Locale.forLanguageTag(normalized).takeUnless { it.language.isBlank() } ?: Locale.US
}

fun localizedAppContext(context: Context, languageTag: String): Context {
    val locale = appLocale(languageTag)
    Locale.setDefault(locale)
    val config = Configuration(context.resources.configuration)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        config.setLocales(LocaleList(locale))
    } else {
        @Suppress("DEPRECATION")
        config.setLocale(locale)
    }
    return context.createConfigurationContext(config)
}

/**
 * Translate [text] using Android string resources (XML-backed).
 * Covers every key from the old AppTranslations map. Unknown strings fall back to
 * AppTranslations so existing call sites keep working during incremental migration.
 */
@Composable
fun tr(text: String): String {
    if (text.isBlank()) return text
    @StringRes val resId: Int? = when (text.trim()) {
        "Calendar" -> R.string.library_calendar
        "All watchlists" -> R.string.calendar_all_watchlists
        "ARVIO watchlist" -> R.string.calendar_arvio_watchlist
        "Today" -> R.string.live_label_today
        "Local time" -> R.string.calendar_local_time
        "Time TBA" -> R.string.calendar_time_tba
        "Next month" -> R.string.calendar_next_month
        "Previous month" -> R.string.calendar_previous_month
        "release" -> R.string.calendar_release
        "releases" -> R.string.calendar_releases
        "more" -> R.string.calendar_more
        "Navigate days" -> R.string.calendar_navigate_days
        "Open day" -> R.string.calendar_open_day
        "Some sources unavailable" -> R.string.calendar_partial
        "Some release details unavailable" -> R.string.calendar_partial_details
        "Next release" -> R.string.calendar_next_release
        "Refresh calendar" -> R.string.calendar_refresh
        "Finding your releases…" -> R.string.calendar_loading
        "Calendar unavailable" -> R.string.calendar_unavailable
        "Your watchlist, on the calendar" -> R.string.calendar_empty_watchlist
        "No releases on this day" -> R.string.calendar_empty_day
        "Add movies and shows to your ARVIO watchlist. No tracking account needed." -> R.string.calendar_add_watchlist
        "Choose another day or watchlist to explore upcoming releases." -> R.string.calendar_choose_day
        "Cinema" -> R.string.calendar_cinema
        "Digital" -> R.string.calendar_digital
        "Physical" -> R.string.calendar_physical
        // Navigation
        "Home" -> R.string.home
        "Search",
        "Search or discover... try \"top 10 horror movies\"" -> R.string.search
        "Watchlist",
        "Add to Watchlist",
        "Remove from Watchlist" -> R.string.watchlist
        "Settings",
        "App Update",
        "App Updates" -> R.string.settings
        "General" -> R.string.general
        "Movies",
        "In Cinema" -> R.string.movies
        "Movie" -> R.string.movie
        "TV Shows" -> R.string.tv_shows
        "Shows" -> R.string.shows
        "Series" -> R.string.series
        "All Genres" -> R.string.all_genres
        "Any Language" -> R.string.any_language
        // Language & subtitles
        "Language & Subtitles" -> R.string.language_and_subtitles
        "App Language",
        "Content Language",
        "App text, titles, descriptions and metadata",
        "Titles, descriptions and metadata" -> R.string.app_language
        "Subtitles",
        "Default Subtitle",
        "Default Subtitles",
        "Auto-select subtitle language",
        "Subtitle Size",
        "Text size for subtitles",
        "Subtitle Color",
        "Text color for subtitles",
        "Subtitle Style",
        "Bold, Normal, or Background style for subtitles" -> R.string.subtitles
        "Audio",
        "Audio Track",
        "Default Audio",
        "Preferred audio track",
        "Volume Boost",
        "Amplify quiet sources (via system LoudnessEnhancer)",
        "No audio tracks available" -> R.string.audio
        // Playback
        "Playback",
        "Match Frame Rate" -> R.string.playback
        "Auto-Play Next",
        "Start next episode automatically",
        "Next Episode",
        "Next",
        "Next channel" -> R.string.next
        "Autoplay",
        "Auto-Play Min Quality",
        "Min quality for auto-play",
        "Auto" -> R.string.auto
        // Interface
        "Interface",
        "Card Layout",
        "Landscape or poster cards",
        "UI Mode",
        "Force TV, Tablet, or Phone",
        "Skip Profile Selection",
        "Auto-load last used profile",
        "Clock Format",
        "Choose 12-hour or 24-hour time" -> R.string.interface_label
        "Show Budget on Home",
        "Display the movie budget on the home hero banner",
        "Budget" -> R.string.budget
        // Network
        "Network",
        "DNS Provider",
        "Resolve API and stream requests" -> R.string.network
        // Catalogs / accounts
        "Catalogs",
        "Add Catalog",
        "Accounts",
        "Linked Accounts" -> R.string.accounts
        // Sources
        "Sources",
        "Off opens the source picker on Play" -> R.string.sources
        "Available Sources" -> R.string.available_sources
        "Finding sources..." -> R.string.finding_sources
        "sources available" -> R.string.sources_available
        "Add Playlist",
        "Add Catalog",
        "Add" -> R.string.add
        "Reload playlists now",
        "Refresh" -> R.string.refresh
        "Waiting for authorization... (Press OK to cancel)" -> R.string.loading_label
        "Remove from Continue Watching",
        "Remove",
        "Delete" -> R.string.delete
        "No playlists configured",
        "Empty" -> R.string.empty
        // Content
        "Details",
        "View Details" -> R.string.details
        "Play" -> R.string.play
        "Seasons" -> R.string.seasons
        "Season" -> R.string.season_label
        "Episodes" -> R.string.episodes
        "Cast" -> R.string.cast
        "Reviews" -> R.string.reviews
        "More Like This" -> R.string.more_like_this
        "Ongoing" -> R.string.ongoing
        // Watchlist
        "Your watchlist is empty" -> R.string.empty_watchlist
        "Add movies and shows to watch later" -> R.string.add_later
        "No results found",
        "Unable to load content",
        "ARVIO uses community streaming addons to find video sources. Without at least one streaming addon, content cannot be played." -> R.string.no_results
        "No results found for" -> R.string.no_results_for
        // Actions
        "Close",
        "Press BACK to close" -> R.string.close
        "Back",
        "Back to channel list",
        "Previous channel" -> R.string.back
        "Cancel" -> R.string.cancel
        "Confirm",
        "Mark as Watched",
        "Mark as Unwatched",
        "Create" -> R.string.confirm
        "Retry" -> R.string.retry
        "Sign In" -> R.string.sign_in
        "Log Out" -> R.string.log_out
        "Off" -> R.string.off
        "On" -> R.string.on
        "Live" -> R.string.live
        "Now" -> R.string.now
        "Later" -> R.string.later
        "Ends at" -> R.string.ends_at
        "selected",
        "Selected" -> R.string.selected
        else -> null
    }
    if (resId != null) return stringResource(resId)

    // Derived / composite strings
    return when (text.trim()) {
        "MY WATCHLIST" -> stringResource(R.string.watchlist).uppercase()
        "BUDGET" -> stringResource(R.string.budget).uppercase()
        "ONGOING" -> stringResource(R.string.ongoing).uppercase()
        "FILTER BY SOURCE" -> stringResource(R.string.sources).uppercase()
        "TRY AGAIN" -> stringResource(R.string.retry).uppercase()
        "GO BACK" -> stringResource(R.string.back).uppercase()
        "UP NEXT" -> stringResource(R.string.next).uppercase()
        "PLAY NOW" -> stringResource(R.string.play).uppercase()
        "CANCEL" -> stringResource(R.string.cancel).uppercase()
        "OK" -> stringResource(R.string.confirm).uppercase()
        "NOW" -> stringResource(R.string.now).uppercase()
        "NEXT" -> stringResource(R.string.next).uppercase()
        "LATER" -> stringResource(R.string.later).uppercase()
        "LIVE" -> stringResource(R.string.live).uppercase()
        "ADD" -> stringResource(R.string.add).uppercase()
        "LOADING" -> stringResource(R.string.loading_label).uppercase()
        "REFRESH" -> stringResource(R.string.refresh).uppercase()
        "EMPTY" -> stringResource(R.string.empty).uppercase()
        "DELETE" -> stringResource(R.string.delete).uppercase()
        "CONNECT" -> stringResource(R.string.sign_in).uppercase()
        "CONNECTED" -> stringResource(R.string.on).uppercase()
        "Subtitles & Audio" ->
            "${stringResource(R.string.subtitles)} / ${stringResource(R.string.audio)}"
        "Switch tabs • Navigate • BACK Close",
        "Switch tabs â¢ Navigate â¢ BACK Close" ->
            "${stringResource(R.string.subtitles)} • ${stringResource(R.string.back)} • ${stringResource(R.string.close)}"
        "Off, Seamless, or Always" ->
            "${stringResource(R.string.off)} / ${stringResource(R.string.auto)}"
        else -> AppTranslations.translate(text, LocalAppLanguage.current)
    }
}

@Composable
fun trUpper(text: String): String = tr(text).uppercase(appLocale(LocalAppLanguage.current))

object AppTranslations {
    fun translate(text: String, languageTag: String): String = text
}
