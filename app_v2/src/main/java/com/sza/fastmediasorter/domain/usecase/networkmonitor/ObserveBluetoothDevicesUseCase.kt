package com.sza.fastmediasorter.domain.usecase.networkmonitor

import com.sza.fastmediasorter.core.di.IoDispatcher
import com.sza.fastmediasorter.data.networkmonitor.BluetoothDeviceDataSource
import com.sza.fastmediasorter.domain.model.networkmonitor.BluetoothDeviceEntry
import com.sza.fastmediasorter.domain.model.networkmonitor.MonitorSection
import com.sza.fastmediasorter.domain.repository.NetworkMonitorRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * S1433: the paired and connected device list, refreshed on the Monitor's own tick.
 *
 * The snapshot flow is used purely as the clock. The adapter exposes no change stream for pairing or for
 * connection, so the alternatives were a timer of its own or a broadcast receiver, and both would add a
 * second cadence to a screen the repository already paces at one sample per second - which is exactly what
 * `NetworkMonitorRepositoryImpl` does for telephony and adapter state.
 *
 * `distinctUntilChanged` keeps that tick from repainting a list that has not moved; the entries are data
 * classes, so equality is the real answer rather than an identity comparison.
 */
class ObserveBluetoothDevicesUseCase @Inject constructor(
    private val repository: NetworkMonitorRepository,
    private val dataSource: BluetoothDeviceDataSource,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    operator fun invoke(): Flow<MonitorSection<List<BluetoothDeviceEntry>>> = repository.observeSnapshot()
        .map { dataSource.sample() }
        .distinctUntilChanged()
        .flowOn(ioDispatcher)
}
