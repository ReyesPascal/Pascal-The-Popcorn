package com.arflix.tv.data.repository

import com.arflix.tv.data.api.TmdbApi
import com.arflix.tv.data.api.TmdbListResponse
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Test

class DiscoverDateWindowTest {
    private val api = mockk<TmdbApi>(relaxed = true)
    private val repository = MediaRepository(mockk(relaxed = true), api, mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true))

    /** And the other half: the window the discover grid sends is the one the card prints. */
    @Test fun theDiscoverWrapperPassesThePremiereWindowOnItsOwnParameters() = runBlocking {
        coEvery {
            api.discoverMovies(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns TmdbListResponse(results = emptyList())

        repository.discoverMovies(
            primaryReleaseDateGte = "1990-01-01",
            primaryReleaseDateLte = "1999-12-31"
        )

        coVerify(exactly = 1) {
            api.discoverMovies(
                any(), genres = any(), crew = any(), sortBy = any(), minVoteCount = any(),
                minVoteAverage = any(), maxVoteAverage = any(), certificationCountry = any(),
                certificationLte = any(), keywords = any(), originalLanguage = any(), year = any(),
                releaseDateGte = null, releaseDateLte = null,
                primaryReleaseDateGte = "1990-01-01", primaryReleaseDateLte = "1999-12-31",
                watchProviders = any(), watchRegion = any(), language = any(), page = any()
            )
        }
    }
}
