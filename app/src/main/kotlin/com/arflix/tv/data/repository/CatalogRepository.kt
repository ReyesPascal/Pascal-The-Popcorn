package com.arflix.tv.data.repository

import android.content.Context
import androidx.annotation.StringRes
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.edit
import com.arflix.tv.data.model.Addon
import com.arflix.tv.data.model.AddonCatalog
import com.arflix.tv.data.model.AddonType
import com.arflix.tv.data.model.CatalogConfig
import com.arflix.tv.data.model.CatalogKind
import com.arflix.tv.data.model.CatalogPackManifest
import com.arflix.tv.data.model.CatalogPackItem
import com.arflix.tv.data.model.CatalogSourceType
import com.arflix.tv.data.model.CatalogValidationResult
import com.arflix.tv.data.model.Category
import com.arflix.tv.R
import com.arflix.tv.util.CatalogUrlParser
import com.arflix.tv.util.Constants
import com.arflix.tv.util.AppLogger
import com.arflix.tv.util.ParsedCatalogUrl
import com.arflix.tv.util.settingsDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import com.arflix.tv.network.OkHttpProvider
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Failure of a catalog operation, carrying the string resource instead of a
 * finished message.
 *
 * The repository only has the application context, whose resources follow the
 * system language rather than the language selected in the app, so the text is
 * resolved at the @Composable display point (see SettingsScreen).
 */
class CatalogException(
    @param:StringRes val messageRes: Int,
    val formatArgs: List<Any> = emptyList()
) : Exception()

@Singleton
class CatalogRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val profileManager: ProfileManager,
    private val okHttpClient: OkHttpClient,
) {
    private val bundledPreinstalledCatalogsById by lazy {
        MediaRepository.buildPreinstalledDefaults()
            .associateBy { it.id }
    }

    // Debounce guard: skip syncAddonCatalogs() when addon list hasn't changed.
    // syncAddonCatalogs() iterates every addon manifest, flatMaps catalogs, and
    // diffs against persisted state — all of which is wasted work when the
    // installed addon list is identical between calls.
    @Volatile private var lastSyncedAddonFingerprint: String? = null

    private val bundledPreinstalledCatalogIds by lazy {
        bundledPreinstalledCatalogsById.keys
    }

    private val gson = Gson()
    private fun catalogsKey(profileId: String) = stringPreferencesKey("profile_${profileId}_catalogs_v1")
    private fun hiddenPreinstalledKey(profileId: String) = stringPreferencesKey("profile_${profileId}_hidden_preinstalled_catalogs_v2")
    private fun hiddenAddonKey(profileId: String) = stringPreferencesKey("profile_${profileId}_hidden_addon_catalogs_v1")
    private val legacyDefaultKey = stringPreferencesKey("profile_default_catalogs_v1")
    private val legacyGlobalKey = stringPreferencesKey("catalogs_v1")
    private val listType = TypeToken.getParameterized(List::class.java, CatalogConfig::class.java).type
    private val hiddenListType = TypeToken.getParameterized(List::class.java, String::class.java).type

    private fun writeCatalogField(prefs: MutablePreferences, profileId: String, field: String, json: String) {
        val key = catalogFieldKey(profileId, field)
        if (prefs[key] == json) return
        prefs[key] = json
    }

    private fun catalogFieldKey(profileId: String, field: String) = when (field) {
        "catalogsByProfile" -> catalogsKey(profileId)
        "hiddenPreinstalledByProfile" -> hiddenPreinstalledKey(profileId)
        "hiddenAddonByProfile" -> hiddenAddonKey(profileId)
        else -> error("Unknown catalog field: $field")
    }

    private fun decodeHiddenPreinstalled(profileId: String, prefs: Preferences): Set<String> {
        val raw = prefs[hiddenPreinstalledKey(profileId)]
        if (raw.isNullOrBlank()) return emptySet()
        return try {
            (gson.fromJson<List<String>>(raw, hiddenListType) ?: emptyList())
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .toSet()
        } catch (e: Exception) {
            AppLogger.e("CatalogRepository", "Error fetching data, returning empty set", e)
            emptySet()
        }
    }

    private fun decodeHiddenAddon(profileId: String, prefs: Preferences): Set<String> {
        val raw = prefs[hiddenAddonKey(profileId)]
        if (raw.isNullOrBlank()) return emptySet()
        return try {
            (gson.fromJson<List<String>>(raw, hiddenListType) ?: emptyList())
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .toSet()
        } catch (e: Exception) {
            AppLogger.e("CatalogRepository", "Error fetching data, returning empty set", e)
            emptySet()
        }
    }



    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeCatalogs(): Flow<List<CatalogConfig>> {
        return profileManager.activeProfileId
            .flatMapLatest { profileId ->
                context.settingsDataStore.data
                    // Unrelated settings/sync writes must not reparse the entire catalog JSON.
                    .distinctUntilChanged { previous, current ->
                        catalogSettingsUnchanged(profileId, previous, current)
                    }
                    .map { prefs -> readCatalogsFromPrefs(profileId, prefs) }
            }
            .distinctUntilChanged()
            .flowOn(Dispatchers.Default)
    }

    internal fun catalogSettingsUnchanged(profileId: String, previous: Preferences, current: Preferences): Boolean {
        val keys = listOf(catalogsKey(profileId), hiddenPreinstalledKey(profileId),
            hiddenAddonKey(profileId), legacyDefaultKey, legacyGlobalKey)
        return keys.all { previous[it] == current[it] }
    }

    private suspend fun activeProfileId(): String {
        return profileManager.getProfileIdSync()
            .ifBlank { profileManager.getProfileId() }
            .ifBlank { "default" }
    }

    private suspend fun readCatalogsForActiveProfile(): List<CatalogConfig> {
        val profileId = activeProfileId()
        val prefs = context.settingsDataStore.data.first()
        val primary = sanitizeCatalogs(
            parseCatalogsJson(prefs[catalogsKey(profileId)]).distinctBy { it.id }
        )
        val resolved = sanitizeCatalogs(readCatalogsFromPrefs(profileId, prefs))
        // Migrate legacy keys only while the captured profile state is still current.
        if (resolved.isNotEmpty() && resolved != primary) {
            if (!saveCatalogs(resolved, prefs, profileId)) return getCatalogsForProfile(profileId)
        }
        return resolved
    }

    suspend fun getCatalogs(): List<CatalogConfig> {
        return readCatalogsForActiveProfile()
    }

    suspend fun getCatalogsForProfile(profileId: String): List<CatalogConfig> {
        val safeProfileId = profileId.trim().ifBlank { "default" }
        val prefs = context.settingsDataStore.data.first()
        return sanitizeCatalogs(readCatalogsFromPrefs(safeProfileId, prefs))
    }

    private fun isBundledPreinstalledCatalogId(catalogId: String): Boolean {
        return bundledPreinstalledCatalogIds.contains(catalogId.trim())
    }

    private fun isPreinstalledCatalog(config: CatalogConfig): Boolean {
        return config.isPreinstalled || isBundledPreinstalledCatalogId(config.id)
    }

    private fun refreshBundledPreinstalledCatalog(config: CatalogConfig): CatalogConfig {
        val bundled = bundledPreinstalledCatalogsById[config.id] ?: return config
        return if (config.isPreinstalled || config.sourceType == CatalogSourceType.PREINSTALLED) bundled else config
    }

    private fun sanitizeCatalogs(catalogs: List<CatalogConfig>): List<CatalogConfig> =
        catalogs.filter { it.sourceType == CatalogSourceType.ADDON }

    suspend fun getHiddenPreinstalledCatalogIdsForActiveProfile(): List<String> {
        val profileId = activeProfileId()
        val prefs = context.settingsDataStore.data.first()
        return decodeHiddenPreinstalled(profileId, prefs).toList()
    }

    suspend fun getHiddenPreinstalledCatalogIdsForProfile(profileId: String): List<String> {
        val safeProfileId = profileId.trim().ifBlank { "default" }
        val prefs = context.settingsDataStore.data.first()
        return decodeHiddenPreinstalled(safeProfileId, prefs).toList()
    }

    suspend fun setHiddenPreinstalledCatalogIdsForActiveProfile(ids: List<String>) {
        val profileId = activeProfileId()
        val cleaned = ids.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        context.settingsDataStore.edit { prefs ->
            writeCatalogField(prefs, profileId, "hiddenPreinstalledByProfile", gson.toJson(cleaned))
        }
    }

    suspend fun setHiddenPreinstalledCatalogIdsForProfile(profileId: String, ids: List<String>) {
        val safeProfileId = profileId.trim().ifBlank { "default" }
        val cleaned = ids.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        context.settingsDataStore.edit { prefs ->
            writeCatalogField(prefs, safeProfileId, "hiddenPreinstalledByProfile", gson.toJson(cleaned))
        }
    }

    suspend fun getHiddenAddonCatalogIdsForActiveProfile(): List<String> {
        val profileId = activeProfileId()
        val prefs = context.settingsDataStore.data.first()
        return decodeHiddenAddon(profileId, prefs).toList()
    }

    suspend fun getHiddenAddonCatalogIdsForProfile(profileId: String): List<String> {
        val safeProfileId = profileId.trim().ifBlank { "default" }
        val prefs = context.settingsDataStore.data.first()
        return decodeHiddenAddon(safeProfileId, prefs).toList()
    }

    suspend fun setHiddenAddonCatalogIdsForActiveProfile(ids: List<String>) {
        val profileId = activeProfileId()
        val cleaned = ids.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        context.settingsDataStore.edit { prefs ->
            writeCatalogField(prefs, profileId, "hiddenAddonByProfile", gson.toJson(cleaned))
        }
    }

    suspend fun setHiddenAddonCatalogIdsForProfile(profileId: String, ids: List<String>) {
        val safeProfileId = profileId.trim().ifBlank { "default" }
        val cleaned = ids.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        context.settingsDataStore.edit { prefs ->
            writeCatalogField(prefs, safeProfileId, "hiddenAddonByProfile", gson.toJson(cleaned))
        }
    }



    private suspend fun saveCatalogs(catalogs: List<CatalogConfig>, expected: Preferences? = null, targetProfileId: String? = null): Boolean {
        val profileId = targetProfileId ?: activeProfileId()
        val sanitized = catalogs
            .distinctBy { it.id }
            .mapNotNull { normalizeCatalogConfig(it) }
        var saved = false
        context.settingsDataStore.edit { prefs ->
            // A background refresh must not overwrite edits made since it read the list.
            if (expected != null && listOf("catalogsByProfile", "hiddenPreinstalledByProfile", "hiddenAddonByProfile", "hiddenHomeServerByProfile").any {
                val key = catalogFieldKey(profileId, it)
                prefs[key] != expected[key]
            }) return@edit
            writeCatalogField(prefs, profileId, "catalogsByProfile", gson.toJson(sanitized))
            saved = true
        }
        return saved
    }

    suspend fun replaceCatalogsForProfile(profileId: String, catalogs: List<CatalogConfig>) {
        val safeProfileId = profileId.trim().ifBlank { "default" }
        val sanitized = catalogs
            .distinctBy { it.id }
            .mapNotNull { normalizeCatalogConfig(it) }
        context.settingsDataStore.edit { prefs ->
            writeCatalogField(prefs, safeProfileId, "catalogsByProfile", gson.toJson(sanitized))
        }
    }

    suspend fun ensurePreinstalled(defaultCategories: List<Category>): List<CatalogConfig> {
        val defaultPreinstalled = defaultCategories.map {
            CatalogConfig(
                id = it.id,
                title = it.title,
                sourceType = CatalogSourceType.PREINSTALLED,
                isPreinstalled = true
            )
        }
        return ensurePreinstalledDefaults(defaultPreinstalled)
    }

    suspend fun ensurePreinstalledDefaults(defaultPreinstalled: List<CatalogConfig>): List<CatalogConfig> {
        val profileId = activeProfileId()
        val prefs = context.settingsDataStore.data.first()
        val hidden = decodeHiddenPreinstalled(profileId, prefs)
        val effectiveDefaults = if (hidden.isEmpty()) {
            defaultPreinstalled.filter { it.sourceType == CatalogSourceType.ADDON }
        } else {
            defaultPreinstalled.filter { it.sourceType == CatalogSourceType.ADDON && it.id !in hidden }
        }

        val defaultIds = defaultPreinstalled.map { it.id }.toSet()
        val existing = readCatalogsFromPrefs(profileId, prefs).mapNotNull { cfg ->

            // Only treat as custom if it's NOT in the preinstalled defaults list
            val looksCustom = !defaultIds.contains(cfg.id) && (
                cfg.id.startsWith("custom_") ||
                !cfg.sourceUrl.isNullOrBlank() ||
                !cfg.sourceRef.isNullOrBlank()
            )
            if (looksCustom) {
                cfg.copy(
                    isPreinstalled = false,
                    sourceType = parseSourceTypeCompat(
                        raw = cfg.sourceType.name,
                        sourceUrl = cfg.sourceUrl,
                        sourceRef = cfg.sourceRef
                    )
                )
            } else {
                cfg
            }
        }
        val defaultMap = effectiveDefaults.associateBy { it.id }

        val merged = if (existing.isEmpty()) {
            effectiveDefaults
        } else {
            val kept = existing.mapNotNull { config ->
                if (config.isPreinstalled) {
                    val defaultCfg = defaultMap[config.id] ?: return@mapNotNull null
                    // Preserve user-renamed titles: if the user changed the title
                    // from a previous default, keep their custom title.
                    if (config.title != defaultCfg.title && config.title.isNotBlank()) {
                        defaultCfg.copy(title = config.title)
                    } else {
                        defaultCfg
                    }
                } else {
                    config
                }
            }.toMutableList()

            // Insert missing preinstalled catalogs at their intended default position
            // so new catalogs appear where they were defined,
            // but only re-sort when there are actually new catalogs to add.
            val missingPreinstalled = effectiveDefaults.filter { pre ->
                kept.none { it.id == pre.id }
            }
            if (missingPreinstalled.isNotEmpty()) {
                val defaultOrder = effectiveDefaults.mapIndexed { idx, cfg -> cfg.id to idx }.toMap()
                for (missing in missingPreinstalled) {
                    val targetIdx = defaultOrder[missing.id] ?: kept.size
                    // Insert after the last kept entry whose default index < targetIdx
                    var insertAt = 0
                    for (i in kept.indices) {
                        val keptIdx = defaultOrder[kept[i].id]
                        if (keptIdx != null && keptIdx < targetIdx) {
                            insertAt = i + 1
                        }
                    }
                    kept.add(insertAt, missing)
                }
            }
            kept
        }

        if (existing != merged) {
            if (!saveCatalogs(merged, prefs, profileId)) return getCatalogsForProfile(profileId)
        }
        return merged
    }

    suspend fun syncAddonCatalogs(addons: List<Addon>): Boolean {
        val profileId = activeProfileId()
        // Debounce: skip if addon manifest state hasn't changed since last sync.
        // Includes profile ID + fields that affect the sync outcome so that
        // switching profiles, toggling addon enable-state, or manifest catalog
        // changes always trigger a fresh sync.
        val fingerprint = buildAddonFingerprint(profileId, addons)
        if (fingerprint == lastSyncedAddonFingerprint) return false
        lastSyncedAddonFingerprint = fingerprint

        val prefs = context.settingsDataStore.data.first()
        val hiddenAddonIds = decodeHiddenAddon(profileId, prefs)
        val supportedCatalogs = addons
            .asSequence()
            .filter { addon ->
                    addon.isInstalled &&
                    addon.isEnabled &&
                    addon.type != AddonType.SUBTITLE &&
                    !addon.url.isNullOrBlank() &&
                    !addon.manifest?.catalogs.isNullOrEmpty()
            }
            .flatMap { addon ->
                addon.manifest?.catalogs.orEmpty().asSequence()
                    .mapNotNull { catalog -> buildAddonCatalogConfig(addon, catalog) }
            }
            // Drop entries the user has explicitly deleted. Without this, every
            // sync would re-add them from the addon manifest on the very next
            // installedAddons emission, making addon catalog deletion impossible.
            .filterNot { it.id in hiddenAddonIds }
            .distinctBy { it.id }
            .toList()

        val current = readCatalogsFromPrefs(profileId, prefs).toMutableList()
        val desiredById = supportedCatalogs.associateBy { it.id }
        var changed = false

        val beforeRemovalSize = current.size
        current.removeAll { cfg ->
            cfg.sourceType == CatalogSourceType.ADDON && !desiredById.containsKey(cfg.id)
        }
        if (current.size != beforeRemovalSize) {
            changed = true
        }

        current.indices.forEach { index ->
            val existing = current[index]
            val desired = desiredById[existing.id] ?: return@forEach
            val merged = existing.copy(
                title = desired.title,
                sourceType = CatalogSourceType.ADDON,
                sourceRef = desired.sourceRef,
                isPreinstalled = false,
                addonId = desired.addonId,
                addonCatalogType = desired.addonCatalogType,
                addonCatalogId = desired.addonCatalogId,
                addonName = desired.addonName
            )
            if (merged != existing) {
                current[index] = merged
                changed = true
            }
        }

        val existingIds = current.map { it.id }.toHashSet()
        val missing = supportedCatalogs.filterNot { existingIds.contains(it.id) }
        if (missing.isNotEmpty()) {
            // New libraries follow existing catalogs; never displace the trending defaults.
            current.addAll(missing)
            changed = true
        }

        if (changed) {
            if (!saveCatalogs(current, prefs, profileId)) {
                lastSyncedAddonFingerprint = null
                return false
            }
        }
        return changed
    }

    /**
     * Builds a fingerprint for [syncAddonCatalogs] debouncing that captures all fields
     * affecting the sync outcome. Unlike the previous ID-only fingerprint, this includes:
     * - Active profile ID (so profile switches always trigger a fresh sync)
     * - Addon installed/enabled state, type, and URL
     * - Manifest catalog contents (catalog type + ID per addon)
     *
     * This ensures that toggling an addon, switching profiles, or manifest catalog
     * changes never leave stale/missing catalogs.
     */
    private fun buildAddonFingerprint(profileId: String, addons: List<Addon>): String {
        return buildString {
            append(profileId)
            append('|')
            addons.forEach { addon ->
                append(addon.id)
                append(':')
                append(if (addon.isInstalled) '1' else '0')
                append(if (addon.isEnabled) '1' else '0')
                append(addon.type.name)
                append(addon.url ?: "")
                addon.manifest?.catalogs?.forEach { catalog ->
                    append('[')
                    append(catalog.type)
                    append(':')
                    append(catalog.id)
                    append(']')
                }
                append(',')
            }
        }
    }


    private fun buildAddonCatalogConfig(
        addon: Addon,
        catalog: AddonCatalog
    ): CatalogConfig? {
        val normalizedType = normalizeAddonCatalogType(catalog.type) ?: return null
        val catalogId = catalog.id.trim().takeIf { it.isNotBlank() } ?: return null
        val hasRequiredExtras = catalog.extra
            ?.any { extra -> extra.isRequired && !extra.name.equals("skip", ignoreCase = true) }
            ?: false
        if (hasRequiredExtras) return null

        val addonId = addon.id.trim().takeIf { it.isNotBlank() } ?: return null
        val title = catalog.name.trim().takeIf { it.isNotBlank() } ?: catalogId.toDisplayTitle()
        val hashInput = "$addonId|$normalizedType|$catalogId"
        val stableId = "addon_${sha256Short(hashInput)}"

        return CatalogConfig(
            id = stableId,
            title = title,
            sourceType = CatalogSourceType.ADDON,
            sourceRef = buildAddonSourceRef(
                addonId = addonId,
                catalogType = normalizedType,
                catalogId = catalogId
            ),
            isPreinstalled = false,
            addonId = addonId,
            addonCatalogType = normalizedType,
            addonCatalogId = catalogId,
            addonName = addon.name
        )
    }

    // Addons may declare any catalog type (AIOMetadata's "anime"/"all"/custom display
    // types, "Podcasts", debrid "other"). Unknown types keep their spelling: the addon
    // routes /catalog/{type}/ by it, case included.
    private fun normalizeAddonCatalogType(rawType: String?): String? {
        val trimmed = rawType?.trim()?.takeIf { it.isNotBlank() } ?: return null
        return when (trimmed.lowercase()) {
            "movie" -> "movie"
            "series" -> "series"
            "tv" -> "tv"
            "show" -> "show"
            "shows" -> "shows"
            else -> trimmed
        }
    }

    private fun buildAddonSourceRef(addonId: String, catalogType: String, catalogId: String): String {
        return "$ADDON_SOURCE_REF_PREFIX" +
            "${urlEncode(addonId)}|" +
            "${urlEncode(catalogType)}|" +
            urlEncode(catalogId)
    }

    private fun parseAddonSourceRef(sourceRef: String?): Triple<String, String, String>? {
        val value = sourceRef?.trim().orEmpty()
        if (!value.startsWith(ADDON_SOURCE_REF_PREFIX)) return null
        val payload = value.removePrefix(ADDON_SOURCE_REF_PREFIX)
        val parts = payload.split("|")
        if (parts.size != 3) return null
        val addonId = urlDecode(parts[0]).trim()
        val catalogType = normalizeAddonCatalogType(urlDecode(parts[1]))
        val catalogId = urlDecode(parts[2]).trim()
        if (addonId.isBlank() || catalogType == null || catalogId.isBlank()) return null
        return Triple(addonId, catalogType, catalogId)
    }

    private fun urlEncode(value: String): String = URLEncoder.encode(value, "UTF-8")
    private fun urlDecode(value: String): String = URLDecoder.decode(value, "UTF-8")

    private fun sha256Short(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray())
        return digest.take(8).joinToString("") { b -> "%02x".format(b) }
    }


    suspend fun fetchCatalogPackManifest(packUrl: String): Result<CatalogPackManifest> {
        val trimmed = packUrl.trim()
        if (!trimmed.startsWith("http://", ignoreCase = true) && !trimmed.startsWith("https://", ignoreCase = true)) {
            return Result.failure(CatalogException(R.string.catalog_pack_invalid_url_scheme))
        }

        val json = fetchUrl(trimmed, fresh = true)
            ?: return Result.failure(CatalogException(R.string.catalog_pack_fetch_failed))
        val manifest = try {
            val type = object : com.google.gson.reflect.TypeToken<CatalogPackManifest>() {}.type
            gson.fromJson<CatalogPackManifest>(json, type)
        } catch (e: Exception) {
            null
        } ?: return Result.failure(CatalogException(R.string.catalog_pack_parse_failed))

        if (manifest.id.isNullOrBlank() || manifest.name.isNullOrBlank()) {
            return Result.failure(CatalogException(R.string.catalog_pack_missing_id_or_name))
        }
        val cats = manifest.catalogs
        if (cats.isNullOrEmpty()) {
            return Result.failure(CatalogException(R.string.catalog_pack_no_catalogs))
        }
        val normalizedUrls = mutableSetOf<String>()
        for (item in cats) {
            if (item.name.isNullOrBlank() || item.url.isNullOrBlank()) {
                return Result.failure(CatalogException(R.string.catalog_pack_item_incomplete))
            }
            val normalized = CatalogUrlParser.normalize(item.url).lowercase()
            if (!normalizedUrls.add(normalized)) {
                return Result.failure(CatalogException(R.string.catalog_pack_duplicate_url, listOf(normalized)))
            }
        }
        return Result.success(manifest)
    }

    suspend fun addCatalogPack(packUrl: String, manifest: CatalogPackManifest? = null): Result<CatalogPackManifest> {
        val finalManifest = if (manifest != null) {
            manifest
        } else {
            val manifestResult = fetchCatalogPackManifest(packUrl)
            if (manifestResult.isFailure) return manifestResult
            manifestResult.getOrThrow()
        }

        val current = getCatalogs().toMutableList()
        val addedConfigs = mutableListOf<CatalogConfig>()

        val manifestId = finalManifest.id ?: return Result.failure(CatalogException(R.string.catalog_pack_manifest_missing_id))
        val manifestName = finalManifest.name ?: return Result.failure(CatalogException(R.string.catalog_pack_manifest_missing_name))
        val manifestCatalogs = finalManifest.catalogs ?: return Result.failure(CatalogException(R.string.catalog_pack_manifest_missing_catalogs))

        val normalizedPackUrl = CatalogUrlParser.normalize(packUrl).lowercase()
        val derivedPackId = "pack_${sha256Short(normalizedPackUrl + "|" + manifestId)}"

        for (item in manifestCatalogs) {
            val itemUrl = item.url ?: continue
            val validation = validateCatalogUrl(itemUrl)
            if (!validation.isValid || validation.normalizedUrl == null || validation.sourceType == null) {
                continue
            }
            val normalizedUrl = validation.normalizedUrl
            val sourceType = validation.sourceType

            // Skip if this URL is already added
            if (current.any { it.sourceUrl.equals(normalizedUrl, ignoreCase = true) }) {
                continue
            }

            val resolved = try { resolveMetadata(normalizedUrl, sourceType) } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                return Result.failure(e)
            }
                ?: fallbackMetadata(normalizedUrl, sourceType)
                ?: continue

            val stableId = "custom_${sha256Short(derivedPackId + "|" + normalizedUrl)}"
            val newCatalog = CatalogConfig(
                id = stableId,
                title = item.name?.trim()?.ifBlank { resolved.title } ?: resolved.title,
                sourceType = sourceType,
                sourceUrl = normalizedUrl,
                sourceRef = resolved.sourceRef,
                isPreinstalled = false,
                packId = derivedPackId,
                packName = manifestName
            )
            addedConfigs.add(newCatalog)
        }

        if (addedConfigs.isEmpty()) {
            return Result.failure(CatalogException(R.string.catalog_pack_nothing_to_import))
        }

        current.addAll(0, addedConfigs)
        saveCatalogs(current)
        return Result.success(finalManifest)
    }


    suspend fun removeCatalogPack(packId: String): Result<Unit> {
        return removeCatalogsMatching(
            CatalogException(R.string.catalog_pack_none_for_pack, listOf(packId))
        ) { it.packId == packId }
    }

    suspend fun addCustomCatalog(rawUrl: String): Result<CatalogConfig> {
        val targetProfileId = profileManager.getProfileIdSync()
        val validation = validateCatalogUrl(rawUrl)
        if (!validation.isValid || validation.normalizedUrl == null || validation.sourceType == null) {
            return Result.failure(CatalogException(validation.errorRes ?: R.string.catalog_url_invalid))
        }

        val normalizedUrl = validation.normalizedUrl
        val sourceType = validation.sourceType
        val resolved = try { resolveMetadata(normalizedUrl, sourceType) } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            return Result.failure(e)
        }
            ?: fallbackMetadata(normalizedUrl, sourceType)
            ?: return Result.failure(CatalogException(R.string.catalog_failed_read_metadata))

        if (targetProfileId != profileManager.getProfileIdSync()) return Result.failure(IllegalStateException("Profile changed while adding the catalogue. Please try again."))
        val current = getCatalogsForProfile(targetProfileId).toMutableList()
        if (current.any { it.sourceUrl.equals(normalizedUrl, ignoreCase = true) }) {
            return Result.failure(CatalogException(R.string.catalog_already_added))
        }

        val newCatalog = CatalogConfig(
            id = "custom_${System.currentTimeMillis()}",
            title = resolved.title,
            sourceType = sourceType,
            sourceUrl = normalizedUrl,
            sourceRef = resolved.sourceRef,
            isPreinstalled = false
        )
        current.add(0, newCatalog)
        saveCatalogs(current, targetProfileId = targetProfileId)
        return Result.success(newCatalog)
    }


    /**
     * Appends TMDB catalogs to [profileId]'s list, after its own rows, skipping any whose page
     * is already there. Used for a kids profile's starter rows; afterwards they are ordinary
     * catalogs the profile can hide, move, rename or remove. [starters] are (id, title, url).
     */
    suspend fun appendTmdbCatalogs(profileId: String, starters: List<Triple<String, String, String>>) {
        val current = getCatalogsForProfile(profileId)
        val added = starters.mapNotNull { (id, title, url) ->
            val parsed = CatalogUrlParser.parseTmdb(url) ?: return@mapNotNull null
            if (current.any { it.id == id || it.sourceUrl.equals(url, ignoreCase = true) }) return@mapNotNull null
            CatalogConfig(
                id = id,
                title = title,
                sourceType = CatalogSourceType.TMDB,
                sourceUrl = url,
                sourceRef = tmdbSourceRef(parsed),
                isPreinstalled = false
            )
        }
        if (added.isNotEmpty()) saveCatalogs(current + added, targetProfileId = profileId)
    }

    suspend fun updateCustomCatalog(catalogId: String, rawUrl: String): Result<CatalogConfig> {
        val targetProfileId = profileManager.getProfileIdSync()
        val current = getCatalogsForProfile(targetProfileId).toMutableList()
        val index = current.indexOfFirst { it.id == catalogId }
        if (index < 0) return Result.failure(CatalogException(R.string.catalog_not_found))
        val existing = current[index]
        if (existing.isPreinstalled) {
            return Result.failure(CatalogException(R.string.catalog_preinstalled_no_edit))
        }

        val validation = validateCatalogUrl(rawUrl)
        if (!validation.isValid || validation.normalizedUrl == null || validation.sourceType == null) {
            return Result.failure(CatalogException(validation.errorRes ?: R.string.catalog_url_invalid))
        }

        val normalizedUrl = validation.normalizedUrl
        if (current.any { it.id != catalogId && it.sourceUrl.equals(normalizedUrl, ignoreCase = true) }) {
            return Result.failure(CatalogException(R.string.catalog_already_added))
        }

        val resolved = try { resolveMetadata(normalizedUrl, validation.sourceType) } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            return Result.failure(e)
        }
            ?: fallbackMetadata(normalizedUrl, validation.sourceType)
            ?: return Result.failure(CatalogException(R.string.catalog_failed_read_metadata))
        if (targetProfileId != profileManager.getProfileIdSync()) return Result.failure(IllegalStateException("Profile changed while updating the catalogue. Please try again."))
        val updated = existing.copy(
            title = resolved.title,
            sourceType = validation.sourceType,
            sourceUrl = normalizedUrl,
            sourceRef = resolved.sourceRef
        )
        current[index] = updated
        saveCatalogs(current, targetProfileId = targetProfileId)
        return Result.success(updated)
    }

    suspend fun removeCustomCatalog(catalogId: String): Result<Unit> {
        return removeCatalogsMatching(CatalogException(R.string.catalog_not_found)) { it.id == catalogId }
    }

    private suspend fun removeCatalogsMatching(error: CatalogException, matches: (CatalogConfig) -> Boolean): Result<Unit> {
        val profileId = activeProfileId()
        var removed = false
        context.settingsDataStore.edit { prefs ->
            val current = readCatalogsFromPrefs(profileId, prefs)
            val targets = current.filter(matches)
            if (targets.isEmpty()) return@edit
            val preinstalled = decodeHiddenPreinstalled(profileId, prefs).toMutableSet()
            val addons = decodeHiddenAddon(profileId, prefs).toMutableSet()
            targets.forEach { target ->
                when {
                    isPreinstalledCatalog(target) -> preinstalled.add(target.id)
                    target.sourceType == CatalogSourceType.ADDON -> addons.add(target.id)
                }
            }
            writeCatalogField(prefs, profileId, "hiddenPreinstalledByProfile", gson.toJson(preinstalled))
            writeCatalogField(prefs, profileId, "hiddenAddonByProfile", gson.toJson(addons))
            writeCatalogField(prefs, profileId, "catalogsByProfile", gson.toJson(current.filterNot(matches)))
            removed = true
        }
        if (!removed) return Result.failure(error)
        lastSyncedAddonFingerprint = null
        return Result.success(Unit)
    }

    suspend fun renameCatalog(catalogId: String, newTitle: String): Boolean {
        val trimmed = newTitle.trim()
        if (trimmed.isBlank()) return false
        val current = getCatalogs().toMutableList()
        val index = current.indexOfFirst { it.id == catalogId }
        if (index < 0) return false
        current[index] = current[index].copy(title = trimmed)
        saveCatalogs(current)
        return true
    }

    suspend fun moveCatalogUp(catalogId: String): Boolean {
        val current = getCatalogs().toMutableList()
        val visible = current.filter { (it.sourceType == CatalogSourceType.ADDON) }
        val visibleIndex = visible.indexOfFirst { it.id == catalogId }
        if (visibleIndex <= 0) return false
        val currentIndex = current.indexOfFirst { it.id == catalogId }
        val previousVisibleId = visible[visibleIndex - 1].id
        val previousIndex = current.indexOfFirst { it.id == previousVisibleId }
        if (currentIndex < 0 || previousIndex < 0) return false
        val moved = current.removeAt(currentIndex)
        val insertAt = if (currentIndex > previousIndex) previousIndex else previousIndex - 1
        current.add(insertAt.coerceAtLeast(0), moved)
        saveCatalogs(current)
        return true
    }

    suspend fun moveCatalogDown(catalogId: String): Boolean {
        val current = getCatalogs().toMutableList()
        val visible = current.filter { (it.sourceType == CatalogSourceType.ADDON) }
        val visibleIndex = visible.indexOfFirst { it.id == catalogId }
        if (visibleIndex < 0 || visibleIndex >= visible.lastIndex) return false
        val currentIndex = current.indexOfFirst { it.id == catalogId }
        val nextVisibleId = visible[visibleIndex + 1].id
        val nextIndex = current.indexOfFirst { it.id == nextVisibleId }
        if (currentIndex < 0 || nextIndex < 0) return false
        val moved = current.removeAt(currentIndex)
        val insertAt = if (currentIndex < nextIndex) nextIndex else nextIndex + 1
        current.add(insertAt.coerceAtMost(current.size), moved)
        saveCatalogs(current)
        return true
    }

    suspend fun replaceCatalogsForActiveProfile(catalogs: List<CatalogConfig>) {
        val sanitized = catalogs
            .distinctBy { it.id }
            .map { cfg ->
                val looksCustom = cfg.id.startsWith("custom_") ||
                    cfg.sourceType == CatalogSourceType.ADDON ||
                    !cfg.sourceUrl.isNullOrBlank() ||
                    !cfg.sourceRef.isNullOrBlank()
                if (looksCustom) cfg.copy(isPreinstalled = false) else cfg
            }
        saveCatalogs(sanitized)
    }

    fun validateCatalogUrl(rawUrl: String): CatalogValidationResult {
        val normalized = CatalogUrlParser.normalize(rawUrl)
        if (normalized.isBlank()) {
            return CatalogValidationResult(isValid = false, errorRes = R.string.catalog_url_required)
        }
        val uri = try { URI(normalized) } catch (e: Exception) { null }
            ?: return CatalogValidationResult(isValid = false, errorRes = R.string.catalog_url_invalid_format)
        val host = uri.host?.lowercase()
            ?: return CatalogValidationResult(isValid = false, errorRes = R.string.catalog_url_invalid_host)

        return when {
            host == "themoviedb.org" || host.endsWith(".themoviedb.org") -> {
                if (CatalogUrlParser.parseTmdb(normalized) == null) {
                    CatalogValidationResult(isValid = false, errorRes = R.string.catalog_url_tmdb_hint)
                } else {
                    CatalogValidationResult(
                        isValid = true,
                        normalizedUrl = normalized,
                        sourceType = CatalogSourceType.TMDB
                    )
                }
            }
            else -> CatalogValidationResult(
                isValid = false,
                errorRes = R.string.catalog_url_unsupported
            )
        }
    }

    private suspend fun resolveMetadata(url: String, sourceType: CatalogSourceType): ResolvedCatalog? {
        return when (sourceType) {
            CatalogSourceType.TMDB -> resolveTmdbMetadata(url)
            CatalogSourceType.PREINSTALLED -> null
            CatalogSourceType.ADDON -> null
        }
    }

    private fun fallbackMetadata(url: String, sourceType: CatalogSourceType): ResolvedCatalog? {
        return when (sourceType) {
            CatalogSourceType.TMDB -> CatalogUrlParser.parseTmdb(url)?.let { parsed ->
                ResolvedCatalog(
                    title = parsed.slug?.replace('-', ' ')?.toDisplayTitle()
                        ?: context.getString(R.string.catalog_tmdb_title),
                    sourceRef = tmdbSourceRef(parsed)
                )
            }
            CatalogSourceType.PREINSTALLED -> null
            CatalogSourceType.ADDON -> null
        }
    }

    /** The TMDB page's own name, so the row isn't named after a URL slug. */
    private suspend fun resolveTmdbMetadata(url: String): ResolvedCatalog? {
        val parsed = CatalogUrlParser.parseTmdb(url) ?: return null
        val apiKey = Constants.TMDB_API_KEY
        val endpoint = when (parsed.kind) {
            "list" -> "list/${parsed.id}"
            "collection" -> "collection/${parsed.id}"
            "company" -> "company/${parsed.id}"
            "network" -> "network/${parsed.id}"
            "person" -> "person/${parsed.id}"
            "keyword" -> "keyword/${parsed.id}"
            // Genre pages have no detail endpoint; the slug names them well enough.
            else -> null
        }
        val name = if (endpoint == null || apiKey.isBlank()) {
            null
        } else {
            val body = fetchUrl("https://api.themoviedb.org/3/$endpoint?api_key=$apiKey")
            body?.let {
                runCatching { org.json.JSONObject(it).optString("name").trim().takeIf { n -> n.isNotEmpty() } }
                    .getOrNull()
            }
        }
        val title = name
            ?: parsed.slug?.replace('-', ' ')?.toDisplayTitle()
            ?: context.getString(R.string.catalog_tmdb_title)
        return ResolvedCatalog(title = title, sourceRef = tmdbSourceRef(parsed))
    }

    private fun tmdbSourceRef(parsed: ParsedCatalogUrl.Tmdb): String =
        "tmdb:${parsed.kind}:${parsed.id}:${parsed.mediaType.orEmpty()}"











    /**
     * [fresh] skips the HTTP cache for user-initiated imports: re-importing an edited pack must
     * not get the copy cached by the previous import.
     */
    private suspend fun fetchUrl(url: String, fresh: Boolean = false): String? {
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", OkHttpProvider.userAgentOr("Mozilla/5.0 (Android TV; ARVIO)"))
                    .apply { if (fresh) cacheControl(CacheControl.FORCE_NETWORK) }
                    .build()
                okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    response.body?.string()
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
        }
    }

    private fun parseCatalogsJson(json: String?): List<CatalogConfig> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            com.google.gson.JsonParser.parseString(json).asJsonArray.mapNotNull { element ->
                runCatching {
                    val row = element.asJsonObject
                    val kind = row.get("kind")?.takeUnless { it.isJsonNull }?.asString
                    if (kind != null && !kind.equals("standard", ignoreCase = true)) return@runCatching null
                    row.get("sourceType")?.takeUnless { it.isJsonNull }?.asString?.let {
                        row.addProperty("sourceType", it.uppercase(Locale.US))
                    }
                    normalizeCatalogConfig(gson.fromJson(row, CatalogConfig::class.java))
                }.getOrNull()
            }
        } catch (e: Exception) { emptyList() }
    }

    private fun normalizeCatalogConfig(config: CatalogConfig): CatalogConfig? {
        if (config.id.isBlank() || config.title.isBlank()) return null
        val normalizedRef = config.sourceRef?.trim().takeUnless { it.isNullOrBlank() }
        val sourceRefAddon = parseAddonSourceRef(normalizedRef)
        if (config.sourceType != CatalogSourceType.ADDON && sourceRefAddon == null) return null
        return config.copy(
            sourceType = CatalogSourceType.ADDON,
            kind = CatalogKind.STANDARD,
            requiredAddonUrls = config.requiredAddonUrls.orEmpty(),
            isPreinstalled = false,
            sourceRef = normalizedRef,
            addonId = config.addonId?.trim()?.takeIf { it.isNotBlank() } ?: sourceRefAddon?.first,
            addonCatalogType = normalizeAddonCatalogType(config.addonCatalogType) ?: sourceRefAddon?.second,
            addonCatalogId = config.addonCatalogId?.trim()?.takeIf { it.isNotBlank() } ?: sourceRefAddon?.third
        )
    }

    private fun parseSourceTypeCompat(
        raw: String,
        sourceUrl: String?,
        sourceRef: String?
    ): CatalogSourceType {
        val normalized = raw.trim().uppercase()
        return when {
            // URL/ref evidence always wins over stale enum values from older builds.
            sourceRef?.startsWith(ADDON_SOURCE_REF_PREFIX, ignoreCase = true) == true -> CatalogSourceType.ADDON
            sourceRef?.startsWith("tmdb:", ignoreCase = true) == true -> CatalogSourceType.TMDB
            sourceUrl?.contains("themoviedb.org", ignoreCase = true) == true -> CatalogSourceType.TMDB
            normalized == CatalogSourceType.TMDB.name -> CatalogSourceType.TMDB
            normalized == CatalogSourceType.ADDON.name -> CatalogSourceType.ADDON
            normalized == CatalogSourceType.PREINSTALLED.name -> CatalogSourceType.PREINSTALLED
            normalized.contains("ADDON") -> CatalogSourceType.ADDON
            normalized.contains("TMDB") -> CatalogSourceType.TMDB
            sourceUrl.isNullOrBlank() -> CatalogSourceType.PREINSTALLED
            else -> CatalogSourceType.PREINSTALLED
        }
    }

    private fun readCatalogsFromPrefs(profileId: String, prefs: Preferences): List<CatalogConfig> {
        val hiddenPreinstalled = decodeHiddenPreinstalled(profileId, prefs)
        val hiddenAddon = decodeHiddenAddon(profileId, prefs)

        fun CatalogConfig.isHidden(): Boolean {
            if (isPreinstalledCatalog(this) && id in hiddenPreinstalled) return true
            if (sourceType == CatalogSourceType.ADDON && id in hiddenAddon) return true
            return false
        }

        // Strict profile-first lookup to avoid leaking or prioritizing
        // catalogs from other profiles.
        val primary = parseCatalogsJson(prefs[catalogsKey(profileId)])
        if (prefs[catalogsKey(profileId)] != null) {
            val base = primary
                .distinctBy { it.id }
                .map { refreshBundledPreinstalledCatalog(it) }
                .filterNot { it.isHidden() }
                .toMutableList()
            // Once a profile has a saved list, even [], legacy data is not authoritative.
            return base
        }

        // Legacy fallback keys (pre profile-scoping).
        val legacyDefault = parseCatalogsJson(prefs[legacyDefaultKey])
        if (legacyDefault.isNotEmpty()) {
            return legacyDefault
                .distinctBy { it.id }
                .map { refreshBundledPreinstalledCatalog(it) }
                .filterNot { it.isHidden() }
        }

        val legacyGlobal = parseCatalogsJson(prefs[legacyGlobalKey])
        if (legacyGlobal.isNotEmpty()) {
            return legacyGlobal
                .distinctBy { it.id }
                .map { refreshBundledPreinstalledCatalog(it) }
                .filterNot { it.isHidden() }
        }

        return emptyList()
    }

    private data class ResolvedCatalog(
        val title: String,
        val sourceRef: String
    )

    private fun asTrimmedString(value: Any?): String? {
        return (value as? String)?.trim().takeUnless { it.isNullOrBlank() }
    }

    private companion object {
        private const val ADDON_SOURCE_REF_PREFIX = "addon_catalog|"

    }
}

private fun String.toDisplayTitle(): String {
    return replace('-', ' ')
        .split(' ')
        .filter { it.isNotBlank() }
        .joinToString(" ") { token ->
            token.replaceFirstChar { ch -> ch.uppercase() }
        }
        .ifBlank { "Custom Catalog" }
}

private object CatalogRepoRegexes {
    val TITLE_FROM_META_REGEX = Regex(
        """<meta\s+property=["']og:title["']\s+content=["']([^"']+)["']""",
        RegexOption.IGNORE_CASE
    )
    val TITLE_FROM_TAG_REGEX = Regex(
        """<title>([^<]+)</title>""",
        RegexOption.IGNORE_CASE
    )
}
