package com.arflix.tv.data.repository

import com.arflix.tv.data.model.CatalogConfig
import com.arflix.tv.data.model.CatalogSourceType
import com.arflix.tv.data.model.MediaItem
import com.arflix.tv.data.model.MediaType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.spyk
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

class ListFreshnessTest {
    @Test fun `marker holds for one window and moves with the next`() {
        val start = 7 * LIST_REFRESH_MS
        assertEquals(listFreshnessMarker(start), listFreshnessMarker(start + LIST_REFRESH_MS - 1))
        assertEquals(listFreshnessMarker(start) + 1, listFreshnessMarker(start + LIST_REFRESH_MS))
    }

    @Test fun `marker is appended as its own query parameter`() {
        val now = 3 * LIST_REFRESH_MS
        assertEquals("https://example.test/list/json?_=3", withListFreshness("https://example.test/list/json", now))
        assertEquals("https://example.test/list/json?a=1&_=3", withListFreshness("https://example.test/list/json?a=1", now))
    }







    @org.junit.Test
    fun `a bump moves the marker on within the same window`() {
        val now = 3 * LIST_REFRESH_MS + 5
        assertEquals(listFreshnessMarker(now, bump = 0) + 1, listFreshnessMarker(now, bump = 1))
        assertEquals("https://example.test/list/json?_=4", withListFreshness("https://example.test/list/json", now, bump = 1))
    }
}
