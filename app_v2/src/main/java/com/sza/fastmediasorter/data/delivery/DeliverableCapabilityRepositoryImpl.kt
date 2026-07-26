package com.sza.fastmediasorter.data.delivery

import com.sza.fastmediasorter.domain.delivery.BundledDeliverableSets
import com.sza.fastmediasorter.domain.delivery.DeliverableCapability
import com.sza.fastmediasorter.domain.delivery.DeliverableCapabilityRepository
import com.sza.fastmediasorter.domain.delivery.DeliverableSet
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Combines the bundled-in-build fact with the downloaded-payload state to answer whether a
 * [DeliverableSet] is usable. A set is installed when it is bundled in this build OR its payload
 * has been downloaded (S0386 Pillar A).
 */
@Singleton
class DeliverableCapabilityRepositoryImpl @Inject constructor(
    private val markerStore: InstalledSetMarkerStore,
    private val bundled: BundledDeliverableSets
) : DeliverableCapabilityRepository {

    override fun stateOf(set: DeliverableSet): Flow<DeliverableCapability> =
        markerStore.installedFlagFlow(set).map {
            if (isInstalledBlocking(set)) {
                DeliverableCapability.INSTALLED
            } else {
                DeliverableCapability.NOT_INSTALLED
            }
        }

    override suspend fun markInstalled(set: DeliverableSet, stamp: String) {
        // S1200: flag and stamp are written together, from the one place that knows an install just
        // succeeded. Splitting them would let the two disagree, and a stamp that outlives its payload
        // reads as a permanent "update available".
        markerStore.setInstalled(set, true)
        markerStore.setStamp(set, stamp)
    }

    override suspend fun markNotInstalled(set: DeliverableSet) {
        markerStore.setInstalled(set, false)
    }

    override suspend fun uninstall(set: DeliverableSet) {
        markerStore.deletePayload(set)
        markerStore.setInstalled(set, false)
        markerStore.clearStamp(set)
    }

    override suspend fun isStale(set: DeliverableSet, expectedStamp: String): Boolean {
        if (bundled.contains(set)) return false
        if (!markerStore.isPayloadPresent(set)) return false
        return markerStore.installedStamp(set) != expectedStamp
    }

    override fun isInstalledBlocking(set: DeliverableSet): Boolean {
        // S0423: translation is bundled in every translation-capable flavor (no on-demand DFM), so it
        // is reported installed via [bundled]. The HTTP/payload path still covers the de-bundled
        // native sets (OCR/DTS) on non-Play builds.
        if (bundled.contains(set)) return true
        return markerStore.isPayloadPresent(set)
    }
}
