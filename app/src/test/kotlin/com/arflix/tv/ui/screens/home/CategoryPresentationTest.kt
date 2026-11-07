package com.arflix.tv.ui.screens.home

import com.arflix.tv.data.model.MediaItem
import com.arflix.tv.data.model.MediaType
import java.util.Locale
import org.junit.Assert.*
import org.junit.Test

class CategoryPresentationTest {
    @Test fun `TV cards retain home sizes instead of stretching to fill columns`() {
        assertEquals(CategoryGridLayout(7, 105), categoryGridLayout(960, false, true))
        assertEquals(CategoryGridLayout(4, 210), categoryGridLayout(960, false, false))
        assertEquals(105, categoryGridLayout(1280, false, true).cardWidthDp)
        assertEquals(210, categoryGridLayout(1280, false, false).cardWidthDp)
    }

    @Test fun `touch grids fill available width and tiny windows cannot overflow`() {
        assertEquals(CategoryGridLayout(3, 115), categoryGridLayout(411, true, true))
        assertEquals(CategoryGridLayout(2, 179), categoryGridLayout(411, true, false))
        assertEquals(CategoryGridLayout(1, 60), categoryGridLayout(100, true, false))
        for (screen in listOf(320, 360, 390, 411, 600, 800, 1280)) {
            for (posters in listOf(false, true)) {
                val layout = categoryGridLayout(screen, true, posters)
                val used = layout.columns * layout.cardWidthDp + (layout.columns - 1) * 12
                assertTrue(used <= screen - 40)
                assertTrue(screen - 40 - used < layout.columns)
            }
        }
    }

    @Test fun `budget is factual USD only for movies with known positive budget`() {
        val movie = MediaItem(1, "Movie", budget = 150_000_000L)
        assertEquals("$150,000,000", categoryBudget(movie, Locale.US))
        assertNull(categoryBudget(movie.copy(budget = 0)))
        assertNull(categoryBudget(movie.copy(budget = -1)))
        assertNull(categoryBudget(movie.copy(budget = null)))
        assertNull(categoryBudget(movie.copy(mediaType = MediaType.TV)))
        assertNull(categoryBudget(null))
    }

    @Test fun `ratings prefer imdb and label tmdb accurately`() {
        val item = MediaItem(1, "Movie", imdbRating = "8.2", tmdbRating = "7.9")
        assertEquals("IMDb 8.2", categoryRating(item))
        assertEquals("TMDB 7.9", categoryRating(item.copy(imdbRating = "")))
        assertNull(categoryRating(item.copy(imdbRating = "0", tmdbRating = "0")))
        assertNull(categoryRating(item.copy(imdbRating = "NaN", tmdbRating = "11")))
        assertNull(categoryRating(item.copy(imdbRating = "-1", tmdbRating = "unknown")))
        assertNull(categoryRating(MediaItem(1, "Movie", rating = "PG-13")))
    }

    @Test fun `compact budget keeps small amounts and scales large amounts accurately`() {
        val movie = MediaItem(1, "Movie", budget = 225_000_000L)
        assertEquals("$225M", categoryCompactBudget(movie, Locale.US))
        assertEquals("$12.5M", categoryCompactBudget(movie.copy(budget = 12_500_000), Locale.US))
        assertEquals("$1.2B", categoryCompactBudget(movie.copy(budget = 1_200_000_000), Locale.US))
        assertEquals("$750,000", categoryCompactBudget(movie.copy(budget = 750_000), Locale.US))
        assertNull(categoryCompactBudget(movie.copy(mediaType = MediaType.TV)))
        assertNull(categoryCompactBudget(movie.copy(budget = 0)))
    }
}
