package com.arflix.tv.ui.screens.details

import com.arflix.tv.util.TmdbImageSizing

/** Personal build: button indices on the details page (the stock indices are kept for the click path). */
internal const val PERSONAL_PLAY_BUTTON_INDEX = 0
internal const val PERSONAL_WATCHED_BUTTON_INDEX = 3

/**
 * Personal build: the button row is only Play and Watched. Right from Play goes to Watched, Left
 * from Watched goes to Play; anything else stays put. An index that is not one of the two (should
 * never happen) snaps back to Play.
 */
internal fun personalNextButtonIndex(current: Int, moveRight: Boolean): Int = when (current) {
    PERSONAL_PLAY_BUTTON_INDEX -> if (moveRight) PERSONAL_WATCHED_BUTTON_INDEX else PERSONAL_PLAY_BUTTON_INDEX
    PERSONAL_WATCHED_BUTTON_INDEX -> if (moveRight) PERSONAL_WATCHED_BUTTON_INDEX else PERSONAL_PLAY_BUTTON_INDEX
    else -> PERSONAL_PLAY_BUTTON_INDEX
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
