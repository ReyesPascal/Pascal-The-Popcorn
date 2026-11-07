package com.arflix.tv.data.repository

import com.arflix.tv.data.api.TmdbApi
import com.arflix.tv.data.api.TmdbEpisode
import com.arflix.tv.data.api.TmdbMovieDetails
import com.arflix.tv.data.api.TmdbSeasonDetails
import com.arflix.tv.data.api.TmdbTvDetails
import com.arflix.tv.data.api.TmdbTvSeason
import com.arflix.tv.data.model.MediaItem
import com.arflix.tv.data.model.MediaType
import com.arflix.tv.data.model.ReleaseCalendarSource
import com.arflix.tv.util.Constants
import com.google.gson.Gson
import com.google.gson.JsonObject
import io.mockk.*
import java.io.IOException
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import okhttp3.ResponseBody.Companion.toResponseBody

@OptIn(ExperimentalCoroutinesApi::class)
class ReleaseCalendarRepositoryTest {
    private val tmdb = mockk<TmdbApi>()
    private val tvmaze = mockk<com.arflix.tv.data.api.TvMazeApi>()
    private val own = mockk<WatchlistRepository>()
    private val media = mockk<MediaRepository>()
    private val profiles = mockk<ProfileManager>()
    private val cache = mockk<ReleaseCalendarCache>()
    private lateinit var repository: ReleaseCalendarRepository
    private val month = YearMonth.of(2026, 10)

    @Before fun setup() {
        mockkObject(Constants)
        every { Constants.TMDB_API_KEY } returns "fixture-key"
        coEvery { tmdb.getTvExternalIds(any(), any()) } answers { com.arflix.tv.data.api.TmdbExternalIds(imdbId = "tt${firstArg<Int>()}") }
        coEvery { tvmaze.lookupShow(any(), any()) } answers { com.arflix.tv.data.api.TvMazeShow(firstArg<String>().removePrefix("tt").toInt()) }
        coEvery { tvmaze.getEpisodes(any()) } returns emptyList()
        every { profiles.getProfileIdSync() } returns "fixture-profile"
        coEvery { own.getLocalWatchlistItems() } returns listOf(MediaItem(1, "Own movie"))
        coEvery { media.getLogoUrl(any<MediaType>(), any()) } returns null
        coEvery { cache.readMetadata<Any>(any(), any()) } returns null
        coEvery { cache.readTitle(any()) } returns null
        coEvery { cache.writeTitle(any(), any()) } just Runs
        coEvery { cache.writeValue(any(), any()) } just Runs
        coEvery { cache.clearMetadata() } just Runs
        repository = ReleaseCalendarRepository(tmdb, tvmaze, own, media, profiles, cache)

    }

    @Test fun `failed TVmaze lookup silently keeps the confirmed TMDB date`() = runTest {
        val episode = TmdbEpisode(seasonNumber = 1, episodeNumber = 1, airDate = "2026-10-04")
        coEvery { tmdb.getTvDetails(5, any(), any(), any()) } returns TmdbTvDetails(5, "Show",
            nextEpisodeToAir = episode, seasons = listOf(TmdbTvSeason(seasonNumber = 1)))
        coEvery { tmdb.getTvSeason(5, 1, any(), any()) } returns TmdbSeasonDetails(episodes = listOf(episode))
        coEvery { tvmaze.lookupShow(any(), any()) } throws IOException("Unavailable")
        val result = repository.loadMonth(CalendarWatchlists("fixture-profile", mapOf(
            ReleaseCalendarSource.ARVIO to listOf(MediaItem(5, "Show", mediaType = MediaType.TV)))),
            month, ZoneId.of("America/Chicago"), "US")
        assertEquals(month.atDay(4), result.entries.single().date)
        assertNull(result.entries.single().releaseInstant)
        assertTrue(result.warnings.isEmpty())
        coVerify(exactly = 0) { tvmaze.getEpisodes(any()) }
    }

    @After fun tearDown() = unmockkAll()

    @Test fun `historical season cache pressure cannot evict an ended show summary`() = runTest {
        val lists = CalendarWatchlists("fixture-profile", mapOf(ReleaseCalendarSource.ARVIO to listOf(MediaItem(5, "Ended show", mediaType = MediaType.TV))))
        coEvery { tmdb.getTvDetails(5, any(), any(), any()) } returns TmdbTvDetails(5, "Ended show", status = "Ended",
            lastEpisodeToAir = TmdbEpisode(seasonNumber = 1, episodeNumber = 1, airDate = "2025-01-01"))
        repository.loadMonth(lists, month, ZoneId.of("UTC"), "US")
        val raw = ReleaseCalendarRepository::class.java.getDeclaredField("metadataCache").apply { isAccessible = true }
        (raw.get(repository) as MutableMap<*, *>).clear()
        repository.loadMonth(lists, month.plusMonths(1), ZoneId.of("UTC"), "US")
        coVerify(exactly = 1) { tmdb.getTvDetails(5, any(), any(), any()) }
    }

    @Test fun `verified schedules avoid repeating season walks for new months and expire normally`() = runTest {
        var now = System.currentTimeMillis()
        repository.clock = { now }
        val show = MediaItem(5, "Anime", mediaType = MediaType.TV)
        val lists = CalendarWatchlists("fixture-profile", mapOf(ReleaseCalendarSource.ARVIO to listOf(show)))
        coEvery { tmdb.getTvDetails(5, any(), any(), any()) } returns TmdbTvDetails(5, "Anime",
            seasons = listOf(TmdbTvSeason(seasonNumber = 1, airDate = "2026-10-01")))
        coEvery { tmdb.getTvSeason(5, 1, any(), any()) } returns TmdbSeasonDetails(seasonNumber = 1,
            episodes = listOf(TmdbEpisode(seasonNumber = 1, episodeNumber = 1, airDate = "2026-10-04"),
                TmdbEpisode(seasonNumber = 1, episodeNumber = 2, airDate = "2026-11-04")))
        assertEquals(1, repository.loadMonth(lists, month, ZoneId.of("UTC"), "US").entries.size)
        assertEquals(2, repository.loadMonth(lists, month.plusMonths(1), ZoneId.of("UTC"), "US").entries.single().episodeNumber)
        coVerify(exactly = 1) { tmdb.getTvSeason(5, 1, any(), any()) }
        coVerify(exactly = 1) { tmdb.getTvDetails(5, any(), any(), any()) }
        now += CALENDAR_ACTIVE_TTL + 1
        repository.loadMonth(lists, month, ZoneId.of("UTC"), "US")
        coVerify(exactly = 2) { tmdb.getTvSeason(5, 1, any(), any()) }
    }

    @Test fun `ended anime announced future season is not suppressed by cached old schedule`() = runTest {
        val show = MediaItem(5, "Anime", mediaType = MediaType.TV)
        val lists = CalendarWatchlists("fixture-profile", mapOf(ReleaseCalendarSource.ARVIO to listOf(show)))
        coEvery { tmdb.getTvDetails(5, any(), any(), any()) } returns TmdbTvDetails(5, "Anime", status = "Ended",
            lastEpisodeToAir = TmdbEpisode(seasonNumber = 1, episodeNumber = 1, airDate = "2026-10-04"),
            seasons = listOf(TmdbTvSeason(seasonNumber = 1, airDate = "2026-10-01"), TmdbTvSeason(seasonNumber = 2, airDate = "2026-11-01")))
        coEvery { tmdb.getTvSeason(5, any(), any(), any()) } answers {
            val season = secondArg<Int>()
            TmdbSeasonDetails(seasonNumber = season, episodes = listOf(TmdbEpisode(seasonNumber = season, episodeNumber = 1,
                airDate = if (season == 1) "2026-10-04" else "2026-11-04")))
        }
        repository.loadMonth(lists, month, ZoneId.of("UTC"), "US")
        assertEquals(2, repository.loadMonth(lists, month.plusMonths(1), ZoneId.of("UTC"), "US").entries.single().seasonNumber)
        coVerify(exactly = 1) { tmdb.getTvSeason(5, 2, any(), any()) }
    }

    @Test fun `retry only refetches failed titles and bypasses their negative cache`() = runTest {
        val lists = CalendarWatchlists("fixture-profile", mapOf(ReleaseCalendarSource.ARVIO to listOf(MediaItem(1, "Good"), MediaItem(2, "Recovering"))))
        coEvery { tmdb.getMovieDetails(1, any(), any(), any()) } returns TmdbMovieDetails(1, "Good", releaseDate = "2026-10-04")
        coEvery { tmdb.getMovieDetails(2, any(), any(), any()) } throws HttpException(Response.error<Any>(404, "Missing".toResponseBody()))
        val first = repository.loadMonth(lists, month, ZoneId.of("UTC"), "US")
        assertEquals(setOf(MediaType.MOVIE to 2), first.failedTitles)
        coEvery { tmdb.getMovieDetails(2, any(), any(), any()) } returns TmdbMovieDetails(2, "Recovered", releaseDate = "2026-10-04")
        val recovered = repository.loadMonth(lists, month, ZoneId.of("UTC"), "US", retryTitles = first.failedTitles)
        assertEquals(2, recovered.entries.size)
        assertTrue(recovered.failedTitles.isEmpty())
        coVerify(exactly = 1) { tmdb.getMovieDetails(1, any(), any(), any()) }
        coVerify(exactly = 2) { tmdb.getMovieDetails(2, any(), any(), any()) }
    }









    @Test fun `logo failure cannot remove confirmed releases`() = runTest {
        coEvery { tmdb.getMovieDetails(1, any(), any(), any()) } returns TmdbMovieDetails(1, "Own movie", releaseDate = "2026-10-04")
        coEvery { media.getLogoUrl(any<MediaType>(), any()) } throws IOException("Fixture logo 404")
        val result = repository.loadMonth(repository.loadWatchlists("fixture-profile"), month, ZoneId.of("UTC"), "NL")
        assertEquals(1, result.entries.size)
        assertNull(result.entries.single().logoUrl)
        assertTrue(result.warnings.isEmpty())
    }



    @Test fun `profile change rejects private watchlist result`() = runTest {
        coEvery { tmdb.getTvExternalIds(any(), any()) } answers { com.arflix.tv.data.api.TmdbExternalIds(imdbId = "tt${firstArg<Int>()}") }
        coEvery { tvmaze.lookupShow(any(), any()) } answers { com.arflix.tv.data.api.TvMazeShow(firstArg<String>().removePrefix("tt").toInt()) }
        coEvery { tvmaze.getEpisodes(any()) } returns emptyList()
        every { profiles.getProfileIdSync() } returns "other-profile"
        try {
            repository.loadWatchlists("fixture-profile")
            fail("Expected cancellation")
        } catch (_: CancellationException) {
            coVerify(exactly = 0) { own.getLocalWatchlistItems() }
        }
    }

    @Test fun `overlapping seasons and specials are not silently excluded`() = runTest {
        val show = MediaItem(5, "Show", mediaType = MediaType.TV)
        coEvery { tmdb.getTvDetails(5, any(), any(), any()) } returns TmdbTvDetails(5, "Show", seasons = listOf(
            TmdbTvSeason(seasonNumber = 0, airDate = "2020-01-01"),
            TmdbTvSeason(seasonNumber = 1, airDate = "2026-01-01"),
            TmdbTvSeason(seasonNumber = 2, airDate = "2026-09-01")
        ))
        coEvery { tmdb.getTvSeason(5, any(), any(), any()) } answers {
            val season = secondArg<Int>()
            TmdbSeasonDetails(seasonNumber = season, episodes = listOf(TmdbEpisode(seasonNumber = season, episodeNumber = 1, airDate = "2026-10-04")))
        }
        val result = repository.loadMonth(CalendarWatchlists("fixture-profile", mapOf(ReleaseCalendarSource.ARVIO to listOf(show))), month, ZoneId.of("UTC"), "US")
        assertEquals(setOf(0, 1, 2), result.entries.map { it.seasonNumber }.toSet())
    }

    @Test fun `twenty five seasons including specials need only two batch requests with identical releases`() = runTest {
        val show = MediaItem(5, "Long running show", mediaType = MediaType.TV)
        coEvery { tmdb.getTvDetails(5, any(), any(), any()) } returns TmdbTvDetails(5, "Long running show",
            seasons = (0..24).map { TmdbTvSeason(seasonNumber = it, airDate = "2020-01-01") })
        val batches = mutableListOf<List<Int>>()
        coEvery { tmdb.getTvSeasons(5, any(), any(), "en-US") } answers {
            val seasons = thirdArg<String>().split(',').map { it.substringAfter('/').toInt() }
            batches += seasons
            JsonObject().apply {
                seasons.forEach { season -> add("season/$season", Gson().toJsonTree(TmdbSeasonDetails(seasonNumber = season,
                    episodes = listOf(TmdbEpisode(seasonNumber = season, episodeNumber = 1, airDate = "2026-10-04"))))) }
            }
        }
        val lists = CalendarWatchlists("fixture-profile", mapOf(ReleaseCalendarSource.ARVIO to listOf(show)))
        val result = repository.loadMonth(lists, month, ZoneId.of("UTC"), "US")
        assertEquals((0..24).toSet(), result.entries.map { it.seasonNumber }.toSet())
        assertEquals(listOf(20, 5), batches.map { it.size })
        assertTrue(result.warnings.isEmpty())
        repository.loadMonth(lists, month.plusMonths(1), ZoneId.of("UTC"), "US")
        coVerify(exactly = 2) { tmdb.getTvSeasons(any(), any(), any(), any()) }
        coVerify(exactly = 0) { tmdb.getTvSeason(any(), any(), any(), any()) }
        coVerify(exactly = 25) { cache.writeValue(match { it.startsWith("season:") }, any()) }
    }

    @Test fun `partial season batch retries only missing blocks and preserves all dates`() = runTest {
        val show = MediaItem(5, "Show", mediaType = MediaType.TV)
        coEvery { tmdb.getTvDetails(5, any(), any(), any()) } returns TmdbTvDetails(5, "Show",
            seasons = (0..2).map { TmdbTvSeason(seasonNumber = it) })
        fun season(number: Int) = TmdbSeasonDetails(seasonNumber = number,
            episodes = listOf(TmdbEpisode(seasonNumber = number, airDate = "2026-10-04")))
        coEvery { tmdb.getTvSeasons(5, any(), any(), any()) } returns JsonObject().apply {
            add("season/2", Gson().toJsonTree(season(2)))
            addProperty("season/1", "invalid")
        }
        coEvery { tmdb.getTvSeason(5, any(), any(), any()) } answers { season(secondArg()) }
        val result = repository.loadMonth(CalendarWatchlists("fixture-profile", mapOf(ReleaseCalendarSource.ARVIO to listOf(show))),
            month, ZoneId.of("UTC"), "US")
        assertEquals(setOf(0, 1, 2), result.entries.map { it.seasonNumber }.toSet())
        coVerify(exactly = 1) { tmdb.getTvSeason(5, 0, any(), any()) }
        coVerify(exactly = 1) { tmdb.getTvSeason(5, 1, any(), any()) }
        coVerify(exactly = 0) { tmdb.getTvSeason(5, 2, any(), any()) }
    }

    @Test fun `batched seasons reuse individual disk cache and respect the five request limit`() = runTest {
        var inFlight = 0
        var peak = 0
        coEvery { tmdb.getTvDetails(any(), any(), any(), any()) } answers {
            TmdbTvDetails(firstArg(), "Show", seasons = (0..2).map { TmdbTvSeason(seasonNumber = it) })
        }
        coEvery { cache.readMetadata<TmdbSeasonDetails>(match { it.startsWith("season:") && it.contains(":0:") }, any()) } returns
            CalendarMetadataRecord(TmdbSeasonDetails(seasonNumber = 0, episodes = listOf(TmdbEpisode(seasonNumber = 0, airDate = "2026-10-04"))), System.currentTimeMillis())
        coEvery { tmdb.getTvSeasons(any(), any(), any(), any()) } coAnswers {
            assertEquals("season/2,season/1", thirdArg<String>())
            inFlight++
            peak = maxOf(peak, inFlight)
            delay(10)
            inFlight--
            JsonObject().apply { (1..2).forEach { add("season/$it", Gson().toJsonTree(TmdbSeasonDetails(seasonNumber = it))) } }
        }
        val lists = CalendarWatchlists("fixture-profile", mapOf(ReleaseCalendarSource.ARVIO to
            (1..12).map { MediaItem(it, "Show $it", mediaType = MediaType.TV) }))
        val result = repository.loadMonth(lists, month, ZoneId.of("UTC"), "US")
        assertEquals(12, result.entries.size)
        assertTrue(peak <= 2)
        coVerify(exactly = 12) { tmdb.getTvSeasons(any(), any(), any(), any()) }
        coVerify(exactly = 0) { tmdb.getTvSeason(any(), any(), any(), any()) }
    }

    @Test fun `large watchlist uses at most five parallel metadata requests and reuses cache`() = runTest {
        var inFlight = 0
        var peak = 0
        coEvery { tmdb.getMovieDetails(any(), any(), any(), any()) } coAnswers {
            inFlight++
            peak = maxOf(peak, inFlight)
            delay(10)
            inFlight--
            TmdbMovieDetails(firstArg(), "Movie", releaseDate = "2026-10-04")
        }
        val lists = CalendarWatchlists("fixture-profile", mapOf(ReleaseCalendarSource.ARVIO to (1..18).map { MediaItem(it, "Movie $it") }))
        val partialSizes = mutableListOf<Int>()
        assertEquals(18, repository.loadMonth(lists, month, ZoneId.of("UTC"), "US", onProgress = { partialSizes += it.entries.size }).entries.size)
        assertTrue(partialSizes.any { it in 1..17 })
        assertEquals(5, peak)
        repository.loadMonth(lists, month.plusMonths(1), ZoneId.of("UTC"), "US")
        coVerify(exactly = 18) { tmdb.getMovieDetails(any(), any(), any(), any()) }
    }

    @Test fun `current show releases are prioritized ahead of a large old movie backlog`() = runTest {
        val show = MediaItem(500, "Current show", mediaType = MediaType.TV)
        coEvery { tmdb.getTvDetails(500, any(), any(), any()) } returns TmdbTvDetails(500, "Current show",
            nextEpisodeToAir = TmdbEpisode(airDate = "2026-10-04"))
        coEvery { tmdb.getMovieDetails(any(), any(), any(), any()) } coAnswers {
            delay(1_000)
            TmdbMovieDetails(firstArg(), "Old movie", releaseDate = "2000-01-01")
        }
        val updates = mutableListOf<Pair<Long, CalendarMonthResult>>()
        val lists = CalendarWatchlists("fixture-profile", mapOf(ReleaseCalendarSource.ARVIO to
            (1..100).map { MediaItem(it, "Old movie") } + show))
        val request = async { repository.loadMonth(lists, month, ZoneId.of("UTC"), "US") { updates += currentTime to it } }
        runCurrent()
        assertTrue(updates.any { (time, result) -> time == 0L && result.entries.any { it.media.id == 500 } })
        assertFalse(request.isCompleted)
        request.cancelAndJoin()
    }

    @Test fun `confirmed missing metadata does not refetch until explicit refresh`() = runTest {
        coEvery { tmdb.getMovieDetails(1, any(), any(), any()) } throws
            HttpException(Response.error<TmdbMovieDetails>(404, "{}".toResponseBody()))
        val lists = repository.loadWatchlists("fixture-profile")
        repeat(2) {
            assertEquals(1, repository.loadMonth(lists, month, ZoneId.of("UTC"), "US").warnings.size)
        }
        coVerify(exactly = 1) { tmdb.getMovieDetails(1, any(), any(), any()) }
        repository.invalidateMetadata()
        repository.loadMonth(lists, month, ZoneId.of("UTC"), "US")
        coVerify(exactly = 2) { tmdb.getMovieDetails(1, any(), any(), any()) }
    }



    @Test fun `localized metadata cache cannot leak the previous language`() = runTest {
        coEvery { tmdb.getMovieDetails(1, any(), any(), "en-US") } returns TmdbMovieDetails(1, "English title", releaseDate = "2026-10-04")
        coEvery { tmdb.getMovieDetails(1, any(), any(), "nl-NL") } returns TmdbMovieDetails(1, "Nederlandse titel", releaseDate = "2026-10-04")
        val lists = repository.loadWatchlists("fixture-profile")
        assertEquals("English title", repository.loadMonth(lists, month, ZoneId.of("UTC"), "US", "en-US").entries.single().media.title)
        assertEquals("Nederlandse titel", repository.loadMonth(lists, month, ZoneId.of("UTC"), "NL", "nl-NL").entries.single().media.title)
        coVerify(exactly = 2) { tmdb.getMovieDetails(any(), any(), any(), any()) }
    }







    @Test fun `unavailable timing service does not repeat the timeout for every queued show`() = runTest {
        val shows = (1..12).map { MediaItem(it, "Show $it", mediaType = MediaType.TV) }
        coEvery { tmdb.getTvDetails(any(), any(), any(), any()) } answers {
            TmdbTvDetails(firstArg(), "Show", seasons = listOf(TmdbTvSeason(seasonNumber = 1)))
        }
        coEvery { tmdb.getTvSeason(any(), 1, any(), any()) } returns TmdbSeasonDetails(
            episodes = listOf(TmdbEpisode(seasonNumber = 1, episodeNumber = 1, airDate = "2026-10-04")))
        coEvery { tvmaze.getEpisodes(any()) } coAnswers {
            delay(20_000)
            emptyList()
        }
        val result = repository.loadMonth(CalendarWatchlists("fixture-profile", mapOf(ReleaseCalendarSource.ARVIO to shows)),
            month, ZoneId.of("UTC"), "US")
        assertEquals(12, result.entries.size)
        assertTrue(result.entries.all { it.releaseInstant == null })
        assertEquals(4_000L, currentTime)
        coVerify(atMost = 2) { tvmaze.getEpisodes(any()) }
    }



    @Test fun `next episode from detail appears while historical seasons are still loading`() = runTest {
        val show = MediaItem(5, "Show", mediaType = MediaType.TV)
        val episode = TmdbEpisode(seasonNumber = 2, episodeNumber = 1, airDate = "2026-10-04")
        coEvery { tmdb.getTvDetails(5, any(), any(), any()) } returns TmdbTvDetails(5, "Show",
            seasons = listOf(TmdbTvSeason(seasonNumber = 1)), nextEpisodeToAir = episode)
        coEvery { tmdb.getTvSeason(5, 1, any(), any()) } coAnswers {
            delay(10_000)
            TmdbSeasonDetails(episodes = listOf(TmdbEpisode(airDate = "2025-01-01")))
        }
        val updates = mutableListOf<CalendarMonthResult>()
        val request = async {
            repository.loadMonth(CalendarWatchlists("fixture-profile", mapOf(ReleaseCalendarSource.ARVIO to listOf(show))),
                month, ZoneId.of("UTC"), "US") { updates += it }
        }
        runCurrent()
        assertFalse(request.isCompleted)
        assertEquals(2, updates.last().entries.single().seasonNumber)
        advanceUntilIdle()
        assertEquals(1, request.await().entries.size)
    }

    @Test fun `stalled time enrichment cannot starve healthy clearlogos`() = runTest {
        val shows = listOf(MediaItem(5, "Show A", mediaType = MediaType.TV),
            MediaItem(6, "Show B", mediaType = MediaType.TV))
        coEvery { tmdb.getTvDetails(any(), any(), any(), any()) } answers {
            TmdbTvDetails(firstArg(), "Show", seasons = listOf(TmdbTvSeason(seasonNumber = 1)))
        }
        coEvery { tmdb.getTvSeason(any(), 1, any(), any()) } returns TmdbSeasonDetails(
            episodes = listOf(TmdbEpisode(airDate = "2026-10-04")))
        coEvery { tvmaze.getEpisodes(any()) } coAnswers {
            delay(20_000)
            emptyList()
        }
        coEvery { tmdb.getMovieDetails(1, any(), any(), any()) } coAnswers {
            delay(1)
            TmdbMovieDetails(1, "Movie", releaseDate = "2026-10-04")
        }
        coEvery { media.getLogoUrl(any<MediaType>(), any()) } returns "https://images.example/logo.png"
        val updates = mutableListOf<Pair<Long, CalendarMonthResult>>()
        repository.loadMonth(CalendarWatchlists("fixture-profile",
            mapOf(ReleaseCalendarSource.ARVIO to shows + MediaItem(1, "Movie"))),
            month, ZoneId.of("UTC"), "US") { updates += currentTime to it }
        assertTrue(updates.any { (time, result) -> time == 1L && result.entries.any { it.media.id == 1 && it.logoUrl != null } })
        assertEquals(4_000L, currentTime)
    }

    @Test fun `profile switch prevents later provider or metadata publications`() = runTest {
        var profile = "fixture-profile"
        coEvery { tmdb.getTvExternalIds(any(), any()) } answers { com.arflix.tv.data.api.TmdbExternalIds(imdbId = "tt${firstArg<Int>()}") }
        coEvery { tvmaze.lookupShow(any(), any()) } answers { com.arflix.tv.data.api.TvMazeShow(firstArg<String>().removePrefix("tt").toInt()) }
        coEvery { tvmaze.getEpisodes(any()) } returns emptyList()
        every { profiles.getProfileIdSync() } answers { profile }
        coEvery { tmdb.getMovieDetails(1, any(), any(), any()) } coAnswers {
            delay(1_000)
            TmdbMovieDetails(1, "Own movie", releaseDate = "2026-10-04")
        }
        val updates = mutableListOf<CalendarLoadProgress>()
        val request = async {
            repository.loadCalendar("fixture-profile", month, ZoneId.of("UTC"), "US") { updates += it }
        }
        runCurrent()
        val beforeSwitch = updates.size
        profile = "other-profile"
        advanceUntilIdle()
        try {
            request.await()
            fail("Expected profile cancellation")
        } catch (_: CancellationException) {
            assertEquals(beforeSwitch, updates.size)
        }
    }

    @Test fun `canceling streamed month stops remaining title publications`() = runTest {
        coEvery { tmdb.getMovieDetails(1, any(), any(), any()) } coAnswers {
            delay(10_000)
            TmdbMovieDetails(1, "Own movie", releaseDate = "2026-10-04")
        }
        val updates = mutableListOf<CalendarLoadProgress>()
        val request = launch {
            repository.loadCalendar("fixture-profile", month, ZoneId.of("UTC"), "US") { updates += it }
        }
        runCurrent()
        val beforeCancel = updates.size
        request.cancelAndJoin()
        advanceUntilIdle()
        assertEquals(beforeCancel, updates.size)
        assertTrue(updates.all { it.month.entries.isEmpty() })
    }

    @Test fun `public metadata restored from disk needs no network after repository recreation`() = runTest {
        coEvery { cache.readMetadata<TmdbMovieDetails>("movie:1:en-US", any()) } returns
            CalendarMetadataRecord(TmdbMovieDetails(1, "Saved movie", releaseDate = "2026-10-04"), System.currentTimeMillis())
        val result = repository.loadMonth(repository.loadWatchlists("fixture-profile"), month, ZoneId.of("UTC"), "US")
        assertEquals("Saved movie", result.entries.single().media.title)
        coVerify(exactly = 0) { tmdb.getMovieDetails(any(), any(), any(), any()) }
    }

    @Test fun `concurrent month loads share a single metadata request`() = runTest {
        coEvery { tmdb.getMovieDetails(1, any(), any(), any()) } coAnswers {
            delay(1_000)
            TmdbMovieDetails(1, "Shared movie", releaseDate = "2026-10-04")
        }
        val lists = repository.loadWatchlists("fixture-profile")
        val first = async { repository.loadMonth(lists, month, ZoneId.of("UTC"), "US") }
        val second = async { repository.loadMonth(lists, month.plusMonths(1), ZoneId.of("UTC"), "US") }
        assertEquals(1, first.await().entries.size)
        assertTrue(second.await().entries.isEmpty())
        coVerify(exactly = 1) { tmdb.getMovieDetails(1, any(), any(), any()) }
    }



    @Test fun `completed title metadata replaces obsolete cached release dates`() = runTest {
        val movie = MediaItem(99, "Rescheduled movie")
        val saved = com.arflix.tv.data.model.CalendarRelease("old-date", movie, month.atDay(4),
            kind = com.arflix.tv.data.model.CalendarReleaseKind.MOVIE, sourceIds = setOf("arvio"))
        val corrected = saved.copy(id = "new-date", date = month.atDay(12))
        val update = CalendarLoadProgress(CalendarWatchlists("fixture-profile", mapOf(ReleaseCalendarSource.ARVIO to listOf(movie))),
            CalendarMonthResult(listOf(corrected), emptyList()), true, setOf(MediaType.MOVIE to 99))
        assertEquals(listOf(corrected), mergeCalendarPreview(listOf(saved), update))
    }








}
