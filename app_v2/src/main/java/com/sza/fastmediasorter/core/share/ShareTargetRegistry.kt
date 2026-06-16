package com.sza.fastmediasorter.core.share

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Registry of all declared [ShareTarget]s (S0452 foundation).
 *
 * Targets are contributed by Hilt multibinding (`@IntoSet ShareTarget`), so a new target ticket
 * (S0443-S0446) registers an entry without editing this class. Holds declaration only - default
 * and availability logic live in [ShareTargetAvailabilityResolver].
 */
@Singleton
class ShareTargetRegistry @Inject constructor(
    targets: Set<@JvmSuppressWildcards ShareTarget>,
) {
    private val byId: Map<String, ShareTarget> = targets.associateBy { it.id }

    /** All registered targets, ordered by id for a stable settings-group layout. */
    fun all(): List<ShareTarget> = byId.values.sortedBy { it.id }

    /** @return the registered target for [id], or null when none is registered. */
    fun byId(id: String): ShareTarget? = byId[id]
}
