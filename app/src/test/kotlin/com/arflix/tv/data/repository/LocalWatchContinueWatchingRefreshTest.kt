package com.arflix.tv.data.repository

import android.app.Application
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import com.arflix.tv.data.api.*
import com.arflix.tv.util.settingsDataStore
import com.arflix.tv.util.watchDataStore
import com.google.gson.Gson
import io.mockk.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import javax.inject.Provider

/**
 * A profile without LocalWatch or Cloud keeps its watched history only in the local snapshot. A mark
 * made before anything loaded the watched cache (right after launch or a profile switch) must add
 * to that history, not replace it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
class LocalWatchContinueWatchingRefreshTest {
    private class MemoryStore(initial: Preferences) : DataStore<Preferences> {
        override val data = MutableStateFlow(initial)
        private val mutex = Mutex()
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences = mutex.withLock {
            transform(data.value).also { data.value = it }
        }
    }

    private val gson = Gson()
    private val moviesKey = stringPreferencesKey("local_watched_movies_v1")
    private val episodesKey = stringPreferencesKey("local_watched_episodes_v1")
    private lateinit var store: MemoryStore
    private lateinit var repository: LocalWatchRepository

    @Before fun setUp() {
        mockkStatic("com.arflix.tv.util.DataStoresKt")
        store = MemoryStore(
            preferencesOf(
                moviesKey to gson.toJson(listOf(1, 2, 3)),
                episodesKey to gson.toJson(listOf("show_tmdb:10:1:1"))
            )
        )
        every { any<Context>().watchDataStore } returns store
        every { any<Context>().settingsDataStore } returns MemoryStore(emptyPreferences())
        val profiles = mockk<ProfileManager>(relaxed = true) {
            every { getProfileIdSync() } returns "p"
            every { activeProfileId } returns MutableStateFlow("p")
            every { profileStringKey(any()) } answers { stringPreferencesKey(firstArg()) }
            every { profileLongKey(any()) } answers { longPreferencesKey(firstArg()) }
            every { profileBooleanKey(any()) } answers { booleanPreferencesKey(firstArg()) }
        }
        repository = LocalWatchRepository(RuntimeEnvironment.getApplication(), mockk(relaxed = true), profiles, ContinueWatchingUpdates())
    }

    @After fun tearDown() {
        unmockkStatic("com.arflix.tv.util.DataStoresKt")
    }

    private fun storedMovies(): Set<Int> =
        store.data.value[moviesKey]?.let { gson.fromJson(it, Array<Int>::class.java).toSet() }.orEmpty()

    private fun storedEpisodes(): Set<String> =
        store.data.value[episodesKey]?.let { gson.fromJson(it, Array<String>::class.java).toSet() }.orEmpty()

    private suspend fun save(episode: Int = 1, position: Long = 300) {
        repository.saveLocalContinueWatching(com.arflix.tv.data.model.MediaType.TV, 10, "Show", null, null,
            1, episode, episodeTitle = "Episode", progress = 20, positionSeconds = position, durationSeconds = 1500)
    }

    @Test fun refreshReadsTheLatestLocalPlaybackPosition() = runBlocking {
        save(position = 300)
        assertEquals(300L, repository.getLocalContinueWatchingEntry(com.arflix.tv.data.model.MediaType.TV, 10, 1, 1)?.resumePositionSeconds)
        save(position = 450)
        assertEquals(450L, repository.getLocalContinueWatchingEntry(com.arflix.tv.data.model.MediaType.TV, 10, 1, 1)?.resumePositionSeconds)
    }

    @Test fun onlyTheLatestEpisodeAppearsForAShow() = runBlocking {
        save(1)
        save(2)
        assertEquals(2, repository.loadPersistedContinueWatchingForStartup().single().episode)
    }

    @Test fun completedEpisodeIsRemovedFromTheSavedRow() = runBlocking {
        save()
        repository.markEpisodeWatched(10, 1, 1)
        assertTrue(repository.loadPersistedContinueWatchingForStartup().isEmpty())
    }

    @Test fun dismissalPersistsAndNewPlaybackClearsIt() = runBlocking {
        save()
        val item = repository.loadPersistedContinueWatchingForStartup().single()
        repository.dismissContinueWatching(item.toMediaItem())
        assertTrue(repository.loadPersistedContinueWatchingForStartup().isEmpty())
        save(position = 600)
        assertEquals(600L, repository.loadPersistedContinueWatchingForStartup().single().resumePositionSeconds)
    }
}
