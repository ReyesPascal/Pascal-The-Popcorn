package com.arflix.tv.data.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResumableGuardTest {
    @Test fun `ordinary movie and episode IDs are resumable`() {
        assertFalse(isNotResumable(id = 123))
        assertFalse(isNotResumable())
    }

    @Test fun `explicit live playback is never resumable`() {
        assertTrue(isNotResumable(id = 123, isLiveStream = true))
        assertTrue(isNotResumable(id = -1, isLiveStream = true, isAddonNative = true))
    }

    @Test fun `invalid catalog IDs are excluded except for addon native media`() {
        assertTrue(isNotResumable(id = 0))
        assertTrue(isNotResumable(id = -123))
        assertFalse(isNotResumable(id = -123, isAddonNative = true))
        assertFalse(isNotResumable(id = 0, isAddonNative = true))
    }
}
