package com.arflix.tv.data.repository

import com.arflix.tv.data.model.AnimeStructuringStyle
import com.arflix.tv.ui.screens.details.DetailsUiState
import com.arflix.tv.ui.screens.settings.SettingsUiState
import com.google.common.truth.Truth.assertThat
import com.google.gson.Gson
import org.junit.Test

class AnimeStructuringPreferenceTest {
    private val gson = Gson()

    @Test
    fun animeStructuringDefaultsToBroadcastInInitialUiStates() {
        assertThat(SettingsUiState().animeStructuringStyle).isEqualTo(AnimeStructuringStyle.BROADCAST)
        assertThat(DetailsUiState().animeStructuringStyle).isEqualTo(AnimeStructuringStyle.BROADCAST)
        assertThat(DetailsUiState().hasAlternateAnimeStructure).isFalse()
    }

    @Test
    fun animeStructuringStyleFromIdResolvesCorrectly() {
        assertThat(AnimeStructuringStyle.fromId("broadcast")).isEqualTo(AnimeStructuringStyle.BROADCAST)
        assertThat(AnimeStructuringStyle.fromId("BROADCAST")).isEqualTo(AnimeStructuringStyle.BROADCAST)
        assertThat(AnimeStructuringStyle.fromId("standard")).isEqualTo(AnimeStructuringStyle.STANDARD)
        assertThat(AnimeStructuringStyle.fromId("STANDARD")).isEqualTo(AnimeStructuringStyle.STANDARD)
        // Backwards compatibility with legacy keys
        assertThat(AnimeStructuringStyle.fromId("japanese")).isEqualTo(AnimeStructuringStyle.BROADCAST)
        assertThat(AnimeStructuringStyle.fromId("western")).isEqualTo(AnimeStructuringStyle.STANDARD)
        assertThat(AnimeStructuringStyle.fromId(null)).isEqualTo(AnimeStructuringStyle.BROADCAST)
        assertThat(AnimeStructuringStyle.fromId("")).isEqualTo(AnimeStructuringStyle.BROADCAST)
        assertThat(AnimeStructuringStyle.fromId("invalid")).isEqualTo(AnimeStructuringStyle.BROADCAST)
    }










}
