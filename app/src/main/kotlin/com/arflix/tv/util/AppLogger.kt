package com.arflix.tv.util

import android.util.Log
import java.security.MessageDigest

/** Application-wide logcat logging with message sanitization. */
object AppLogger {
    private const val MAX_DIAGNOSTIC_LENGTH = 500

    /** Retained for source compatibility. Logging always goes directly to logcat. */
    interface CrashContextProvider {
        fun setCustomKey(key: String, value: String)
        fun setCustomKey(key: String, value: Int)
        fun setCustomKey(key: String, value: Boolean)
        fun log(message: String)
        fun recordException(throwable: Throwable)
        fun setUserId(userId: String?)
    }

    /** Compatibility entry point; external logging providers are no longer used. */
    @Suppress("UNUSED_PARAMETER")
    fun init(provider: CrashContextProvider?) {}

    fun v(tag: String, message: String) {
        Log.v(tag, sanitize(message))
    }

    fun d(tag: String, message: String) {
        Log.d(tag, sanitize(message))
    }

    fun i(tag: String, message: String) {
        Log.i(tag, sanitize(message))
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        Log.w(tag, sanitize(message), throwable)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        Log.e(tag, sanitize(message), throwable)
    }

    fun breadcrumb(tag: String, message: String, severity: String = "info") {
        Log.i(tag, "[$severity]: ${sanitize(message)}")
    }

    fun setCrashContext(key: String, value: String) {
        Log.d("AppLogger", "${safeTag(key)}=${sanitize(value)}")
    }

    fun setCrashContext(key: String, value: Int) {
        Log.d("AppLogger", "${safeTag(key)}=$value")
    }

    fun setCrashContext(key: String, value: Boolean) {
        Log.d("AppLogger", "${safeTag(key)}=$value")
    }

    fun setUserId(userId: String?) {
        Log.d("AppLogger", "user_id=${userId?.hash()}")
    }

    fun recordException(throwable: Throwable, context: Map<String, String> = emptyMap()) {
        context.forEach { (key, value) -> setCrashContext(key, value) }
        Log.e("AppLogger", sanitize(throwable::class.java.simpleName), throwable)
    }

    // ============================================
    // PII Sanitization
    // ============================================

    private val EMAIL_PATTERN = Regex("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}")
    private val URL_PATTERN = Regex("\\b(?:https?|wss?|ftp|file|content)://[^\\s\"'<>]+", RegexOption.IGNORE_CASE)
    private val MAGNET_PATTERN = Regex("\\bmagnet:\\?[^\\s\"'<>]+", RegexOption.IGNORE_CASE)
    private val JWT_PATTERN = Regex("\\b[A-Za-z0-9_-]{16,}\\.[A-Za-z0-9_-]{16,}\\.[A-Za-z0-9_-]{16,}\\b")
    private val IPV4_PATTERN = Regex("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b")
    private val TOKEN_PATTERN = Regex("(token|jwt|bearer|api[_-]?key|secret)[\"':\\s=]+([a-zA-Z0-9._-]{20,})", RegexOption.IGNORE_CASE)
    private val LONG_HEX_PATTERN = Regex("[a-fA-F0-9]{32,}")
    private val SAFE_TAG_PATTERN = Regex("[^A-Za-z0-9_.-]")

    /**
     * Sanitize a message by removing/masking PII.
     */
    internal fun sanitize(message: String): String {
        var result = message

        result = URL_PATTERN.replace(result, "[URL]")
        result = MAGNET_PATTERN.replace(result, "[MAGNET]")
        result = JWT_PATTERN.replace(result, "[TOKEN]")
        result = IPV4_PATTERN.replace(result, "[IP]")

        // Mask emails: user@example.com -> u***@***.com
        result = EMAIL_PATTERN.replace(result) { match ->
            val email = match.value
            val atIndex = email.indexOf('@')
            val dotIndex = email.lastIndexOf('.')
            if (atIndex > 0 && dotIndex > atIndex) {
                "${email[0]}***@***.${email.substring(dotIndex + 1)}"
            } else {
                "[EMAIL]"
            }
        }

        // Mask tokens/API keys: show only first 4 chars
        result = TOKEN_PATTERN.replace(result) { match ->
            val prefix = match.groupValues[1]
            val token = match.groupValues[2]
            val masked = if (token.length > 4) "${token.take(4)}***" else "***"
            "$prefix:$masked"
        }

        // Mask long hex strings (likely hashes/tokens): show first 8
        result = LONG_HEX_PATTERN.replace(result) { match ->
            val hex = match.value
            if (hex.length > 8) "${hex.take(8)}..." else hex
        }

        return result.take(MAX_DIAGNOSTIC_LENGTH)
    }

    private fun safeTag(tag: String): String {
        return tag
            .replace(SAFE_TAG_PATTERN, "_")
            .take(40)
            .ifBlank { "app" }
    }

}

// ============================================
// Extension functions for PII-safe logging
// ============================================

/**
 * Hash a string for safe logging (e.g., user IDs).
 * Uses first 12 chars of SHA-256 hash.
 */
fun String.hash(): String {
    return try {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(this.toByteArray())
        hash.take(6).joinToString("") { "%02x".format(it) }
    } catch (e: Exception) {
        "hash_error"
    }
}

/**
 * Sanitize an email for logging: user@example.com -> u***@***.com
 */
fun String.sanitizeEmail(): String {
    val atIndex = indexOf('@')
    val dotIndex = lastIndexOf('.')
    return if (atIndex > 0 && dotIndex > atIndex) {
        "${this[0]}***@***.${substring(dotIndex + 1)}"
    } else {
        "[EMAIL]"
    }
}

/**
 * Mask a token/key for logging: show only first 4 characters.
 */
fun String.maskToken(): String {
    return if (length > 4) "${take(4)}***" else "***"
}
