package com.sza.fastmediasorter.domain.network

import com.sza.fastmediasorter.domain.model.network.HotspotState
import kotlinx.coroutines.flow.Flow

/**
 * Read-only source of Wi-Fi hotspot state.
 *
 * Emits the current state at subscription time and on every observed change.
 */
interface HotspotStateSource {
    fun state(): Flow<HotspotState>
}
