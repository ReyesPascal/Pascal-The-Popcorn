package com.arflix.tv.data.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryModelTest {

    @Test
    fun `isPortrait respects globalPosterMode for category rows`() {
        val category = Category(
            id = "trending_movies",
            title = "Trending Movies",
            items = listOf(
                MediaItem(
                    id = 3,
                    title = "Some Movie"
                )
            )
        )
        assertTrue(category.isPortrait(globalPosterMode = true))
        assertFalse(category.isPortrait(globalPosterMode = false))
    }
}
