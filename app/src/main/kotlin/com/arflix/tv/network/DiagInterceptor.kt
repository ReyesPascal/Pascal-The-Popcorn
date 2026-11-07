package com.arflix.tv.network

import com.arflix.tv.util.PtpDiag
import okhttp3.Interceptor
import okhttp3.Response

/** Observes headers only. Application-level so cache hits and redirects remain OkHttp-owned. */
class DiagInterceptor internal constructor(
    private val client: String,
    private val emit: (String, Array<out Pair<String, Any?>>) -> Unit = { event, fields ->
        PtpDiag.i(event, *fields)
    }
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val started = System.nanoTime()
        val fields = arrayOf<Pair<String, Any?>>(
            "client" to client,
            "method" to request.method,
            "host" to request.url.host,
            "path" to diagSafeText(request.url.encodedPath).takeLast(100),
            "range" to if (client == "playback") request.header("Range")?.take(160) else null
        )
        val response = try {
            chain.proceed(request)
        } catch (error: Exception) {
            runCatching {
                emit("http-error", fields + arrayOf(
                    "ms" to (System.nanoTime() - started) / 1_000_000,
                    "exception" to error.javaClass.simpleName,
                    "message" to diagSafeText(error.message.orEmpty()).take(300)
                ))
            }
            throw error
        }
        runCatching {
            emit("http-done", fields + arrayOf(
                "status" to response.code,
                "ms" to (System.nanoTime() - started) / 1_000_000,
                "contentLengthBytes" to response.header("Content-Length")?.toLongOrNull(),
                "protocol" to response.protocol.toString()
            ))
        }
        return response
    }
}

private val diagUrl = Regex("https?://[^\\s]+", RegexOption.IGNORE_CASE)
private val diagSecret = Regex("(?i)(token|api[_-]?key|password|authorization|auth|key)=([^&\\s]+)")
private val diagLongSegment = Regex("(?<=/)[^/\\s?]{24,}")

/** Redact before truncation, which could otherwise remove the prefix used to recognize a token. */
internal fun diagSafeText(value: String): String = diagLongSegment.replace(
    diagSecret.replace(diagUrl.replace(value) { it.value.substringBefore('?').substringBefore('#') }) {
        "${it.groupValues[1]}=<redacted>"
    },
    "<redacted>"
)
