package com.sza.fastmediasorter.data.transfer.drive

import com.sza.fastmediasorter.core.util.rethrowIfCancellation
import com.sza.fastmediasorter.domain.model.transfer.CrossDevicePayloadKind
import com.sza.fastmediasorter.domain.model.transfer.CrossDeviceQueueSnapshot
import com.sza.fastmediasorter.domain.port.LocalDeviceIdentitySource
import com.sza.fastmediasorter.domain.transfer.CrossDeviceTransferRepository
import javax.inject.Inject

/**
 * S4231: one poll of the Drive queue - exactly one listing, then the work that listing asks for: answer
 * the requests addressed here, purge what expired on Drive's clock, thin out superseded clipboard values
 * (contract DEVICE-EXCHANGE items CQ, CR, CP). The foreground loop and the background worker both run
 * this unit, so the two can never poll differently.
 */
class DriveChannelPollCycle @Inject constructor(
    private val repository: CrossDeviceTransferRepository,
    private val responder: DriveChannelResponder,
    private val clipboard: DriveClipboardChannel,
    private val identity: LocalDeviceIdentitySource,
) {

    suspend fun run(): Result<CrossDeviceQueueSnapshot> = repository.listQueue().onSuccess { snapshot ->
        val self = identity.current()
        snapshot.entries
            .filter { it.manifest.payloadKind == CrossDevicePayloadKind.REQUEST }
            .forEach { responder.handle(it, self, snapshot.serverNowMs) }
        repository.purgeExpired(snapshot).onFailure { it.rethrowIfCancellation() }
        clipboard.cleanup(snapshot)
    }
}
