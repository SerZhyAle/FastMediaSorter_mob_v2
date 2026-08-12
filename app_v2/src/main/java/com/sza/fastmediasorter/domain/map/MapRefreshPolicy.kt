package com.sza.fastmediasorter.domain.map

import java.util.concurrent.TimeUnit

/**
 * S1175: how often the map cell is worth refreshing, owned by the domain because both the view and
 * the cache need the same answer and neither should read it from the other.
 */
object MapRefreshPolicy {

    private const val TTL_MINUTES = 20L
    private const val POLL_MARGIN_MINUTES = 1L

    /** Matches the weather gadget's cadence - a home screen is not a navigation view. */
    val TTL_MS: Long = TimeUnit.MINUTES.toMillis(TTL_MINUTES)

    /**
     * Strictly longer than [TTL_MS] on purpose: a poll exactly on the boundary refreshes only because
     * the snapshot is stamped before its fetch, so any change to that stamping would silently halve
     * the refresh rate instead of failing.
     */
    val POLL_INTERVAL_MS: Long = TTL_MS + TimeUnit.MINUTES.toMillis(POLL_MARGIN_MINUTES)
}
