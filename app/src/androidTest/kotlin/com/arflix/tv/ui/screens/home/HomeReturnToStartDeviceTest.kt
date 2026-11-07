package com.arflix.tv.ui.screens.home

import android.os.SystemClock
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.arflix.tv.data.model.Category
import com.arflix.tv.data.model.MediaItem
import com.arflix.tv.util.DeviceType
import com.arflix.tv.util.LocalDeviceType
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class HomeReturnToStartDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var focus: HomeFocusState
    private var exits = 0

    @Test fun heldBackResetsContinueWatching() {
        show()
        holdBack()
        assertReset()
        assertRailAtStart()
    }



    @Test fun shortBackPreservesTheCurrentRowAndHorizontalPosition() {
        show()
        val down = SystemClock.uptimeMillis()
        dispatch(down, KeyEvent.ACTION_DOWN)
        dispatch(down, KeyEvent.ACTION_UP)
        compose.runOnIdle {
            assertTrue(focus.isSidebarFocused)
            assertEquals(0, focus.currentRowIndex)
            assertEquals(7, focus.currentItemIndex)
            assertEquals(0, focus.scrollResetRequest)
            assertEquals(0, exits)
        }
    }

    private fun holdBack() {
        val down = SystemClock.uptimeMillis()
        dispatch(down, KeyEvent.ACTION_DOWN)
        SystemClock.sleep(HOME_BACK_HOLD_MS + 200)
        // Compose controls coroutine delays; native key timestamps still use the device clock.
        compose.mainClock.advanceTimeBy(HOME_BACK_HOLD_MS + 32)
        compose.runOnIdle { assertEquals(1, focus.scrollResetRequest) }
        dispatch(down, KeyEvent.ACTION_DOWN, repeat = 3)
        dispatch(down, KeyEvent.ACTION_UP)
        compose.waitForIdle()
    }

    private fun dispatch(down: Long, action: Int, repeat: Int = 0) {
        compose.runOnUiThread {
            compose.activity.dispatchKeyEvent(KeyEvent(down, SystemClock.uptimeMillis(), action, KeyEvent.KEYCODE_BACK, repeat))
        }
    }

    private fun assertReset() = compose.runOnIdle {
        assertEquals(0, focus.currentRowIndex)
        assertEquals(0, focus.currentItemIndex)
        assertFalse(focus.isSidebarFocused)
        assertTrue(focus.rowItemIndicesByCategoryId.values.all { it == 0 })
        assertEquals(1, focus.scrollResetRequest)
        assertEquals(0, exits)
    }

    private fun assertRailAtStart() {
        val rail = compose.onNodeWithTag("home_rail_continue_watching", useUnmergedTree = true).fetchSemanticsNode()
        assertEquals(0f, rail.config[SemanticsProperties.HorizontalScrollAxisRange].value(), 0.1f)
    }

    private fun show() {
        val rows = listOf(Category("continue_watching", "Continue Watching", (1..24).map { MediaItem(it, "Title $it") }))
        focus = HomeFocusState(0, 7).apply {
            userHasNavigated = true
            rows.forEach { rowItemIndicesByCategoryId[it.id] = 7 }
        }
        compose.setContent {
            CompositionLocalProvider(LocalDeviceType provides DeviceType.TV) {
                HomeInputLayer(
                    categories = rows, cardLogoUrls = emptyMap(), focusState = focus,
                    limitRowsDuringStartup = false, suppressSelectUntilMs = 0L,
                    contentStartPadding = 24.dp, fastScrollThresholdMs = 80L,
                    usePosterCards = false, isContextMenuOpen = false,
                    onNavigateToDetails = { _, _, _, _ -> },
                    onNavigateToSearch = {}, onNavigateToWatchlist = {},
                    onNavigateToSettings = {}, onExitApp = { exits++ },
                    onOpenContextMenu = { _, _ -> }
                )
            }
        }
        compose.waitForIdle()
    }
}
