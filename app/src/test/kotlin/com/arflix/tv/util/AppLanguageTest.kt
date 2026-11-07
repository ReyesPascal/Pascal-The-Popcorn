package com.arflix.tv.util

import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class AppLanguageTest {
    @Test fun personalBuildAlwaysUsesEnglishWhateverTheTvLocale() {
        listOf("pt-BR", "pt-PT", "nl-NL", "ar-SA", "zh-Hant", "en-US").forEach {
            assertEquals("en-US", defaultAppLanguage(Locale.forLanguageTag(it)))
        }
    }

    @Test fun savedProfileAndLastLanguageCannotOverrideEnglish() {
        val preferences = mutablePreferencesOf(
            stringPreferencesKey("profile_primary_content_language") to "ar-SA",
            LAST_APP_LANGUAGE_KEY to "pt-BR"
        )
        listOf(null, "", "primary", "secondary").forEach {
            assertEquals("en-US", resolveAppLanguage(preferences, it) { error("Unexpected locale read") })
        }
    }

    @Test fun freshInstallUsesEnglishWithoutReadingSystemLocale() {
        assertEquals("en-US", resolveAppLanguage(mutablePreferencesOf(), "primary") { error("Unexpected locale read") })
    }
}
