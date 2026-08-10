package com.sza.fastmediasorter.domain.usecase.networkmonitor

import com.sza.fastmediasorter.core.di.IoDispatcher
import com.sza.fastmediasorter.data.networkmonitor.WifiSignalSampler
import com.sza.fastmediasorter.domain.model.networkmonitor.MonitorSection
import com.sza.fastmediasorter.domain.model.networkmonitor.SignalSample
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * S1433: the Wi-Fi RSSI chart's read path.
 *
 * Two jobs, both of which the section would otherwise have to do itself and one of which it must not.
 *
 * It sets the dispatcher. `WifiSignalSampler` applies no `flowOn` of its own - Phase 05 left that to the
 * consumer deliberately - and every read behind it is binder IPC at one sample per second, so a section
 * collecting the sampler directly would put that IPC on the main thread for as long as the screen is open.
 *
 * It also keeps `data` out of `ui`: the sampler's `WifiSignalReading` is a data-layer type, and mapping it
 * to the domain [SignalSample] here is what lets the ViewModel import neither the sampler nor its model.
 */
class ObserveWifiSignalUseCase @Inject constructor(
    private val sampler: WifiSignalSampler,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    operator fun invoke(): Flow<MonitorSection<SignalSample>> = sampler.observe()
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
