package com.arflix.tv.ui.screens.settings
import com.arflix.tv.data.model.AnimeStructuringStyle
import com.arflix.tv.data.model.AutoplayLimits

import android.content.Context
import android.graphics.Bitmap
import coil.Coil
import androidx.annotation.StringRes
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arflix.tv.R
import com.arflix.tv.ui.screens.player.SubtitleFontOption
import com.arflix.tv.util.AppLogger
import com.arflix.tv.util.Constants
import com.arflix.tv.util.DeviceIpAddress
import com.arflix.tv.util.QrCodeGenerator
import com.arflix.tv.data.model.Addon
import com.arflix.tv.data.model.needsConfiguration
import com.arflix.tv.data.model.isRemovable
import com.arflix.tv.data.model.CommunityAddon
import com.arflix.tv.data.model.CatalogConfig
import com.arflix.tv.data.model.CatalogDiscoveryResult
import com.arflix.tv.data.model.CatalogKind
import com.arflix.tv.data.model.CatalogPackManifest
import com.arflix.tv.util.CatalogUrlParser
import com.arflix.tv.data.repository.CatalogDiscoveryRepository
import com.arflix.tv.data.repository.CatalogRepository
import com.arflix.tv.data.repository.CommunityAddonRepository
import com.arflix.tv.data.repository.LauncherContinueWatchingRepository
import com.arflix.tv.data.repository.MediaRepository
import com.arflix.tv.data.repository.ProfileManager
import com.arflix.tv.data.repository.StreamRepository
import com.arflix.tv.data.repository.LocalWatchRepository
import com.arflix.tv.data.repository.WatchlistRepository
import com.arflix.tv.network.OkHttpProvider
import com.arflix.tv.data.repository.CatalogException
import com.arflix.tv.ui.components.CARD_LAYOUT_MODE_LANDSCAPE
import com.arflix.tv.ui.components.normalizeCardLayoutMode
import com.arflix.tv.util.LAST_APP_LANGUAGE_KEY
import com.arflix.tv.util.PersonalPrefs
import com.arflix.tv.util.PersonalSubtitles
import com.arflix.tv.util.resolveAppLanguage
import com.arflix.tv.util.settingsDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import javax.inject.Inject

enum class ToastType {
    SUCCESS, ERROR, INFO
}

/**
 * A user-facing settings message, kept as a resource reference until a composable renders it.
 * A ViewModel only has the application context, whose resources follow the SYSTEM language
 * instead of the language selected in the app, so resolving here would show the wrong
 * language whenever the two differ. Mirrors [com.arflix.tv.ui.screens.player.PlayerMessage].
 */
sealed interface SettingsMessage {
    /** A localizable message. [formatArgs] may itself contain [SettingsMessage] entries. */
    data class Res(
        @param:StringRes val resourceId: Int,
        val formatArgs: List<Any> = emptyList()
    ) : SettingsMessage

    /** Text without a resource (e.g. a platform exception message); shown as it is. */
    data class Raw(val text: String) : SettingsMessage
}

/** Wraps a non-null error text from a repository/exception into a [SettingsMessage]. */
private fun String?.orMessage(fallback: SettingsMessage): SettingsMessage =
    this?.takeIf { it.isNotBlank() }?.let { SettingsMessage.Raw(it) } ?: fallback

/**
 * Turns a catalog failure into a [SettingsMessage]. A [CatalogException] carries the
 * string resource rather than a finished text, so it stays in the language selected
 * in the app; anything else falls back to its platform message.
 */
private fun Throwable.orCatalogMessage(fallback: SettingsMessage): SettingsMessage =
    when (this) {
        is CatalogException -> SettingsMessage.Res(messageRes, formatArgs)
        else -> message.orMessage(fallback)
    }

/**
 * An addon fetched but not installed yet, waiting for the user to confirm.
 * [existing] holds installed setups of the same addon (same manifest id, other settings).
 */
data class PendingAddonInstall(
    val addon: Addon,
    val existing: List<Addon> = emptyList(),
    val fromLink: Boolean = false
)

data class SettingsUiState(
    // Personal build: saved language and appearance preferences cannot override the contract.
    val defaultSubtitle: String = if (com.arflix.tv.BuildConfig.PERSONAL_BUILD) PersonalPrefs.SUBTITLE else "Off",
    val subtitleOptions: List<String> = emptyList(),
    val defaultAudioLanguage: String = "Auto (Original)",
    val audioLanguageOptions: List<String> = emptyList(),
    val frameRateMatchingMode: String = if (com.arflix.tv.BuildConfig.PERSONAL_BUILD) "Always" else "Off",
    val dnsProvider: String = "System DNS",
    val dnsProviderOptions: List<String> = listOf("System DNS", "Cloudflare", "Google", "AdGuard"),
    val customUserAgent: String = "",
    val subtitleSize: String = "Medium",
    val subtitleColor: String = "White",
    val subtitleStyle: String = "Bold",
    val subtitleFont: String = SubtitleFontOption.DefaultPreference,
    val subtitleOffset: String = "Bottom",
    val subtitleStylized: Boolean = true,
    val filterSubtitlesByLanguage: Boolean = true,
    val useForcedSubtitles: Boolean = false,
    val secondarySubtitle: String = "Off",
    val animeStructuringStyle: AnimeStructuringStyle = AnimeStructuringStyle.BROADCAST,
    // Volume boost in decibels (0 = off, up to 15 dB). Applied via system LoudnessEnhancer
    // attached to the ExoPlayer audio session. Issue #88.
    val volumeBoostDb: Int = 0,
    val showLoadingStats: Boolean = true,
    val includeSpecials: Boolean = false,
    val watchlistCount: Int = 0,
    val historyCount: Int = 0,
    // Catalogs
    val catalogs: List<CatalogConfig> = emptyList(),
    val catalogSearchQuery: String = "",
    val catalogSearchResults: List<CatalogDiscoveryResult> = emptyList(),
    val isCatalogSearching: Boolean = false,
    val catalogSearchError: SettingsMessage? = null,
    val pendingPackManifest: CatalogPackManifest? = null,
    val pendingPackUrl: String? = null,
    val isPackLoading: Boolean = false,
    val packError: SettingsMessage? = null,
    // Addons
    val addons: List<Addon> = emptyList(),
    val isRefreshingAddons: Boolean = false,
    val isAddonInstallLoading: Boolean = false,
    val pendingAddonInstall: PendingAddonInstall? = null,
    // Community addon catalog
    val communityAddons: List<CommunityAddon> = emptyList(),
    val isCommunityAddonsLoading: Boolean = false,
    val communityAddonsFailed: Boolean = false,
    /** Transport URLs of community addons being installed or removed right now. */
    val communityAddonBusyUrls: Set<String> = emptySet(),
    val torrServerBaseUrl: String = "",
    // Content language (TMDB metadata)
    val contentLanguage: String = "en-US",
    // Device mode override
    val deviceModeOverride: String = "auto",
    // Skip profile selection
    val oledBlackBackground: Boolean = false,
    val clockFormat: String = "24h",
    // Spoiler blur â€” blur unwatched episode card images and hide synopsis
    val spoilerBlurEnabled: Boolean = false,
    // Accent color — user-selectable theme colour for focus rings, buttons, and selected items
    val accentColor: String = "White",
    // Toast
    val toastMessage: SettingsMessage? = null,
    val toastType: ToastType = ToastType.INFO,
    val subtitlePreloadEnabled: Boolean = true,
    val dolbyVisionCompatEnabled: Boolean = true,
    val smoothScrolling: Boolean = true,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val profileManager: ProfileManager,
    private val streamRepository: StreamRepository,
    private val mediaRepository: MediaRepository,
    private val catalogRepository: CatalogRepository,
    private val catalogDiscoveryRepository: CatalogDiscoveryRepository,
    private val watchlistRepository: WatchlistRepository,
    private val launcherContinueWatchingRepository: LauncherContinueWatchingRepository,
    private val watchHistoryRepository: com.arflix.tv.data.repository.WatchHistoryRepository,
    private val communityAddonRepository: CommunityAddonRepository
) : ViewModel() {


    private fun visibleCatalogs(catalogs: List<CatalogConfig>): List<CatalogConfig> = catalogs

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()
    private val preferenceWriter by lazy {
        SettingsPreferenceWriter(context.settingsDataStore, _uiState, viewModelScope,
            profileId = profileManager::getProfileIdSync,
            onSaved = {},
            onFailure = { error ->
                AppLogger.recordException(error, mapOf("error_area" to "Settings", "action" to "savePreference"))
                _uiState.value = _uiState.value.copy(
                    toastMessage = SettingsMessage.Res(R.string.settings_preference_save_failed),
                    toastType = ToastType.ERROR)
            },
            onSyncFailure = { error ->
                AppLogger.recordException(error, mapOf("error_area" to "Settings", "action" to "syncPreference"))
            },
            onCommitted = {})
    }

    private fun contentLanguageKey() = profileManager.profileStringKey("content_language")

    private fun defaultSubtitleKey() = profileManager.profileStringKey("default_subtitle")
    private fun defaultSubtitleKeyFor(profileId: String) = profileManager.profileStringKeyFor(profileId, "default_subtitle")
    private fun subtitleSettingsUpdatedAtKey() = profileManager.profileStringKey("subtitle_settings_updated_at")
    private fun defaultAudioLanguageKey() = profileManager.profileStringKey("default_audio_language")
    private fun defaultAudioLanguageKeyFor(profileId: String) = profileManager.profileStringKeyFor(profileId, "default_audio_language")
    private fun subtitleUsageKey() = profileManager.profileStringKey("subtitle_usage_v1")
    private fun frameRateMatchingModeKey() = profileManager.profileStringKey("frame_rate_matching_mode")
    private fun frameRateMatchingModeKeyFor(profileId: String) = profileManager.profileStringKeyFor(profileId, "frame_rate_matching_mode")
    private fun animeStructuringStyleKey() = profileManager.profileStringKey(AnimeStructuringStyle.PREFERENCE_KEY)
    private fun clockFormatKey() = profileManager.profileStringKey("clock_format")
    private fun smoothScrollingKey() = profileManager.profileBooleanKey("smooth_scrolling")
    private fun spoilerBlurKey() = profileManager.profileBooleanKey("spoiler_blur")
    // Stored as a string because ProfileManager has no int helper and we only persist
    // a handful of discrete dB values. Parsed back to Int on read.
    private fun volumeBoostDbKey() = profileManager.profileStringKey("volume_boost_db")
    private fun showLoadingStatsKey() = profileManager.profileBooleanKey("show_loading_stats")

    private fun subtitleSizeKey() = profileManager.profileStringKey("subtitle_size")
    private fun subtitleColorKey() = profileManager.profileStringKey("subtitle_color")
    private fun subtitleOffsetKey() = profileManager.profileStringKey("subtitle_offset")
    private fun subtitleStyleKey() = profileManager.profileStringKey("subtitle_style")
    private fun subtitleFontKey() = profileManager.profileStringKey("subtitle_font")
    private fun subtitleStylizedKey() = profileManager.profileBooleanKey("subtitle_stylized")
    private fun filterSubtitlesByLanguageKey() = profileManager.profileBooleanKey("filter_subtitles_by_lang")
    private fun useForcedSubtitlesKey() = profileManager.profileBooleanKey("use_forced_subtitles")
    private fun secondarySubtitleKey() = profileManager.profileStringKey("secondary_subtitle")
    private val dnsProviderKey = stringPreferencesKey(OkHttpProvider.DNS_PROVIDER_PREF_KEY)
    private val customUserAgentKey = stringPreferencesKey(OkHttpProvider.USER_AGENT_PREF_KEY)
    private fun includeSpecialsKey() = profileManager.profileBooleanKey("include_specials")

    private val subtitlePreloadEnabledKey = booleanPreferencesKey("subtitle_preload_enabled")
    private val dolbyVisionCompatKey = booleanPreferencesKey("dolby_vision_compat")
    private fun includeSpecialsKeyFor(profileId: String) = profileManager.profileBooleanKeyFor(profileId, "include_specials")
    private val gson = Gson()

    private var loadSettingsJob: Job? = null
    private var catalogSearchJob: Job? = null
    private var observedProfileId: String? = null





    init {
        loadSettings()
        observeProfileChanges()
        observeAddons()
        observeTorrServer()
        observeCatalogs()
    }



    private fun loadSettings() {
        loadSettingsJob?.cancel()
        loadSettingsJob = viewModelScope.launch {
            val loadProfileId = profileManager.getProfileIdSync()
            // Load local preferences first
            val prefs = context.settingsDataStore.data.first()
            var defaultSub = if (com.arflix.tv.BuildConfig.PERSONAL_BUILD) PersonalPrefs.SUBTITLE else prefs[defaultSubtitleKey()] ?: "Off"
            val defaultAudio = if (com.arflix.tv.BuildConfig.PERSONAL_BUILD) PersonalPrefs.AUDIO else prefs[defaultAudioLanguageKey()] ?: "Auto (Original)"
            if (com.arflix.tv.BuildConfig.PERSONAL_BUILD && prefs[frameRateMatchingModeKey()] == null) {
                context.settingsDataStore.edit { it[frameRateMatchingModeKey()] = "always" }
            }
            val frameRateMode = normalizeFrameRateMode(prefs[frameRateMatchingModeKey()] ?: if (com.arflix.tv.BuildConfig.PERSONAL_BUILD) "always" else null)
            val deviceModeOverride = prefs[com.arflix.tv.util.DEVICE_MODE_OVERRIDE_KEY] ?: "auto"
            val oledBlackBackground = prefs[com.arflix.tv.util.OLED_BLACK_BACKGROUND_KEY] ?: false
            val contentLang = resolveAppLanguage(prefs, loadProfileId)
            // Apply content language to MediaRepository immediately
            mediaRepository.contentLanguage = contentLang
            val spoilerBlurEnabled = prefs[spoilerBlurKey()] ?: false
            val animeStructuringStyle = AnimeStructuringStyle.fromId(prefs[animeStructuringStyleKey()])
            val clockFormat = prefs[clockFormatKey()] ?: "24h"
            // One-time migration: read old "focus_border_color" key if new "accent_color" is absent
            val OLD_FOCUS_BORDER_COLOR_KEY = stringPreferencesKey("focus_border_color")
            val legacyColor = prefs[OLD_FOCUS_BORDER_COLOR_KEY]
            val accentColor = prefs[com.arflix.tv.util.ACCENT_COLOR_KEY] ?: legacyColor ?: "White"
            // Schedule async migration to copy old key → new key and delete old
            if (legacyColor != null) {
                viewModelScope.launch {
                    context.settingsDataStore.edit {
                        val old = it[OLD_FOCUS_BORDER_COLOR_KEY] ?: return@edit
                        it[com.arflix.tv.util.ACCENT_COLOR_KEY] = old
                        it.remove(OLD_FOCUS_BORDER_COLOR_KEY)
                    }
                }
            }
            val volumeBoostDb = prefs[volumeBoostDbKey()]?.toIntOrNull()?.coerceIn(0, 15) ?: 0
            val showLoadingStats = prefs[showLoadingStatsKey()] ?: true
            val smoothScrolling = prefs[smoothScrollingKey()] ?: true

            val subtitleSize = if (com.arflix.tv.BuildConfig.PERSONAL_BUILD) PersonalSubtitles.SIZE else prefs[subtitleSizeKey()] ?: "Medium"
            val subtitleColor = if (com.arflix.tv.BuildConfig.PERSONAL_BUILD) PersonalSubtitles.COLOR else prefs[subtitleColorKey()] ?: "White"
            val subtitleStyle = if (com.arflix.tv.BuildConfig.PERSONAL_BUILD) PersonalSubtitles.STYLE else prefs[subtitleStyleKey()] ?: "Bold"
            val subtitleFont = if (com.arflix.tv.BuildConfig.PERSONAL_BUILD) PersonalSubtitles.FONT else SubtitleFontOption.fromPreference(prefs[subtitleFontKey()]).preferenceValue
            val subtitleOffset = if (com.arflix.tv.BuildConfig.PERSONAL_BUILD) PersonalSubtitles.OFFSET else prefs[subtitleOffsetKey()] ?: "Bottom"
            val subtitleStylized = if (com.arflix.tv.BuildConfig.PERSONAL_BUILD) PersonalSubtitles.STYLIZED else prefs[subtitleStylizedKey()] ?: true
            val filterSubtitlesByLanguage = if (com.arflix.tv.BuildConfig.PERSONAL_BUILD) PersonalSubtitles.FILTER_BY_LANGUAGE else prefs[filterSubtitlesByLanguageKey()] ?: true
            val useForcedSubtitles = if (com.arflix.tv.BuildConfig.PERSONAL_BUILD) PersonalSubtitles.FORCED else prefs[useForcedSubtitlesKey()] ?: false
            val secondarySubtitle = if (com.arflix.tv.BuildConfig.PERSONAL_BUILD) PersonalPrefs.SECONDARY_SUBTITLE else prefs[secondarySubtitleKey()]?.trim()?.takeIf { it.isNotBlank() } ?: "Off"
            val dnsProviderValue = normalizeDnsProviderValue(prefs[dnsProviderKey])
            val customUserAgent = prefs[customUserAgentKey].orEmpty().trim()
            OkHttpProvider.setCustomUserAgent(customUserAgent)
            val includeSpecials = prefs[includeSpecialsKey()] ?: false
            val subtitlePreloadEnabled = if (com.arflix.tv.BuildConfig.PERSONAL_BUILD) PersonalSubtitles.PRELOAD else prefs[subtitlePreloadEnabledKey] ?: true
            val dolbyVisionCompatEnabled = prefs[dolbyVisionCompatKey] ?: true

            // Check auth statuses
            if (profileManager.getProfileIdSync() != loadProfileId) return@launch

            val subtitleOptions = loadSubtitleOptions(defaultSub)
            val audioLanguageOptions = loadAudioLanguageOptions(defaultAudio)
            withContext(Dispatchers.Default) {
                catalogRepository.ensurePreinstalledDefaults(mediaRepository.getDefaultCatalogConfigs())
            }
            val watchlistCount = try {
                withContext(Dispatchers.IO) { watchlistRepository.getLocalWatchlistItems().size }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                0
            }
            val historyCount = try {
                withContext(Dispatchers.IO) { watchHistoryRepository.getContinueWatching().size }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                0
            }

            if (profileManager.getProfileIdSync() != loadProfileId) return@launch
            val currentState = _uiState.value
            _uiState.value = currentState.copy(
                defaultSubtitle = defaultSub,
                subtitleOptions = subtitleOptions,
                defaultAudioLanguage = defaultAudio,
                audioLanguageOptions = audioLanguageOptions,
                frameRateMatchingMode = frameRateMode,
                animeStructuringStyle = animeStructuringStyle,
                volumeBoostDb = volumeBoostDb,
                showLoadingStats = showLoadingStats,

                subtitleSize = subtitleSize,
                subtitleColor = subtitleColor,
                subtitleStyle = subtitleStyle,
                subtitleFont = subtitleFont,
                subtitleOffset = subtitleOffset,
                subtitleStylized = subtitleStylized,
                filterSubtitlesByLanguage = filterSubtitlesByLanguage,
                useForcedSubtitles = useForcedSubtitles,
                secondarySubtitle = secondarySubtitle,
                dnsProvider = dnsProviderLabel(dnsProviderValue),
                customUserAgent = customUserAgent,
                includeSpecials = includeSpecials,
                spoilerBlurEnabled = spoilerBlurEnabled,
                watchlistCount = watchlistCount,
                historyCount = historyCount,



                contentLanguage = contentLang,
                deviceModeOverride = deviceModeOverride,
                oledBlackBackground = oledBlackBackground,
                clockFormat = clockFormat,
                accentColor = accentColor,
                subtitlePreloadEnabled = subtitlePreloadEnabled,
                dolbyVisionCompatEnabled = dolbyVisionCompatEnabled,
                smoothScrolling = smoothScrolling,
            )

        }
    }

    private fun observeProfileChanges() {
        viewModelScope.launch {
            profileManager.activeProfileId.collect { profileId ->
                if (observedProfileId == profileId) return@collect
                observedProfileId = profileId
                loadSettings()
            }
        }
    }

    fun refreshSubtitleOptions() {
        viewModelScope.launch {
            val options = loadSubtitleOptions(_uiState.value.defaultSubtitle)
            if (_uiState.value.subtitleOptions != options) {
                _uiState.value = _uiState.value.copy(subtitleOptions = options)
            }
        }
    }

    fun refreshAudioLanguageOptions() {
        viewModelScope.launch {
            val options = loadAudioLanguageOptions(_uiState.value.defaultAudioLanguage)
            if (_uiState.value.audioLanguageOptions != options) {
                _uiState.value = _uiState.value.copy(audioLanguageOptions = options)
            }
        }
    }

    private fun observeAddons() {
        viewModelScope.launch {
            streamRepository.installedAddons.collect { addons ->
                runCatching {
                    withContext(Dispatchers.Default) { catalogRepository.syncAddonCatalogs(addons) }
                }
                if (_uiState.value.addons != addons) {
                    _uiState.value = _uiState.value.copy(addons = addons)
                }
            }
        }
    }

    private fun observeTorrServer() {
        viewModelScope.launch {
            streamRepository.observeTorrServerBaseUrl().collect { url ->
                if (_uiState.value.torrServerBaseUrl != url) {
                    _uiState.value = _uiState.value.copy(torrServerBaseUrl = url)
                }
            }
        }
    }






    fun setDefaultSubtitle(language: String) {
        viewModelScope.launch {
            // Save locally
            val changedAt = System.currentTimeMillis()
            context.settingsDataStore.edit { prefs ->
                prefs[defaultSubtitleKey()] = language
                prefs[subtitleSettingsUpdatedAtKey()] = changedAt.toString()
            }
            _uiState.value = _uiState.value.copy(
                defaultSubtitle = language,
                subtitleOptions = loadSubtitleOptions(language)
            )

            // Sync to cloud
        }
    }

    fun setDefaultAudioLanguage(language: String) {
        viewModelScope.launch {
            context.settingsDataStore.edit { prefs ->
                prefs[defaultAudioLanguageKey()] = language
            }
            _uiState.value = _uiState.value.copy(
                defaultAudioLanguage = language,
                audioLanguageOptions = loadAudioLanguageOptions(language)
            )
        }
    }

    private suspend fun loadSubtitleOptions(current: String): List<String> {
        val prefs = context.settingsDataStore.data.first()
        val json = prefs[subtitleUsageKey()]
        val type = TypeToken.getParameterized(Map::class.java, String::class.java, Int::class.javaObjectType).type
        val usage: Map<String, Int> = if (!json.isNullOrBlank()) {
            gson.fromJson(json, type)
        } else {
            emptyMap()
        }

        val topUsed = usage.entries
            .sortedByDescending { it.value }
            .map { entry -> displayLanguage(entry.key) }
            .filter { it.isNotBlank() }
            .take(30)

        // Keep this list >= 25 items; this is the "always available" picker list.
        val defaults = listOf(
            "English",
            "Arabic",
            "Bengali",
            "Bulgarian",
            "Chinese",
            "Croatian",
            "Czech",
            "Danish",
            "Dutch",
            "Estonian",
            "Finnish",
            "French",
            "German",
            "Greek",
            "Gujarati",
            "Hebrew",
            "Hindi",
            "Hungarian",
            "Indonesian",
            "Italian",
            "Japanese",
            "Kannada",
            "Korean",
            "Lithuanian",
            "Malay",
            "Malayalam",
            "Marathi",
            "Norwegian",
            "Persian",
            "Polish",
            "Portuguese",
            "Portuguese (Brazil)",
            "Punjabi",
            "Romanian",
            "Russian",
            "Serbian",
            "Slovak",
            "Slovenian",
            "Spanish",
            "Swedish",
            "Tamil",
            "Telugu",
            "Thai",
            "Turkish",
            "Ukrainian",
            "Vietnamese"
        )
        val base = buildList {
            add("Off")
            add("Forced")
            if (current.isNotBlank() && current != "Off" && current != "Forced") add(current)
            addAll(topUsed)
            addAll(defaults)
        }

        return base.distinct().take(60)
    }

    private fun loadAudioLanguageOptions(current: String): List<String> {
        val defaults = listOf(
            "Auto (Original)",
            "None",
            "English",
            "Arabic",
            "Bengali",
            "Bulgarian",
            "Chinese",
            "Croatian",
            "Czech",
            "Danish",
            "Dutch",
            "Estonian",
            "Finnish",
            "French",
            "German",
            "Greek",
            "Gujarati",
            "Hebrew",
            "Hindi",
            "Hungarian",
            "Indonesian",
            "Italian",
            "Japanese",
            "Kannada",
            "Korean",
            "Lithuanian",
            "Malayalam",
            "Marathi",
            "Norwegian",
            "Persian",
            "Polish",
            "Portuguese",
            "Portuguese (Brazil)",
            "Punjabi",
            "Romanian",
            "Russian",
            "Serbian",
            "Slovak",
            "Slovenian",
            "Spanish",
            "Swedish",
            "Tamil",
            "Telugu",
            "Thai",
            "Turkish",
            "Ukrainian",
            "Vietnamese"
        )
        return buildList {
            if (current.isNotBlank()) add(current)
            addAll(defaults)
        }.distinct().take(60)
    }

    private fun displayLanguage(code: String): String {
        val normalized = code.trim()
        if (normalized.isBlank()) return ""
        val isCode = normalized.length <= 3 && normalized.all { it.isLetter() }
        if (!isCode) return normalized.replaceFirstChar { it.uppercase() }
        val locale = java.util.Locale(normalized)
        val name = locale.getDisplayLanguage(java.util.Locale.ENGLISH)
        return if (name.isNullOrBlank()) normalized else name
    }





    fun setSecondarySubtitle(language: String) {
        viewModelScope.launch {
            context.settingsDataStore.edit { prefs ->
                prefs[secondarySubtitleKey()] = language
            }
            _uiState.value = _uiState.value.copy(secondarySubtitle = language)
        }
    }

    fun setFilterSubtitlesByLanguage(enabled: Boolean) {
        preferenceWriter.save(filterSubtitlesByLanguageKey(), enabled, { it.filterSubtitlesByLanguage }, { state, value -> state.copy(filterSubtitlesByLanguage = value) })
    }

    fun setUseForcedSubtitles(enabled: Boolean) {
        preferenceWriter.save(useForcedSubtitlesKey(), enabled, { it.useForcedSubtitles }, { state, value -> state.copy(useForcedSubtitles = value) })
    }











    /** Set content/metadata language for TMDB (e.g. "en-US", "fr-FR", "nl-NL"). */
    fun setContentLanguage(lang: String) {
        viewModelScope.launch {
            context.settingsDataStore.edit { prefs ->
                prefs[contentLanguageKey()] = lang
                prefs[LAST_APP_LANGUAGE_KEY] = lang
            }
            // Mirror to SharedPreferences so attachBaseContext can read it synchronously on next launch
            context.getSharedPreferences("app_locale", android.content.Context.MODE_PRIVATE)
                .edit().putString("locale_tag", lang).apply()
            mediaRepository.contentLanguage = lang
            _uiState.value = _uiState.value.copy(contentLanguage = lang)

            // Refresh the Launcher "Keep watching" with the new language
            launcherContinueWatchingRepository.refreshForCurrentProfile()
        }
    }

    /** Set UI mode override: "auto", "tv", "tablet", "phone". Requires app restart. */
    fun setDeviceModeOverride(mode: String) {
        viewModelScope.launch {
            context.settingsDataStore.edit { prefs ->
                prefs[com.arflix.tv.util.DEVICE_MODE_OVERRIDE_KEY] = mode
            }
            // Mirror to SharedPreferences so the next cold start's
            // pre-onCreate detectDeviceType() read picks it up synchronously.
            com.arflix.tv.util.setDeviceModeOverrideCache(
                context,
                if (mode == "auto") null else mode,
            )
            _uiState.value = _uiState.value.copy(deviceModeOverride = mode)
        }
    }



    fun setOledBlackBackground(enabled: Boolean) {
        preferenceWriter.save(com.arflix.tv.util.OLED_BLACK_BACKGROUND_KEY, enabled, { it.oledBlackBackground }, { state, value -> state.copy(oledBlackBackground = value) })
    }

    fun cycleFrameRateMatchingMode() {
        val current = normalizeFrameRateMode(_uiState.value.frameRateMatchingMode)
        val next = when (current) {
            "Off" -> "Seamless only"
            "Seamless only" -> "Always"
            else -> "Off"
        }
        setFrameRateMatchingMode(next)
    }

    fun setFrameRateMatchingMode(mode: String) {
        val normalized = normalizeFrameRateMode(mode)
        preferenceWriter.save(frameRateMatchingModeKey(), normalized, { it.frameRateMatchingMode }, { state, value -> state.copy(frameRateMatchingMode = value) })
    }

    private fun normalizeFrameRateMode(raw: String?): String {
        return when (raw?.trim()?.lowercase()) {
            "off" -> "Off"
            "seamless", "seamless only", "only if seamless", "only_if_seamless" -> "Seamless only"
            "always" -> "Always"
            else -> "Off"
        }
    }



    fun setSpoilerBlurEnabled(enabled: Boolean) {
        preferenceWriter.save(spoilerBlurKey(), enabled, { it.spoilerBlurEnabled }, { state, value -> state.copy(spoilerBlurEnabled = value) })
    }

















    fun setAnimeStructuringStyle(style: AnimeStructuringStyle) {
        viewModelScope.launch {
            context.settingsDataStore.edit { it[animeStructuringStyleKey()] = style.id }
            _uiState.value = _uiState.value.copy(animeStructuringStyle = style)
        }
    }

    fun cycleAnimeStructuringStyle() {
        val current = _uiState.value.animeStructuringStyle
        val next = if (current == AnimeStructuringStyle.BROADCAST) AnimeStructuringStyle.STANDARD else AnimeStructuringStyle.BROADCAST
        setAnimeStructuringStyle(next)
    }

    fun setSmoothScrolling(enabled: Boolean) {
        preferenceWriter.save(smoothScrollingKey(), enabled, { it.smoothScrolling }, { state, value -> state.copy(smoothScrolling = value) })
    }







    fun setShowLoadingStats(enabled: Boolean) {
        preferenceWriter.save(showLoadingStatsKey(), enabled, { it.showLoadingStats }, { state, value -> state.copy(showLoadingStats = value) })
    }

    fun cycleClockFormat() {
        val next = if (_uiState.value.clockFormat == "24h") "12h" else "24h"
        preferenceWriter.save(clockFormatKey(), next, { it.clockFormat }, { state, value -> state.copy(clockFormat = value) })
    }

    /**
     * Cycle the accent color through the rainbow palette.
     * Order: White → Red → Orange → Yellow → Green → Blue → Indigo → Violet → White
     */
    fun cycleAccentColor() {
        val colors = listOf("White", "Red", "Orange", "Yellow", "Green", "Blue", "Indigo", "Violet")
        val current = _uiState.value.accentColor
        val nextIndex = (colors.indexOf(current) + 1) % colors.size
        val next = colors[nextIndex]
        viewModelScope.launch {
            context.settingsDataStore.edit { it[com.arflix.tv.util.ACCENT_COLOR_KEY] = next }
            _uiState.value = _uiState.value.copy(accentColor = next)
        }
    }

    /**
     * Cycle the volume boost through discrete dB steps: 0 -> 3 -> 6 -> 9 -> 12 -> 15 -> 0.
     * 0 dB = LoudnessEnhancer disabled (no overhead, no clipping). Above +12 dB is
     * cropped to +15 dB since higher values tend to introduce audible distortion on
     * streaming content with already-compressed audio. Issue #88.
     */
    fun cycleVolumeBoost() {
        val current = _uiState.value.volumeBoostDb
        val next = when {
            current < 3 -> 3
            current < 6 -> 6
            current < 9 -> 9
            current < 12 -> 12
            current < 15 -> 15
            else -> 0
        }
        preferenceWriter.save(volumeBoostDbKey(), next.toString(), { it.volumeBoostDb.toString() }, { state, value -> state.copy(volumeBoostDb = value.toInt()) })
    }

    fun cycleSubtitleSize() {
        val next = when (_uiState.value.subtitleSize) { "Small" -> "Medium"; "Medium" -> "Large"; "Large" -> "Extra Large"; else -> "Small" }
        preferenceWriter.save(subtitleSizeKey(), next, { it.subtitleSize }, { state, value -> state.copy(subtitleSize = value) })
    }

    fun cycleSubtitleColor() {
        val next = when (_uiState.value.subtitleColor) { "White" -> "Yellow"; "Yellow" -> "Green"; "Green" -> "Cyan"; else -> "White" }
        preferenceWriter.save(subtitleColorKey(), next, { it.subtitleColor }, { state, value -> state.copy(subtitleColor = value) })
    }

    fun cycleSubtitleOffset() {
        val next = when (_uiState.value.subtitleOffset) { "Bottom" -> "Low"; "Low" -> "Medium"; "Medium" -> "High"; else -> "Bottom" }
        preferenceWriter.save(subtitleOffsetKey(), next, { it.subtitleOffset }, { state, value -> state.copy(subtitleOffset = value) })
    }

    fun cycleSubtitleStyle() {
        val next = when (_uiState.value.subtitleStyle) { "Bold" -> "Normal"; "Normal" -> "Background"; else -> "Bold" }
        preferenceWriter.save(subtitleStyleKey(), next, { it.subtitleStyle }, { state, value -> state.copy(subtitleStyle = value) })
    }

    fun cycleSubtitleFont() {
        val next = SubtitleFontOption.nextPreference(_uiState.value.subtitleFont)
        preferenceWriter.save(subtitleFontKey(), next, { it.subtitleFont }, { state, value -> state.copy(subtitleFont = value) })
    }

    fun toggleSubtitleStylized() {
        val next = !_uiState.value.subtitleStylized
        preferenceWriter.save(subtitleStylizedKey(), next, { it.subtitleStylized }, { state, value -> state.copy(subtitleStylized = value) })
    }

    fun setSubtitlePreloadEnabled(enabled: Boolean) {
        viewModelScope.launch {
            context.settingsDataStore.edit { it[subtitlePreloadEnabledKey] = enabled }
            _uiState.value = _uiState.value.copy(subtitlePreloadEnabled = enabled)
        }
    }

    fun setDolbyVisionCompatEnabled(enabled: Boolean) {
        viewModelScope.launch {
            context.settingsDataStore.edit { it[dolbyVisionCompatKey] = enabled }
            _uiState.value = _uiState.value.copy(dolbyVisionCompatEnabled = enabled)
        }
    }

    private fun normalizeDnsProviderValue(raw: String?): String {
        return when (raw?.trim()?.lowercase()) {
            "system", "system dns", "system_dns" -> "system"
            "cloudflare", "cloudflare dns", "cloudflare_dns" -> "cloudflare"
            "google" -> "google"
            "adguard", "ad guard" -> "adguard"
            else -> "system"
        }
    }

    private fun dnsProviderLabel(value: String): String {
        return when (normalizeDnsProviderValue(value)) {
            "system" -> "System DNS"
            "google" -> "Google"
            "adguard" -> "AdGuard"
            else -> "Cloudflare"
        }
    }

    private fun dnsProviderValueFromLabel(label: String): String {
        return when (label.trim().lowercase()) {
            "system dns" -> "system"
            "google" -> "google"
            "adguard" -> "adguard"
            else -> "cloudflare"
        }
    }

    fun setDnsProvider(label: String) {
        val value = dnsProviderValueFromLabel(label)
        viewModelScope.launch {
            val currentValue = dnsProviderValueFromLabel(_uiState.value.dnsProvider)
            if (value == currentValue) {
                return@launch
            }

            withContext(Dispatchers.IO) {
                OkHttpProvider.setDnsProvider(OkHttpProvider.parseDnsProvider(value))
                // Warm up the new DNS provider's lazy init off the main thread
                // so the first image request doesn't block
                runCatching { OkHttpProvider.dns.lookup("image.tmdb.org") }
            }
            context.settingsDataStore.edit { prefs ->
                prefs[dnsProviderKey] = value
            }
            _uiState.value = _uiState.value.copy(
                dnsProvider = dnsProviderLabel(value)
            )

            // Replace Coil image loader with one using the new DNS
            val imageLoader = withContext(Dispatchers.IO) {
                OkHttpProvider.createCoilImageLoader(context)
            }
            Coil.setImageLoader(imageLoader)
        }
    }

    fun setCustomUserAgent(value: String) {
        val trimmed = value.trim()
        viewModelScope.launch {
            context.settingsDataStore.edit { prefs ->
                if (trimmed.isBlank()) {
                    prefs.remove(customUserAgentKey)
                } else {
                    prefs[customUserAgentKey] = trimmed
                }
            }
            OkHttpProvider.setCustomUserAgent(trimmed)
            _uiState.value = _uiState.value.copy(
                customUserAgent = trimmed
            )
        }
    }

    fun setIncludeSpecials(enabled: Boolean) {
        viewModelScope.launch {
            context.settingsDataStore.edit { prefs ->
                prefs[includeSpecialsKey()] = enabled
            }
            _uiState.value = _uiState.value.copy(includeSpecials = enabled)
        }
    }















    // ========== Addon Management ==========

    @Deprecated("Addon order is the install order.")
    fun moveAddonUp(addonId: String) {
        viewModelScope.launch {
            streamRepository.moveAddonUp(addonId)
        }
    }

    @Deprecated("Addon order is the install order.")
    fun moveAddonDown(addonId: String) {
        viewModelScope.launch {
            streamRepository.moveAddonDown(addonId)
        }
    }

    fun toggleAddon(addonId: String) {
        viewModelScope.launch {
            val addon = streamRepository.installedAddons.first().firstOrNull { it.id == addonId }
            if (addon != null && addon.needsConfiguration && !addon.isEnabled) {
                _uiState.value = _uiState.value.copy(
                    toastMessage = SettingsMessage.Res(R.string.settings_addon_setup_first, listOf(addon.name)),
                    toastType = ToastType.INFO
                )
                return@launch
            }
            streamRepository.toggleAddon(addonId)
            val addonsAfterToggle = streamRepository.installedAddons.first()
            runCatching {
                catalogRepository.syncAddonCatalogs(addonsAfterToggle)
            }
        }
    }

    fun addCustomAddon(url: String) {
        requestAddonInstall(url, fromLink = false)
    }

    private var addonInstallJob: Job? = null

    /**
     * Fetch the addon behind [url] and install it. A link opened from a website always asks
     * first; so does any install that would add a second setup of an addon already installed.
     */
    fun requestAddonInstall(url: String, fromLink: Boolean) {
        addonInstallJob?.cancel()
        addonInstallJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isAddonInstallLoading = fromLink,
                pendingAddonInstall = null
            )
            val result = streamRepository.prepareCustomAddon(url)
            _uiState.value = _uiState.value.copy(isAddonInstallLoading = false)
            result.onSuccess { addon ->
                val manifestId = addon.manifest?.id
                val existing = if (manifestId == null) {
                    emptyList()
                } else {
                    streamRepository.installedAddons.first()
                        .filter { it.manifest?.id == manifestId && it.id != addon.id }
                }
                if (fromLink || existing.isNotEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        pendingAddonInstall = PendingAddonInstall(addon, existing, fromLink)
                    )
                } else {
                    installAddon(addon, replaceAddonIds = emptySet())
                }
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    toastMessage = error.message.orMessage(
                        SettingsMessage.Res(R.string.addon_failed_add)
                    ),
                    toastType = ToastType.ERROR
                )
            }
        }
    }

    /** Install the pending addon; [replaceExisting] removes the older setups of it. */
    fun confirmAddonInstall(replaceExisting: Boolean) {
        val pending = _uiState.value.pendingAddonInstall ?: return
        _uiState.value = _uiState.value.copy(pendingAddonInstall = null)
        viewModelScope.launch {
            val replaceIds = if (replaceExisting) pending.existing.map { it.id }.toSet() else emptySet()
            installAddon(pending.addon, replaceIds)
        }
    }

    fun cancelAddonInstall() {
        addonInstallJob?.cancel()
        _uiState.value = _uiState.value.copy(pendingAddonInstall = null, isAddonInstallLoading = false)
    }

    private suspend fun installAddon(addon: Addon, replaceAddonIds: Set<String>) {
        runCatching { streamRepository.installPreparedAddon(addon, replaceAddonIds) }
            .onSuccess {
                // Small delay to let DataStore flush the write before reading back
                delay(150)
                val currentAddons = streamRepository.installedAddons.first()
                val importedCatalogs = addon.manifest?.catalogs?.size ?: 0
                runCatching {
                    catalogRepository.syncAddonCatalogs(currentAddons)
                }
                _uiState.value = _uiState.value.copy(
                    addons = currentAddons,
                    toastMessage = if (importedCatalogs > 0) {
                        SettingsMessage.Res(
                            R.string.settings_addon_added_with_catalogs,
                            listOf(addon.name, importedCatalogs)
                        )
                    } else {
                        SettingsMessage.Res(
                            R.string.settings_addon_added_no_catalogs,
                            listOf(addon.name)
                        )
                    },
                    toastType = ToastType.SUCCESS
                )
            }.onFailure { error ->
                if (error is kotlinx.coroutines.CancellationException) throw error
                _uiState.value = _uiState.value.copy(
                    toastMessage = error.message.orMessage(
                        SettingsMessage.Res(R.string.addon_failed_add)
                    ),
                    toastType = ToastType.ERROR
                )
            }
    }

    fun loadCommunityAddons(forceRefresh: Boolean = false) {
        if (_uiState.value.isCommunityAddonsLoading) return
        _uiState.value = _uiState.value.copy(isCommunityAddonsLoading = true, communityAddonsFailed = false)
        viewModelScope.launch {
            communityAddonRepository.getCommunityAddons(forceRefresh)
                .onSuccess { addons ->
                    _uiState.value = _uiState.value.copy(
                        communityAddons = addons,
                        isCommunityAddonsLoading = false
                    )
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(
                        isCommunityAddonsLoading = false,
                        communityAddonsFailed = true
                    )
                }
        }
    }

    /** Install a community addon straight away: it was picked from a list, not opened from a link. */
    fun installCommunityAddon(entry: CommunityAddon) {
        if (!markCommunityAddonBusy(entry)) return
        viewModelScope.launch {
            try {
                streamRepository.prepareCustomAddon(entry.transportUrl)
                    .onSuccess { addon -> installAddon(addon, replaceAddonIds = emptySet()) }
                    .onFailure { error ->
                        _uiState.value = _uiState.value.copy(
                            toastMessage = error.message.orMessage(
                                SettingsMessage.Res(R.string.addon_failed_add)
                            ),
                            toastType = ToastType.ERROR
                        )
                    }
            } finally {
                clearCommunityAddonBusy(entry)
            }
        }
    }

    /** Remove every installed setup of a community addon. */
    fun uninstallCommunityAddon(entry: CommunityAddon) {
        if (!markCommunityAddonBusy(entry)) return
        viewModelScope.launch {
            try {
                val setups = entry.installedSetups(streamRepository.installedAddons.first())
                    .filter { it.isRemovable }
                if (setups.isEmpty()) return@launch
                setups.forEach { streamRepository.removeAddon(it.id) }
                runCatching {
                    catalogRepository.syncAddonCatalogs(streamRepository.installedAddons.first())
                }
                _uiState.value = _uiState.value.copy(
                    toastMessage = SettingsMessage.Res(R.string.settings_community_addon_removed, listOf(entry.name)),
                    toastType = ToastType.SUCCESS
                )
            } finally {
                clearCommunityAddonBusy(entry)
            }
        }
    }

    private fun markCommunityAddonBusy(entry: CommunityAddon): Boolean {
        val busy = _uiState.value.communityAddonBusyUrls
        if (entry.transportUrl in busy) return false
        _uiState.value = _uiState.value.copy(communityAddonBusyUrls = busy + entry.transportUrl)
        return true
    }

    private fun clearCommunityAddonBusy(entry: CommunityAddon) {
        _uiState.value = _uiState.value.copy(
            communityAddonBusyUrls = _uiState.value.communityAddonBusyUrls - entry.transportUrl
        )
    }

    fun refreshAddons() {
        if (_uiState.value.isRefreshingAddons) return
        _uiState.value = _uiState.value.copy(isRefreshingAddons = true)
        viewModelScope.launch {
            try {

                val report = streamRepository.refreshInstalledAddons()
                val updatedAddons = streamRepository.installedAddons.first()
                runCatching {
                    catalogRepository.syncAddonCatalogs(updatedAddons)
                }
                val toast = SettingsMessage.Res(
                    R.string.settings_addons_refresh_report,
                    listOf(report.refreshed, report.failed)
                )
                _uiState.value = _uiState.value.copy(
                    addons = updatedAddons,
                    isRefreshingAddons = false,
                    toastMessage = toast,
                    toastType = if (report.failed == 0) ToastType.SUCCESS else ToastType.INFO
                )
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _uiState.value = _uiState.value.copy(
                    isRefreshingAddons = false,
                    toastMessage = SettingsMessage.Res(R.string.settings_addons_refresh_failed),
                    toastType = ToastType.ERROR
                )
            }
        }
    }





    private fun observeCatalogs() {
        viewModelScope.launch {
            catalogRepository.observeCatalogs().collect { catalogs ->
                val visible = visibleCatalogs(catalogs)
                if (_uiState.value.catalogs != visible) {
                    _uiState.value = _uiState.value.copy(catalogs = visible)
                }
            }
        }
    }



    fun loadPackManifest(url: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isPackLoading = true,
                packError = null,
                pendingPackManifest = null,
                pendingPackUrl = null
            )
            val result = catalogRepository.fetchCatalogPackManifest(url)
            result.onSuccess { manifest ->
                _uiState.value = _uiState.value.copy(
                    isPackLoading = false,
                    pendingPackManifest = manifest,
                    pendingPackUrl = url
                )
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    isPackLoading = false,
                    packError = error.orCatalogMessage(
                        SettingsMessage.Res(R.string.settings_pack_load_failed)
                    ),
                    pendingPackUrl = null
                )
            }
        }
    }

    fun clearPendingPack() {
        _uiState.value = _uiState.value.copy(
            pendingPackManifest = null,
            pendingPackUrl = null,
            isPackLoading = false,
            packError = null
        )
    }

    fun confirmInstallPack(url: String) {
        val manifest = _uiState.value.pendingPackManifest
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isPackLoading = true,
                packError = null
            )
            val result = catalogRepository.addCatalogPack(url, manifest)
            result.onSuccess { installedManifest ->
                _uiState.value = _uiState.value.copy(
                    isPackLoading = false,
                    pendingPackManifest = null,
                    pendingPackUrl = null,
                    toastMessage = SettingsMessage.Res(
                        R.string.settings_pack_installed,
                        listOf(installedManifest.name.orEmpty())
                    ),
                    toastType = ToastType.SUCCESS
                )
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    isPackLoading = false,
                    packError = error.orCatalogMessage(
                        SettingsMessage.Res(R.string.settings_pack_install_failed)
                    )
                )
            }
        }
    }


    fun removeCatalogPack(packId: String) {
        viewModelScope.launch {
            val result = catalogRepository.removeCatalogPack(packId)
            result.onSuccess {
                _uiState.value = _uiState.value.copy(
                    toastMessage = SettingsMessage.Res(R.string.settings_pack_removed),
                    toastType = ToastType.SUCCESS
                )
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    toastMessage = error.orCatalogMessage(
                        SettingsMessage.Res(R.string.settings_pack_remove_failed)
                    ),
                    toastType = ToastType.ERROR
                )
            }
        }
    }

    fun addCatalog(url: String) {
        // An addon manifest also brings catalogs, so it is a natural thing to paste
        // here. Install it as an addon rather than rejecting it as a bad catalog URL.
        if (CatalogUrlParser.isStremioManifestUrl(url)) {
            requestAddonInstall(url, fromLink = false)
            return
        }

        viewModelScope.launch {
            val result = catalogRepository.addCustomCatalog(url)
            result.onSuccess { catalog ->
                _uiState.value = _uiState.value.copy(
                    toastMessage = SettingsMessage.Res(
                        R.string.settings_catalog_added,
                        listOf(catalog.title)
                    ),
                    toastType = ToastType.SUCCESS
                )
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    toastMessage = error.orCatalogMessage(
                        SettingsMessage.Res(R.string.catalog_failed_add)
                    ),
                    toastType = ToastType.ERROR
                )
            }
        }
    }


    fun setCatalogSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(
            catalogSearchQuery = query,
            catalogSearchError = null
        )
    }

    fun searchCatalogLists(query: String = _uiState.value.catalogSearchQuery) {
        val normalizedQuery = query.trim()
        catalogSearchJob?.cancel()
        if (normalizedQuery.length < 2) {
            _uiState.value = _uiState.value.copy(
                catalogSearchResults = emptyList(),
                isCatalogSearching = false,
                catalogSearchError = if (normalizedQuery.isBlank()) {
                    null
                } else {
                    SettingsMessage.Res(R.string.settings_catalog_search_min_chars)
                }
            )
            return
        }

        catalogSearchJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isCatalogSearching = true,
                catalogSearchError = null
            )
            val result = catalogDiscoveryRepository.searchCatalogLists(normalizedQuery)
            result.onSuccess { lists ->
                _uiState.value = _uiState.value.copy(
                    catalogSearchResults = lists,
                    isCatalogSearching = false,
                    catalogSearchError = if (lists.isEmpty()) {
                        SettingsMessage.Res(R.string.settings_catalog_search_no_lists)
                    } else {
                        null
                    }
                )
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    catalogSearchResults = emptyList(),
                    isCatalogSearching = false,
                    catalogSearchError = error.message.orMessage(
                        SettingsMessage.Res(R.string.catalog_failed_search)
                    )
                )
            }
        }
    }

    fun clearCatalogDiscovery() {
        catalogSearchJob?.cancel()
        catalogSearchJob = null
        _uiState.value = _uiState.value.copy(
            catalogSearchQuery = "",
            catalogSearchResults = emptyList(),
            isCatalogSearching = false,
            catalogSearchError = null
        )
    }

    fun addDiscoveredCatalog(result: CatalogDiscoveryResult) {
        viewModelScope.launch {
            val addResult = catalogRepository.addCustomCatalog(result.sourceUrl)
            addResult.onSuccess { catalog ->
                _uiState.value = _uiState.value.copy(
                    toastMessage = SettingsMessage.Res(
                        R.string.settings_catalog_added,
                        listOf(catalog.title)
                    ),
                    toastType = ToastType.SUCCESS
                )
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    toastMessage = error.orCatalogMessage(
                        SettingsMessage.Res(R.string.catalog_failed_add)
                    ),
                    toastType = ToastType.ERROR
                )
            }
        }
    }

    fun updateCatalog(catalogId: String, url: String) {
        viewModelScope.launch {
            val result = catalogRepository.updateCustomCatalog(catalogId, url)
            result.onSuccess { catalog ->
                _uiState.value = _uiState.value.copy(
                    toastMessage = SettingsMessage.Res(
                        R.string.settings_catalog_updated,
                        listOf(catalog.title)
                    ),
                    toastType = ToastType.SUCCESS
                )
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    toastMessage = error.orCatalogMessage(
                        SettingsMessage.Res(R.string.catalog_failed_update)
                    ),
                    toastType = ToastType.ERROR
                )
            }
        }
    }

    fun removeCatalog(catalogId: String) {
        viewModelScope.launch {
            val result = catalogRepository.removeCustomCatalog(catalogId)
            result.onSuccess {
                // Refresh the catalog list in UI state after removal
                val updatedCatalogs = visibleCatalogs(catalogRepository.getCatalogs())
                _uiState.value = _uiState.value.copy(
                    catalogs = updatedCatalogs,
                    toastMessage = SettingsMessage.Res(R.string.settings_catalog_removed),
                    toastType = ToastType.SUCCESS
                )
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    toastMessage = error.orCatalogMessage(
                        SettingsMessage.Res(R.string.catalog_failed_remove)
                    ),
                    toastType = ToastType.ERROR
                )
            }
        }
    }

    fun unpackCatalog(catalogId: String) {
        viewModelScope.launch {
            val current = catalogRepository.getCatalogs()
            val index = current.indexOfFirst { it.id == catalogId }
            if (index != -1) {
                val target = current[index]
                if (target.packId != null) {
                    val updated = current.toMutableList()
                    updated[index] = target.copy(packId = null, packName = null)
                    catalogRepository.replaceCatalogsForActiveProfile(updated)

                    // Update state
                    val visible = visibleCatalogs(updated)
                    _uiState.value = _uiState.value.copy(
                        catalogs = visible,
                        toastMessage = SettingsMessage.Res(R.string.settings_catalog_unpacked),
                        toastType = ToastType.SUCCESS
                    )
                }
            }
        }
    }

    fun renameCatalog(catalogId: String, newTitle: String) {
        viewModelScope.launch {
            val success = catalogRepository.renameCatalog(catalogId, newTitle)
            if (success) {
            }
        }
    }

    fun moveCatalogUp(catalogId: String) {
        viewModelScope.launch {
            catalogRepository.moveCatalogUp(catalogId)
        }
    }

    fun moveCatalogDown(catalogId: String) {
        viewModelScope.launch {
            catalogRepository.moveCatalogDown(catalogId)
        }
    }

    fun removeAddon(addonId: String) {
        viewModelScope.launch {
            streamRepository.removeAddon(addonId)
            val addonsAfterRemove = streamRepository.installedAddons.first()
            runCatching {
                catalogRepository.syncAddonCatalogs(addonsAfterRemove)
            }
        }
    }

    fun setTorrServerBaseUrl(url: String) {
        viewModelScope.launch {
            streamRepository.setTorrServerBaseUrl(url)
        }
    }





































    fun dismissToast() {
        _uiState.value = _uiState.value.copy(toastMessage = null)
    }



    override fun onCleared() {
        super.onCleared()
    }
}
