package com.arflix.tv.ui.diag

import android.os.SystemClock
import android.view.KeyEvent
import androidx.navigation.NavController
import com.arflix.tv.BuildConfig
import com.arflix.tv.util.PtpDiag

/** Observers only: never consume keys or modify navigation arguments. */
object UiDiag {
    private val repeats = UiKeyRepeatGate()

    fun key(event: KeyEvent, route: String?) {
        if (!BuildConfig.PERSONAL_BUILD) return
        if (event.action != KeyEvent.ACTION_DOWN && event.action != KeyEvent.ACTION_UP) return
        if (event.action == KeyEvent.ACTION_DOWN &&
            !repeats.shouldLog(event.repeatCount, event.downTime, SystemClock.elapsedRealtime())) return
        PtpDiag.d(
            "ui-key",
            "key" to uiKeyName(KeyEvent.keyCodeToString(event.keyCode), event.isPrintingKey),
            "action" to if (event.action == KeyEvent.ACTION_DOWN) "down" else "up",
            "repeatCount" to event.repeatCount,
            "longPress" to (event.isLongPress || event.repeatCount > 0),
            "route" to route?.substringBefore('?')?.take(160),
            // This is the length of this key's input, never the text field's contents.
            "length" to if (event.isPrintingKey) 1 else null
        )
    }

    fun destinationListener(): NavController.OnDestinationChangedListener {
        var previousRoute: String? = null
        var openedAtMs: Long? = null
        return NavController.OnDestinationChangedListener { _, destination, arguments ->
            if (BuildConfig.PERSONAL_BUILD) {
                val nowMs = SystemClock.elapsedRealtime()
                val ids = uiIdFields(buildMap {
                    for (name in UI_ID_FIELDS) {
                        if (arguments?.containsKey(name) == true) {
                            put(name, if (name == "imdbId") arguments.getString(name) else arguments.getInt(name))
                        }
                    }
                })
                val route = destination.route?.substringBefore('?')?.take(160)
                PtpDiag.i(
                    "ui-screen",
                    "route" to route,
                    "previousRoute" to previousRoute,
                    "previousScreenMs" to openedAtMs?.let { (nowMs - it).coerceAtLeast(0L) },
                    *ids.toTypedArray()
                )
                previousRoute = route
                openedAtMs = nowMs
            }
        }
    }

    /** Details' playback callback also covers resume and manual source selection. */
    fun playPressed(mediaId: Int, imdbId: String?, season: Int?, episode: Int?, startPositionMs: Long?) {
        if (!BuildConfig.PERSONAL_BUILD) return
        PtpDiag.i(
            "ui-play-pressed",
            "origin" to "details-player-callback",
            "mediaId" to mediaId,
            "imdbId" to imdbId?.takeIf { it.matches(Regex("tt[0-9]{1,16}")) },
            "seasonNumber" to season,
            "episodeNumber" to episode,
            "startPositionMs" to startPositionMs
        )
    }
}

internal val UI_ID_FIELDS = listOf(
    "mediaId", "imdbId", "initialSeason", "initialEpisode", "seasonNumber", "episodeNumber",
    "tmdbSeasonNumber", "tmdbEpisodeNumber", "kitsuId", "kitsuEpisodeNumber"
)

/** Only known ID types pass through, never arbitrary strings from deep links. */
internal fun uiIdFields(arguments: Map<String, Any?>): List<Pair<String, Any?>> =
    UI_ID_FIELDS.mapNotNull { name ->
        val value = arguments[name]
        when {
            name == "imdbId" && value is String && value.matches(Regex("tt[0-9]{1,16}")) -> name to value
            name != "imdbId" && value is Int && (value >= 0 || name == "mediaId") -> name to value
            else -> null
        }
    }

internal fun uiKeyName(platformName: String, printing: Boolean): String =
    if (printing) "text-input" else platformName.take(80)

/** Initial down/up events are retained; held-key repeats are sampled at most once a second. */
internal class UiKeyRepeatGate {
    private var downTime: Long? = null
    private var lastRepeatMs: Long? = null

    fun shouldLog(repeatCount: Int, eventDownTime: Long, nowMs: Long): Boolean {
        if (repeatCount == 0 || downTime != eventDownTime) {
            downTime = eventDownTime
            lastRepeatMs = null
        }
        if (repeatCount == 0) return true
        if (lastRepeatMs?.let { nowMs - it < 1_000L } == true) return false
        lastRepeatMs = nowMs
        return true
    }
}
