package com.arflix.tv.data.model

/**
 * True when [url] plays directly (an http/https link).
 * Moved here from the deleted Stalker code (1.0.2): add-on streams, autoplay and the source list still use it.
 */
internal fun isDirectStreamUrl(url: String?): Boolean {
    val trimmed = url?.trim().orEmpty()
    if (trimmed.isBlank()) return false
    return trimmed.startsWith("http", ignoreCase = true)
}
