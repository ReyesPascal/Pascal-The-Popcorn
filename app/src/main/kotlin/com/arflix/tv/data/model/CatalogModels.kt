package com.arflix.tv.data.model

import androidx.annotation.StringRes
import java.io.Serializable

enum class CatalogSourceType {
    PREINSTALLED,
    // A TMDB page added by its URL: list, collection, company, network,
    // person, keyword or genre.
    TMDB,
    ADDON,
}

enum class CatalogKind {
    STANDARD
}

/** Query descriptors for user-supplied TMDB page URLs. */
enum class CatalogQuerySourceKind { TMDB_COLLECTION, TMDB_DISCOVER, TMDB_LIST }

data class CatalogQuerySourceConfig(
    val kind: CatalogQuerySourceKind,
    val mediaType: String? = null,
    val tmdbCollectionId: Int? = null,
    val sortBy: String? = null,
    val discoverParams: Map<String, String>? = null,
    val tmdbListId: Int? = null
)

data class CatalogConfig(
    val id: String,
    val title: String,
    val sourceType: CatalogSourceType,
    val sourceUrl: String? = null,
    val sourceRef: String? = null,
    val isPreinstalled: Boolean = false,
    val addonId: String? = null,
    val addonCatalogType: String? = null,
    val addonCatalogId: String? = null,
    val addonName: String? = null,
    val kind: CatalogKind = CatalogKind.STANDARD,
    val requiredAddonUrls: List<String> = emptyList(),
    val packId: String? = null,
    val packName: String? = null,
) : Serializable

val CatalogConfig.effectivePackId: String
    get() = packId ?: when (sourceType) {
        CatalogSourceType.PREINSTALLED -> "system"
        CatalogSourceType.ADDON -> "addon"
        CatalogSourceType.TMDB -> "tmdb"
    }

val CatalogConfig.effectivePackName: String
    get() = packName ?: when (sourceType) {
        CatalogSourceType.PREINSTALLED -> "System Catalogs"
        CatalogSourceType.ADDON -> "Addon Catalogs"
        CatalogSourceType.TMDB -> "TMDB Catalogs"
    }

val CatalogConfig.isBulkDeletablePack: Boolean
    get() = packId != null &&
            packId != "system" &&
            packId != "addon" &&
            packId != "tmdb" &&
            packId != "individual"

data class CatalogDiscoveryResult(
    val id: String,
    val title: String,
    val description: String?,
    val sourceType: CatalogSourceType,
    val sourceUrl: String,
    val creatorName: String?,
    val creatorHandle: String?,
    val updatedAt: String?,
    val itemCount: Int?,
    val likes: Int?,
    val previewPosterUrls: List<String> = emptyList()
)

data class CatalogValidationResult(
    val isValid: Boolean,
    val normalizedUrl: String? = null,
    val sourceType: CatalogSourceType? = null,
    /** Resource of the validation error; resolved where it is displayed. */
    @param:StringRes val errorRes: Int? = null
)

data class CatalogPackManifest(
    val id: String?,
    val name: String?,
    val author: String? = null,
    val version: String? = null,
    val description: String? = null,
    val catalogs: List<CatalogPackItem>?
) : Serializable

data class CatalogPackItem(
    val name: String?,
    val url: String?
) : Serializable
