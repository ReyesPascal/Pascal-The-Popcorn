package com.arflix.tv.data.repository


internal const val LIST_REFRESH_MS = 30 * 60 * 1000L

/**
 * Raised after the app itself edits a list ("Add to list"): the marker moves on at once instead
 * of at the next window, so the edited list is fetched fresh right away. It only ever grows, and
 * so does the window, so a marker never repeats an earlier one.
 */
private val listFreshnessBump = java.util.concurrent.atomic.AtomicLong(0)

internal fun bumpListFreshness() {
    listFreshnessBump.incrementAndGet()
}

internal fun listFreshnessMarker(
    nowMs: Long = System.currentTimeMillis(),
    bump: Long = listFreshnessBump.get()
): Long = nowMs / LIST_REFRESH_MS + bump

internal fun withListFreshness(
    url: String,
    nowMs: Long = System.currentTimeMillis(),
    bump: Long = listFreshnessBump.get()
): String {
    val separator = if ('?' in url) '&' else '?'
    return "$url${separator}_=${listFreshnessMarker(nowMs, bump)}"
}
