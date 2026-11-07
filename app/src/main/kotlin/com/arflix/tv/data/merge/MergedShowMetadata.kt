package com.arflix.tv.data.merge

import com.arflix.tv.data.api.TmdbApi
import com.arflix.tv.data.api.TmdbSeasonDetails
import com.arflix.tv.data.api.TmdbTvDetails
import com.arflix.tv.network.OkHttpProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Request
import org.json.JSONObject

/** Only these metadata requests use source TMDB coordinates. Callers retain IMDb coordinates. */
suspend fun TmdbApi.getMergedTvDetails(
    tvId: Int, apiKey: String, language: String? = null
): TmdbTvDetails {
    val show = ShowMergeTable.entry(tvId) ?: return getTvDetails(tvId, apiKey, language = language)
    val primary = getTvDetails(show.primaryTmdbId, apiKey, language = language)
    val continuation = getTvDetails(show.parts.last().tmdbId, apiKey, language = language)
    val seasons = primary.seasons.filter { it.seasonNumber in 0..7 } +
        continuation.seasons.filter { it.seasonNumber in 1..10 }.map {
            it.copy(seasonNumber = it.seasonNumber + 7)
        }
    return primary.copy(numberOfSeasons = 17, seasons = seasons,
        numberOfEpisodes = seasons.sumOf { it.episodeCount })
}

suspend fun TmdbApi.getMergedTvSeason(
    tvId: Int, seasonNumber: Int, apiKey: String, language: String? = null
): TmdbSeasonDetails {
    if (ShowMergeTable.entry(tvId) == null) return getTvSeason(tvId, seasonNumber, apiKey, language)
    val source = ShowMergeTable.sourceSeason(tvId, seasonNumber)
    val season = getTvSeason(source.tmdbId, source.season, apiKey, language)
    val sourceEpisodes = if (seasonNumber == 0) {
        season.episodes + (2..7).flatMap { s ->
            getTvSeason(34549, s, apiKey, language).episodes.filter {
                ShowMergeTable.isExtra(34549, s, it.episodeNumber)
            }
        }
    } else season.episodes
    val needsSpecials = sourceEpisodes.any { it.seasonNumber == 0 ||
        ShowMergeTable.isExtra(source.tmdbId, it.seasonNumber, it.episodeNumber) }
    val specials = if (needsSpecials) {
        try { BakeOffSpecials.byAirDate() } catch (e: CancellationException) { throw e }
        catch (_: Exception) { emptyMap() }
    } else emptyMap()
    val episodes = sourceEpisodes.mapNotNull { episode ->
        val number = ShowMergeTable.imdbEpisode(source.tmdbId, episode.seasonNumber,
            episode.episodeNumber, episode.airDate, specials) ?: return@mapNotNull null
        if (seasonNumber != 0 && number.season != seasonNumber) return@mapNotNull null
        episode.copy(seasonNumber = number.season, episodeNumber = number.episode)
    }.distinctBy { it.seasonNumber to it.episodeNumber }
    return season.copy(seasonNumber = seasonNumber, episodes = episodes)
}

internal object BakeOffSpecials {
    private val mutex = Mutex()
    private var cached: Map<String, Int>? = null

    suspend fun byAirDate(): Map<String, Int> = mutex.withLock {
        cached?.let { return@withLock it }
        // Runtime metadata only. Missing or ambiguous dates must never select a regular episode.
        val result = withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("https://v3-cinemeta.strem.io/meta/series/${ShowMergeTable.bakeOff.imdbId}.json").build()
            OkHttpProvider.client.newCall(request).execute().use { response ->
                check(response.isSuccessful) { "Bake Off specials metadata unavailable" }
                val videos = JSONObject(response.body?.string().orEmpty()).getJSONObject("meta").getJSONArray("videos")
                val dated = (0 until videos.length()).map { videos.getJSONObject(it) }
                    .filter { it.optInt("season", -1) == 0 }
                    .mapNotNull { video ->
                        val date = video.optString("released").take(10)
                        val episode = video.optInt("episode", -1)
                        if (date.length == 10 && episode > 0) date to episode else null
                    }
                dated.groupBy({ it.first }, { it.second }).filterValues { it.distinct().size == 1 }
                    .mapValues { it.value.first() }
            }
        }
        cached = result
        result
    }
}
