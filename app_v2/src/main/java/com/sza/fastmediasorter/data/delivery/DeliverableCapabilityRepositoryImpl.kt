package com.sza.fastmediasorter.data.delivery

import android.content.Context
import com.sza.fastmediasorter.domain.delivery.BundledDeliverableSets
import com.sza.fastmediasorter.domain.delivery.DeliverableCapability
import com.sza.fastmediasorter.domain.delivery.DeliverableCapabilityRepository
import com.sza.fastmediasorter.domain.delivery.DeliverableSet
import dagger.hilt.android.qualifiers.ApplicationContext
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
    @ApplicationContext private val context: Context,
    private val markerStore: InstalledSetMarkerStore,
    private val bundled: BundledDeliverableSets
) : DeliverableCapabilityRepository {

    override fun stateOf(set: DeliverableSet): Flow<DeliverableCapability> =
        markerStore.installedFlagFlow(set).map { downloaded ->
            if (isInstalledBlocking(set)) {
                DeliverableCapability.INSTALLED
            } else {
                DeliverableCapability.NOT_INSTALLED
            }
        }

    override suspend fun markInstalled(set: DeliverableSet) {
        markerStore.setInstalled(set, true)
    }

    override suspend fun markNotInstalled(set: DeliverableSet) {
        markerStore.setInstalled(set, false)
    }

    override suspend fun uninstall(set: DeliverableSet) {
        if (set == DeliverableSet.TRANSLATION) {
            try {
                val splitInstallManager = com.google.android.play.core.splitinstall.SplitInstallManagerFactory.create(context)
                splitInstallManager.deferredUninstall(listOf("translate_feature"))
            } catch (e: Exception) {
                // ignore
            }
        }
        markerStore.deletePayload(set)
        markerStore.setInstalled(set, false)
    }

    override fun isInstalledBlocking(set: DeliverableSet): Boolean {
        if (bundled.contains(set)) return true
        if (set == DeliverableSet.TRANSLATION) {
            return try {
                val splitInstallManager = com.google.android.play.core.splitinstall.SplitInstallManagerFactory.create(context)
                splitInstallManager.installedModules.contains("translate_feature")
            } catch (e: Exception) {
                false
            }
        }
        return markerStore.isPayloadPresent(set)
    }
}
