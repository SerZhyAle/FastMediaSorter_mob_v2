package com.sza.fastmediasorter.domain.usecase.networkmonitor

import com.sza.fastmediasorter.core.di.IoDispatcher
import com.sza.fastmediasorter.data.networkmonitor.TrafficRateSampler
import com.sza.fastmediasorter.domain.model.networkmonitor.MonitorSection
import com.sza.fastmediasorter.domain.model.networkmonitor.TrafficRate
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * S1433: the Internet section's traffic-rate read path.
 *
 * Same two jobs as the other `Observe*` use cases of this ticket: it sets the dispatcher the sampler
 * deliberately leaves to its consumer, and it maps the sampler's data-layer reading to the domain
 * [TrafficRate] so a ViewModel in `ui` imports neither.
 */
class ObserveTrafficRateUseCase @Inject constructor(
    private val sampler: TrafficRateSampler,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    operator fun invoke(): Flow<MonitorSection<TrafficRate>> = sampler.observe()
        .map { section ->
            val reading = section.data
            if (reading == null) {
                MonitorSection.absent(section.availability)
            } else {
                MonitorSection.available(
                    TrafficRate(
                        takenAtMillis = reading.takenAtMillis,
                        receivedBytesPerSecond = reading.rxBytesPerSecond,
                        transmittedBytesPerSecond = reading.txBytesPerSecond,
                    )
                )
            }
        }
        .flowOn(ioDispatcher)
}
