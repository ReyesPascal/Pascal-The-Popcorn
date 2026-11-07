package com.arflix.tv.data.repository

import android.app.Application
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import com.arflix.tv.data.model.CatalogConfig
import com.arflix.tv.data.model.CatalogSourceType
import com.arflix.tv.util.settingsDataStore
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.junit.Assert.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, manifest = Config.NONE, sdk = [28])
@ConscryptMode(ConscryptMode.Mode.OFF)
class CatalogDeletionSyncTest {
    private val state = MutableStateFlow(emptyPreferences())
    private var beforeWrite: (suspend () -> Unit)? = null
    private lateinit var repository: CatalogRepository
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val profileId = "default"
    private val catalogKey = stringPreferencesKey("profile_default_catalogs_v1")

    @Before fun setup() {
        val mutex = Mutex()
        val store = object : DataStore<Preferences> {
            override val data = state
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
                beforeWrite?.also { beforeWrite = null }?.invoke()
                return mutex.withLock { transform(state.value).toPreferences().also { state.value = it } }
            }
        }
        mockkStatic("com.arflix.tv.util.DataStoresKt")
        every { any<Context>().settingsDataStore } returns store
        val profiles = mockk<ProfileManager>(relaxed = true)
        every { profiles.getProfileIdSync() } returns profileId
        repository = CatalogRepository(context, profiles, mockk(relaxed = true))
    }

    @After fun cleanup() { unmockkStatic("com.arflix.tv.util.DataStoresKt") }

    private fun catalog(id: String = "custom_test", packId: String? = null) = CatalogConfig(
        id = id, title = id, sourceType = CatalogSourceType.ADDON,
        addonId = "test-addon", addonCatalogType = "movie", addonCatalogId = id, isPreinstalled = false, packId = packId
    )

    @Test fun unrelatedPreferencesDoNotInvalidateCatalogParsing() {
        val initial = preferencesOf(catalogKey to "[]")
        val changed = initial.toMutablePreferences().apply {
            this[booleanPreferencesKey("auto_play_next")] = false
            this[stringPreferencesKey("cloud_sync_field_ts")] = "{\"other\":123}"
            this[stringPreferencesKey("profile_other_catalogs_v1")] = "[]"
        }
        assertTrue(repository.catalogSettingsUnchanged(profileId, initial, changed))
    }

    @Test fun catalogAndHiddenOrLegacyChangesInvalidateParsing() {
        val initial = emptyPreferences()
        for (name in listOf("profile_default_catalogs_v1", "catalogs_v1",
            "profile_default_hidden_preinstalled_catalogs_v2", "profile_default_hidden_addon_catalogs_v1")) {
            val changed = preferencesOf(stringPreferencesKey(name) to "[]")
            assertFalse(name, repository.catalogSettingsUnchanged(profileId, initial, changed))
        }
    }

    @Test fun deletingLastCatalogSurvivesLegacyFallbackAndStaleCloud() = runBlocking {
        repository.replaceCatalogsForProfile(profileId, listOf(catalog()))
        val old = state.value[catalogKey]!!
        context.settingsDataStore.edit { it[stringPreferencesKey("catalogs_v1")] = old }
        assertTrue(repository.removeCustomCatalog("custom_test").isSuccess)
        assertEquals("[]", state.value[catalogKey])
        assertTrue(repository.getCatalogs().isEmpty())
        assertTrue(repository.getCatalogs().isEmpty())
    }

    @Test fun removingPackAlsoHidesItsAutoDerivedRowsAtomically() = runBlocking {
        val addon = catalog("addon_test", "pack").copy(sourceType = CatalogSourceType.ADDON, sourceUrl = null)
        repository.replaceCatalogsForProfile(profileId, listOf(addon, catalog("other")))
        assertTrue(repository.removeCatalogPack("pack").isSuccess)
        assertEquals(listOf("addon_test"), repository.getHiddenAddonCatalogIdsForProfile(profileId))
        assertEquals(listOf("other"), repository.getCatalogs().map { it.id })
    }

    @Test fun backgroundDefaultRefreshCannotRestoreCustomCatalogDeletedWhileItWasRunning() = runBlocking {
        repository.replaceCatalogsForProfile(profileId, listOf(catalog()))
        val captured = CompletableDeferred<Unit>()
        val resume = CompletableDeferred<Unit>()
        beforeWrite = { captured.complete(Unit); resume.await() }
        val refresh = async { repository.ensurePreinstalledDefaults(listOf(catalog("pre_default").copy(isPreinstalled = true))) }
        withTimeout(5000) { captured.await() }
        repository.removeCustomCatalog("custom_test").getOrThrow()
        resume.complete(Unit)
        refresh.await()
        assertFalse(repository.getCatalogs().any { it.id == "custom_test" })
    }
}
