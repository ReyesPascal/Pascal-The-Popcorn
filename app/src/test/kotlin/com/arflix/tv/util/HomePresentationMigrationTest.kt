package com.arflix.tv.util

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

class HomePresentationMigrationTest {
    private fun key(profile: String, name: String) = booleanPreferencesKey("profile_${profile}_$name")

    @Test
    fun `legacy timestamps cannot override migrated fields but other settings still merge`() {
        for (field in listOf("homePresentationVersion", "netflixHomeStyle")) {
            assertThat(canMergeHomePresentationSetting(field, 1, 0)).isFalse()
            assertThat(canMergeHomePresentationSetting(field, 1, 1)).isTrue()
            assertThat(canMergeHomePresentationSetting(field, 0, 1)).isTrue()
        }
    }

    @Test
    fun `upgrade enables presentation in every existing profile`() = runTest {
        val prefs = mutablePreferencesOf(
            key("a", "netflix_home_style") to false,
            key("b", "netflix_home_style") to false,
        )
        val result = HomePresentationMigration().migrate(prefs)
        for (profile in listOf("a", "b")) {
            assertThat(result[key(profile, "netflix_home_style")]).isTrue()
        }
    }

    @Test
    fun `restart preserves opt out after upgrade`() = runTest {
        val migration = HomePresentationMigration()
        val prefs = migration.migrate(mutablePreferencesOf()).toMutablePreferences()
        prefs[key("a", "netflix_home_style")] = false
        assertThat(migration.shouldMigrate(prefs)).isFalse()
        assertThat(migration.migrate(prefs)).isEqualTo(prefs)
    }

    @Test
    fun `old cloud backups cannot undo defaults or later local choices`() = runTest {
        val prefs = HomePresentationMigration().migrate(mutablePreferencesOf()).toMutablePreferences()
        restoreHomePresentation(prefs, "new", 0, false)
        assertThat(prefs[key("new", "netflix_home_style")]).isNull()
        prefs[key("local", "netflix_home_style")] = false
        restoreHomePresentation(prefs, "local", 0, true)
        assertThat(prefs[key("local", "netflix_home_style")]).isFalse()
    }

    @Test
    fun `new cloud backups preserve explicit opt outs and profile isolation`() {
        val prefs = mutablePreferencesOf(key("other", "netflix_home_style") to true)
        restoreHomePresentation(prefs, "a", HOME_PRESENTATION_VERSION, false)
        assertThat(prefs[key("a", "netflix_home_style")]).isFalse()
        assertThat(prefs[key("other", "netflix_home_style")]).isTrue()
    }
}
