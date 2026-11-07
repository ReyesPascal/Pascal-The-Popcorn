package com.arflix.tv.data.model

/**
 * Personal build: Usenet Ultimate decides. It sorts, filters and health-checks streams per its own web config,
 * so whenever it returned streams they are the only candidates, in exactly the order it sent them. ARVIO's own
 * ranking (quality, size, host health, recent source, device quality limits and regex filters) does not apply.
 */
fun isUsenetUltimateStream(stream: StreamSource): Boolean =
    stream.addonId.contains("usenetultimate", ignoreCase = true) ||
        stream.addonName.contains("usenet ultimate", ignoreCase = true)

/** Usenet Ultimate's streams in its own order, or null when it returned none (ARVIO's normal logic then applies). */
fun followUsenetUltimate(streams: List<StreamSource>): List<StreamSource>? =
    streams.filter(::isUsenetUltimateStream).takeIf { it.isNotEmpty() }

/** The installed Usenet Ultimate addon itself: Play waits for its streams before picking one. */
fun isUsenetUltimateAddon(addon: Addon): Boolean =
    addon.id.contains("usenetultimate", ignoreCase = true) ||
        addon.name.contains("usenet ultimate", ignoreCase = true)
