package com.arflix.tv.data.repository

import android.content.Context
import com.arflix.tv.data.model.CatalogConfig
import com.arflix.tv.data.model.CatalogKind
import com.arflix.tv.data.model.CatalogSourceType
import io.mockk.mockk
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Test

class CatalogRepositoryLegacyCompatibilityTest {
    private val repository = CatalogRepository(
        context = mockk<Context>(relaxed = true),
        profileManager = mockk<ProfileManager>(relaxed = true),
        okHttpClient = mockk<OkHttpClient>(relaxed = true)
    )

    @Suppress("UNCHECKED_CAST")
    private fun decode(json: String): List<CatalogConfig> =
        CatalogRepository::class.java.getDeclaredMethod("parseCatalogsJson", String::class.java)
            .apply { isAccessible = true }.invoke(repository, json) as List<CatalogConfig>

    @Test fun `saved collection kinds and non-addon catalogs are dropped`() {
        val restored = decode("""[
            {"id":"old_tile","title":"Old tile","sourceType":"ADDON","kind":"collection"},
            {"id":"first","title":"Renamed","sourceType":"addon","addonId":"one","addonCatalogType":"movie","addonCatalogId":"movies"},
            {"id":"old_rail","title":"Old rail","sourceType":"PREINSTALLED","kind":"COLLECTION_RAIL"},
            {"id":"youtube_old","title":"Videos","sourceType":"YOUTUBE"},
            {"id":"old_default","title":"Trending","sourceType":"PREINSTALLED"},
            {"id":"second","title":"Shows","sourceType":"ADDON","kind":"STANDARD","addonId":"two","addonCatalogType":"series","addonCatalogId":"shows"}
        ]""")
        assertEquals(listOf("first", "second"), restored.map { it.id })
        assertEquals("Renamed", restored.first().title)
        assertEquals("one", restored.first().addonId)
        assertEquals("movie", restored.first().addonCatalogType)
        assertEquals(CatalogKind.STANDARD, restored.first().kind)
    }

    @Test fun `malformed row does not hide subsequent valid addon catalog`() {
        assertEquals(listOf("kept"), decode("""[null,
            {"id":"kept","title":"Kept","sourceType":"ADDON"}]
        """).map { it.id })
        assertEquals(emptyList<CatalogConfig>(), decode("not json"))
    }
}
