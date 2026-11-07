package com.arflix.tv.network

import io.mockk.every
import io.mockk.mockk
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okio.BufferedSource
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class DiagInterceptorTest {
    private val request = Request.Builder()
        .url("https://example.test/stream/movie.json?api_key=secret")
        .header("Range", "bytes=123-456")
        .build()

    private fun chain(request: Request = this.request): Interceptor.Chain = mockk<Interceptor.Chain>().also {
        every { it.request() } returns request
    }

    @Test fun realRequestPreservesRangeStatusHeadersAndBody() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(206).setBody("chunk"))
            server.start()
            val events = mutableListOf<Pair<String, Map<String, Any?>>>()
            val client = OkHttpClient.Builder().addInterceptor(DiagInterceptor("playback") { event, fields ->
                events += event to fields.toMap()
            }).build()
            try {
                val request = Request.Builder().url(server.url("/movie/chunk?token=secret"))
                    .header("Range", "bytes=0-4").build()
                client.newCall(request).execute().use { response ->
                    assertEquals(206, response.code)
                    assertEquals("chunk", response.body!!.string())
                }
                assertEquals("bytes=0-4", server.takeRequest().getHeader("Range"))
                assertEquals("http-done", events.single().first)
                assertEquals("/movie/chunk", events.single().second["path"])
                assertEquals(5L, events.single().second["contentLengthBytes"])
                assertFalse(events.toString().contains("secret"))
            } finally {
                client.connectionPool.evictAll()
                client.dispatcher.executorService.shutdown()
            }
        }
    }

    @Test fun successPreservesResponseAndDoesNotInspectBody() {
        val events = mutableListOf<Pair<String, Map<String, Any?>>>()
        val body = object : ResponseBody() {
            override fun contentType(): okhttp3.MediaType? = error("body inspected")
            override fun contentLength(): Long = error("body inspected")
            override fun source(): BufferedSource = error("body consumed")
        }
        val response = Response.Builder().request(request).protocol(Protocol.HTTP_2)
            .code(206).message("Partial Content").header("Content-Length", "334").body(body).build()
        val chain = chain()
        every { chain.proceed(request) } returns response
        val actual = DiagInterceptor("playback") { event, fields -> events += event to fields.toMap() }.intercept(chain)
        assertSame(response, actual)
        assertSame(body, actual.body)
        assertEquals("http-done", events.single().first)
        val fields = events.single().second
        assertEquals("/stream/movie.json", fields["path"])
        assertEquals("example.test", fields["host"])
        assertEquals("GET", fields["method"])
        assertEquals("playback", fields["client"])
        assertEquals("bytes=123-456", fields["range"])
        assertEquals(206, fields["status"])
        assertEquals(334L, fields["contentLengthBytes"])
        assertEquals("h2", fields["protocol"])
        assertTrue(fields["ms"] is Long && fields["ms"] as Long >= 0)
        assertFalse(fields.toString().contains("secret"))
    }

    @Test fun failureRethrowsSameExceptionAndStripsQuerySecrets() {
        val chain = chain()
        val error = IOException("failed https://example.test/file?token=secret")
        every { chain.proceed(request) } throws error
        var event = ""
        var values = emptyMap<String, Any?>()
        try {
            DiagInterceptor("app") { name, fields -> event = name; values = fields.toMap() }.intercept(chain)
            fail("expected failure")
        } catch (actual: IOException) {
            assertSame(error, actual)
        }
        assertEquals("http-error", event)
        assertEquals("IOException", values["exception"])
        assertEquals("failed https://example.test/file", values["message"])
        assertNull(values["range"])
        assertFalse(values.containsKey("status"))
    }

    @Test fun diagnosticFailureDoesNotChangeHttpOutcome() {
        val chain = chain()
        val response = Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
            .code(503).message("Unavailable").build()
        every { chain.proceed(request) } returns response
        assertSame(response, DiagInterceptor("app") { _, _ -> error("logger failed") }.intercept(chain))
        val error = IOException("offline")
        every { chain.proceed(request) } throws error
        try {
            DiagInterceptor("app") { _, _ -> error("logger failed") }.intercept(chain)
            fail("expected failure")
        } catch (actual: IOException) {
            assertSame(error, actual)
        }
    }

    @Test fun pathIsBoundedAndRedactedBeforeTruncation() {
        val request = Request.Builder().url("https://example.test/" + "t".repeat(180) + "/file.json").build()
        val chain = chain(request)
        every { chain.proceed(request) } returns Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
            .code(200).message("OK").build()
        var values = emptyMap<String, Any?>()
        DiagInterceptor("app") { _, fields -> values = fields.toMap() }.intercept(chain)
        assertEquals("/<redacted>/file.json", values["path"])
        assertNull(values["contentLengthBytes"])
        assertNull(values["range"])
    }

    @Test fun secretAssignmentsAreRedacted() {
        assertEquals("token=<redacted> api_key=<redacted> password=<redacted>",
            diagSafeText("token=abc api_key=def password=ghi"))
    }
}
