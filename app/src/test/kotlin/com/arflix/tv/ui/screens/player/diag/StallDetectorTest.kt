package com.arflix.tv.ui.screens.player.diag

import org.junit.Assert.*
import org.junit.Test

class StallDetectorTest {
    @Test fun bufferingThresholdRepeatAndRecovery() {
        val d = StallDetector()
        assertTrue(d.sample(0, true, true, false, 0).isEmpty())
        assertTrue(d.sample(9_999, true, true, false, 0).isEmpty())
        assertEquals(StallDetector.Signal("buffering", 10_000, false), d.sample(10_000, true, true, false, 0).single())
        assertTrue(d.sample(39_999, true, true, false, 0).isEmpty())
        assertEquals(40_000L, d.sample(40_000, true, true, false, 0).single().stuckMs)
        assertEquals(StallDetector.Signal("buffering", 41_000, true), d.sample(41_000, true, false, true, 1).single())
    }
    @Test fun positionProgressAndPauseResetTimer() {
        val d = StallDetector()
        d.sample(0, true, false, true, 100)
        d.sample(1_000, true, false, true, 100)
        assertTrue(d.sample(9_999, true, false, true, 100).isEmpty())
        assertEquals("position", d.sample(10_000, true, false, true, 100).single().kind)
        assertTrue(d.sample(12_000, false, false, false, 100).single().ended)
        assertTrue(d.sample(50_000, false, false, false, 100).isEmpty())
        assertTrue(d.sample(60_000, true, false, true, 100).isEmpty())
        d.sample(61_000, true, false, true, 100)
        assertTrue(d.sample(65_000, true, false, true, 101).isEmpty())
        assertTrue(d.sample(71_000, true, false, true, 102).isEmpty())
    }
    @Test fun suppressedReadyNeverReportsPositionStall() {
        val d = StallDetector()
        d.sample(0, true, false, false, 0)
        assertTrue(d.sample(30_000, true, false, false, 0).isEmpty())
    }
    @Test fun resumedPositionEndsStallAndStartsFreshThreshold() {
        val d = StallDetector()
        d.sample(0, true, false, true, 100)
        d.sample(10_000, true, false, true, 100)
        assertEquals(StallDetector.Signal("position", 11_000, true),
            d.sample(11_000, true, false, true, 101).single())
        assertTrue(d.sample(20_999, true, false, true, 101).isEmpty())
        assertEquals(StallDetector.Signal("position", 10_000, false),
            d.sample(21_000, true, false, true, 101).single())
    }
    @Test fun changingStallKindEndsPreviousAndStartsNewThreshold() {
        val d = StallDetector()
        d.sample(0, true, true, false, 0)
        d.sample(10_000, true, true, false, 0)
        assertTrue(d.sample(11_000, true, false, true, 0).single().ended)
        assertTrue(d.sample(20_999, true, false, true, 0).isEmpty())
        assertEquals("position", d.sample(21_000, true, false, true, 0).single().kind)
    }
}
