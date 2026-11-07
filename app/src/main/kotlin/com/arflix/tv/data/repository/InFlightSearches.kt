package com.arflix.tv.data.repository

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Owns searches independently of their screen callers. Handles release exactly once. */
class InFlightSearches<T>(private val scope: CoroutineScope) {
    private class Shared<T>(val result: Deferred<T>, val updates: MutableStateFlow<T?>, var waiters: Int = 0, var playPressed: Boolean = false)
    private val entries = ConcurrentHashMap<String, Shared<T>>()

    class Lease<T> internal constructor(
        val result: Deferred<T>,
        val joined: Boolean,
        val updates: StateFlow<T?>,
        private val onRelease: (Boolean) -> Unit,
        private val onPlay: () -> Unit
    ) {
        private var released = false
        @Synchronized fun release() {
            if (!released) {
                released = true
                onRelease(false)
            }
        }
        @Synchronized fun releaseAndClearPlay() {
            if (!released) {
                released = true
                onRelease(true)
            }
        }
        fun markPlayPressed() = onPlay()
    }

    @Synchronized
    fun acquire(key: String, search: suspend (publish: (T) -> Unit) -> T): Lease<T> {
        val existing = entries[key]
        val shared = existing ?: run {
            val updates = MutableStateFlow<T?>(null)
            Shared(scope.async(start = CoroutineStart.LAZY) {
                search { updates.value = it }.also { updates.value = it }
            }, updates).also { entries[key] = it }
        }
        shared.waiters++
        if (existing == null) {
            shared.result.invokeOnCompletion { remove(key, shared) }
            shared.result.start()
        }
        return Lease(shared.result, existing != null, shared.updates.asStateFlow(),
            onRelease = { clearPlay -> release(key, shared, clearPlay) },
            onPlay = { markPlayPressed(shared) })
    }

    @Synchronized private fun remove(key: String, shared: Shared<T>) {
        entries.remove(key, shared)
    }

    @Synchronized private fun markPlayPressed(shared: Shared<T>) {
        shared.playPressed = true
    }

    @Synchronized private fun release(key: String, shared: Shared<T>, clearPlay: Boolean) {
        if (clearPlay) shared.playPressed = false
        shared.waiters--
        if (shared.waiters == 0 && !shared.playPressed) {
            entries.remove(key, shared)
            shared.result.cancel()
        }
    }

    internal val size: Int get() = entries.size
}
