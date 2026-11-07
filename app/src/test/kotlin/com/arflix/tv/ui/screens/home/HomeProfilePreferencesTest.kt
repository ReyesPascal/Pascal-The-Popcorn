package com.arflix.tv.ui.screens.home

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test

class HomeProfilePreferencesTest {
    @Test fun `missing settings preserve clock and scroll defaults`() {
        val settings = readHomeProfilePreferences(mutablePreferencesOf(), "default")
        assertThat(settings.clockFormat).isEqualTo("24h")
        assertThat(settings.smoothScrolling).isFalse()
    }

    @Test fun `settings changes update Home without recreating it`() = runTest {
        val profileId = MutableStateFlow("default")
        val preferences = MutableStateFlow<Preferences>(mutablePreferencesOf())
        observeHomeProfilePreferences(profileId, preferences).test {
            assertThat(awaitItem().clockFormat).isEqualTo("24h")
            preferences.value = mutablePreferencesOf(
                stringPreferencesKey("profile_default_clock_format") to "12h",
                booleanPreferencesKey("profile_default_smooth_scrolling") to true
            )
            val updated = awaitItem()
            assertThat(updated.clockFormat).isEqualTo("12h")
            assertThat(updated.smoothScrolling).isTrue()
            cancelAndIgnoreRemainingEvents()
        }
    }
}
