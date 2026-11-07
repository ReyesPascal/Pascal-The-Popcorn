package com.arflix.tv.data.repository

import org.junit.Assert.*
import org.junit.Test

class AddonChangesTest {
    @Test fun `deletion is deterministic on a timestamp tie and survives replay`() {
        val add = mapOf("a" to AddonChange(200, false))
        val remove = mapOf("a" to AddonChange(200, true))
        assertEquals(remove, mergeAddonChanges(add, remove))
        assertEquals(remove, mergeAddonChanges(remove, add))
    }

    @Test fun `OpenSubtitles and invalid records are protected`() {
        val changes = recordAddonChanges(emptyMap(), emptySet(), setOf("opensubtitles", "a"), 300)
        assertEquals(setOf("a"), changes.keys)
        assertTrue(mergeAddonChanges(emptyMap(), mapOf("" to AddonChange(2, true), "b" to AddonChange(0, true))).isEmpty())
    }
}
