package com.arflix.tv.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.arflix.tv.util.profilesDataStore
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class ProfileManagerTest {
    private val context = mockk<Context>()
    private val storedId = stringPreferencesKey("active_profile_id")
    private val state = MutableStateFlow(emptyPreferences())

    @Before fun setup() {
        val dataStore = object : DataStore<Preferences> {
            override val data = state
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
                transform(state.value).also { state.value = it }
        }
        mockkStatic("com.arflix.tv.util.DataStoresKt")
        every { context.profilesDataStore } returns dataStore
    }

    @After fun cleanup() { unmockkStatic("com.arflix.tv.util.DataStoresKt") }

    @Test fun existingPersonalProfileKeepsItsPreferenceNamespace() = runBlocking {
        state.value = preferencesOf(storedId to "existing-personal-uuid")
        val manager = ProfileManager(context)
        manager.initialize()
        assertEquals("existing-personal-uuid", manager.getProfileId())
        assertEquals("existing-personal-uuid", manager.currentProfileId.value)
        assertEquals("profile_existing-personal-uuid_playback_progress", manager.profileStringKey("playback_progress").name)
        assertEquals("profile_existing-personal-uuid_", manager.getKeyPrefix())
    }

    @Test fun resolvedProfileCannotSwitchAfterInitialization() = runBlocking {
        state.value = preferencesOf(storedId to "first-id")
        val manager = ProfileManager(context)
        manager.initialize()
        state.value = preferencesOf(storedId to "different-id")
        assertEquals("first-id", manager.getProfileIdSync())
        assertEquals("first-id", manager.getProfileId())
        assertEquals("first-id", manager.activeProfileId.first())
    }

    @Test fun freshAndBlankInstallsUseTheBuiltInDefault() = runBlocking {
        assertEquals("default", ProfileManager(context).getProfileIdSync())
        state.value = preferencesOf(storedId to " ")
        val manager = ProfileManager(context)
        assertEquals("default", manager.getProfileId())
        assertEquals("profile_default_watched", manager.profileBooleanKey("watched").name)
    }
}
