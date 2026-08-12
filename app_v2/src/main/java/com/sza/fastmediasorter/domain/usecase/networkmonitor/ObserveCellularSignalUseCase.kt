package com.sza.fastmediasorter.domain.usecase.networkmonitor

import com.sza.fastmediasorter.core.di.IoDispatcher
import com.sza.fastmediasorter.data.networkmonitor.CellularSignalSampler
import com.sza.fastmediasorter.domain.model.networkmonitor.MonitorSection
import com.sza.fastmediasorter.domain.model.networkmonitor.SignalSample
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * S1433: one signal reading tagged with the SIM slot it belongs to.
 *
 * The slot rather than the subscription id: the slot is what the section labels a chart with, and the
 * subscription id is an identifier of the SIM itself, which strategic §3.2 keeps out of the UI entirely.
 */
data class SimSignalSample(
    val slotIndex: Int,
    val sample: SignalSample,
)

/**
 * S1433: the per-SIM signal charts' read path.
 *
 * Sets the dispatcher the sampler deliberately leaves unset (Phase 05 handoff) and maps the data-layer
 * reading onto a domain type, so the Mobile ViewModel imports neither `data` nor a subscription id.
 */
class ObserveCellularSignalUseCase @Inject constructor(
    private val sampler: CellularSignalSampler,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    operator fun invoke(): Flow<MonitorSection<List<SimSignalSample>>> = sampler.observe()
        .map { section ->
            val readings = section.data
            if (readings == null) {
                MonitorSection.absent(section.availability)
            } else {
                MonitorSection.available(
                    readings.map { reading ->
                        SimSignalSample(
                            slotIndex = reading.slotIndex,
                            sample = SignalSample(reading.takenAtMillis, reading.dbm.toDouble()),
                        )
                    }
                )
            }
        }
        .flowOn(ioDispatcher)
}
