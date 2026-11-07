package com.arflix.tv.data.api

import com.arflix.tv.data.model.CalendarEpisodeTime
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface TvMazeApi {
    @GET("lookup/shows")
    suspend fun lookupShow(@Query("imdb") imdb: String? = null, @Query("thetvdb") tvdb: Int? = null): TvMazeShow

    @GET("shows/{id}/episodes")
    suspend fun getEpisodes(@Path("id") showId: Int): List<CalendarEpisodeTime>
}

data class TvMazeShow(val id: Int)
