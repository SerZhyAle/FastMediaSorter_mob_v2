package com.sza.fastmediasorter.domain.usecase.tourist

import com.sza.fastmediasorter.core.capability.CapabilityAvailability
import com.sza.fastmediasorter.domain.model.tourist.WatchHealthStats
import com.sza.fastmediasorter.domain.model.tourist.toStats
import com.sza.fastmediasorter.domain.repository.WatchHealthRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject

/**
 * S4150: the watch heart rate and body temperature as the Tourist dashboard shows them.
 *
 * The ticker re-judges freshness while no report arrives, so a reading disappears once the watch stops
 * sending instead of staying on screen. A build without the watch-health capability gets one empty
 * answer and keeps no ticker running.
 */
class ObserveWatchTouristHealthUseCase @Inject constructor(
    private val repository: WatchHealthRepository,
    private val capabilityAvailability: CapabilityAvailability,
) {

    operator fun invoke(): Flow<WatchHealthStats> {
        val offered = capabilityAvailability.isWatchHealthAvailable()
        if (!offered) return flowOf(WatchHealthStats())
        return combine(repository.latest, clockTicks()) { reading, now -> reading.toStats(offered = true, now) }
            .distinctUntilChanged()
    }

    private fun clockTicks(): Flow<Long> = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(FRESHNESS_CHECK_INTERVAL_MS)
        }
    }

    private companion object {
        const val FRESHNESS_CHECK_INTERVAL_MS = 5_000L
    }
}
