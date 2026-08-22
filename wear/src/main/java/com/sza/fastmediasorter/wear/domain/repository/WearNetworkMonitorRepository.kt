package com.sza.fastmediasorter.wear.domain.repository

import com.sza.fastmediasorter.wear.domain.netmonitor.WearNetworkCapabilities
import com.sza.fastmediasorter.wear.domain.netmonitor.WearNetworkSnapshot
import kotlinx.coroutines.flow.Flow

/**
 * The watch's own radios, not a mirror of the phone's snapshot.
 *
 * [capabilities] answers what this watch has at all, so a section for absent hardware is never shown.
 * [snapshots] measures only while it is collected: the reading starts with the collection and every
 * platform callback it registers is released when the collection ends.
 */
interface WearNetworkMonitorRepository {

    fun capabilities(): WearNetworkCapabilities

    fun snapshots(): Flow<WearNetworkSnapshot>

    /**
     * Whether every runtime permission the sampling needs on THIS API level is granted. False means
     * some fields will read as absent for a reason the user can change, which the screen says plainly
     * instead of leaving a silent blank.
     */
    fun permissionsGranted(): Boolean
}
