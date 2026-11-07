package com.arflix.tv.data.merge

import com.arflix.tv.data.repository.buildTmdbEpisodeIdCandidate
import com.arflix.tv.data.api.TmdbApi
import com.arflix.tv.data.api.TmdbEpisode
import com.arflix.tv.data.api.TmdbSeasonDetails
import com.arflix.tv.data.api.TmdbTvDetails
import com.arflix.tv.data.api.TmdbTvSeason
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class ShowMergeTableTest {
    @Test fun `Channel 4 season one is IMDb season eight in both directions`() {
        assertEquals(ShowMergeTable.EpisodeNumber(8, 1), ShowMergeTable.imdbEpisode(87012, 1, 1))
        assertEquals(ShowMergeTable.SeasonSource(87012, 1), ShowMergeTable.sourceSeason(34549, 8))
    }

    @Test fun `2026 pastry week uses season seventeen not ten`() {
        assertEquals(ShowMergeTable.EpisodeNumber(17, 6), ShowMergeTable.imdbEpisode(87012, 10, 6))
        assertEquals(ShowMergeTable.SeasonSource(87012, 10), ShowMergeTable.sourceSeason(34549, 17))
    }

    @Test fun `BBC regular episode retains its numbers`() {
        assertEquals(ShowMergeTable.EpisodeNumber(3, 2), ShowMergeTable.imdbEpisode(34549, 3, 2))
        assertEquals(ShowMergeTable.SeasonSource(34549, 3), ShowMergeTable.sourceSeason(34549, 3))
    }

    @Test fun `BBC trailing extra is resolved by air date not trailing episode number`() {
        val dates = mapOf("2012-10-22" to 5, "2012-10-23" to 6)
        assertEquals(ShowMergeTable.EpisodeNumber(0, 5),
            ShowMergeTable.imdbEpisode(34549, 3, 11, "2012-10-22", dates))
        assertEquals(ShowMergeTable.EpisodeNumber(0, 6),
            ShowMergeTable.imdbEpisode(34549, 3, 11, "2012-10-23", dates))
        assertNull(ShowMergeTable.imdbEpisode(34549, 3, 11))
        assertNull(ShowMergeTable.imdbEpisode(34549, 3, 11, "unknown", dates))
        assertTrue(ShowMergeTable.isExtra(34549, 2, 9))
        assertFalse(ShowMergeTable.isExtra(34549, 3, 10))
    }

    @Test fun `unrelated shows are untouched`() {
        assertEquals(ShowMergeTable.EpisodeNumber(10, 6), ShowMergeTable.imdbEpisode(123, 10, 6))
        assertEquals(ShowMergeTable.SeasonSource(123, 10), ShowMergeTable.sourceSeason(123, 10))
        assertEquals(123, ShowMergeTable.canonicalId(123))
        assertFalse(ShowMergeTable.isSecondary(123))
        assertEquals(34549, ShowMergeTable.canonicalId(87012))
    }

    @Test fun `TMDB source fallback translates back while IMDb keeps merged numbering`() {
        assertEquals("tmdb:87012:10:6", buildTmdbEpisodeIdCandidate(34549, 17, 6, true))
        assertEquals("tmdb:34549:3:2", buildTmdbEpisodeIdCandidate(34549, 3, 2, true))
        assertNull(buildTmdbEpisodeIdCandidate(34549, 0, 5, true))
        assertEquals("tmdb:123:17:6", buildTmdbEpisodeIdCandidate(123, 17, 6, true))
    }

    @Test fun `all regular seasons round trip without a double offset`() {
        for (season in 1..17) {
            val source = ShowMergeTable.sourceSeason(34549, season)
            assertEquals(ShowMergeTable.EpisodeNumber(season, 1),
                ShowMergeTable.imdbEpisode(source.tmdbId, source.season, 1))
        }
    }

    @Test fun `metadata keeps primary art and description and merges seventeen seasons`() = runTest {
        val api = mockk<TmdbApi>()
        coEvery { api.getTvDetails(34549, "key", language = null) } returns TmdbTvDetails(
            id = 34549, name = "BBC title", overview = "BBC overview", posterPath = "bbc.jpg",
            seasons = (1..7).map { TmdbTvSeason(seasonNumber = it, episodeCount = 10) })
        coEvery { api.getTvDetails(87012, "key", language = null) } returns TmdbTvDetails(
            id = 87012, name = "C4 title", seasons = (1..10).map { TmdbTvSeason(seasonNumber = it, episodeCount = 10) })
        val details = api.getMergedTvDetails(87012, "key")
        assertEquals(34549, details.id)
        assertEquals("BBC title", details.name)
        assertEquals("BBC overview", details.overview)
        assertEquals("bbc.jpg", details.posterPath)
        assertEquals(17, details.numberOfSeasons)
        assertEquals((1..17).toList(), details.seasons.map { it.seasonNumber })
    }

    @Test fun `episode metadata returns merged coordinates for playback and local history`() = runTest {
        val api = mockk<TmdbApi>()
        coEvery { api.getTvSeason(87012, 10, "key", null) } returns TmdbSeasonDetails(
            seasonNumber = 10, episodes = listOf(TmdbEpisode(seasonNumber = 10, episodeNumber = 6, name = "Pastry Week")))
        val episode = api.getMergedTvSeason(34549, 17, "key").episodes.single()
        assertEquals(17, episode.seasonNumber)
        assertEquals(6, episode.episodeNumber)
        assertEquals("Pastry Week", episode.name)
    }

    @Test fun `trailing extras appear only in merged season zero by air date`() = runTest {
        val api = mockk<TmdbApi>()
        val regular = TmdbEpisode(seasonNumber = 3, episodeNumber = 5, name = "Regular episode")
        val extra = TmdbEpisode(seasonNumber = 3, episodeNumber = 11,
            name = "Masterclass", airDate = "2012-10-22")
        for (season in listOf(0) + (2..7)) {
            coEvery { api.getTvSeason(34549, season, "key", null) } returns TmdbSeasonDetails(
                seasonNumber = season, episodes = if (season == 3) listOf(extra, regular) else emptyList())
        }
        mockkObject(BakeOffSpecials)
        try {
            coEvery { BakeOffSpecials.byAirDate() } returns mapOf("2012-10-22" to 5)
            for (tvId in listOf(34549, 87012)) {
                val season = api.getMergedTvSeason(tvId, 3, "key")
                assertEquals(3, season.seasonNumber)
                assertTrue(season.episodes.none { it.seasonNumber == 0 || it.name == "Masterclass" })
                assertEquals(listOf(regular), season.episodes)
                val specials = api.getMergedTvSeason(tvId, 0, "key")
                assertEquals(0, specials.seasonNumber)
                assertEquals(listOf(extra.copy(seasonNumber = 0, episodeNumber = 5)), specials.episodes)
            }
        } finally {
            unmockkObject(BakeOffSpecials)
        }
    }

    @Test fun `merged details preserve primary season zero and drop continuation season zero`() = runTest {
        val api = mockk<TmdbApi>()
        val specials = TmdbTvSeason(id = 42, seasonNumber = 0, episodeCount = 3,
            name = "BBC specials", overview = "Special episodes", posterPath = "specials.jpg", airDate = "2012-10-22")
        coEvery { api.getTvDetails(34549, "key", language = null) } returns TmdbTvDetails(
            id = 34549, seasons = listOf(specials) + (1..7).map { TmdbTvSeason(seasonNumber = it) })
        coEvery { api.getTvDetails(87012, "key", language = null) } returns TmdbTvDetails(
            id = 87012, seasons = (0..10).map { TmdbTvSeason(seasonNumber = it) })
        for (tvId in listOf(34549, 87012)) {
            val details = api.getMergedTvDetails(tvId, "key")
            assertEquals(17, details.numberOfSeasons)
            assertEquals((0..17).toList(), details.seasons.map { it.seasonNumber })
            assertEquals(specials, details.seasons.single { it.seasonNumber == 0 })
        }
    }
}
