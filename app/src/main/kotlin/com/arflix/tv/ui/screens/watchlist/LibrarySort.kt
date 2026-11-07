package com.arflix.tv.ui.screens.watchlist

import com.arflix.tv.data.model.MediaItem
import java.time.LocalDate

internal enum class LibrarySort { RECENTLY_ADDED, TITLE, RATING, RELEASE_DATE_NEWEST, RELEASE_DATE_OLDEST }

private val YEAR_REGEX = Regex("\\d{4}")

private fun MediaItem.libraryReleaseDate(): LocalDate? {
    val fullDate = releaseDate?.take(10)?.let {
        try { LocalDate.parse(it) } catch (_: Exception) { null }
    }
    if (fullDate != null && fullDate.year > 0) return fullDate
    val releaseYear = year.takeIf { it.matches(YEAR_REGEX) }?.toIntOrNull()
        ?: releaseDate?.takeIf { it.matches(YEAR_REGEX) }?.toIntOrNull()
    return releaseYear?.takeIf { it > 0 }?.let { LocalDate.of(it, 1, 1) }
}

internal fun sortLibraryItems(items: List<MediaItem>, sort: LibrarySort): List<MediaItem> {
    return when (sort) {
        // Preserve the tracker/source's existing explicit ordering by default.
        LibrarySort.RECENTLY_ADDED -> items
        LibrarySort.TITLE -> items.sortedBy { it.title.lowercase() }
        LibrarySort.RATING -> items.sortedByDescending { it.rating.toDoubleOrNull() ?: 0.0 }
        LibrarySort.RELEASE_DATE_NEWEST, LibrarySort.RELEASE_DATE_OLDEST -> {
            val dates = items.associateWith { it.libraryReleaseDate() }
            items.sortedWith { a, b ->
                val left = dates[a]
                val right = dates[b]
                when {
                    left == null && right != null -> 1
                    left != null && right == null -> -1
                    else -> {
                        val order = if (left != null && right != null) left.compareTo(right) else 0
                        val directed = if (sort == LibrarySort.RELEASE_DATE_NEWEST) -order else order
                        directed.takeIf { it != 0 } ?: a.title.compareTo(b.title, ignoreCase = true).takeIf { it != 0 }
                            ?: a.id.compareTo(b.id)
                    }
                }
            }
        }
    }
}
