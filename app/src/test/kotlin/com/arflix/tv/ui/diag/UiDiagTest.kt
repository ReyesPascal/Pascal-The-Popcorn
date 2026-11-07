package com.arflix.tv.ui.diag

import org.junit.Assert.*
import org.junit.Test

class UiDiagTest {
    @Test fun `printable keys never reveal letters digits or punctuation`() {
        listOf("KEYCODE_A", "KEYCODE_7", "KEYCODE_SPACE", "KEYCODE_AT").forEach {
            assertEquals("text-input", uiKeyName(it, printing = true))
        }
        assertEquals("KEYCODE_DPAD_DOWN", uiKeyName("KEYCODE_DPAD_DOWN", printing = false))
        assertEquals(80, uiKeyName("x".repeat(200), printing = false).length)
    }

    @Test fun `identifier allowlist drops URLs secrets malformed IDs and unset episodes`() {
        val fields = uiIdFields(mapOf(
            "mediaId" to -42, "imdbId" to "tt0137523", "initialSeason" to 0,
            "episodeNumber" to 3, "tmdbEpisodeNumber" to -1,
            "streamUrl" to "https://secret.example/token", "password" to "secret",
            "categoryId" to "search text", "kitsuId" to "123"
        )).toMap()
        assertEquals(mapOf("mediaId" to -42, "imdbId" to "tt0137523", "initialSeason" to 0, "episodeNumber" to 3), fields)
        assertTrue(uiIdFields(mapOf("imdbId" to "tt123?token=secret")).isEmpty())
    }

    @Test fun `held keys retain first repeat and sample subsequent repeats once per second`() {
        val gate = UiKeyRepeatGate()
        assertTrue(gate.shouldLog(0, 10, 10))
        assertTrue(gate.shouldLog(1, 10, 510))
        assertFalse(gate.shouldLog(2, 10, 560))
        assertFalse(gate.shouldLog(20, 10, 1509))
        assertTrue(gate.shouldLog(21, 10, 1510))
        assertTrue(gate.shouldLog(0, 1600, 1600))
        assertTrue(gate.shouldLog(1, 1600, 2100))
    }
}
