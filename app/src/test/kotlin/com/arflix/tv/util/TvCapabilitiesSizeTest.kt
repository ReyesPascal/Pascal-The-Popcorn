package com.arflix.tv.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TvCapabilitiesSizeTest {
    @Test fun parsesPanelSizeProperty() {
        assertEquals(3840 to 2160, TvCapabilities.parseSize("3840x2160"))
        assertEquals(3840 to 2160, TvCapabilities.parseSize(" 3840X2160 "))
        assertEquals(1920 to 1080, TvCapabilities.parseSize("1920,1080"))
    }

    @Test fun rejectsJunk() {
        assertNull(TvCapabilities.parseSize(null))
        assertNull(TvCapabilities.parseSize(""))
        assertNull(TvCapabilities.parseSize("4k"))
        assertNull(TvCapabilities.parseSize("0x0"))
        assertNull(TvCapabilities.parseSize("3840x2160x60"))
    }
}
