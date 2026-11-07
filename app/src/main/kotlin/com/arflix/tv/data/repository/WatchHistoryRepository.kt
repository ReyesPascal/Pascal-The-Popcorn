package com.arflix.tv.data.repository

import com.arflix.tv.data.model.MediaType
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A watch-history entry (the shape Continue Watching and episode progress read).
 */
@Serializable
data class WatchHistoryEntry(
    val id: String? = null,
    val user_id: String,
    val profile_id: String? = null,
    val media_type: String, // "movie" or "tv"
    val show_tmdb_id: Int,
    val season: Int? = null,
    val episode: Int? = null,
    val tmdb_episode_id: Int? = null,
    val title: String? = null,
    val episode_title: String? = null,
    val progress: Float = 0f, // 0.0-1.0
    val duration_seconds: Long = 0,
    val position_seconds: Long = 0,
    val paused_at: String? = null,
    val updated_at: String? = null,
    val source: String? = null,
    val backdrop_path: String? = null,
    val poster_path: String? = null,
    val stream_key: String? = null,
    val stream_addon_id: String? = null,
    val stream_title: String? = null
)

/**
 * Server-side watch history is gone (it only existed for the cloud account). The app keeps its
 * Continue Watching, progress and watched marks locally in [LocalWatchRepository]'s local store.
 *
 * This class keeps its old public surface so the callers keep compiling, but it holds no data:
 * reads return nothing and writes are ignored. That is exactly what it did in the personal build
 * before, where no account was ever signed in.
 */
@Singleton
class WatchHistoryRepository @Inject constructor() {

    suspend fun saveProgress(
        mediaType: MediaType,
        tmdbId: Int,
        title: String,
        poster: String?,
        backdrop: String?,
        season: Int?,
        episode: Int?,
        episodeTitle: String?,
        progress: Float,
        duration: Long,
        position: Long,
        streamKey: String? = null,
        streamAddonId: String? = null,
        streamTitle: String? = null,
        sessionStartTime: Long = 0L
    ) = Unit

    suspend fun getWatchHistory(): List<WatchHistoryEntry> = emptyList()

    suspend fun getContinueWatching(): List<WatchHistoryEntry> = emptyList()

    suspend fun getProgress(
        mediaType: MediaType,
        tmdbId: Int,
        season: Int?,
        episode: Int?
    ): WatchHistoryEntry? = null

    suspend fun getLatestProgress(mediaType: MediaType, tmdbId: Int): WatchHistoryEntry? = null

    suspend fun removeFromHistory(tmdbId: Int, season: Int?, episode: Int?) = Unit

    suspend fun clearHistory() = Unit

    fun clearProfileCaches() = Unit
}
