package com.arflix.tv.util

import com.arflix.tv.data.model.StreamSource
import java.util.Locale

enum class VideoCodec { UNKNOWN, AVC, HEVC, AV1, VP9 }

/** What a source's labels say about its video. Resolution 0 = not stated. */
data class SourceTraits(
    val resolution: Int = 0,
    val codec: VideoCodec = VideoCodec.UNKNOWN,
    val tenBit: Boolean = false,
    val dolbyVision: Boolean = false,
    val hdr10: Boolean = false,
    val hdr10Plus: Boolean = false,
    val hlg: Boolean = false,
    val sizeBytes: Long = 0L
) {
    val isHdr: Boolean get() = dolbyVision || hdr10 || hdr10Plus || hlg

    fun label(): String = buildList {
        add(if (resolution > 0) "${resolution}p" else "res?")
        add(if (codec == VideoCodec.UNKNOWN) "codec?" else codec.name)
        if (tenBit) add("10bit")
        if (dolbyVision) add("DV")
        if (hdr10Plus) add("HDR10+")
        if (hdr10) add("HDR10")
        if (hlg) add("HLG")
        if (sizeBytes > 0L) add(String.format(Locale.US, "%.1fGB", sizeBytes / (1024.0 * 1024.0 * 1024.0)))
    }.joinToString(" ")
}

data class SourceVerdict(val suits: Boolean, val reason: String)

/**
 * Personal build (PERSONAL_PLAN goal 7): Play starts the first source in Usenet Ultimate's order
 * that suits this TV (or, with a preferred picture format, the first that suits and matches it — 1.0.1).
 * UU's list is never re-sorted or filtered; this only chooses an index in it.
 * If nothing suits, UU's first source plays anyway.
 */
object BestSourcePicker {
    private const val TAG = "BestSource"

    /** Decision log sink; Logcat by default, swallowed where android.util.Log is unavailable (unit tests). */
    @Volatile
    // System.err: release builds strip Log.i, and the pick/skip/failover reasons are what on-TV tests need.
    var log: (String) -> Unit = { line -> System.err.println("[$TAG] $line") }

    private object Patterns {
        private const val B = """(?<![a-z0-9])""" // left boundary (treats '_' and '.' as separators)
        private const val E = """(?![a-z0-9])"""  // right boundary
        val res4320 = Regex("""${B}(4320p?|8k)$E""")
        val res2160 = Regex("""${B}(2160p?|4k|uhd)$E""")
        val res1080 = Regex("""${B}(1080[pi]?|fhd|full[ ._-]?hd)$E""")
        val res720 = Regex("""${B}720p?$E""")
        val res480 = Regex("""${B}(480p?|576p?|sd)$E""")
        val av1 = Regex("""${B}av1$E""")
        val hevc = Regex("""${B}(x265|h[ .]?265|hevc(10)?)$E""")
        val avc = Regex("""${B}(x264|h[ .]?264|avc)$E""")
        val vp9 = Regex("""${B}vp9$E""")
        val tenBit = Regex("""${B}(10[ ._-]?bits?|hi10p?|main ?10)$E""")
        val dolbyVision = Regex("""${B}(dv|dovi|dolby[ ._-]?vision)$E""")
        val hdr10Plus = Regex("""${B}hdr10(\+|plus|p)""")
        val hdr10 = Regex("""${B}hdr(10)?$E""")
        val hlg = Regex("""${B}hlg$E""")
        val size = Regex("""(\d+(?:[.,]\d+)?)\s*(tib|gib|mib|tb|gb|mb)$E""")
    }

    /** Parses resolution, codec, bit depth, HDR flags and size from every label the source carries. */
    fun parse(stream: StreamSource): SourceTraits {
        val primary = listOfNotNull(stream.quality, stream.rawLabel).joinToString(" ")
        val secondary = listOfNotNull(
            stream.source,
            stream.addonTitle,
            stream.description,
            stream.behaviorHints?.filename,
            stream.behaviorHints?.sourceLabel
        ).joinToString(" ")
        val size = stream.sizeBytes?.takeIf { it > 0L }
            ?: stream.behaviorHints?.videoSize?.takeIf { it > 0L }
            ?: parseSize(stream.size).takeIf { it > 0L }
            ?: parseSize("$primary $secondary")
        return parseText(primary, secondary).copy(sizeBytes = size)
    }

    /**
     * Resolution comes from the structured labels ([primary]: quality / addon name) when they state one,
     * else from the free text ([secondary]: title, description, filename). Everything else uses both.
     */
    fun parseText(primary: String, secondary: String = ""): SourceTraits {
        val p = primary.lowercase(Locale.US)
        val all = "$p ${secondary.lowercase(Locale.US)}"
        val resolution = resolutionOf(p).takeIf { it > 0 } ?: resolutionOf(all)
        val codec = when {
            Patterns.av1.containsMatchIn(all) -> VideoCodec.AV1
            Patterns.hevc.containsMatchIn(all) -> VideoCodec.HEVC
            Patterns.vp9.containsMatchIn(all) -> VideoCodec.VP9
            Patterns.avc.containsMatchIn(all) -> VideoCodec.AVC
            else -> VideoCodec.UNKNOWN
        }
        val dv = Patterns.dolbyVision.containsMatchIn(all)
        val hdr10Plus = Patterns.hdr10Plus.containsMatchIn(all)
        // "HDR10" inside "HDR10+" also matches the plain pattern; count it as HDR10 only when it
        // appears on its own as well.
        val hdr10 = Patterns.hdr10.findAll(all).any { m ->
            val next = all.getOrNull(m.range.last + 1)
            next != '+'
        }
        val hlg = Patterns.hlg.containsMatchIn(all)
        val hevc10 = all.contains("hevc10")
        val tenBit = Patterns.tenBit.containsMatchIn(all) || hevc10 || dv || hdr10 || hdr10Plus || hlg
        return SourceTraits(
            resolution = resolution,
            codec = codec,
            tenBit = tenBit,
            dolbyVision = dv,
            hdr10 = hdr10,
            hdr10Plus = hdr10Plus,
            hlg = hlg
        )
    }

    private fun resolutionOf(text: String): Int = when {
        Patterns.res4320.containsMatchIn(text) -> 4320
        Patterns.res2160.containsMatchIn(text) -> 2160
        Patterns.res1080.containsMatchIn(text) -> 1080
        Patterns.res720.containsMatchIn(text) -> 720
        Patterns.res480.containsMatchIn(text) -> 480
        else -> 0
    }

    internal fun parseSize(text: String): Long {
        val m = Patterns.size.find(text.lowercase(Locale.US)) ?: return 0L
        val value = m.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return 0L
        val unit = when (m.groupValues[2]) {
            "tb", "tib" -> 1024.0 * 1024.0 * 1024.0 * 1024.0
            "gb", "gib" -> 1024.0 * 1024.0 * 1024.0
            else -> 1024.0 * 1024.0
        }
        return (value * unit).toLong()
    }

    /** Whether a source with these traits plays correctly on [tv], and why (one short phrase). */
    fun verdict(traits: SourceTraits, tv: TvProfile): SourceVerdict {
        if (traits.resolution > tv.screenClass) {
            return SourceVerdict(false, "${traits.resolution}p above ${tv.screenClass}p screen")
        }

        // Dolby Vision: native on a DV TV; otherwise only through its HDR10/HLG base layer.
        val dvNative = traits.dolbyVision && tv.hasDolbyVision
        if (traits.dolbyVision && !dvNative) {
            val hasHdr10Base = traits.hdr10 || traits.hdr10Plus
            when {
                hasHdr10Base && tv.displayHdr10 -> Unit
                traits.hlg && tv.displayHlg -> Unit
                hasHdr10Base || traits.hlg -> return SourceVerdict(false, "DV with HDR base, but TV lacks that HDR type and Dolby Vision")
                else -> return SourceVerdict(false, "Dolby Vision only on a non-DV TV (wrong colours)")
            }
        }
        if (!traits.dolbyVision) {
            // HDR10+ carries an HDR10 base layer, so an HDR10 TV plays it as HDR10.
            if (traits.hdr10Plus && !tv.displayHdr10Plus && !tv.displayHdr10) {
                return SourceVerdict(false, "HDR10+ but TV has no HDR10/HDR10+")
            }
            if (traits.hdr10 && !tv.displayHdr10) return SourceVerdict(false, "HDR10 but TV has no HDR10")
            if (traits.hlg && !tv.displayHlg) return SourceVerdict(false, "HLG but TV has no HLG")
        }

        val uhd = traits.resolution >= 2160
        when (traits.codec) {
            VideoCodec.HEVC -> {
                if (!dvNative) {
                    if (!tv.hevc.hardware) return SourceVerdict(false, "no hardware HEVC decoder")
                    if (traits.tenBit && !tv.hevc.tenBit) return SourceVerdict(false, "HEVC 10-bit but no Main10 decoder")
                    if (uhd && !tv.hevc.uhd) return SourceVerdict(false, "HEVC decoder can't do 2160p")
                }
            }
            VideoCodec.AV1 -> {
                if (!tv.av1.hardware) return SourceVerdict(false, "no hardware AV1 decoder")
                if (traits.tenBit && !tv.av1.tenBit) return SourceVerdict(false, "AV1 10-bit but no 10-bit AV1 decoder")
                if (uhd && !tv.av1.uhd) return SourceVerdict(false, "AV1 decoder can't do 2160p")
            }
            VideoCodec.VP9 -> {
                if (!tv.vp9.hardware) return SourceVerdict(false, "no hardware VP9 decoder")
                if (traits.tenBit && !tv.vp9.tenBit) return SourceVerdict(false, "VP9 10-bit but no profile 2 decoder")
                if (uhd && !tv.vp9.uhd) return SourceVerdict(false, "VP9 decoder can't do 2160p")
            }
            VideoCodec.AVC, VideoCodec.UNKNOWN -> {
                // Unknown codec: assume H.264, which every TV decodes. Untagged 4K is usually HEVC,
                // so 2160p only needs some decoder that handles 4K. HDR/DV need 10-bit HEVC.
                if (traits.codec == VideoCodec.AVC && traits.tenBit && !traits.isHdr) {
                    return SourceVerdict(false, "H.264 10-bit (Hi10P) has no hardware decoder")
                }
                if (traits.isHdr && !dvNative && !tv.hevc.tenBit && traits.codec == VideoCodec.UNKNOWN) {
                    return SourceVerdict(false, "HDR source but no 10-bit HEVC decoder")
                }
                if (uhd && !tv.avc.uhd && !tv.hevc.uhd) return SourceVerdict(false, "no decoder handles 2160p")
            }
        }
        return SourceVerdict(true, "suits")
    }

    fun verdict(stream: StreamSource, tv: TvProfile): SourceVerdict = verdict(parse(stream), tv)

    fun suits(stream: StreamSource, tv: TvProfile): Boolean = verdict(stream, tv).suits

    /**
     * Owner's rule when NO source suits the TV: play one anyway, choosing by resolution.
     * The TV's own resolution first, then sources with no stated resolution, then lower ones
     * (closest first: 720p before 480p), then higher ones (closest first: 4K before 8K).
     * Within each resolution, UU's order is kept.
     */
    fun fallbackOrder(streams: List<StreamSource>, tv: TvProfile): List<Int> {
        val screen = tv.screenClass
        fun rank(res: Int): Long = when {
            res == screen -> 0L
            res == 0 -> 1L
            res < screen -> 2L * 100_000 + (screen - res)
            else -> 3L * 100_000 + (res - screen)
        }
        return streams.indices.sortedWith(compareBy<Int>({ rank(parse(streams[it]).resolution) }, { it }))
    }

    /**
     * Owner's preferred picture format (1.0.1). Hybrids count for both: "DV + HDR10" matches a Dolby Vision
     * and an HDR10 preference; HDR10+ matches an HDR10 preference (it plays as HDR10 on any HDR10 TV).
     */
    fun matchesFormat(traits: SourceTraits, preferred: PictureFormat): Boolean = when (preferred) {
        PictureFormat.BEST_FOR_TV -> true
        PictureFormat.DOLBY_VISION -> traits.dolbyVision
        PictureFormat.HDR10_PLUS -> traits.hdr10Plus
        PictureFormat.HDR10 -> traits.hdr10 || traits.hdr10Plus
    }

    /**
     * Whether a preferred-format source may replace a default pick of [defaultResolution]: on a 4K TV only
     * at the same resolution (resolution beats format); on a 1080p TV any resolution the TV suits.
     */
    private fun resolutionAllowed(candidate: Int, defaultResolution: Int, tv: TvProfile): Boolean =
        tv.screenClass < 2160 || candidate == defaultResolution

    /**
     * Index of the source to play, walking [streams] in UU's order (never re-sorted or filtered):
     * the default pick is the first one that suits [tv]. If [preferred] is set and the default pick is not in
     * that format, the first source that suits [tv] AND matches it (same resolution on a 4K TV) plays instead;
     * none → the default pick. If nothing suits, the first of [fallbackOrder]. -1 only when [streams] is empty.
     */
    fun pickIndex(streams: List<StreamSource>, tv: TvProfile, preferred: PictureFormat = PictureFormat.BEST_FOR_TV): Int {
        val default = pickDefaultIndex(streams, tv)
        if (default < 0 || preferred == PictureFormat.BEST_FOR_TV) return default
        val defaultTraits = parse(streams[default])
        if (!verdict(defaultTraits, tv).suits || matchesFormat(defaultTraits, preferred)) return default
        streams.forEachIndexed { index, stream ->
            val traits = parse(stream)
            if (matchesFormat(traits, preferred) &&
                resolutionAllowed(traits.resolution, defaultTraits.resolution, tv) &&
                verdict(traits, tv).suits
            ) {
                log("prefer $preferred: #$index [${traits.label()}] instead of #$default [${defaultTraits.label()}] ${describe(stream)}")
                return index
            }
        }
        log("prefer $preferred: none suits at ${defaultTraits.resolution}p, keeping #$default")
        return default
    }

    private fun pickDefaultIndex(streams: List<StreamSource>, tv: TvProfile): Int {
        if (streams.isEmpty()) return -1
        val skipped = mutableListOf<String>()
        streams.forEachIndexed { index, stream ->
            val traits = parse(stream)
            val v = verdict(traits, tv)
            if (v.suits) {
                log(
                    "pick #$index/${streams.size} [${traits.label()}] ${describe(stream)}" +
                        (if (skipped.isEmpty()) "" else " | skipped: ${skipped.joinToString("; ")}") +
                        " | tv: ${tv.summary}"
                )
                return index
            }
            if (skipped.size < 8) skipped += "#$index ${traits.label()}: ${v.reason}"
        }
        val fallback = fallbackOrder(streams, tv).first()
        log("pick #$fallback/${streams.size} (nothing suits, playing best resolution for the TV anyway) ${describe(streams[fallback])} | skipped: ${skipped.joinToString("; ")} | tv: ${tv.summary}")
        return fallback
    }

    /**
     * Failover after [failedIndex] did not start: the untried sources that suit [tv], those after
     * [failedIndex] first, all in UU's order. When no source in the list suits at all, the untried
     * sources in [fallbackOrder] (best resolution for the TV, then lower, then higher).
     */
    fun nextCandidates(
        streams: List<StreamSource>,
        tv: TvProfile,
        failedIndex: Int,
        tried: Set<Int> = emptySet(),
        preferred: PictureFormat = PictureFormat.BEST_FOR_TV
    ): List<Int> {
        if (streams.isEmpty()) return emptyList()
        val skip = tried + failedIndex
        val order = ((failedIndex + 1 until streams.size) + (0 until failedIndex.coerceIn(0, streams.size)))
            .filter { it in streams.indices && it !in skip }
        val suitsAt = BooleanArray(streams.size) { suits(streams[it], tv) }
        val result = if (suitsAt.any { it }) {
            val suitable = order.filter { suitsAt[it] }
            if (preferred == PictureFormat.BEST_FOR_TV) {
                suitable
            } else {
                // Same rule as pickIndex: preferred-format sources first (same resolution as the failed one on a
                // 4K TV), then the rest; UU's order kept inside each group.
                val failedRes = streams.getOrNull(failedIndex)?.let { parse(it).resolution } ?: 0
                val (preferredFirst, rest) = suitable.partition { i ->
                    val t = parse(streams[i])
                    matchesFormat(t, preferred) && resolutionAllowed(t.resolution, failedRes, tv)
                }
                preferredFirst + rest
            }
        } else {
            fallbackOrder(streams, tv).filter { it !in skip }
        }
        log(
            "failover after #$failedIndex: next ${result.take(3).joinToString(prefix = "[", postfix = "]")}" +
                if (suitsAt.none { it }) " (nothing suits, by resolution: TV's own, then lower, then higher)" else ""
        )
        return result
    }

    private fun describe(stream: StreamSource): String =
        (stream.behaviorHints?.filename ?: stream.source).replace('\n', ' ').take(120)
}
