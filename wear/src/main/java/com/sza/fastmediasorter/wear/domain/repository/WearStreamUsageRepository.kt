package com.sza.fastmediasorter.wear.domain.repository

import com.sza.fastmediasorter.wear.domain.model.WearStreamUsage

/**
 * S2146: the watch's own record of which streams the owner actually starts, used as the default
 * ordering key of the streams list.
 */
interface WearStreamUsageRepository {

    /**
     * Every recorded channel, keyed by the canonical address.
     *
     * Read once per catalogue emission and passed into the projection as a ready map: a per-row
     * lookup would turn scrolling a nineteen-thousand-row catalogue into a store read per frame.
     */
    suspend fun usageByIdentity(): Map<String, WearStreamUsage>

    /**
     * Accepts the play and returns without waiting for the write.
     *
     * S2146 ADR-7: the single point both entrances to playback share is synchronous, and its result
     * is used immediately to navigate to the player, so a suspending write would restructure the
     * screen's navigation for a counter. The cost is that a write which did not land before the
     * process died is lost - one play out of many, which cannot change the order of the list.
     */
    fun recordPlay(identity: String)
}
