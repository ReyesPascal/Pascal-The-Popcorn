package com.arflix.tv.data.repository

import com.arflix.tv.data.model.MediaType
import com.arflix.tv.data.model.isNotResumable
import com.arflix.tv.util.Constants
import java.time.Instant

/** Combines profile-scoped local resume data and cached presentation snapshots. */
internal object ContinueWatchingMerge {
    private fun ContinueWatchingItem.showKey() = "${mediaType}:${id}"

    private val localRecency = compareBy<ContinueWatchingItem> { it.updatedAtMs }
        .thenBy { it.resumePositionSeconds }
        .thenBy { it.progress }

    /** Reconcile saved local projections without dropping titles absent from an older snapshot. */
    fun merge(
        snapshotItems: List<ContinueWatchingItem>,
        localItems: List<ContinueWatchingItem>,
        historyItems: List<ContinueWatchingItem> = emptyList()
    ): List<ContinueWatchingItem> = (localItems + historyItems + snapshotItems)
        .groupBy { it.showKey() }
        .values.map { matches ->
            val newest = matches.maxWith(localRecency)
            matches.filter { it.season == newest.season && it.episode == newest.episode }
                .fold(newest, ::mergeVisuals)
        }.filter { item ->
            (item.id > 0 || !item.addonNativeId.isNullOrBlank()) &&
                !isNotResumable(id = item.id, isAddonNative = !item.addonNativeId.isNullOrBlank()) && item.progress < Constants.WATCHED_THRESHOLD &&
                (item.isUpNext || item.durationSeconds <= 0L ||
                    item.resumePositionSeconds.toDouble() / item.durationSeconds < Constants.WATCHED_THRESHOLD / 100.0) &&
                (item.isUpNext || item.progress >= Constants.MIN_PROGRESS_THRESHOLD || item.resumePositionSeconds >= 60L)
        }.sortedByDescending { it.updatedAtMs }

    fun fromHistory(entry: WatchHistoryEntry): ContinueWatchingItem {
        val storedPct = (entry.progress * 100f).toInt()
        val progress = when {
            storedPct > 0 -> storedPct
            entry.duration_seconds > 0 && entry.position_seconds > 0 ->
                (entry.position_seconds.toDouble() / entry.duration_seconds * 100).toInt()
            entry.position_seconds > 0 -> 1
            else -> 0
        }
        fun timestamp(value: String?) = value?.let {
            runCatching { Instant.parse(it).toEpochMilli() }.getOrDefault(0L)
        } ?: 0L
        return ContinueWatchingItem(
            id = entry.show_tmdb_id,
            title = entry.title?.trim()?.takeIf { it.isNotEmpty() }
                ?: entry.episode_title?.trim()?.takeIf { it.isNotEmpty() } ?: "Untitled",
            mediaType = if (entry.media_type == "tv") MediaType.TV else MediaType.MOVIE,
            progress = progress.coerceIn(0, 100),
            resumePositionSeconds = entry.position_seconds.coerceAtLeast(0L),
            durationSeconds = entry.duration_seconds.coerceAtLeast(0L),
            season = entry.season,
            episode = entry.episode,
            episodeTitle = entry.episode_title,
            backdropPath = entry.backdrop_path,
            posterPath = entry.poster_path,
            streamKey = entry.stream_key,
            streamAddonId = entry.stream_addon_id,
            streamTitle = entry.stream_title,
            updatedAtMs = maxOf(timestamp(entry.updated_at), timestamp(entry.paused_at))
        )
    }

    fun mergeVisuals(
        preferred: ContinueWatchingItem,
        fallback: ContinueWatchingItem
    ): ContinueWatchingItem {
        val sameEpisode = preferred.season == fallback.season && preferred.episode == fallback.episode
        return preferred.copy(
            title = preferred.title.ifBlank { fallback.title },
            episodeTitle = preferred.episodeTitle ?: fallback.episodeTitle,
            backdropPath = preferred.backdropPath ?: fallback.backdropPath,
            episodeStillPath = preferred.episodeStillPath ?: fallback.episodeStillPath.takeIf { sameEpisode },
            posterPath = preferred.posterPath ?: fallback.posterPath,
            streamKey = preferred.streamKey ?: fallback.streamKey,
            streamAddonId = preferred.streamAddonId ?: fallback.streamAddonId,
            streamTitle = preferred.streamTitle ?: fallback.streamTitle,
            addonNativeId = preferred.addonNativeId ?: fallback.addonNativeId,
            addonNativeAddonId = preferred.addonNativeAddonId ?: fallback.addonNativeAddonId,
            addonNativeType = preferred.addonNativeType ?: fallback.addonNativeType,
            year = preferred.year.ifBlank { fallback.year },
            releaseDate = preferred.releaseDate.ifBlank { fallback.releaseDate },
            overview = preferred.overview.ifBlank { fallback.overview },
            imdbRating = preferred.imdbRating.ifBlank { fallback.imdbRating },
            duration = preferred.duration.ifBlank { fallback.duration },
            durationSeconds = maxOf(preferred.durationSeconds, fallback.durationSeconds),
            budget = preferred.budget ?: fallback.budget,
            totalEpisodes = if (preferred.totalEpisodes > 0) preferred.totalEpisodes else fallback.totalEpisodes,
            watchedEpisodes = if (preferred.watchedEpisodes > 0) preferred.watchedEpisodes else fallback.watchedEpisodes,
            updatedAtMs = maxOf(preferred.updatedAtMs, fallback.updatedAtMs)
        )
    }
}
