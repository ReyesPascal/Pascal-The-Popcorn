package com.arflix.tv.util

import android.app.ActivityManager
import android.content.Context
import android.hardware.display.DisplayManager
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.os.Build
import android.util.Log
import android.view.Display
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/** Hardware decode support for one video codec (software decoders are ignored). */
data class CodecSupport(
    /** A hardware decoder exists for this codec. */
    val hardware: Boolean = false,
    /** It decodes 10-bit (HEVC Main10, AV1 Main10, VP9 profile 2/3). */
    val tenBit: Boolean = false,
    /** It decodes 3840x2160. */
    val uhd: Boolean = false,
    val maxWidth: Int = 0,
    val maxHeight: Int = 0,
    val decoderName: String = ""
)

/**
 * Personal build: what this TV can play, scanned once and saved (PERSONAL_PLAN goal 6).
 * Every field has a default so tests and the picker can build profiles by hand.
 */
data class TvProfile(
    val schemaVersion: Int = TvCapabilities.SCHEMA_VERSION,
    val scannedAtMs: Long = 0L,
    /** SoC, e.g. "MT5867" (Build.SOC_MODEL on API 31+, else ro.board.platform / Build.HARDWARE). */
    val chip: String = "",
    val manufacturer: String = "",
    val model: String = "",
    val sdkInt: Int = 0,
    val abis: List<String> = emptyList(),
    val totalRamMb: Long = 0L,
    val isLowRamDevice: Boolean = false,
    val avc: CodecSupport = CodecSupport(),
    val hevc: CodecSupport = CodecSupport(),
    val av1: CodecSupport = CodecSupport(),
    val vp9: CodecSupport = CodecSupport(),
    /** Hardware Dolby Vision decoder ("video/dolby-vision"). */
    val dolbyVisionDecoder: CodecSupport = CodecSupport(),
    /** Dolby Vision profile numbers (5, 7, 8, ...) the DV decoder advertises. */
    val dolbyVisionProfiles: List<Int> = emptyList(),
    /** Display modes as "WIDTHxHEIGHT@HZ". */
    val displayModes: List<String> = emptyList(),
    val maxDisplayWidth: Int = 0,
    val maxDisplayHeight: Int = 0,
    val refreshRates: List<Float> = emptyList(),
    /** Display.HdrCapabilities types: 1 = Dolby Vision, 2 = HDR10, 3 = HLG, 4 = HDR10+. */
    val hdrTypes: List<Int> = emptyList(),
    val maxLuminance: Float = 0f,
    /**
     * 2160 for a 4K panel, else 1080. Many Android TVs report only a 1080p (or 720p) UI mode for a
     * 4K panel, so the panel size properties are checked too (see [TvCapabilities.detectScreen]).
     */
    val screenClass: Int = 1080,
    /** What decided [screenClass], e.g. "vendor.display-size=3840x2160" or "display modes". */
    val screenClassSource: String = "",
    val audioAc3: Boolean = false,
    val audioEac3: Boolean = false,
    val audioEac3Joc: Boolean = false,
    val audioTrueHd: Boolean = false,
    val audioDts: Boolean = false,
    val audioDtsHd: Boolean = false,
    /** One short human line, e.g. "MT5867 · 1080p · H.264/HEVC 10-bit · HDR10/HLG · no Dolby Vision". */
    val summary: String = ""
) {
    val displayDolbyVision: Boolean get() = HDR_TYPE_DOLBY_VISION in hdrTypes
    val displayHdr10: Boolean get() = HDR_TYPE_HDR10 in hdrTypes
    val displayHlg: Boolean get() = HDR_TYPE_HLG in hdrTypes
    val displayHdr10Plus: Boolean get() = HDR_TYPE_HDR10_PLUS in hdrTypes

    /** Real Dolby Vision playback needs both a DV display path and a hardware DV decoder. */
    val hasDolbyVision: Boolean get() = displayDolbyVision && dolbyVisionDecoder.hardware

    companion object {
        const val HDR_TYPE_DOLBY_VISION = 1
        const val HDR_TYPE_HDR10 = 2
        const val HDR_TYPE_HLG = 3
        const val HDR_TYPE_HDR10_PLUS = 4
    }
}

/**
 * Personal build: one-time TV capability scan (PERSONAL_PLAN goal 6). The result is saved in
 * SharedPreferences as JSON so [cached] is synchronous; [rescan] (Settings "Re-scan TV") overwrites it.
 */
object TvCapabilities {
    /** Bump when TvProfile changes shape; an older saved profile is then re-scanned. */
    // 2: 4K detection also reads the panel size properties (a 4K Hisense was scanned as 1080p).
    const val SCHEMA_VERSION = 2

    private const val TAG = "TvCapabilities"
    private const val PREFS_NAME = "personal_tv_capabilities"
    private const val KEY_PROFILE = "profile_json"

    private val scanLock = Mutex()

    @Volatile
    private var memory: TvProfile? = null

    /** The saved profile, or null until the first scan has finished. */
    fun cached(context: Context): TvProfile? {
        memory?.let { return it }
        val raw = runCatching {
            context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_PROFILE, null)
        }.getOrNull() ?: return null
        val profile = runCatching { fromJson(JSONObject(raw)) }.getOrNull()
            ?.takeIf { it.schemaVersion == SCHEMA_VERSION }
            ?: return null
        memory = profile
        return profile
    }

    /** Scans once, then returns the saved profile. Safe to call from anywhere, any number of times. */
    suspend fun ensureScanned(context: Context): TvProfile {
        cached(context)?.let { return it }
        return scanLock.withLock {
            cached(context) ?: scanAndSave(context)
        }
    }

    /** Forced re-scan; overwrites the saved profile. */
    suspend fun rescan(context: Context): TvProfile = scanLock.withLock { scanAndSave(context) }

    private suspend fun scanAndSave(context: Context): TvProfile = withContext(Dispatchers.Default) {
        val app = context.applicationContext
        val profile = scan(app)
        runCatching {
            app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_PROFILE, toJson(profile).toString())
                .apply()
        }.onFailure { Log.w(TAG, "Could not save TV profile", it) }
        memory = profile
        // Owner corrections are kept across scans; publish scan + corrections for the UI and Play.
        TvOverridesStore.refresh(app)
        Log.i(TAG, "TV scan:${profile.summary} | $profile")
        // Release builds strip Log.i; System.err stays visible over adb for on-TV checks.
        System.err.println("[TvScan] ${profile.summary} | screen=${profile.screenClass} from ${profile.screenClassSource} | modes=${profile.displayModes} hdr=${profile.hdrTypes} dv=${profile.dolbyVisionProfiles} hevc=${profile.hevc} av1=${profile.av1} audio ac3=${profile.audioAc3} eac3=${profile.audioEac3} joc=${profile.audioEac3Joc} truehd=${profile.audioTrueHd} dts=${profile.audioDts} dtshd=${profile.audioDtsHd}")
        profile
    }

    // ---------------------------------------------------------------- scanning

    private fun scan(context: Context): TvProfile {
        val chip = runCatching { detectChip() }.getOrDefault("")
        val (totalRamMb, lowRam) = runCatching {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val info = ActivityManager.MemoryInfo()
            am.getMemoryInfo(info)
            (info.totalMem / (1024L * 1024L)) to am.isLowRamDevice
        }.getOrDefault(0L to false)

        val decoders = runCatching { scanDecoders() }.getOrElse {
            Log.w(TAG, "Decoder scan failed", it)
            DecoderScan()
        }
        val display = runCatching { scanDisplay(context) }.getOrElse {
            Log.w(TAG, "Display scan failed", it)
            DisplayScan()
        }
        val audio = runCatching { scanAudio(context) }.getOrElse {
            Log.w(TAG, "Audio scan failed", it)
            AudioScan()
        }

        val (screenClass, screenClassSource) = runCatching { detectScreen(context, display.modes) }
            .getOrElse {
                Log.w(TAG, "Screen size check failed", it)
                1080 to "check failed"
            }
        val profile = TvProfile(
            schemaVersion = SCHEMA_VERSION,
            scannedAtMs = System.currentTimeMillis(),
            chip = chip,
            manufacturer = Build.MANUFACTURER.orEmpty(),
            model = Build.MODEL.orEmpty(),
            sdkInt = Build.VERSION.SDK_INT,
            abis = Build.SUPPORTED_ABIS?.toList().orEmpty(),
            totalRamMb = totalRamMb,
            isLowRamDevice = lowRam,
            avc = decoders.avc,
            hevc = decoders.hevc,
            av1 = decoders.av1,
            vp9 = decoders.vp9,
            dolbyVisionDecoder = decoders.dolbyVision,
            dolbyVisionProfiles = decoders.dolbyVisionProfiles,
            displayModes = display.modes.map { "${it.width}x${it.height}@${formatHz(it.refreshRate)}" },
            maxDisplayWidth = display.modes.maxOfOrNull { it.width } ?: 0,
            maxDisplayHeight = display.modes.maxOfOrNull { it.height } ?: 0,
            refreshRates = display.modes.map { it.refreshRate }.distinct().sorted(),
            hdrTypes = display.hdrTypes,
            maxLuminance = display.maxLuminance,
            screenClass = screenClass,
            screenClassSource = screenClassSource,
            audioAc3 = audio.ac3,
            audioEac3 = audio.eac3,
            audioEac3Joc = audio.eac3Joc,
            audioTrueHd = audio.trueHd,
            audioDts = audio.dts,
            audioDtsHd = audio.dtsHd
        )
        return profile.copy(summary = describe(profile))
    }

    /**
     * 4K or 1080p, and why. Android's display modes often describe only the UI layer (1080p on most
     * 4K TVs, e.g. the Hisense 65U6H), while video goes to the panel at full size. In order:
     * 1. any display mode at least 3840 wide;
     * 2. the panel size properties TVs publish for this (vendor.display-size, sys.display-size);
     * 3. Media3's own display size check (it reads those properties and some vendor ones);
     * 4. vendor 4K flags (ro.vt.vcom_display_resolution=4k on MediaTek/Hisense);
     * 5. a model name that says 4K / UHD.
     * A 4K-capable decoder alone is not proof: 1080p TVs often have one.
     */
    internal fun detectScreen(context: Context, modes: List<Mode>): Pair<Int, String> {
        modes.firstOrNull { it.width >= 3840 }?.let { return 2160 to "display mode ${it.width}x${it.height}" }
        for (key in listOf("vendor.display-size", "sys.display-size")) {
            val size = parseSize(systemProperty(key))
            if (size != null && size.first >= 3840) return 2160 to "$key=${size.first}x${size.second}"
        }
        runCatching {
            @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
            val p = androidx.media3.common.util.Util.getCurrentDisplayModeSize(context)
            if (p.x >= 3840) return 2160 to "player display size ${p.x}x${p.y}"
        }
        val vendorFlag = systemProperty("ro.vt.vcom_display_resolution")
        if (vendorFlag != null && vendorFlag.lowercase(Locale.US).let { it == "4k" || it.contains("2160") }) {
            return 2160 to "ro.vt.vcom_display_resolution=$vendorFlag"
        }
        val model = Build.MODEL.orEmpty()
        if (Regex("""(?i)(\b4k\b|\buhd\b|2160)""").containsMatchIn(model)) return 2160 to "model name \"$model\""
        return 1080 to "no 4K signal (modes ${modes.maxOfOrNull { it.width } ?: 0} wide)"
    }

    /** "3840x2160" or "3840,2160" -> (3840, 2160). */
    internal fun parseSize(value: String?): Pair<Int, Int>? {
        val parts = value?.trim()?.split('x', 'X', ',')?.map { it.trim() } ?: return null
        if (parts.size != 2) return null
        val w = parts[0].toIntOrNull() ?: return null
        val h = parts[1].toIntOrNull() ?: return null
        return if (w > 0 && h > 0) w to h else null
    }

    private fun detectChip(): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val soc = Build.SOC_MODEL
            if (!soc.isNullOrBlank() && !soc.equals(Build.UNKNOWN, ignoreCase = true)) return soc
        }
        for (key in listOf("ro.soc.model", "ro.board.platform", "ro.hardware.chipname")) {
            val value = systemProperty(key)
            if (!value.isNullOrBlank()) return value
        }
        return Build.HARDWARE.orEmpty()
    }

    private fun systemProperty(key: String): String? = runCatching {
        val clazz = Class.forName("android.os.SystemProperties")
        clazz.getMethod("get", String::class.java).invoke(null, key) as? String
    }.getOrNull()?.trim()

    private data class DecoderScan(
        val avc: CodecSupport = CodecSupport(),
        val hevc: CodecSupport = CodecSupport(),
        val av1: CodecSupport = CodecSupport(),
        val vp9: CodecSupport = CodecSupport(),
        val dolbyVision: CodecSupport = CodecSupport(),
        val dolbyVisionProfiles: List<Int> = emptyList()
    )

    private const val MIME_AVC = "video/avc"
    private const val MIME_HEVC = "video/hevc"
    private const val MIME_AV1 = "video/av01"
    private const val MIME_VP9 = "video/x-vnd.on2.vp9"
    private const val MIME_DV = "video/dolby-vision"

    // MediaCodecInfo.CodecProfileLevel values, as literals so older API levels never look them up.
    private val HEVC_10BIT_PROFILES = setOf(0x2, 0x1000, 0x2000) // Main10, Main10HDR10, Main10HDR10Plus
    private val AV1_10BIT_PROFILES = setOf(0x2, 0x1000, 0x2000)  // Main10, Main10HDR10, Main10HDR10Plus
    private val VP9_10BIT_PROFILES = setOf(0x4, 0x8, 0x1000, 0x2000, 0x4000, 0x8000) // Profile 2/3 (+HDR, +HDR10Plus)
    private val DV_PROFILE_BITS = mapOf(
        0x1 to 0, 0x2 to 1, 0x4 to 2, 0x8 to 3, 0x10 to 4, 0x20 to 5,
        0x40 to 6, 0x80 to 7, 0x100 to 8, 0x200 to 9, 0x400 to 10
    )

    private fun scanDecoders(): DecoderScan {
        val infos = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
            .filter { !it.isEncoder && !isSoftwareOnly(it) }
        val dvProfiles = sortedSetOf<Int>()
        fun support(mime: String, tenBitProfiles: Set<Int>): CodecSupport {
            var result = CodecSupport()
            for (info in infos) {
                if (info.supportedTypes.none { it.equals(mime, ignoreCase = true) }) continue
                val caps = runCatching { info.getCapabilitiesForType(mime) }.getOrNull() ?: continue
                val profiles = caps.profileLevels?.map { it.profile }.orEmpty()
                if (mime == MIME_DV) {
                    profiles.forEach { bit -> DV_PROFILE_BITS[bit]?.let(dvProfiles::add) }
                }
                val video = caps.videoCapabilities
                val uhd = video?.let { v ->
                    runCatching { v.isSizeSupported(3840, 2160) }.getOrDefault(false)
                } ?: false
                val maxW = video?.supportedWidths?.upper ?: 0
                val maxH = video?.supportedHeights?.upper ?: 0
                val tenBit = profiles.any { it in tenBitProfiles }
                result = CodecSupport(
                    hardware = true,
                    tenBit = result.tenBit || tenBit,
                    uhd = result.uhd || uhd,
                    maxWidth = maxOf(result.maxWidth, maxW),
                    maxHeight = maxOf(result.maxHeight, maxH),
                    decoderName = result.decoderName.ifBlank { info.name }
                )
            }
            return result
        }
        val avc = support(MIME_AVC, emptySet())
        val hevc = support(MIME_HEVC, HEVC_10BIT_PROFILES)
        val av1 = support(MIME_AV1, AV1_10BIT_PROFILES)
        val vp9 = support(MIME_VP9, VP9_10BIT_PROFILES)
        // Dolby Vision is always 10-bit.
        val dv = support(MIME_DV, emptySet()).let { if (it.hardware) it.copy(tenBit = true) else it }
        return DecoderScan(avc, hevc, av1, vp9, dv, dvProfiles.toList())
    }

    private fun isSoftwareOnly(info: MediaCodecInfo): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) return info.isSoftwareOnly
        val name = info.name.lowercase(Locale.US)
        return name.startsWith("omx.google.") || name.startsWith("c2.android.") ||
            name.startsWith("omx.ffmpeg.") || name.startsWith("c2.ffmpeg.")
    }

    internal data class Mode(val width: Int, val height: Int, val refreshRate: Float)

    private data class DisplayScan(
        val modes: List<Mode> = emptyList(),
        val hdrTypes: List<Int> = emptyList(),
        val maxLuminance: Float = 0f
    )

    @Suppress("DEPRECATION")
    private fun scanDisplay(context: Context): DisplayScan {
        val dm = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
        val display = dm?.getDisplay(Display.DEFAULT_DISPLAY) ?: return DisplayScan()
        val modes = display.supportedModes.orEmpty()
            .map { Mode(it.physicalWidth, it.physicalHeight, it.refreshRate) }
            .distinct()
            .sortedWith(compareByDescending<Mode> { it.width }.thenByDescending { it.refreshRate })
        val hdr = sortedSetOf<Int>()
        var maxLuminance = 0f
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            display.hdrCapabilities?.let { caps ->
                caps.supportedHdrTypes?.forEach { hdr.add(it) }
                maxLuminance = caps.desiredMaxLuminance
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            runCatching { display.mode.supportedHdrTypes.forEach { hdr.add(it) } }
        }
        return DisplayScan(modes, hdr.toList(), maxLuminance)
    }

    private data class AudioScan(
        val ac3: Boolean = false,
        val eac3: Boolean = false,
        val eac3Joc: Boolean = false,
        val trueHd: Boolean = false,
        val dts: Boolean = false,
        val dtsHd: Boolean = false
    )

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    private fun scanAudio(context: Context): AudioScan {
        val caps = androidx.media3.exoplayer.audio.AudioCapabilities.getCapabilities(
            context,
            androidx.media3.common.AudioAttributes.DEFAULT,
            null
        )
        return AudioScan(
            ac3 = caps.supportsEncoding(androidx.media3.common.C.ENCODING_AC3),
            eac3 = caps.supportsEncoding(androidx.media3.common.C.ENCODING_E_AC3),
            eac3Joc = caps.supportsEncoding(androidx.media3.common.C.ENCODING_E_AC3_JOC),
            trueHd = caps.supportsEncoding(androidx.media3.common.C.ENCODING_DOLBY_TRUEHD),
            dts = caps.supportsEncoding(androidx.media3.common.C.ENCODING_DTS),
            dtsHd = caps.supportsEncoding(androidx.media3.common.C.ENCODING_DTS_HD)
        )
    }

    private fun formatHz(hz: Float): String = String.format(Locale.US, "%.2f", hz).trimEnd('0').trimEnd('.')

    // ---------------------------------------------------------------- summary

    /** One short human line for Settings, e.g. "MT5867 · 1080p · H.264/HEVC 10-bit · HDR10/HLG · no Dolby Vision". */
    fun describe(profile: TvProfile): String {
        val parts = mutableListOf<String>()
        profile.chip.takeIf { it.isNotBlank() }?.let { parts += it.uppercase(Locale.US) }
        parts += if (profile.screenClass >= 2160) "4K" else "1080p"
        val codecs = buildList {
            if (profile.avc.hardware) add("H.264")
            if (profile.hevc.hardware) add(if (profile.hevc.tenBit) "HEVC 10-bit" else "HEVC")
            if (profile.av1.hardware) add(if (profile.av1.tenBit) "AV1 10-bit" else "AV1")
            if (profile.vp9.hardware) add("VP9")
        }
        parts += codecs.joinToString("/").ifBlank { "no hardware video decoders found" }
        val hdr = buildList {
            if (profile.displayHdr10) add("HDR10")
            if (profile.displayHdr10Plus) add("HDR10+")
            if (profile.displayHlg) add("HLG")
        }
        parts += hdr.joinToString("/").ifBlank { "SDR only" }
        parts += if (profile.hasDolbyVision) {
            val p = profile.dolbyVisionProfiles.takeIf { it.isNotEmpty() }?.joinToString("/", prefix = " (P", postfix = ")")
            "Dolby Vision${p.orEmpty()}"
        } else {
            "no Dolby Vision"
        }
        val audio = buildList {
            if (profile.audioEac3Joc) add("Atmos") else if (profile.audioEac3) add("DD+")
            if (profile.audioTrueHd) add("TrueHD")
            if (profile.audioDts || profile.audioDtsHd) add("DTS")
        }
        if (audio.isNotEmpty()) parts += audio.joinToString("/")
        return parts.joinToString(" · ")
    }

    // ---------------------------------------------------------------- JSON

    private fun codecToJson(c: CodecSupport) = JSONObject()
        .put("hardware", c.hardware)
        .put("tenBit", c.tenBit)
        .put("uhd", c.uhd)
        .put("maxWidth", c.maxWidth)
        .put("maxHeight", c.maxHeight)
        .put("decoderName", c.decoderName)

    private fun codecFromJson(o: JSONObject?): CodecSupport {
        o ?: return CodecSupport()
        return CodecSupport(
            hardware = o.optBoolean("hardware"),
            tenBit = o.optBoolean("tenBit"),
            uhd = o.optBoolean("uhd"),
            maxWidth = o.optInt("maxWidth"),
            maxHeight = o.optInt("maxHeight"),
            decoderName = o.optString("decoderName")
        )
    }

    private fun toJson(p: TvProfile): JSONObject = JSONObject()
        .put("schemaVersion", p.schemaVersion)
        .put("scannedAtMs", p.scannedAtMs)
        .put("chip", p.chip)
        .put("manufacturer", p.manufacturer)
        .put("model", p.model)
        .put("sdkInt", p.sdkInt)
        .put("abis", JSONArray(p.abis))
        .put("totalRamMb", p.totalRamMb)
        .put("isLowRamDevice", p.isLowRamDevice)
        .put("avc", codecToJson(p.avc))
        .put("hevc", codecToJson(p.hevc))
        .put("av1", codecToJson(p.av1))
        .put("vp9", codecToJson(p.vp9))
        .put("dolbyVisionDecoder", codecToJson(p.dolbyVisionDecoder))
        .put("dolbyVisionProfiles", JSONArray(p.dolbyVisionProfiles))
        .put("displayModes", JSONArray(p.displayModes))
        .put("maxDisplayWidth", p.maxDisplayWidth)
        .put("maxDisplayHeight", p.maxDisplayHeight)
        .put("refreshRates", JSONArray(p.refreshRates.map { it.toDouble() }))
        .put("hdrTypes", JSONArray(p.hdrTypes))
        .put("maxLuminance", p.maxLuminance.toDouble())
        .put("screenClass", p.screenClass)
        .put("screenClassSource", p.screenClassSource)
        .put("audioAc3", p.audioAc3)
        .put("audioEac3", p.audioEac3)
        .put("audioEac3Joc", p.audioEac3Joc)
        .put("audioTrueHd", p.audioTrueHd)
        .put("audioDts", p.audioDts)
        .put("audioDtsHd", p.audioDtsHd)
        .put("summary", p.summary)

    private fun JSONArray?.strings(): List<String> =
        if (this == null) emptyList() else (0 until length()).map { optString(it) }

    private fun JSONArray?.ints(): List<Int> =
        if (this == null) emptyList() else (0 until length()).map { optInt(it) }

    private fun JSONArray?.floats(): List<Float> =
        if (this == null) emptyList() else (0 until length()).map { optDouble(it).toFloat() }

    private fun fromJson(o: JSONObject): TvProfile = TvProfile(
        schemaVersion = o.optInt("schemaVersion", -1),
        scannedAtMs = o.optLong("scannedAtMs"),
        chip = o.optString("chip"),
        manufacturer = o.optString("manufacturer"),
        model = o.optString("model"),
        sdkInt = o.optInt("sdkInt"),
        abis = o.optJSONArray("abis").strings(),
        totalRamMb = o.optLong("totalRamMb"),
        isLowRamDevice = o.optBoolean("isLowRamDevice"),
        avc = codecFromJson(o.optJSONObject("avc")),
        hevc = codecFromJson(o.optJSONObject("hevc")),
        av1 = codecFromJson(o.optJSONObject("av1")),
        vp9 = codecFromJson(o.optJSONObject("vp9")),
        dolbyVisionDecoder = codecFromJson(o.optJSONObject("dolbyVisionDecoder")),
        dolbyVisionProfiles = o.optJSONArray("dolbyVisionProfiles").ints(),
        displayModes = o.optJSONArray("displayModes").strings(),
        maxDisplayWidth = o.optInt("maxDisplayWidth"),
        maxDisplayHeight = o.optInt("maxDisplayHeight"),
        refreshRates = o.optJSONArray("refreshRates").floats(),
        hdrTypes = o.optJSONArray("hdrTypes").ints(),
        maxLuminance = o.optDouble("maxLuminance", 0.0).toFloat(),
        screenClass = o.optInt("screenClass", 1080),
        screenClassSource = o.optString("screenClassSource"),
        audioAc3 = o.optBoolean("audioAc3"),
        audioEac3 = o.optBoolean("audioEac3"),
        audioEac3Joc = o.optBoolean("audioEac3Joc"),
        audioTrueHd = o.optBoolean("audioTrueHd"),
        audioDts = o.optBoolean("audioDts"),
        audioDtsHd = o.optBoolean("audioDtsHd"),
        summary = o.optString("summary")
    )
}
