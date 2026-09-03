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

    /**
     * Performs a lightweight non-blocking reachability probe to verify internet connectivity.
     */
    suspend fun probeReachability(host: String = "1.1.1.1"): Boolean

    /**
     * The address the watch is seen under from outside its own link.
     *
     * Kept off [snapshots] deliberately: every other field is read from a local radio and costs
     * nothing, while this one leaves the device. It is asked for once per link change instead of on
     * every poll tick, and null means the lookup did not answer - never that the watch has no address.
     */
    suspend fun resolveExternalIp(): String?
}
