package com.sza.fastmediasorter.domain.delivery

import kotlinx.coroutines.flow.Flow

/**
 * Source of truth for whether a [DeliverableSet] is available (bundled or downloaded), so base
 * code asks this contract instead of touching ML/OCR engines directly (S0386 Pillar A).
 */
interface DeliverableCapabilityRepository {

    /** Reactive availability state for the extensions screen and toggles. */
    fun stateOf(set: DeliverableSet): Flow<DeliverableCapability>

    /**
     * Persist that [set] is installed (survives app update and cache clear). [stamp] identifies which
     * payload was installed - see [DeliverableSourceDescriptor.stamp] - so a later build can tell that
     * the copy on disk is not the one it expects (S1200).
     */
    suspend fun markInstalled(set: DeliverableSet, stamp: String)

    /**
     * True when [set] has a payload on disk that was installed against a different [expectedStamp]
     * than this build carries.
     *
     * A bundled set is never stale - it has no payload to compare. A set that is not installed is not
     * stale either; absent is a different question. A payload installed before stamps existed has none
     * recorded and therefore reads as stale: one redundant offer is a better failure than permanently
     * hiding a real update from exactly the people who have the old copy (S1200 §5).
     */
    suspend fun isStale(set: DeliverableSet, expectedStamp: String): Boolean

    /** Persist that [set] is not installed. */
    suspend fun markNotInstalled(set: DeliverableSet)

    /** Delete the downloaded payload and mark not-installed, freeing space (extensions screen). */
    suspend fun uninstall(set: DeliverableSet)

    /** Synchronous gate for engine call sites: true if bundled or the payload is present. */
    fun isInstalledBlocking(set: DeliverableSet): Boolean
}
