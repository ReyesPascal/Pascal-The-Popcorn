package com.arflix.tv.data.merge

/** Coordinates at the metadata boundary. Everywhere else uses the merged IMDb numbering. */
object ShowMergeTable {
    data class Part(val tmdbId: Int, val sourceSeasons: IntRange, val seasonOffset: Int)
    data class Entry(val title: String, val imdbId: String, val primaryTmdbId: Int, val parts: List<Part>)
    data class SeasonSource(val tmdbId: Int, val season: Int)
    data class EpisodeNumber(val season: Int, val episode: Int)

    val bakeOff = Entry(
        title = "The Great British Bake Off",
        imdbId = "tt1877368",
        primaryTmdbId = 34549,
        parts = listOf(Part(34549, 1..7, 0), Part(87012, 1..10, 7))
    )
    val entries = listOf(bakeOff)

    fun entry(tmdbId: Int): Entry? = entries.firstOrNull { show -> show.parts.any { it.tmdbId == tmdbId } }
    fun canonicalId(tmdbId: Int): Int = entry(tmdbId)?.primaryTmdbId ?: tmdbId
    fun isSecondary(tmdbId: Int): Boolean = canonicalId(tmdbId) != tmdbId

    fun sourceSeason(tmdbId: Int, mergedSeason: Int): SeasonSource {
        val show = entry(tmdbId) ?: return SeasonSource(tmdbId, mergedSeason)
        val part = show.parts.firstOrNull { mergedSeason - it.seasonOffset in it.sourceSeasons }
            ?: return SeasonSource(show.primaryTmdbId, mergedSeason)
        return SeasonSource(part.tmdbId, mergedSeason - part.seasonOffset)
    }

    // BBC competition episodes end before the trailing Masterclass/Revisited episodes.
    fun isExtra(tmdbId: Int, season: Int, episode: Int): Boolean = tmdbId == 34549 &&
        season in 2..7 && episode > if (season == 2) 8 else 10

    /** specialsByAirDate comes from IMDb/Cinemeta season zero, never from list order. */
    fun imdbEpisode(
        tmdbId: Int, season: Int, episode: Int, airDate: String? = null,
        specialsByAirDate: Map<String, Int> = emptyMap()
    ): EpisodeNumber? {
        val show = entry(tmdbId) ?: return EpisodeNumber(season, episode)
        if (isExtra(tmdbId, season, episode) || season == 0) {
            return airDate?.takeIf { it.isNotBlank() }?.let(specialsByAirDate::get)?.let { EpisodeNumber(0, it) }
        }
        val part = show.parts.firstOrNull { it.tmdbId == tmdbId && season in it.sourceSeasons } ?: return null
        return EpisodeNumber(season + part.seasonOffset, episode)
    }
}
