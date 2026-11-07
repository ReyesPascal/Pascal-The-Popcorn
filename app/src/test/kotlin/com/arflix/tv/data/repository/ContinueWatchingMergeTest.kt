package com.arflix.tv.data.repository

import com.arflix.tv.data.model.MediaType
import org.junit.Assert.*
import org.junit.Test

class ContinueWatchingMergeTest {
    private val movie = ContinueWatchingItem(10, "Movie", MediaType.MOVIE, 20,
        resumePositionSeconds = 300, durationSeconds = 1500, updatedAtMs = 100)

    @Test fun partialHistoryPreservesEveryLocallySavedTitle() {
        val other = movie.copy(id = 20, updatedAtMs = 200)
        assertEquals(listOf(other, movie), ContinueWatchingMerge.merge(listOf(movie), listOf(movie, other)))
    }

    @Test fun emptyPresentationSnapshotKeepsLocalProgress() {
        assertEquals(listOf(movie), ContinueWatchingMerge.merge(emptyList(), listOf(movie)))
    }

    @Test fun newerSeekBackKeepsItsExactPosition() {
        val newer = movie.copy(progress = 10, resumePositionSeconds = 150, updatedAtMs = 200)
        assertEquals(newer, ContinueWatchingMerge.merge(listOf(movie), listOf(newer)).single())
    }

    @Test fun latestEpisodeWinsAndDoesNotInheritTheOldPosition() {
        val old = movie.copy(mediaType = MediaType.TV, season = 1, episode = 1)
        val next = old.copy(episode = 2, progress = 0, resumePositionSeconds = 0,
            durationSeconds = 0, isUpNext = true, updatedAtMs = 200)
        assertEquals(next, ContinueWatchingMerge.merge(listOf(old), listOf(next)).single())
    }

    @Test fun newerCompletionSuppressesOlderResumeHistory() {
        val completed = movie.copy(progress = 100, updatedAtMs = 200)
        assertTrue(ContinueWatchingMerge.merge(listOf(movie), listOf(completed)).isEmpty())
    }

    @Test fun unstartedAndInvalidItemsAreExcluded() {
        val unstarted = movie.copy(progress = 0, resumePositionSeconds = 0)
        val invalid = movie.copy(id = -1)
        assertTrue(ContinueWatchingMerge.merge(emptyList(), listOf(unstarted, invalid)).isEmpty())
    }

    @Test fun metadataFallbackPreservesTheSavedSource() {
        val saved = movie.copy(streamKey = "saved-source", streamAddonId = "addon")
        val newer = movie.copy(updatedAtMs = 200)
        val merged = ContinueWatchingMerge.merge(listOf(newer), listOf(saved)).single()
        assertEquals("saved-source", merged.streamKey)
        assertEquals(300L, merged.resumePositionSeconds)
    }

    @Test fun historyMappingKeepsExactPositionAndSource() {
        val mapped = ContinueWatchingMerge.fromHistory(WatchHistoryEntry(user_id = "local", media_type = "movie",
            show_tmdb_id = 10, title = "Movie", position_seconds = 300, duration_seconds = 1500,
            progress = .2f, stream_key = "source", paused_at = "2026-11-01T12:00:00Z"))
        assertEquals(300L, mapped.resumePositionSeconds)
        assertEquals("source", mapped.streamKey)
        assertTrue(mapped.updatedAtMs > 0)
    }
}
