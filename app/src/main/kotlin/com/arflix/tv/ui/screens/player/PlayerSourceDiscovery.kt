package com.arflix.tv.ui.screens.player

import com.arflix.tv.data.model.StreamSource
import com.arflix.tv.data.model.AutoplayLimits
import com.arflix.tv.data.repository.ProgressiveStreamResult
import com.arflix.tv.ui.screens.details.isAutoPlayableStream
import com.arflix.tv.ui.screens.details.matchesAutoplayLimits
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onStart

internal fun eligiblePlayerAutoplayStreams(
    streams: List<StreamSource>, minimumQuality: Int, limits: AutoplayLimits = AutoplayLimits()
): List<StreamSource> =
    com.arflix.tv.data.model.followUsenetUltimate(streams)
        ?: streams.filter { isAutoPlayableStream(it) && matchesAutoplayLimits(it, minimumQuality, limits) }

internal enum class PlayerAutoplayAvailability { SEARCHING, READY, NO_MATCH, NO_SOURCES, SELECTED }

internal class SequentialStreamCandidates(private val minimumQuality: Int, private val limits: AutoplayLimits) {
    var streams: List<StreamSource> = emptyList()
        private set

    fun accept(candidates: List<StreamSource>): Boolean {
        val playable = eligiblePlayerAutoplayStreams(candidates, minimumQuality, limits).isNotEmpty()
        streams = if (playable) candidates else streams + candidates
        return playable
    }
}

internal fun playerAutoplayAvailability(
    streams: List<StreamSource>, minimumQuality: Int, searchActive: Boolean, hasSelection: Boolean,
    limits: AutoplayLimits = AutoplayLimits()
): PlayerAutoplayAvailability = when {
    hasSelection -> PlayerAutoplayAvailability.SELECTED
    eligiblePlayerAutoplayStreams(streams, minimumQuality, limits).isNotEmpty() -> PlayerAutoplayAvailability.READY
    searchActive -> PlayerAutoplayAvailability.SEARCHING
    streams.isEmpty() -> PlayerAutoplayAvailability.NO_SOURCES
    else -> PlayerAutoplayAvailability.NO_MATCH
}

/** The player always sees an empty, not-final result first, before the first add-on answers. */
internal fun playerSourceDiscovery(addons: Flow<ProgressiveStreamResult>): Flow<ProgressiveStreamResult> =
    addons.onStart { emit(ProgressiveStreamResult(emptyList(), completedAddons = 0, totalAddons = 0, isFinal = false)) }
