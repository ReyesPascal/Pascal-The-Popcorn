package com.arflix.tv.data.repository

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow


internal class WatchedCacheReloads {
    private val _reloads = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val reloads: SharedFlow<Unit> = _reloads.asSharedFlow()

    @Volatile private var reloadPending = false

    fun invalidated() {
        reloadPending = true
    }

    fun reset() {
        reloadPending = false
    }

    fun loaded() {
        if (!reloadPending) return
        reloadPending = false
        _reloads.tryEmit(Unit)
    }
}
