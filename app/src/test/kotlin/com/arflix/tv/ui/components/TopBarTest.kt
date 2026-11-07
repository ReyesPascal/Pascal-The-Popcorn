package com.arflix.tv.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TopBarTest {
    @Test fun allFourItemsAreReachableInOrder() {
        val expected = listOf(SidebarItem.SEARCH, SidebarItem.HOME, SidebarItem.WATCHLIST, SidebarItem.SETTINGS)
        assertEquals(3, topBarMaxIndex())
        expected.forEachIndexed { index, item ->
            assertEquals(item, topBarFocusedItem(index))
            assertEquals(index, topBarSelectedIndex(item))
        }
        assertNull(topBarFocusedItem(-1))
        assertNull(topBarFocusedItem(4))
    }
}
