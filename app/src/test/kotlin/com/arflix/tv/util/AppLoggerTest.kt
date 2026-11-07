package com.arflix.tv.util

import com.google.common.truth.Truth.assertThat
import android.util.Log
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28], application = android.app.Application::class)
class AppLoggerTest {

    @Before
    fun clearLogs() {
        ShadowLog.clear()
    }

    @Test
    fun `logcat retains levels tags and sanitized messages without initialization`() {
        val tag = "[PlaybackDiag]"
        AppLogger.v(tag, "verbose")
        AppLogger.d(tag, "debug")
        AppLogger.i(tag, "stream https://example.com/token")
        AppLogger.w(tag, "warning")
        AppLogger.e(tag, "error")

        val logs = ShadowLog.getLogsForTag(tag)
        assertThat(logs.map { it.type }).containsExactly(Log.VERBOSE, Log.DEBUG, Log.INFO, Log.WARN, Log.ERROR).inOrder()
        assertThat(logs[2].msg).isEqualTo("stream [URL]")
    }

    @Test
    fun `exceptions and context go to logcat`() {
        val error = IllegalStateException("failure")
        AppLogger.recordException(error, mapOf("source" to "https://example.com/private"))
        AppLogger.breadcrumb("[PageTiming]", "first frame")
        AppLogger.setCrashContext("count", 2)
        AppLogger.setCrashContext("ready", true)
        AppLogger.setUserId("private-id")

        val logs = ShadowLog.getLogsForTag("AppLogger")
        assertThat(logs.first().msg).isEqualTo("source=[URL]")
        assertThat(logs[1].throwable).isSameInstanceAs(error)
        assertThat(logs.last().msg).isEqualTo("user_id=${"private-id".hash()}")
        assertThat(ShadowLog.getLogsForTag("[PageTiming]").single().msg).contains("first frame")
    }


    @Test
    fun `sanitizeEmail masks email correctly`() {
        val email = "user@example.com"
        val sanitized = email.sanitizeEmail()

        assertThat(sanitized).isEqualTo("u***@***.com")
    }

    @Test
    fun `sanitizeEmail handles short emails`() {
        val email = "a@b.co"
        val sanitized = email.sanitizeEmail()

        assertThat(sanitized).isEqualTo("a***@***.co")
    }

    @Test
    fun `sanitizeEmail handles invalid format`() {
        val invalid = "notanemail"
        val sanitized = invalid.sanitizeEmail()

        assertThat(sanitized).isEqualTo("[EMAIL]")
    }

    @Test
    fun `hash produces consistent short hash`() {
        val input = "user-123-uuid"
        val hash1 = input.hash()
        val hash2 = input.hash()

        assertThat(hash1).isEqualTo(hash2)
        assertThat(hash1.length).isEqualTo(12)  // 6 bytes = 12 hex chars
    }

    @Test
    fun `hash produces different results for different inputs`() {
        val hash1 = "user1".hash()
        val hash2 = "user2".hash()

        assertThat(hash1).isNotEqualTo(hash2)
    }

    @Test
    fun `maskToken shows first 4 chars`() {
        val token = "sk-12345678901234567890"
        val masked = token.maskToken()

        assertThat(masked).isEqualTo("sk-1***")
    }

    @Test
    fun `maskToken handles short tokens`() {
        val token = "abc"
        val masked = token.maskToken()

        assertThat(masked).isEqualTo("***")
    }

    @Test
    fun `maskToken handles exactly 4 char token`() {
        val token = "abcd"
        val masked = token.maskToken()

        assertThat(masked).isEqualTo("***")
    }

    @Test
    fun `maskToken handles 5 char token`() {
        val token = "abcde"
        val masked = token.maskToken()

        assertThat(masked).isEqualTo("abcd***")
    }
}
