package com.arflix.tv.ui.screens.player.diag

/** Clock and player independent; called once per second on the player's application looper. */
internal class StallDetector {
    data class Signal(val kind: String, val stuckMs: Long, val ended: Boolean)
    private var candidate: String? = null
    private var since = 0L
    private var lastPosition: Long? = null
    private var reported: String? = null
    private var lastReport = 0L

    fun sample(nowMs: Long, playWhenReady: Boolean, buffering: Boolean, playing: Boolean, positionMs: Long): List<Signal> {
        val kind = when {
            !playWhenReady -> null
            buffering -> "buffering"
            playing -> "position"
            else -> null
        }
        val moved = kind == "position" && lastPosition != positionMs
        lastPosition = if (playWhenReady) positionMs else null
        val result = mutableListOf<Signal>()
        if (candidate != kind || moved) {
            reported?.let { result += Signal(it, nowMs - since, true) }
            candidate = kind
            since = nowMs
            reported = null
        }
        if (kind != null && nowMs - since >= 10_000 && (reported == null || nowMs - lastReport >= 30_000)) {
            reported = kind
            lastReport = nowMs
            result += Signal(kind, nowMs - since, false)
        }
        return result
    }
}
