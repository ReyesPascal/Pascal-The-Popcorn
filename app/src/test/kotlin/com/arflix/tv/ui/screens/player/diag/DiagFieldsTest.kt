package com.arflix.tv.ui.screens.player.diag

import org.junit.Assert.*
import org.junit.Test

class DiagFieldsTest {
    @Test fun boundsUtf8AndJsonEscapesAcrossFields() {
        val fields = DiagFields.bounded(arrayOf("stack" to "\n\"\\界".repeat(2000), "message" to "x".repeat(3000), "count" to 42, "empty" to null))
        assertTrue((fields[0].second as String).length < 1000)
        assertEquals("", fields[1].second)
        assertEquals(42, fields[2].second)
        assertNull(fields[3].second)
    }
    @Test fun preservesShortValues() {
        val values = arrayOf<Pair<String, Any?>>("kind" to "buffering", "ms" to 10000L, "playing" to true)
        assertArrayEquals(values, DiagFields.bounded(values))
    }
}
