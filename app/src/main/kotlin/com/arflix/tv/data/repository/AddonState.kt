package com.arflix.tv.data.repository

import com.arflix.tv.data.model.Addon

data class AddonChange(val updatedAt: Long = 0L, val removed: Boolean = false)

internal data class AddonState(
    val addons: List<Addon>,
    val updatedAt: Long,
    val changes: Map<String, AddonChange>
)

internal fun mergeAddonChanges(
    local: Map<String, AddonChange>,
    remote: Map<String, AddonChange>
): Map<String, AddonChange> = buildMap {
    for ((id, change) in local.entries + remote.entries) {
        if (id.isBlank() || change.updatedAt <= 0 || (id == "opensubtitles" && change.removed)) continue
        val previous = get(id)
        if (previous == null || change.updatedAt > previous.updatedAt ||
            (change.updatedAt == previous.updatedAt && change.removed)) put(id, change)
    }
}



internal fun recordAddonChanges(previous: Map<String, AddonChange>, added: Set<String>, removed: Set<String>, timestamp: Long): Map<String, AddonChange> {
    val edits = added.associateWith { AddonChange(timestamp, false) } + removed.associateWith { AddonChange(timestamp, true) }
    return mergeAddonChanges(previous, edits)
}
