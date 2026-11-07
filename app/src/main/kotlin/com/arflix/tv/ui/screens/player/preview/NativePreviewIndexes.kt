package com.arflix.tv.ui.screens.player.preview

import java.nio.ByteBuffer
import java.nio.ByteOrder

internal object BifPreviewParser {
    private val magic = byteArrayOf(0x89.toByte(), 0x42, 0x49, 0x46, 0x0d, 0x0a, 0x1a, 0x0a)

    fun indexSize(header: ByteArray): Int? {
        if (header.size < 64 || !header.copyOfRange(0, 8).contentEquals(magic)) return null
        val bytes = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        if (bytes.getInt(8) != 0) return null
        val count = bytes.getInt(12).toLong() and 0xffffffffL
        if (count !in 1..PREVIEW_MAX_CUES.toLong()) return null
        return 64 + (count.toInt() + 1) * 8
    }

    fun parse(index: ByteArray, url: String, totalBytes: Long, durationMs: Long): PreviewStoryboard? {
        val size = indexSize(index) ?: return null
        if (index.size < size || totalBytes < size) return null
        val bytes = ByteBuffer.wrap(index).order(ByteOrder.LITTLE_ENDIAN)
        fun uint(offset: Int): Long = bytes.getInt(offset).toLong() and 0xffffffffL
        val count = bytes.getInt(12)
        val multiplier = uint(16).takeIf { it > 0L } ?: 1000L
        if (uint(64 + count * 8) != 0xffffffffL || uint(68 + count * 8) != totalBytes) return null
        val cues = mutableListOf<PreviewCue>()
        for (i in 0 until count) {
            val timestamp = uint(64 + i * 8)
            val nextTimestamp = if (i + 1 < count) uint(72 + i * 8) else null
            if (timestamp > PREVIEW_MAX_TIME_MS / multiplier ||
                (nextTimestamp != null && nextTimestamp > PREVIEW_MAX_TIME_MS / multiplier)) return null
            val start = timestamp * multiplier
            val end = nextTimestamp?.times(multiplier) ?: durationMs
            val offset = uint(68 + i * 8)
            val nextOffset = uint(76 + i * 8)
            if (start !in 0..PREVIEW_MAX_TIME_MS || offset < size || nextOffset <= offset ||
                nextOffset > totalBytes || nextOffset - offset > PREVIEW_MAX_IMAGE_BYTES) return null
            // The sentinel contains a byte offset, not an end time. Do not invent a last interval.
            if (i + 1 == count && durationMs <= 0) continue
            if (end <= start || end > PREVIEW_MAX_TIME_MS) return null
            cues += PreviewCue(start, end, url, byteOffset = offset, byteLength = (nextOffset - offset).toInt())
        }
        return try { PreviewStoryboard(cues).takeIf { cues.isNotEmpty() } } catch (_: IllegalArgumentException) { null }
    }
}
