package com.arflix.tv.ui.screens.watchlist

import com.arflix.tv.data.model.*
import org.junit.Assert.*
import org.junit.Test

class OledLibraryLayoutTest {
    @Test fun gridAdaptsToActualSpaceAndCardPreference() {
        assertEquals(2, libraryColumns(330, false))
        assertEquals(4, libraryColumns(780, false))
        assertEquals(6, libraryColumns(780, true))
    }
    @Test fun libraryContainsOnlyLocalWatchlistAndCalendar() {
        val custom = WatchlistSourceItem.Catalog(CatalogConfig("custom", "Friday night", CatalogSourceType.TMDB))
        val sources = listOf(WatchlistSourceItem.MyWatchlist, custom)
        assertEquals(listOf("Watchlist", "Calendar"), LibrarySection.entries.map { it.label })
        assertEquals(emptyList<WatchlistSourceItem>(), librarySources(sources, LibrarySection.CALENDAR))
        assertEquals(listOf(WatchlistSourceItem.MyWatchlist), librarySources(sources, LibrarySection.WATCHLISTS))
    }
}
