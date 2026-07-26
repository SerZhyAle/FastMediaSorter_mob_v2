package com.sza.fastmediasorter.ui.streams

import com.sza.fastmediasorter.data.local.db.StreamSourceEntity

/**
 * S1169: DiffUtil change payloads shared by the stream list ([StreamSourceAdapter]) and grid
 * ([StreamGridAdapter]) adapters. A change limited to the play-outcome status or the pin flag repaints
 * only that affordance instead of running a full row rebind (which would re-decode the favicon, rebuild
 * the overflow menu, and re-set the frame bitmap - the redundant work behind the visible thumbnail churn).
 */
internal object StreamAdapterPayloads {
    const val STATUS = "stream_status"
    const val PIN = "stream_pin"
}

/**
 * S1169: returns a partial-rebind payload when [oldItem] and [newItem] differ ONLY in the play-outcome
 * status or ONLY in the pin flag; null (full rebind) for any other difference. The `copy(..) == newItem`
 * guard proves no other field changed, so the narrow repaint is safe.
 */
internal fun streamRowChangePayload(oldItem: StreamSourceEntity, newItem: StreamSourceEntity): Any? {
    val statusChanged =
        oldItem.lastPlayOutcome != newItem.lastPlayOutcome || oldItem.lastPlayOutcomeAt != newItem.lastPlayOutcomeAt
    if (statusChanged &&
        oldItem.copy(
            lastPlayOutcome = newItem.lastPlayOutcome,
            lastPlayOutcomeAt = newItem.lastPlayOutcomeAt,
        ) == newItem
    ) {
        return StreamAdapterPayloads.STATUS
    }
    if (oldItem.pinned != newItem.pinned && oldItem.copy(pinned = newItem.pinned) == newItem) {
        return StreamAdapterPayloads.PIN
    }
    return null
}
