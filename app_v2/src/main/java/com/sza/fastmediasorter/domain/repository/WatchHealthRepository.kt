package com.sza.fastmediasorter.domain.repository

import com.sza.fastmediasorter.domain.model.tourist.WatchHealthReading
import kotlinx.coroutines.flow.StateFlow

/**
 * S4150: the last Tourist health report received from the watch. Filled by the companion bridge, which
 * only some flavors mount; read by the Tourist dashboard, which does not depend on that bridge.
 */
interface WatchHealthRepository {

    val latest: StateFlow<WatchHealthReading?>

    fun publish(reading: WatchHealthReading)
}
