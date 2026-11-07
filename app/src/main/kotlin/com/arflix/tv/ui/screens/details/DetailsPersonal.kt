package com.arflix.tv.ui.screens.details

import com.arflix.tv.util.TmdbImageSizing

/** Personal build: button indices on the details page (the stock indices are kept for the click path). */
internal const val PERSONAL_PLAY_BUTTON_INDEX = 0
internal const val PERSONAL_WATCHED_BUTTON_INDEX = 3
internal const val PERSONAL_WATCHLIST_BUTTON_INDEX = 4

internal const val PERSONAL_AUTOPLAY_BUTTON_INDEX = 7

/** Play → Watched → Watchlist, followed by the show-only auto-play toggle. */
internal fun personalNextButtonIndex(current: Int, moveRight: Boolean, isShow: Boolean = false): Int {
    val order = if (isShow) listOf(PERSONAL_PLAY_BUTTON_INDEX, PERSONAL_WATCHED_BUTTON_INDEX, PERSONAL_WATCHLIST_BUTTON_INDEX, PERSONAL_AUTOPLAY_BUTTON_INDEX)
        else listOf(PERSONAL_PLAY_BUTTON_INDEX, PERSONAL_WATCHED_BUTTON_INDEX, PERSONAL_WATCHLIST_BUTTON_INDEX)
    val index = order.indexOf(current)
    if (index < 0) return PERSONAL_PLAY_BUTTON_INDEX
    return order[(index + if (moveRight) 1 else -1).coerceIn(0, order.lastIndex)]
}

/** Width of the personal-build details backdrop (TMDB `w1280`), instead of the `original` file. */
internal const val PERSONAL_DETAILS_BACKDROP_WIDTH = 1280

/**
 * Personal build: the details backdrop asks TMDB for a 1280-wide image (`/t/p/w1280/`) instead of
 * `original`. Non-TMDB URLs are returned unchanged, and a URL already smaller than 1280 is kept.
 */
internal fun personalDetailsBackdropUrl(url: String?): String? {
    if (url.isNullOrBlank()) return url
    return TmdbImageSizing.forSlot(url, PERSONAL_DETAILS_BACKDROP_WIDTH, 0, TmdbImageSizing.Kind.BACKDROP)
}
