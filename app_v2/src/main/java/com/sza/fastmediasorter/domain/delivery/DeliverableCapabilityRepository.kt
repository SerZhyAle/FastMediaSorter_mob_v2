package com.sza.fastmediasorter.domain.delivery

import kotlinx.coroutines.flow.Flow

/**
 * Source of truth for whether a [DeliverableSet] is available (bundled or downloaded), so base
 * code asks this contract instead of touching ML/OCR engines directly (S0386 Pillar A).
 */
interface DeliverableCapabilityRepository {

    /** Reactive availability state for the extensions screen and toggles. */
    fun stateOf(set: DeliverableSet): Flow<DeliverableCapability>

    /** Persist that [set] is installed (survives app update and cache clear). */
    suspend fun markInstalled(set: DeliverableSet)

    /** Persist that [set] is not installed. */
    suspend fun markNotInstalled(set: DeliverableSet)

    /** Delete the downloaded payload and mark not-installed, freeing space (extensions screen). */
    suspend fun uninstall(set: DeliverableSet)

    /** Synchronous gate for engine call sites: true if bundled or the payload is present. */
    fun isInstalledBlocking(set: DeliverableSet): Boolean
}
