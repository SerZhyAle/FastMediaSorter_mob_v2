package com.sza.fastmediasorter.ui.streams

import com.sza.fastmediasorter.data.local.db.StreamSourceEntity

/**
 * S1169: partial-rebind payloads shared by the stream list ([StreamSourceAdapter]) and grid
 * ([StreamGridAdapter]) adapters. A change limited to the play-outcome status or the pin flag repaints
 * only that affordance instead of running a full row rebind (which would re-decode the favicon, rebuild
 * the overflow menu, and re-set the frame bitmap - the redundant work behind the visible thumbnail churn).
 *
 * S1502: [PIN] still comes from DiffUtil; [STATUS] no longer does. The outcome left the catalog row for
 * its own table, so the adapters emit it themselves from their `playOutcomes` setter.
 */
internal object StreamAdapterPayloads {
    const val STATUS = "stream_status"
    const val PIN = "stream_pin"
}

/**
 * S1169: returns a partial-rebind payload when [oldItem] and [newItem] differ ONLY in the pin flag;
 * null (full rebind) for any other difference. The `copy(..) == newItem` guard proves no other field
 * changed, so the narrow repaint is safe.
 *
 * S1502: the play-outcome branch went with the columns. A probe result no longer alters the catalog row,
 * so it never reaches DiffUtil - it arrives on the outcome Flow and repaints through the same [STATUS]
 * payload from the adapter side.
 */
internal fun streamRowChangePayload(oldItem: StreamSourceEntity, newItem: StreamSourceEntity): Any? {
    if (oldItem.pinned != newItem.pinned && oldItem.copy(pinned = newItem.pinned) == newItem) {
        return StreamAdapterPayloads.PIN
    }
    return null
}
