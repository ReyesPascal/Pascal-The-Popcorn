package com.arflix.tv.ui.screens.player.preview

import org.junit.Assert.*
import org.junit.Test

class SeekPreviewRequestTest {
    @Test fun extractionFollowsTheDeviceMemoryBudget() {
        assertTrue(allowSecondarySeekPreviewExtraction(256))
        assertTrue(allowSecondarySeekPreviewExtraction(192))
        assertFalse(allowSecondarySeekPreviewExtraction(128))
    }
}
