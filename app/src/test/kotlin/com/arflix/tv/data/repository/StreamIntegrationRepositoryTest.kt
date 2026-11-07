package com.arflix.tv.data.repository

import com.arflix.tv.data.model.Addon
import com.arflix.tv.data.model.AddonType
import com.arflix.tv.data.model.StreamIntegrationType
import com.arflix.tv.data.model.StreamSearchMode
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.inject.Provider

class StreamIntegrationRepositoryTest {

    private fun addon(id: String, type: AddonType = AddonType.COMMUNITY, enabled: Boolean = true) = Addon(
        id = id,
        name = id,
        version = "1.0.0",
        description = id,
        isInstalled = true,
        type = type,
        transportUrl = "https://$id.example.com/manifest.json",
        isEnabled = enabled
    )

    private val addons = MutableStateFlow(
        listOf(
            addon("usenet-ultimate"),
            addon("opensubtitles", AddonType.SUBTITLE),
            addon("other"),
            addon("disabled", enabled = false)
        )
    )

    private val streamRepository = mockk<StreamRepository>(relaxed = true) {
        every { installedAddons } returns addons
    }

    private fun createRepository() = StreamIntegrationRepository(Provider { streamRepository })

    @Test
    fun onlyStremioAddonsIsConfiguredAndEnabled() = runBlocking {
        val repo = createRepository()
        val configs = repo.getConfigs()

        assertEquals(listOf(StreamIntegrationType.STREMIO_ADDONS), configs.map { it.type })
        assertTrue(configs.single().isEnabled)
        assertEquals(configs, repo.observeConfigs().first())
    }

    @Test
    fun onlyStremioAddonsIntegrationIsEnabled() = runBlocking {
        val repo = createRepository()
        assertTrue(repo.isIntegrationEnabled(StreamIntegrationType.STREMIO_ADDONS))
        StreamIntegrationType.entries.filter { it != StreamIntegrationType.STREMIO_ADDONS }.forEach {
            assertFalse(repo.isIntegrationEnabled(it))
        }
    }

    @Test
    fun unifiedOrderIsInstalledStreamAddonsInInstallOrder() = runBlocking {
        val repo = createRepository()
        assertEquals(listOf("usenet-ultimate", "other"), repo.getUnifiedSourceOrderedIdsSync())

        addons.value = listOf(addon("other"), addon("usenet-ultimate"))
        assertEquals(listOf("other", "usenet-ultimate"), repo.getUnifiedSourceOrderedIds().first())
    }

    @Test
    fun providerItemsAreStremioAddonsOnly() = runBlocking {
        val repo = createRepository()
        val items = repo.getProviderItems()

        assertEquals(listOf("stremio:usenet-ultimate", "stremio:other", "stremio:disabled"), items.map { it.id })
        assertTrue(items.all { it.type == StreamIntegrationType.STREMIO_ADDONS })
        assertEquals(setOf("stremio:usenet-ultimate", "stremio:other"), repo.enabledProviderIds(StreamIntegrationType.STREMIO_ADDONS))
        assertEquals(setOf("stremio:other"), repo.enabledProviderIds(StreamIntegrationType.STREMIO_ADDONS, "stremio:other"))
    }

    @Test
    fun searchModeIsAlwaysParallel() = runBlocking {
        val repo = createRepository()
        assertEquals(StreamSearchMode.PARALLEL, repo.getSearchMode())
        assertEquals(StreamSearchMode.PARALLEL, repo.observeSearchMode().first())
    }
}
