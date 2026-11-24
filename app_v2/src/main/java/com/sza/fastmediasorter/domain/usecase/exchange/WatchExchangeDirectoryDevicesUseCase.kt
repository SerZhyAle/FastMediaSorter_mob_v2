package com.sza.fastmediasorter.domain.usecase.exchange

import com.sza.fastmediasorter.data.remote.exchange.DeviceExchangeDirectoryWatcher
import com.sza.fastmediasorter.domain.model.DeviceExchangeAccountEntry
import com.sza.fastmediasorter.domain.model.SftpRendezvousDevice
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * The devices of one account entry's directory, live through the server's `subscribe` stream. A surface that
 * lists devices needs nothing else of the snapshot, so the resources and broadcasts stay in the data layer.
 */
class WatchExchangeDirectoryDevicesUseCase @Inject constructor(
    private val watcher: DeviceExchangeDirectoryWatcher,
) {

    /**
     * Fails the way the stream does: [java.io.IOException] when the server cannot be reached or the stream
     * breaks, so a caller can tell an unreachable server from a refusal.
     */
    operator fun invoke(entry: DeviceExchangeAccountEntry): Flow<List<SftpRendezvousDevice>> =
        watcher.watch(entry).map { snapshot -> snapshot.devices }
}
