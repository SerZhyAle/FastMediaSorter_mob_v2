package com.sza.fastmediasorter.domain.usecase.exchange

import com.sza.fastmediasorter.broadcast.BroadcastMode
import com.sza.fastmediasorter.data.repository.DeviceExchangeAccountRepository
import com.sza.fastmediasorter.data.repository.ExchangeDirectoryRepository
import com.sza.fastmediasorter.domain.model.ExchangeDeviceBroadcasts
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * S4392: Account-wide live broadcasts for the Streams "Own" category filter.
 * Observes broadcasts of all account devices, including this phone and sibling devices.
 */
class ObserveAccountBroadcastsUseCase @Inject constructor(
    private val accounts: DeviceExchangeAccountRepository,
    private val directory: ExchangeDirectoryRepository,
) {

    /** True while at least one exchange entry is held. */
    fun hasAccount(): Flow<Boolean> = accounts.entries.map { it.isNotEmpty() }.distinctUntilChanged()

    operator fun invoke(): Flow<ExchangeDeviceBroadcasts> =
        directory.observeAllBroadcasts(PLAYABLE_MODES).map(ObserveDeviceBroadcastsUseCase::toBroadcasts)

    companion object {
        private val PLAYABLE_MODES = BroadcastMode.entries.mapTo(HashSet()) { it.name }
    }
}
