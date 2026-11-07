package com.arflix.tv.ui.screens.player.engine.exoplayer

import android.content.Context
import android.os.Handler
import androidx.media3.common.Format
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.MediaCodecAudioRenderer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.text.TextOutput
import androidx.media3.exoplayer.video.VideoRendererEventListener
import com.arflix.tv.ui.screens.player.subtitles.SubtitleAutoSync

/**
 * The player's renderers factory: standard Media3 [DefaultRenderersFactory] with the normal text
 * renderer, plus the few playback behaviours the app relies on (audio decoder fallback, hardware-first
 * video, live subtitle/audio delay, buffered-cue reads for "Find best match").
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlayerRenderersFactory(
    context: Context
) : DefaultRenderersFactory(context) {

    val syncOffsetUs = java.util.concurrent.atomic.AtomicLong(0L)
    val audioDelayUs = java.util.concurrent.atomic.AtomicLong(0L)

    /**
     * Timing correction found by subtitle auto-match, applied live at the renderer instead of being
     * baked into the served file — so a correction (and a later refinement of it) lands with no
     * MediaItem rebuild. Set/cleared by PlayerScreen for the *currently selected* track only: that
     * scoping is what keeps a correction from leaking onto a different subtitle or a new file.
     */
    val autoSync = java.util.concurrent.atomic.AtomicReference<SubtitleAutoSync?>(null)

    private val audioDecoderFallback = AudioDecoderFallback()

    /** Retry a crashed decoder only when another renderer can handle the failing track. */
    fun blockAudioDecoder(decoderName: String, format: Format?): Boolean =
        audioDecoderFallback.blockIfSupported(decoderName, format)

    private val offsetRenderers = mutableListOf<SubtitleOffsetRenderer>()

    /**
     * Cue-visible intervals (startMs to endMs) of the currently selected text track, read from
     * ExoPlayer's buffered cue list via reflection — i.e. upcoming cues that haven't rendered yet.
     * Lets "Find best match" build its built-in reference from prefetched cues instead of waiting
     * for playback to reach them. Returns empty if nothing is buffered yet.
     */
    fun extractBufferedReferenceIntervals(maxCount: Int): List<Pair<Long, Long>> {
        for (renderer in offsetRenderers) {
            val intervals = renderer.extractBufferedIntervals(maxCount)
            if (intervals.isNotEmpty()) return intervals
        }
        return emptyList()
    }

    /**
     * Text of the currently-buffered cues of the selected text track. Lets "Find best match"
     * verify WHICH track the buffer belongs to (by comparing against the displayed candidate's
     * own lines) — timing-based self-detection false-positived on subs cut from the same master.
     */
    fun extractBufferedCueTexts(maxCount: Int): List<String> {
        for (renderer in offsetRenderers) {
            val texts = renderer.extractAllCueTexts(maxCount)
            if (texts.isNotEmpty()) return texts.take(maxCount)
        }
        return emptyList()
    }

    override fun buildAudioRenderers(
        context: Context,
        extensionRendererMode: Int,
        mediaCodecSelector: MediaCodecSelector,
        enableDecoderFallback: Boolean,
        audioSink: AudioSink,
        eventHandler: Handler,
        eventListener: AudioRendererEventListener,
        out: ArrayList<Renderer>
    ) {
        // EXTENSION_RENDERER_MODE_PREFER exists only for the Amlogic/SEI *video* C2 decoder hang
        // (PlayerScreen preferExtensionDecoder). For AUDIO, FFmpeg-first software-decodes every
        // E-AC3/TrueHD/DTS stream to PCM — killing AVR bitstream passthrough — and jellyfin-ffmpeg's
        // TrueHD path fails outright. Always build audio with mode ON: the platform
        // MediaCodecAudioRenderer (whose DefaultAudioSink auto-detects AVR capabilities and
        // bitstreams via bypass) goes first; FFmpeg stays strictly a fallback for codecs the
        // device can neither passthrough nor decode.
        val firstNewIndex = out.size
        super.buildAudioRenderers(
            context, EXTENSION_RENDERER_MODE_ON, audioDecoderFallback.wrapSelector(mediaCodecSelector),
            enableDecoderFallback, audioSink, eventHandler, eventListener, out
        )
        audioDecoderFallback.softwareCapabilities = out.subList(firstNewIndex, out.size)
            .filterNot { it is MediaCodecAudioRenderer }
            .map { it.capabilities }
    }

    override fun buildVideoRenderers(
        context: Context,
        extensionRendererMode: Int,
        mediaCodecSelector: MediaCodecSelector,
        enableDecoderFallback: Boolean,
        eventHandler: Handler,
        eventListener: VideoRendererEventListener,
        allowedVideoJoiningTimeMs: Long,
        out: ArrayList<Renderer>
    ) {
        // VIDEO is ALWAYS hardware-first, exactly like NuvioTV (which ships no software video
        // renderer at all and never guesses by device name). The bundled jellyfin FFmpeg *video*
        // decoder must never be PREFERRED — software 4K HEVC/DV decode can't keep up on TV boxes
        // (Homatics/Mi Box), producing audio-but-no-video → source skip, while the hardware
        // decoder plays fine. Forcing MODE_ON keeps the platform MediaCodecVideoRenderer first;
        // enableDecoderFallback + forceDisableMediaCodecAsynchronousQueueing (set on the factory)
        // handle genuine hardware failures/hangs reactively — no device-class heuristic.
        val baseOut = ArrayList<Renderer>()
        super.buildVideoRenderers(
            context, EXTENSION_RENDERER_MODE_ON, mediaCodecSelector,
            enableDecoderFallback, eventHandler, eventListener, allowedVideoJoiningTimeMs, baseOut
        )
        for (renderer in baseOut) {
            out.add(VideoOffsetRenderer(renderer, audioDelayUs))
        }
    }

    override fun buildTextRenderers(
        context: Context,
        output: TextOutput,
        outputLooper: android.os.Looper,
        extensionRendererMode: Int,
        out: ArrayList<Renderer>
    ) {
        val startIndex = out.size
        super.buildTextRenderers(context, output, outputLooper, extensionRendererMode, out)
        offsetRenderers.clear()
        for (index in startIndex until out.size) {
            val offsetRenderer = SubtitleOffsetRenderer(
                baseRenderer = out[index],
                syncOffsetUs = syncOffsetUs,
                autoSync = autoSync
            )
            offsetRenderers.add(offsetRenderer)
            out[index] = offsetRenderer
        }
    }
}

/**
 * Forwarding renderer that wraps ExoPlayer's TextRenderer to apply the subtitle delay (manual knob
 * plus the auto-match correction) and to read the renderer's already-buffered cues.
 *
 * Cue texts are extracted from ExoPlayer's internal cue buffer via reflection,
 * supporting both the modern CuesResolver path and the legacy subtitle/nextSubtitle fields.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
private class SubtitleOffsetRenderer(
    private val baseRenderer: Renderer,
    private val syncOffsetUs: java.util.concurrent.atomic.AtomicLong = java.util.concurrent.atomic.AtomicLong(0L),
    private val autoSync: java.util.concurrent.atomic.AtomicReference<SubtitleAutoSync?> =
        java.util.concurrent.atomic.AtomicReference(null)
) : Renderer by baseRenderer {

    override fun render(positionUs: Long, elapsedRealtimeUs: Long) {
        // The manual delay knob and the auto-match correction compose: a cue authored at t is meant
        // to show at t + offset, so to display it at player position p the base renderer must be
        // driven at t = p - offset. This runs on every render tick — keep it to one subtraction.
        val offset = syncOffsetUs.get() + (autoSync.get()?.offsetUs ?: 0L)
        val adjustedUs = if (offset != 0L) (positionUs - offset).coerceAtLeast(0L) else positionUs
        baseRenderer.render(adjustedUs, elapsedRealtimeUs)
    }

    // ── Reflection-based cue extraction ──────────────────────────────────────
    // Reflection lives in BufferedCueReader.

    /** Text of the buffered cues, at most [maxCount]. */
    fun extractAllCueTexts(maxCount: Int): List<String> =
        BufferedCueReader.allCueTexts(baseRenderer, maxCount)

    /** Buffered cue intervals (startMs, endMs) for text-carrying cues, from the modern resolver. */
    fun extractBufferedIntervals(maxCount: Int): List<Pair<Long, Long>> =
        BufferedCueReader.intervals(baseRenderer, maxCount)
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
private class VideoOffsetRenderer(
    private val baseRenderer: Renderer,
    private val audioDelayUs: java.util.concurrent.atomic.AtomicLong
) : Renderer by baseRenderer {

    override fun render(positionUs: Long, elapsedRealtimeUs: Long) {
        val delayUs = audioDelayUs.get()
        val adjustedUs = if (delayUs != 0L) (positionUs + delayUs).coerceAtLeast(0L) else positionUs
        baseRenderer.render(adjustedUs, elapsedRealtimeUs)
    }
}
