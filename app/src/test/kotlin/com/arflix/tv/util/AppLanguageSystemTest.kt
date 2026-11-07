package com.arflix.tv.util

import androidx.datastore.preferences.core.mutablePreferencesOf
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28], qualifiers = "pt-rBR")
@ConscryptMode(ConscryptMode.Mode.OFF)
class AppLanguageSystemTest {
    @Test fun portugueseTvAndRuntimeLocaleStillUseEnglish() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.US)
            assertEquals("en-US", defaultAppLanguage())
            assertEquals("en-US", resolveAppLanguage(mutablePreferencesOf(), "primary"))
        } finally {
            Locale.setDefault(original)
        }
    }

    @Test fun inheritedLanguageIsStableAcrossProfileSwitches() {
        val preferences = mutablePreferencesOf(LAST_APP_LANGUAGE_KEY to "en-US")
        assertEquals("en-US", resolveAppLanguage(preferences, "primary"))
        assertEquals("en-US", resolveAppLanguage(preferences, "secondary"))
    }
}
