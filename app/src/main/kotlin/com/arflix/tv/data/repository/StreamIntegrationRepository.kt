package com.arflix.tv.data.repository

import com.arflix.tv.data.model.AddonType
import com.arflix.tv.data.model.StreamIntegrationConfig
import com.arflix.tv.data.model.StreamIntegrationType
import com.arflix.tv.data.model.StreamProviderItem
import com.arflix.tv.data.model.StreamSearchMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * Usenet Ultimate / Stremio add-ons are the only stream source (PTP 1.0.2). Nothing here is
 * user-configurable any more: no saved order, no per-provider toggles, parallel search. The
 * unified source order is the installed add-ons in install order (Usenet Ultimate first).
 */
@Singleton
class StreamIntegrationRepository @Inject constructor(
    private val streamRepositoryProvider: Provider<StreamRepository>
) {

    fun observeConfigs(): Flow<List<StreamIntegrationConfig>> = flowOf(HARDCODED_CONFIGS)

    suspend fun getConfigs(): List<StreamIntegrationConfig> = HARDCODED_CONFIGS

    suspend fun isIntegrationEnabled(type: StreamIntegrationType): Boolean =
        type == StreamIntegrationType.STREMIO_ADDONS

    /** Installed add-ons that provide streams, in install order. */
    fun observeProviderItems(): Flow<List<StreamProviderItem>> {
        return streamRepositoryProvider.get().installedAddons.map { installedAddons ->
            val vodAddons = installedAddons.filter { addon ->
                if (!addon.isInstalled) return@filter false
                if (addon.type == AddonType.SUBTITLE || addon.type == AddonType.METADATA) return@filter false
                val manifest = addon.manifest
                if (manifest != null && manifest.resources.isNotEmpty()) {
                    manifest.resources.any { resource ->
                        resource.name.equals("stream", ignoreCase = true) ||
                            resource.name.equals("streams", ignoreCase = true)
                    }
                } else {
                    true
                }
            }
            val total = vodAddons.size
            vodAddons.mapIndexed { index, addon ->
                StreamProviderItem(
                    id = "stremio:${addon.id}",
                    rawProviderKeys = listOf(addon.id),
                    displayName = addon.name,
                    subtitle = if (!addon.version.isNullOrBlank()) "v${addon.version}" else "Stremio Addon",
                    type = StreamIntegrationType.STREMIO_ADDONS,
                    isEnabled = addon.isEnabled,
                    priority = index + 1,
                    targetConfigId = "stremio:${addon.id}",
                    canMoveUp = index > 0,
                    canMoveDown = index < total - 1
                )
            }
        }
    }

    suspend fun getProviderItems(): List<StreamProviderItem> = observeProviderItems().first()

    suspend fun enabledProviderIds(type: StreamIntegrationType, selectedId: String? = null): Set<String> {
        if (type != StreamIntegrationType.STREMIO_ADDONS) return emptySet()
        return getProviderItems().filter {
            it.isEnabled && (selectedId == null || it.id == selectedId)
        }.map { it.id }.toSet()
    }

    /** Enabled add-on ids in install order (Usenet Ultimate first). */
    fun getUnifiedSourceOrderedIds(): Flow<List<String>> {
        return observeProviderItems().map { items ->
            buildList {
                items.forEach { item ->
                    if (item.isEnabled) {
                        item.rawProviderKeys.forEach { key ->
                            if (key.isNotBlank()) add(key)
                        }
                    }
                }
            }.distinct()
        }
    }

    suspend fun getUnifiedSourceOrderedIdsSync(): List<String> = getUnifiedSourceOrderedIds().first()

    fun observeSearchMode(): Flow<StreamSearchMode> = flowOf(StreamSearchMode.PARALLEL)

    suspend fun getSearchMode(): StreamSearchMode = StreamSearchMode.PARALLEL

    private companion object {
        val HARDCODED_CONFIGS = listOf(
            StreamIntegrationConfig(type = StreamIntegrationType.STREMIO_ADDONS, isEnabled = true, priority = 0)
        )
    }
}
