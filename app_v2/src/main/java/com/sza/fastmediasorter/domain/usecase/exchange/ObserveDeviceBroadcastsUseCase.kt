package com.sza.fastmediasorter.domain.usecase.exchange

import com.sza.fastmediasorter.broadcast.BroadcastMode
import com.sza.fastmediasorter.data.repository.DeviceExchangeAccountRepository
import com.sza.fastmediasorter.data.repository.ExchangeDirectoryRepository
import com.sza.fastmediasorter.domain.model.ExchangeDeviceBroadcast
import com.sza.fastmediasorter.domain.model.ExchangeDeviceBroadcasts
import com.sza.fastmediasorter.domain.model.ExchangeDirectoryListing
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * The broadcasts half of the account directory for the streams list (S4116 strategic 6.3, phase 07). Watching it
 * holds a server stream per entry, so the caller collects only while the section is open (strategic 3.2).
 */
class ObserveDeviceBroadcastsUseCase @Inject constructor(
    private val accounts: DeviceExchangeAccountRepository,
    private val directory: ExchangeDirectoryRepository,
) {

    /** True while at least one exchange entry is held; without one the section is not offered (section 9.7). */
    fun hasAccount(): Flow<Boolean> = accounts.entries.map { it.isNotEmpty() }.distinctUntilChanged()

    operator fun invoke(): Flow<ExchangeDeviceBroadcasts> = directory.observe(PLAYABLE_MODES).map(::toBroadcasts)

    companion object {

        // The streams player plays every LIVE-BROADCAST mode; this section exists only where it is built in.
        private val PLAYABLE_MODES = BroadcastMode.entries.mapTo(HashSet()) { it.name }

        fun toBroadcasts(listing: ExchangeDirectoryListing) = ExchangeDeviceBroadcasts(
            items = listing.devices.flatMap { device ->
                // A box that did not open leaves the record keyless, and a keyless sealed broadcast never plays.
                device.broadcasts.filter { it.record.sealIssue == null }.map { listed ->
                    ExchangeDeviceBroadcast(
                        broadcastId = listed.record.broadcastId,
                        deviceName = device.device?.deviceName,
                        title = listed.record.title,
                        mode = listed.record.mode,
                        playable = listed.playable,
                        descriptorJson = listed.record.descriptorJson,
                    )
                }
            }.sortedWith(compareBy({ it.deviceName.orEmpty().lowercase() }, { it.title.orEmpty().lowercase() })),
            pending = listing.pending,
            incomplete = listing.unreachableSources.isNotEmpty(),
        )
    }
}
