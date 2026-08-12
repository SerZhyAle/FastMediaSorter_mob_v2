package com.sza.fastmediasorter.data.map

import com.sza.fastmediasorter.domain.map.MapRefreshPolicy
import com.sza.fastmediasorter.domain.model.map.MapSnapshot
import com.sza.fastmediasorter.domain.repository.MapResult

/**
 * S1175: when the map cell may reuse what it already has, and what it shows when the refresh fails.
 *
 * Kept as a pure object because both answers depend on wall-clock age, which is exactly what a test
 * cannot control once the decision is buried inside the repository's suspend path. The window itself
 * belongs to the domain - the gadget polls on the same number.
 */
object MapCachePolicy {

    /** Returns the snapshot itself rather than a flag, so the caller needs no null assertion after it. */
    fun freshOrNull(snapshot: MapSnapshot?, nowMs: Long): MapSnapshot? =
        snapshot?.takeIf { nowMs - it.capturedAt < MapRefreshPolicy.TTL_MS }

    /**
     * A failed refresh keeps the previous snapshot on screen with its age, because an old picture of
     * where the user is still says where the user is, while an empty cell reads as a broken gadget.
     */
    fun resultFor(previous: MapSnapshot?, fetched: MapSnapshot?, nowMs: Long): MapResult = when {
        fetched != null -> MapResult.Fresh(fetched)
        previous != null -> MapResult.Stale(previous, nowMs - previous.capturedAt)
        else -> MapResult.Unavailable
    }
}
