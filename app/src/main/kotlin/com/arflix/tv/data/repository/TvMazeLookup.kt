package com.arflix.tv.data.repository

import com.arflix.tv.data.api.TvMazeApi
import com.arflix.tv.data.api.TvMazeShow
import kotlinx.coroutines.CancellationException

/** Optional calendar metadata: either identifier may be missing from the public service. */
internal suspend fun lookupTvMazeShow(api: TvMazeApi, imdb: String?, tvdb: Int?, beforeRequest: suspend () -> Unit = {}): TvMazeShow? {
    suspend fun attempt(block: suspend () -> TvMazeShow): TvMazeShow? = try { beforeRequest(); block() }
        catch (error: CancellationException) { throw error }
        catch (_: Exception) { null }
    return imdb?.takeIf { it.startsWith("tt") }?.let { attempt { api.lookupShow(imdb = it) } }
        ?: tvdb?.let { attempt { api.lookupShow(tvdb = it) } }
}

/** Keep public lookup and episode calls below TVmaze's per-IP request allowance. */
internal class TvMazeRequestGate(private val now: () -> Long = { System.nanoTime() / 1_000_000L }) {
    private val mutex = kotlinx.coroutines.sync.Mutex()
    private var nextAt = 0L
    suspend fun awaitTurn() {
        mutex.lock()
        try {
            kotlinx.coroutines.delay((nextAt - now()).coerceAtLeast(0L))
            nextAt = now() + 550L
        } finally { mutex.unlock() }
    }
}
