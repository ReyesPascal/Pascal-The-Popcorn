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
class LocalWatchWatchedCacheLoadTest {
    private class MemoryStore(initial: Preferences) : DataStore<Preferences> {
        override val data = MutableStateFlow(initial)
        private val mutex = Mutex()
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences = mutex.withLock {
            transform(data.value).also { data.value = it }
        }
    }

    private val profileId = MutableStateFlow("p")
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
            every { getProfileIdSync() } answers { profileId.value }
            every { activeProfileId } returns profileId
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

    @Test fun cacheReadsTheSavedSnapshot() = runBlocking {
        repository.initializeWatchedCache()
        assertEquals(setOf(1, 2, 3), repository.getWatchedMoviesFromCache())
    }

    @Test fun anInvalidatedLoadRetriesWithTheNewSnapshot() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var calls = 0
        every { any<Context>().watchDataStore } returns object : DataStore<Preferences> by store {
            override val data = flow {
                val old = store.data.value
                if (calls++ == 0) { entered.complete(Unit); release.await(); emit(old) }
                else emitAll(store.data)
            }
        }
        val loading = launch { repository.initializeWatchedCache() }
        entered.await()
        store.updateData { it.toMutablePreferences().apply { this[moviesKey] = gson.toJson(listOf(7)) } }
        repository.invalidateWatchedCache()
        release.complete(Unit)
        loading.join()
        assertEquals(setOf(7), repository.getWatchedMoviesFromCache())
    }

    @Test fun invalidationReloadsExternalLocalChanges() = runBlocking {
        repository.initializeWatchedCache()
        store.updateData { it.toMutablePreferences().apply { this[moviesKey] = gson.toJson(listOf(8)) } }
        repository.invalidateWatchedCache()
        repository.initializeWatchedCache()
        assertEquals(setOf(8), repository.getWatchedMoviesFromCache())
    }
    @Test fun profileChangeCancelsAnInFlightOldSnapshot() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        every { any<Context>().watchDataStore } returns object : DataStore<Preferences> by store {
            override val data = flow {
                entered.complete(Unit)
                release.await()
                emitAll(store.data)
            }
        }
        var cancelled = false
        val loading = launch {
            try { repository.initializeWatchedCache() }
            catch (_: kotlinx.coroutines.CancellationException) { cancelled = true }
        }
        entered.await()
        profileId.value = "other"
        repository.clearAllProfileCaches()
        release.complete(Unit)
        loading.join()
        assertTrue(cancelled)
        assertTrue(repository.getWatchedMoviesFromCache().isEmpty())
    }
}
