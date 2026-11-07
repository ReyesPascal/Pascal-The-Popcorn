package com.arflix.tv.data.model

/** Live playback and invalid catalog IDs must never enter resume/history storage. */
fun isNotResumable(
    id: Int? = null,
    isLiveStream: Boolean = false,
    isAddonNative: Boolean = false
): Boolean = isLiveStream || (id != null && id <= 0 && !isAddonNative)
