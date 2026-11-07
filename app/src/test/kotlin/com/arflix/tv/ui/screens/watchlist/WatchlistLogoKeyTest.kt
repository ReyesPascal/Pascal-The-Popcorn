package com.arflix.tv.ui.screens.watchlist

import com.arflix.tv.data.model.MediaItem
import com.arflix.tv.data.model.MediaType
import org.junit.Assert.*
import org.junit.Test

class WatchlistLogoKeyTest {
    @Test fun mediaTypeSeparatesIds() {
        val movie = MediaItem(42, "Movie")
        assertNotEquals(watchlistLogoKey(movie), watchlistLogoKey(movie.copy(mediaType = MediaType.TV)))
    }
    @Test fun addonIdentitySeparatesProvidersAndItems() {
        val item = MediaItem(-1, "Native", addonNativeId = "one", addonNativeAddonId = "a")
        assertNotEquals(watchlistLogoKey(item), watchlistLogoKey(item.copy(addonNativeAddonId = "b")))
        assertNotEquals(watchlistItemKey(item, 0), watchlistItemKey(item, 1))
    }
}
