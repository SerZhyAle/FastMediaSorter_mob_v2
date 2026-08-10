package com.sza.fastmediasorter.domain.repository

import com.sza.fastmediasorter.domain.model.networkmonitor.NetworkMonitorSnapshot
import kotlinx.coroutines.flow.Flow

/**
 * S1433: the Monitor's single read path for device network state.
 *
 * One method on purpose. Strategic §5.2 makes the screen a consumer of one composed state rather than of
 * three device APIs, so a section that wants only Wi-Fi still reads the same snapshot and picks its field
 * out of it - which is what keeps the sections from disagreeing about when they were sampled.
 *
 * Collection is the whole lifetime: nothing observes the device before the flow is collected or after it is
 * cancelled.
 */
interface NetworkMonitorRepository {

    /** Emits a composed snapshot on collection, on every network change, and at most once per second. */
    fun observeSnapshot(): Flow<NetworkMonitorSnapshot>
}
