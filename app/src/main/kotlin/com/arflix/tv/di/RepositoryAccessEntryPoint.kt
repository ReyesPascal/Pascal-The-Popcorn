package com.arflix.tv.di

import com.arflix.tv.data.api.TmdbApi
import com.arflix.tv.data.repository.MediaRepository
import com.arflix.tv.data.repository.ProfileManager
import com.arflix.tv.data.repository.ReleaseCalendarRepository
import com.arflix.tv.data.repository.StreamRepository
import com.arflix.tv.data.repository.LocalWatchRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface RepositoryAccessEntryPoint {
    fun streamRepository(): StreamRepository
    fun mediaRepository(): MediaRepository
    fun profileManager(): ProfileManager
    fun tmdbApi(): TmdbApi
    fun releaseCalendarRepository(): ReleaseCalendarRepository

    /** The discover filter "hide watched" reads the watched lists from here. */
    fun localWatchRepository(): LocalWatchRepository
}
