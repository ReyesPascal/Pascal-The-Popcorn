package com.arflix.tv.ui.focus

import org.junit.Assert.*
import org.junit.Test

class TvFocusCoordinatorTest {
    @Test fun `show focus walks buttons seasons episodes and back`() {
        val content = DetailsSectionContent(episodeCount = 10, totalSeasons = 3)
        assertEquals(FocusSection.SEASONS, moveDownSection(FocusSection.BUTTONS, content))
        assertEquals(FocusSection.EPISODES, moveDownSection(FocusSection.SEASONS, content))
        assertEquals(FocusSection.EPISODES, moveDownSection(FocusSection.EPISODES, content))
        assertEquals(FocusSection.SEASONS, moveUpSection(FocusSection.EPISODES, content))
        assertEquals(FocusSection.BUTTONS, moveUpSection(FocusSection.SEASONS, content))
    }

    @Test fun `single season show skips season picker`() {
        val content = DetailsSectionContent(episodeCount = 1)
        assertEquals(FocusSection.EPISODES, moveDownSection(FocusSection.BUTTONS, content))
        assertEquals(FocusSection.BUTTONS, moveUpSection(FocusSection.EPISODES, content))
    }

    @Test fun `movie has no lower focus targets even with stale show content`() {
        val content = DetailsSectionContent(episodeCount = 10, totalSeasons = 3, isTv = false)
        assertEquals(FocusSection.BUTTONS, moveDownSection(FocusSection.BUTTONS, content))
        assertFalse(content.isAvailable(FocusSection.SEASONS))
        assertFalse(content.isAvailable(FocusSection.EPISODES))
    }

    @Test fun `empty season holds focus while episodes load`() {
        val content = DetailsSectionContent(totalSeasons = 3)
        assertEquals(FocusSection.SEASONS, moveDownSection(FocusSection.BUTTONS, content))
        assertEquals(FocusSection.SEASONS, moveDownSection(FocusSection.SEASONS, content))
        assertEquals(FocusSection.BUTTONS, moveDownSection(FocusSection.BUTTONS, DetailsSectionContent()))
    }

    @Test fun `horizontal movement stops at each rail boundary`() {
        for (section in FocusSection.entries) {
            var changed = false
            val set: (Int) -> Unit = { changed = true }
            handleLeft(section, 0, 0, 0, set, set, set)
            assertFalse("$section left boundary", changed)
            handleRight(section, 2, 9, 2, 2, 10, 3, set, set, set)
            assertFalse("$section right boundary", changed)
            assertTrue(isAtLeftmost(section, DetailsFocusIndices()))
            assertFalse(isAtLeftmost(section, DetailsFocusIndices(1, 1, 1)))
        }
    }

    @Test fun `horizontal movement changes only the focused rail`() {
        val changes = mutableMapOf<FocusSection, Int>()
        val button: (Int) -> Unit = { changes[FocusSection.BUTTONS] = it }
        val episode: (Int) -> Unit = { changes[FocusSection.EPISODES] = it }
        val season: (Int) -> Unit = { changes[FocusSection.SEASONS] = it }
        handleRight(FocusSection.EPISODES, 0, 2, 1, 3, 10, 3, button, episode, season)
        assertEquals(mapOf(FocusSection.EPISODES to 3), changes)
        changes.clear()
        handleLeft(FocusSection.SEASONS, 0, 2, 1, button, episode, season)
        assertEquals(mapOf(FocusSection.SEASONS to 0), changes)
    }
}
