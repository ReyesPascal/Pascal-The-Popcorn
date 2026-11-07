package com.arflix.tv.util

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Personal build 1.0.1 — SHARED CONTRACT (see PLAN-1.0.1.md "Contract").
 * Types and signatures here are fixed; Agent A owns the function bodies.
 * The lead (picker/player) only calls these; nobody else changes the signatures.
 */

/** The owner's preferred picture format (Results/Edit screen). Only formats the TV supports are offered. */
enum class PictureFormat { BEST_FOR_TV, DOLBY_VISION, HDR10_PLUS, HDR10 }

/**
 * Owner corrections to the scan ("set by you"). Null = use the scan's answer.
 * Saved separately from the scan; re-scans never touch them.
 */
data class TvOverrides(
    /** 2160 or 1080. */
    val screenClass: Int? = null,
    val dolbyVision: Boolean? = null,
    val hdr10: Boolean? = null,
    val hdr10Plus: Boolean? = null,
    val hlg: Boolean? = null,
    val preferredFormat: PictureFormat = PictureFormat.BEST_FOR_TV
) {
    val hasCorrections: Boolean
        get() = screenClass != null || dolbyVision != null || hdr10 != null || hdr10Plus != null || hlg != null
}

object TvOverridesStore {
    private const val PREFS_NAME = "personal_tv_overrides"
    private const val KEY_SCREEN_CLASS = "screen_class"
    private const val KEY_DOLBY_VISION = "dolby_vision"
    private const val KEY_HDR10 = "hdr10"
    private const val KEY_HDR10_PLUS = "hdr10_plus"
    private const val KEY_HLG = "hlg"
    private const val KEY_PREFERRED_FORMAT = "preferred_format"
    /** New key in 1.0.1, so installs updated from 1.0 see the TV check once. */
    private const val KEY_INTRO_DONE = "tv_check_done_v1"

    private val _effective = MutableStateFlow<TvProfile?>(null)

    /** The effective profile (scan + overrides); updates on scan, re-scan, save and reset. */
    val effectiveFlow: StateFlow<TvProfile?> get() = _effective

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Saved overrides (synchronous; defaults when none saved). */
    fun overrides(context: Context): TvOverrides = runCatching {
        val p = prefs(context)
        fun bool(key: String): Boolean? = if (p.contains(key)) p.getBoolean(key, false) else null
        TvOverrides(
            screenClass = if (p.contains(KEY_SCREEN_CLASS)) p.getInt(KEY_SCREEN_CLASS, 1080) else null,
            dolbyVision = bool(KEY_DOLBY_VISION),
            hdr10 = bool(KEY_HDR10),
            hdr10Plus = bool(KEY_HDR10_PLUS),
            hlg = bool(KEY_HLG),
            preferredFormat = p.getString(KEY_PREFERRED_FORMAT, null)
                ?.let { name -> PictureFormat.entries.firstOrNull { it.name == name } }
                ?: PictureFormat.BEST_FOR_TV
        )
    }.getOrDefault(TvOverrides())

    /** Save corrections + preference; updates [effectiveFlow]. */
    fun save(context: Context, overrides: TvOverrides) {
        runCatching {
            prefs(context).edit().apply {
                overrides.screenClass?.let { putInt(KEY_SCREEN_CLASS, it) } ?: remove(KEY_SCREEN_CLASS)
                putOrRemove(KEY_DOLBY_VISION, overrides.dolbyVision)
                putOrRemove(KEY_HDR10, overrides.hdr10)
                putOrRemove(KEY_HDR10_PLUS, overrides.hdr10Plus)
                putOrRemove(KEY_HLG, overrides.hlg)
                putString(KEY_PREFERRED_FORMAT, overrides.preferredFormat.name)
            }.commit()
        }
        refresh(context)
    }

    private fun android.content.SharedPreferences.Editor.putOrRemove(key: String, value: Boolean?) {
        if (value == null) remove(key) else putBoolean(key, value)
    }

    /** "Reset my changes": clears the corrections (screen/HDR/DV) but keeps preferredFormat; updates [effectiveFlow]. */
    fun resetCorrections(context: Context) {
        save(context, TvOverrides(preferredFormat = overrides(context).preferredFormat))
    }

    /**
     * Scan result with overrides applied, or null before the first scan. Rules:
     * screenClass override replaces screenClass; hdr10/hdr10Plus/hlg overrides add/remove HDR types 2/4/3;
     * dolbyVision=true adds HDR type 1 AND marks dolbyVisionDecoder.hardware=true (so hasDolbyVision is true);
     * dolbyVision=false removes HDR type 1. summary is rebuilt with TvCapabilities.describe().
     * Also brings [effectiveFlow] up to date.
     */
    fun effective(context: Context): TvProfile? {
        val result = TvCapabilities.cached(context)?.let { applyOverrides(it, overrides(context)) }
        _effective.value = result
        return result
    }

    /** Recomputes [effectiveFlow] (called after a scan, save or reset). */
    internal fun refresh(context: Context) {
        effective(context)
    }

    /** Formats this TV supports (for the preference row), always starting with BEST_FOR_TV. */
    fun supportedFormats(profile: TvProfile): List<PictureFormat> = buildList {
        add(PictureFormat.BEST_FOR_TV)
        if (profile.hasDolbyVision) add(PictureFormat.DOLBY_VISION)
        if (profile.displayHdr10Plus) add(PictureFormat.HDR10_PLUS)
        if (profile.displayHdr10) add(PictureFormat.HDR10)
    }

    /** First-run TV check finished ("Looks right" or Save). Existing installs must see it once after 1.0.1. */
    fun introDone(context: Context): Boolean =
        runCatching { prefs(context).getBoolean(KEY_INTRO_DONE, false) }.getOrDefault(false)

    fun markIntroDone(context: Context) {
        runCatching { prefs(context).edit().putBoolean(KEY_INTRO_DONE, true).commit() }
    }

    /** Pure: [profile] (a scan) with [overrides] applied, per the rules on [effective]. */
    internal fun applyOverrides(profile: TvProfile, overrides: TvOverrides): TvProfile {
        if (!overrides.hasCorrections) return profile
        val hdr = profile.hdrTypes.toMutableSet()
        fun set(type: Int, value: Boolean?) {
            when (value) {
                true -> hdr += type
                false -> hdr -= type
                null -> Unit
            }
        }
        set(TvProfile.HDR_TYPE_DOLBY_VISION, overrides.dolbyVision)
        set(TvProfile.HDR_TYPE_HDR10, overrides.hdr10)
        set(TvProfile.HDR_TYPE_HDR10_PLUS, overrides.hdr10Plus)
        set(TvProfile.HDR_TYPE_HLG, overrides.hlg)
        val dvDecoder = if (overrides.dolbyVision == true && !profile.dolbyVisionDecoder.hardware) {
            profile.dolbyVisionDecoder.copy(hardware = true, tenBit = true)
        } else {
            profile.dolbyVisionDecoder
        }
        val applied = profile.copy(
            screenClass = overrides.screenClass ?: profile.screenClass,
            hdrTypes = hdr.sorted(),
            dolbyVisionDecoder = dvDecoder
        )
        return applied.copy(summary = TvCapabilities.describe(applied))
    }
}
