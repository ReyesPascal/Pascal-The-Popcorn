package com.arflix.tv.data.repository

import com.arflix.tv.data.api.TvMazeApi
import com.arflix.tv.data.api.TvMazeShow
import com.arflix.tv.data.api.TmdbEpisode
import com.arflix.tv.data.model.CalendarEpisodeTime
import com.arflix.tv.data.model.MediaItem
import com.arflix.tv.data.model.MediaType
import com.google.gson.Gson
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.currentTime
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class TvMazeCalendarTest {
    private val title = CalendarWatchlistTitle(MediaItem(id = 1, title = "Show", mediaType = MediaType.TV), setOf("arvio"))
    private val episode = TmdbEpisode(id = 1, name = "First", seasonNumber = 1, episodeNumber = 1, airDate = "2026-11-04")

    @Test fun episodesJsonUsesTheViewersLocalDateAndTime() {
        val times = Gson().fromJson("""[{"season":1,"number":1,"airstamp":"2026-11-04T01:30:00+00:00"}]""", Array<CalendarEpisodeTime>::class.java).toList()
        val release = calendarEpisodeReleases(title, listOf(episode), times, YearMonth.of(2026, 11), ZoneId.of("America/Chicago")).single()
        val local = release.releaseInstant!!.atZone(ZoneId.of("America/Chicago"))
        assertEquals(LocalDate.of(2026, 11, 3), release.date)
        assertEquals(19, local.hour)
        assertEquals(30, local.minute)
    }

    @Test fun failedLookupsLeaveTheTmdbDateOnly() = runBlocking {
        val api = mockk<TvMazeApi>()
        coEvery { api.lookupShow(any(), any()) } throws java.io.IOException("missing")
        assertNull(lookupTvMazeShow(api, "tt123", 123))
        val release = calendarEpisodeReleases(title, listOf(episode), emptyList(), YearMonth.of(2026, 11), ZoneId.of("America/Chicago")).single()
        assertEquals(LocalDate.of(2026, 11, 4), release.date)
        assertNull(release.releaseInstant)
    }

    @Test fun tvdbIsUsedWhenImdbLookupFails() = runBlocking {
        val api = mockk<TvMazeApi>()
        coEvery { api.lookupShow(imdb = "tt123") } throws java.io.IOException("missing")
        coEvery { api.lookupShow(tvdb = 123) } returns TvMazeShow(42)
        assertEquals(42, lookupTvMazeShow(api, "tt123", 123)?.id)
    }
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test fun concurrentOptionalRequestsRespectTheServiceCadence() = runTest {
        val gate = TvMazeRequestGate { currentTime }
        val sent = mutableListOf<Long>()
        (1..8).map { async { gate.awaitTurn(); sent += currentTime } }.awaitAll()
        assertEquals((0L..7L).map { it * 550L }, sent)
    }

    @Test fun missingExternalIdsDoNotCallTheTimingService() = runBlocking {
        val api = mockk<TvMazeApi>()
        assertNull(lookupTvMazeShow(api, null, null))
        io.mockk.coVerify(exactly = 0) { api.lookupShow(any(), any()) }
    }
}
