package com.sza.fastmediasorter.domain.usecase.networkmonitor

import com.sza.fastmediasorter.core.di.IoDispatcher
import com.sza.fastmediasorter.data.networkmonitor.BluetoothRssiSampler
import com.sza.fastmediasorter.domain.model.networkmonitor.MonitorSection
import com.sza.fastmediasorter.domain.model.networkmonitor.SignalSample
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * S1433: the Bluetooth signal chart's read path for one explicitly selected device.
 *
 * [deviceAddress] is a precondition and not a filter: a null address yields a flow that emits nothing, so no
 * GATT connection is opened at all. Research artifact 02 permits RSSI only for a device the user picked from
 * the connected set, and expressing that by opening nothing is stronger than discarding samples afterwards.
 *
 * Sets the dispatcher the sampler leaves unset (Phase 05 handoff) and maps the reading onto a domain type,
 * so the section ViewModel imports neither the sampler nor a device address it has no use for.
 */
class ObserveBluetoothRssiUseCase @Inject constructor(
    private val sampler: BluetoothRssiSampler,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    operator fun invoke(deviceAddress: String?): Flow<MonitorSection<SignalSample>> =
        sampler.observe(deviceAddress)
            .map { section ->
                val reading = section.data
                if (reading == null) {
                    MonitorSection.absent(section.availability)
                } else {
                    MonitorSection.available(SignalSample(reading.takenAtMillis, reading.rssiDbm.toDouble()))
                }
            }
            .flowOn(ioDispatcher)
}
