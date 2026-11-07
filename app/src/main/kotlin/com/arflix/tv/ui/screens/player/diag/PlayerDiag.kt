package com.arflix.tv.ui.screens.player.diag

import android.os.Handler
import android.os.SystemClock
import androidx.media3.common.*
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.LoadEventInfo
import androidx.media3.exoplayer.source.MediaLoadData
import com.arflix.tv.BuildConfig
import com.arflix.tv.util.PtpDiag
import java.io.IOException
import java.util.IdentityHashMap

/** Observes only: never changes player state, selection, buffering or recovery. */
class PlayerDiag(private val player: ExoPlayer) : AnalyticsListener {
    private val handler = Handler(player.applicationLooper)
    private val stall = StallDetector()
    private var closed = false
    private var reason = 0
    private var seekAt: Long? = null
    private var seekWarned = false
    private var lastBeat = SystemClock.elapsedRealtime()
    private var bandwidth = 0L
    private var lastBandwidthLog = Long.MIN_VALUE
    private var dropped = 0L
    private var underruns = 0L
    private var videoDecoder: String? = null
    private var audioDecoder: String? = null
    private var lastLoad = "none"
    private var lastChunkInfo: LoadEventInfo? = null
    private var lastChunkData: MediaLoadData? = null
    private var chunkCount = 0
    private var chunkBytes = 0L
    private var chunkDuration = 0L
    private var lastChunkLog = SystemClock.elapsedRealtime()
    private val tick = object : Runnable {
        override fun run() {
            if (!shouldTick()) return
            val now = SystemClock.elapsedRealtime()
            checkSeek(now)
            sampleStall(now)
            flushChunks(now)
            if (player.isPlaying && now - lastBeat >= 10_000) {
                lastBeat = now
                log("player-beat", "bitrateEstimate" to bandwidth, "droppedFrames" to dropped,
                    "speed" to player.playbackParameters.speed, "volume" to player.volume,
                    "videoDecoder" to videoDecoder, "audioDecoder" to audioDecoder)
            }
            if (shouldTick()) handler.postDelayed(this, 1_000)
        }
    }

    private fun snapshot() = arrayOf<Pair<String, Any?>>("positionMs" to player.currentPosition,
        "bufferedMs" to player.bufferedPosition, "bufferAheadMs" to (player.bufferedPosition - player.currentPosition).coerceAtLeast(0),
        "durationMs" to player.duration, "playWhenReady" to player.playWhenReady,
        "isPlaying" to player.isPlaying, "reason" to reason, "playbackState" to state(player.playbackState),
        "uri" to player.currentMediaItem?.localConfiguration?.uri?.encodedPath?.takeLast(80))
    private fun log(event: String, vararg fields: Pair<String, Any?>) = PtpDiag.i(event, *DiagFields.bounded(snapshot() + fields))
    private fun shouldTick() = !closed && player.playWhenReady &&
        ((player.playbackState != Player.STATE_IDLE && player.playbackState != Player.STATE_ENDED) ||
            (seekAt != null && !seekWarned))
    private fun updateTimer() {
        handler.removeCallbacks(tick)
        val now = SystemClock.elapsedRealtime()
        sampleStall(now)
        if (!player.isPlaying) lastBeat = now
        if (shouldTick()) {
            handler.postDelayed(tick, 1_000)
        } else {
            lastBeat = now
        }
    }
    private fun sampleStall(now: Long) {
        stall.sample(now, player.playWhenReady && !closed, player.playbackState == Player.STATE_BUFFERING,
            player.playbackState == Player.STATE_READY && player.isPlaying, player.currentPosition).forEach {
            if (it.ended) log("player-stall-end", "kind" to it.kind, "stuckMs" to it.stuckMs)
            else log("player-stall", "kind" to it.kind, "stuckMs" to it.stuckMs, "isLoading" to player.isLoading,
                "lastLoad" to lastLoad, "bandwidthBps" to bandwidth, "videoDecoder" to videoDecoder,
                "audioDecoder" to audioDecoder, "playerThreadStack" to player.playbackLooper.thread.stackTrace.take(30).joinToString("\n") {
                    "${it.className.substringAfterLast('.')}.${it.methodName}:${it.lineNumber}".take(43)
                })
        }
    }
    override fun onPlaybackStateChanged(eventTime: AnalyticsListener.EventTime, state: Int) {
        log("player-state", "state" to state(state))
        if (state == Player.STATE_READY) finishSeek()
        updateTimer()
    }
    override fun onPlayWhenReadyChanged(eventTime: AnalyticsListener.EventTime, playWhenReady: Boolean, reason: Int) {
        this.reason = reason
        log("player-playing", "change" to "playWhenReady", "reasonName" to readyReason(reason))
        updateTimer()
    }
    override fun onIsPlayingChanged(eventTime: AnalyticsListener.EventTime, isPlaying: Boolean) {
        log("player-playing", "change" to "isPlaying", "suppressionReason" to player.playbackSuppressionReason,
            "reasonName" to readyReason(reason))
        updateTimer()
    }
    override fun onPositionDiscontinuity(eventTime: AnalyticsListener.EventTime, oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
        val name = when (reason) {
            Player.DISCONTINUITY_REASON_SEEK -> "SEEK"
            Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT -> "SEEK_ADJUSTMENT"
            Player.DISCONTINUITY_REASON_AUTO_TRANSITION -> "AUTO_TRANSITION"
            Player.DISCONTINUITY_REASON_SKIP -> "SKIP"
            else -> return
        }
        seekAt = SystemClock.elapsedRealtime()
        seekWarned = false
        log("seek-start", "fromMs" to oldPosition.positionMs, "toMs" to newPosition.positionMs,
            "deltaMs" to newPosition.positionMs - oldPosition.positionMs, "discontinuityReason" to name)
        updateTimer()
    }
    override fun onEvents(player: Player, events: AnalyticsListener.Events) {
        // Buffered seeks can remain READY without a state-change callback.
        if (seekAt != null && player.playbackState == Player.STATE_READY) finishSeek()
    }
    private fun finishSeek() {
        seekAt?.let { log("seek-done", "tookMs" to SystemClock.elapsedRealtime() - it, "lastLoad" to lastLoad) }
        seekAt = null
    }
    private fun checkSeek(now: Long) {
        seekAt?.let {
            if (!seekWarned && now - it >= 10_000) {
                seekWarned = true
                log("seek-stuck", "tookMs" to now - it, "lastLoad" to lastLoad)
            }
        }
    }
    override fun onLoadStarted(eventTime: AnalyticsListener.EventTime, info: LoadEventInfo, data: MediaLoadData, retryCount: Int) {
        load("load-start", info, data, retryCount = retryCount)
    }
    override fun onLoadCompleted(eventTime: AnalyticsListener.EventTime, info: LoadEventInfo, data: MediaLoadData) {
        if (data.dataType == C.DATA_TYPE_MEDIA) {
            lastChunkInfo = info
            lastChunkData = data
            chunkCount++
            chunkBytes += info.bytesLoaded
            chunkDuration += info.loadDurationMs
            lastLoad = "id=${info.loadTaskId} path=${path(info)} bytes=${info.bytesLoaded} ms=${info.loadDurationMs}"
            flushChunks(SystemClock.elapsedRealtime(), info, data)
        } else load("load-done", info, data)
    }
    private fun flushChunks(now: Long, info: LoadEventInfo? = lastChunkInfo, data: MediaLoadData? = lastChunkData) {
        if (chunkCount == 0 || now - lastChunkLog < 1_000) return
        val fields = arrayOf<Pair<String, Any?>>("count" to chunkCount, "bytes" to chunkBytes,
            "avgKbps" to kbps(chunkBytes, chunkDuration), "totalLoadDurationMs" to chunkDuration, "lastLoad" to lastLoad)
        if (info != null && data != null) load("load-done", info, data, extra = fields)
        else log("load-done", "summary" to true, *fields)
        chunkCount = 0; chunkBytes = 0; chunkDuration = 0; lastChunkLog = now
    }
    override fun onLoadCanceled(eventTime: AnalyticsListener.EventTime, info: LoadEventInfo, data: MediaLoadData) = load("load-canceled", info, data, canceled = true)
    override fun onLoadError(eventTime: AnalyticsListener.EventTime, info: LoadEventInfo, data: MediaLoadData, error: IOException, wasCanceled: Boolean) = load("load-error", info, data, error, wasCanceled)
    private fun path(info: LoadEventInfo) = info.uri.encodedPath?.takeLast(80)
    private fun load(event: String, info: LoadEventInfo, data: MediaLoadData, error: IOException? = null, canceled: Boolean = false,
        retryCount: Int? = null, extra: Array<Pair<String, Any?>> = emptyArray()) {
        fun header(name: String) = info.responseHeaders.entries.firstOrNull { it.key.equals(name, true) }?.value?.firstOrNull()?.take(100)
        val status = (error as? HttpDataSource.InvalidResponseCodeException)?.responseCode
            ?: info.responseHeaders.entries.firstOrNull { it.key.isNullOrEmpty() || it.key.equals(":status", true) }?.value?.firstOrNull()?.let { Regex("(?:HTTP/[^ ]+ )?(\\d{3})").find(it)?.groupValues?.get(1)?.toIntOrNull() }
        lastLoad = "id=${info.loadTaskId} path=${path(info)} bytes=${info.bytesLoaded} ms=${info.loadDurationMs} event=$event"
        log(event, "loadTaskId" to info.loadTaskId, "dataType" to data.dataType, "trackType" to data.trackType,
            "uri" to path(info), "rangePosition" to info.dataSpec.position, "rangeLength" to info.dataSpec.length,
            "bytesLoaded" to info.bytesLoaded, "loadDurationMs" to info.loadDurationMs, "kbps" to kbps(info.bytesLoaded, info.loadDurationMs),
            "httpCode" to status, "contentRange" to header("Content-Range"), "contentLength" to header("Content-Length"),
            "errorClass" to error?.javaClass?.name, "errorMessage" to error?.message?.take(300), "wasCanceled" to canceled,
            "retryCount" to retryCount, "mediaStartMs" to data.mediaStartTimeMs, "mediaEndMs" to data.mediaEndTimeMs, *extra)
    }
    override fun onPlayerError(eventTime: AnalyticsListener.EventTime, error: PlaybackException) {
        val seen = java.util.Collections.newSetFromMap(IdentityHashMap<Throwable, Boolean>())
        var cause: Throwable? = error
        var index = 0
        while (cause != null && seen.add(cause)) {
            PtpDiag.e("player-error", null, *DiagFields.bounded(snapshot() + arrayOf("errorCodeName" to error.errorCodeName,
                "causeIndex" to index++, "errorClass" to cause.javaClass.name, "errorMessage" to cause.message?.take(400),
                "host" to player.currentMediaItem?.localConfiguration?.uri?.host?.take(180),
                "uri" to player.currentMediaItem?.localConfiguration?.uri?.encodedPath?.takeLast(80))))
            cause = cause.cause
        }
    }
    private fun warn(kind: String, error: Exception) = PtpDiag.w("player-warn", *DiagFields.bounded(snapshot() + arrayOf("kind" to kind,
        "errorClass" to error.javaClass.name, "errorMessage" to error.message?.take(400))))
    override fun onAudioSinkError(eventTime: AnalyticsListener.EventTime, audioSinkError: Exception) = warn("audio-sink", audioSinkError)
    override fun onVideoCodecError(eventTime: AnalyticsListener.EventTime, videoCodecError: Exception) = warn("video-codec", videoCodecError)
    override fun onAudioCodecError(eventTime: AnalyticsListener.EventTime, audioCodecError: Exception) = warn("audio-codec", audioCodecError)
    override fun onAudioUnderrun(eventTime: AnalyticsListener.EventTime, bufferSize: Int, bufferSizeMs: Long, elapsedSinceLastFeedMs: Long) {
        PtpDiag.w("player-warn", *snapshot(), "kind" to "audio-underrun", "bufferSize" to bufferSize,
            "bufferSizeMs" to bufferSizeMs, "msSinceLast" to elapsedSinceLastFeedMs, "count" to ++underruns)
    }
    override fun onDroppedVideoFrames(eventTime: AnalyticsListener.EventTime, droppedFrames: Int, elapsedMs: Long) {
        dropped += droppedFrames
        PtpDiag.w("player-warn", *snapshot(), "kind" to "dropped-frames", "count" to droppedFrames, "elapsedMs" to elapsedMs, "total" to dropped)
    }
    private fun format(type: String, f: Format) = log("player-format", "type" to type, "codecs" to f.codecs?.take(120),
        "mime" to f.sampleMimeType, "width" to f.width, "height" to f.height, "fps" to f.frameRate, "bitrate" to f.bitrate,
        "colorSpace" to f.colorInfo?.colorSpace, "colorRange" to f.colorInfo?.colorRange, "hdrTransfer" to f.colorInfo?.colorTransfer,
        "dolbyVision" to (f.sampleMimeType == "video/dolby-vision" || f.codecs?.startsWith("dv") == true),
        "channels" to f.channelCount, "sampleRate" to f.sampleRate)
    override fun onVideoInputFormatChanged(eventTime: AnalyticsListener.EventTime, format: Format, decoderReuseEvaluation: DecoderReuseEvaluation?) = format("video", format)
    override fun onAudioInputFormatChanged(eventTime: AnalyticsListener.EventTime, format: Format, decoderReuseEvaluation: DecoderReuseEvaluation?) = format("audio", format)
    override fun onVideoDecoderInitialized(eventTime: AnalyticsListener.EventTime, decoderName: String, initializedTimestampMs: Long, initializationDurationMs: Long) {
        videoDecoder = decoderName; decoder("video", "init", decoderName, initializationDurationMs)
    }
    override fun onAudioDecoderInitialized(eventTime: AnalyticsListener.EventTime, decoderName: String, initializedTimestampMs: Long, initializationDurationMs: Long) {
        audioDecoder = decoderName; decoder("audio", "init", decoderName, initializationDurationMs)
    }
    override fun onVideoDecoderReleased(eventTime: AnalyticsListener.EventTime, decoderName: String) { videoDecoder = null; decoder("video", "release", decoderName, null) }
    override fun onAudioDecoderReleased(eventTime: AnalyticsListener.EventTime, decoderName: String) { audioDecoder = null; decoder("audio", "release", decoderName, null) }
    private fun decoder(type: String, action: String, name: String, ms: Long?) = log("player-decoder", "type" to type, "action" to action, "name" to name.take(180), "initMs" to ms)
    override fun onTracksChanged(eventTime: AnalyticsListener.EventTime, tracks: Tracks) {
        tracks.groups.groupBy { it.type }.forEach { (type, groups) ->
            val text = if (type == C.TRACK_TYPE_TEXT) groups.asSequence().flatMap { g -> (0 until g.length).asSequence().map { i ->
                val f = g.getTrackFormat(i)
                "${f.language?.take(20)}:${f.label?.take(32)}:${g.isTrackSelected(i)}"
            } }.take(20).joinToString("|").take(1000) else null
            log("player-tracks", "type" to type, "count" to groups.sumOf { it.length },
                "selected" to groups.sumOf { g -> (0 until g.length).count { g.isTrackSelected(it) } }, "textTracks" to text)
        }
    }
    override fun onVideoSizeChanged(eventTime: AnalyticsListener.EventTime, videoSize: VideoSize) = log("player-video-size",
        "width" to videoSize.width, "height" to videoSize.height, "pixelRatio" to videoSize.pixelWidthHeightRatio)
    override fun onBandwidthEstimate(eventTime: AnalyticsListener.EventTime, totalLoadTimeMs: Int, totalBytesLoaded: Long, bitrateEstimate: Long) {
        bandwidth = bitrateEstimate
        val now = SystemClock.elapsedRealtime()
        if (lastBandwidthLog == Long.MIN_VALUE || now - lastBandwidthLog >= 5_000) {
            lastBandwidthLog = now
            log("player-bandwidth", "bitrateEstimate" to bandwidth, "loadTimeMs" to totalLoadTimeMs, "bytesLoaded" to totalBytesLoaded)
        }
    }
    override fun onPlayerReleased(eventTime: AnalyticsListener.EventTime) { detach(player) }
    private fun close() {
        closed = true
        handler.removeCallbacksAndMessages(null)
        sampleStall(SystemClock.elapsedRealtime())
        flushChunks(SystemClock.elapsedRealtime())
        log("player-release", "pendingLoadCount" to chunkCount, "pendingLoadBytes" to chunkBytes,
            "pendingLoadDurationMs" to chunkDuration, "pendingAvgKbps" to kbps(chunkBytes, chunkDuration),
            "lastLoad" to lastLoad, "droppedFrames" to dropped)
        player.removeAnalyticsListener(this)
    }
    companion object {
        private val attached = IdentityHashMap<ExoPlayer, PlayerDiag>()
        fun attach(player: ExoPlayer) {
            if (BuildConfig.PERSONAL_BUILD && !attached.containsKey(player)) {
                val diag = PlayerDiag(player)
                attached[player] = diag
                player.addAnalyticsListener(diag)
            }
        }
        fun detach(player: ExoPlayer) { attached.remove(player)?.close() }
        private fun kbps(bytes: Long, ms: Long): Long? = if (ms > 0) bytes * 8 / ms else null
        private fun state(value: Int) = when (value) { Player.STATE_IDLE -> "IDLE"; Player.STATE_BUFFERING -> "BUFFERING"; Player.STATE_READY -> "READY"; Player.STATE_ENDED -> "ENDED"; else -> "UNKNOWN" }
        private fun readyReason(value: Int) = when (value) { 1 -> "USER_REQUEST"; 2 -> "AUDIO_FOCUS_LOSS"; 3 -> "AUDIO_BECOMING_NOISY"; 4 -> "REMOTE"; 5 -> "END_OF_MEDIA_ITEM"; 6 -> "SUPPRESSED_TOO_LONG"; else -> "UNKNOWN" }
    }
}
