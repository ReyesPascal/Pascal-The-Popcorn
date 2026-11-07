package com.arflix.tv.ui.screens.details

import android.util.Log
import androidx.lifecycle.ViewModelStore
import com.arflix.tv.data.model.StreamIntegrationType
import com.arflix.tv.data.repository.MediaRepository
import com.arflix.tv.data.repository.ProgressiveStreamResult
import com.arflix.tv.data.repository.StreamIntegrationRepository
import com.arflix.tv.data.repository.StreamRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DetailsStreamCompletionTest {
    @Test
    fun `view model stops spinner only after the last empty provider in every order`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0
        try {
            val orders = listOf(
                listOf(0)
            )
            for (order in orders) {
                val gates = List(1) { CompletableDeferred<Unit>() }
                val streams = mockk<StreamRepository>(relaxed = true)
                val media = mockk<MediaRepository>(relaxed = true)
                val integrations = mockk<StreamIntegrationRepository>(relaxed = true)

                every { streams.installedAddons } returns flowOf(emptyList())
                every { integrations.getUnifiedSourceOrderedIds() } returns flowOf(emptyList())
                coEvery { integrations.isIntegrationEnabled(any()) } returns true
                every { streams.resolveMovieStreamsProgressive(any(), any(), any(), any(), any(), any()) } returns flow {
                    gates[0].await()
                    emit(ProgressiveStreamResult(emptyList(), completedAddons = 0, totalAddons = 0, isFinal = true))
                }
                val model = DetailsViewModel(
                    context = mockk(relaxed = true),
                    mediaRepository = media,
                    profileManager = mockk(relaxed = true),
                    localWatchRepository = mockk(relaxed = true),
                    streamRepository = streams,
                    networkMonitor = mockk(relaxed = true),
                    tmdbPriorityDispatcher = mockk(relaxed = true),
                    animeMapper = mockk(relaxed = true),
                    tmdbApi = mockk(relaxed = true),
                    watchHistoryRepository = mockk(relaxed = true),
                    watchlistRepository = mockk(relaxed = true),
                    launcherContinueWatchingRepository = mockk(relaxed = true),
                    streamIntegrationRepository = integrations,
                    recentPlayedSourceRepository = mockk(relaxed = true),
                )
                val store = ViewModelStore().apply { put("details", model) }
                try {
                    model.loadStreams("tt1234567")
                    runCurrent()
                    assertTrue("Order $order must start loading", model.uiState.value.isLoadingStreams)
                    for ((position, slot) in order.withIndex()) {
                        gates[slot].complete(Unit)
                        runCurrent()
                        assertEquals("Order $order after $slot", position < 0, model.uiState.value.isLoadingStreams)
                    }
                    assertTrue(model.uiState.value.streams.isEmpty())
                } finally {
                    store.clear()
                    runCurrent()
                }
            }
        } finally {
            Dispatchers.resetMain()
            unmockkStatic(Log::class)
        }
    }
}
