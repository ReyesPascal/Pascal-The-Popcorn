package com.arflix.tv.ui.screens.watchlist

import com.arflix.tv.data.model.CatalogConfig
import com.arflix.tv.data.model.CatalogSourceType
import com.arflix.tv.data.model.MediaItem
import com.arflix.tv.data.model.MediaType
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class WatchlistSourceItemTest {

    @Test
    fun myWatchlistHasStandardLabel() {
        val myWatchlist = WatchlistSourceItem.MyWatchlist
        assertThat(myWatchlist.id).isEqualTo("my_watchlist")
        assertThat(myWatchlist.title).isEqualTo("My watchlist")
        assertThat(myWatchlist.displayLabel).isEqualTo("My watchlist")
    }



    @Test
    fun watchlistUiStateCorrectlyIdentifiesEmptyAndSingleType() {
        val emptyState = WatchlistUiState(
            movies = emptyList(),
            series = emptyList()
        )
        assertThat(emptyState.isEmpty).isTrue()

        val movieItem = MediaItem(id = 1, title = "Movie 1", mediaType = MediaType.MOVIE)
        val moviesOnlyState = WatchlistUiState(
            movies = listOf(movieItem),
            series = emptyList()
        )
        assertThat(moviesOnlyState.isEmpty).isFalse()

        val seriesItem = MediaItem(id = 2, title = "Series 1", mediaType = MediaType.TV)
        val mixedState = WatchlistUiState(
            movies = listOf(movieItem),
            series = listOf(seriesItem)
        )
        assertThat(mixedState.isEmpty).isFalse()
        assertThat(mixedState.allItems).hasSize(2)
    }
}
