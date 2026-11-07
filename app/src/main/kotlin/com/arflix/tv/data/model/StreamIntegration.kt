package com.arflix.tv.data.model

import com.arflix.tv.R
import java.util.Locale

/**
 * Stream acquisition mechanisms.
 */
enum class StreamIntegrationType(
    val id: String,
    val titleRes: Int,
    val descRes: Int
) {
    STREMIO_ADDONS(
        id = "stremio_addons",
        titleRes = R.string.stream_integration_stremio,
        descRes = R.string.stream_integration_stremio_desc
    );

    companion object {
        val DEFAULT_ORDER = listOf(
            STREMIO_ADDONS
        )

        fun fromId(id: String?): StreamIntegrationType? {
            val raw = id?.trim()?.lowercase(Locale.US) ?: return null
            return entries.firstOrNull { it.id == raw }
        }

        fun inferFromStreamSource(stream: StreamSource): StreamIntegrationType {
            val addonId = stream.addonId.trim().lowercase(Locale.US)
            return when {
                else -> STREMIO_ADDONS
            }
        }
    }
}

/**
 * User-configurable priority and enabled state for a stream integration.
 */
data class StreamIntegrationConfig(
    val type: StreamIntegrationType,
    val isEnabled: Boolean = true,
    val priority: Int = 0
)

/**
 * An individual stream provider:
 * - Stremio Addon (e.g. Torrentio, Comet)
 * - Plugin Repository (e.g. SoraStream Repo)
 */
data class StreamProviderItem(
    val id: String,
    val rawProviderKeys: List<String>,
    val displayName: String,
    val subtitle: String? = null,
    val type: StreamIntegrationType,
    val isEnabled: Boolean = true,
    val priority: Int = 0,
    val targetConfigId: String? = null,
    val canMoveUp: Boolean = true,
    val canMoveDown: Boolean = true
)

/**
 * Strategy for discovering and resolving streams across integrations.
 */
enum class StreamSearchMode(val id: String) {
    PARALLEL("parallel"),
    SEQUENTIAL("sequential");

    companion object {
        fun fromId(id: String?): StreamSearchMode {
            val raw = id?.trim()?.lowercase(Locale.US) ?: return PARALLEL
            return entries.firstOrNull { it.id == raw } ?: PARALLEL
        }
    }
}
