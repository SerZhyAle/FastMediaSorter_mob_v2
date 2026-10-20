package com.sza.fastmediasorter.domain.usecase.transfer

import com.sza.fastmediasorter.domain.model.transfer.CrossDevicePacketEntry
import com.sza.fastmediasorter.domain.model.transfer.CrossDevicePacketExpiry
import com.sza.fastmediasorter.domain.model.transfer.CrossDevicePacketManifest
import com.sza.fastmediasorter.domain.model.transfer.CrossDevicePayloadKind
import com.sza.fastmediasorter.domain.model.transfer.CrossDeviceQueueSnapshot
import com.sza.fastmediasorter.domain.model.transfer.CrossDeviceQueueView
import com.sza.fastmediasorter.domain.port.LocalDeviceIdentity
import com.sza.fastmediasorter.domain.transfer.CrossDeviceTransferRepository
import com.sza.fastmediasorter.domain.transfer.DriveChannelSettings
import javax.inject.Inject

/**
 * S3040: the packets this device may claim - everything pending except its own sends.
 *
 * A packet naming another device is hidden rather than merely sorted down: the queue is one shared
 * space, and offering the tablet's packet on the phone is how a transfer gets claimed twice.
 *
 * S4231: also this device's own sends still waiting, so the person sees and can delete what the queue
 * holds (contract item CS). `CLAIMED` written by a shipped phone is offered as `PENDING` (item CR).
 */
class GetPendingCrossDevicePacketsUseCase @Inject constructor(
    private val repository: CrossDeviceTransferRepository,
    private val settings: DriveChannelSettings
) {

    suspend operator fun invoke(self: LocalDeviceIdentity): Result<CrossDeviceQueueView> {
        val received = settings.receivedPacketIds()
        return repository.listQueue().map { snapshot -> viewOf(snapshot, self, received) }
    }

    companion object {
        private val offeredKinds = setOf(
            CrossDevicePayloadKind.SETTINGS,
            CrossDevicePayloadKind.MEDIA_FILES,
            CrossDevicePayloadKind.FILES
        )

        /** The queue as [self] sees it: no requests, replies or clipboard, nothing expired on Drive's clock. */
        fun viewOf(
            snapshot: CrossDeviceQueueSnapshot,
            self: LocalDeviceIdentity,
            received: Set<String>
        ): CrossDeviceQueueView {
            val live = snapshot.entries
                .filter { it.manifest.payloadKind in offeredKinds }
                .filterNot { CrossDevicePacketExpiry.isExpired(it, snapshot.serverNowMs) }
                .sortedByDescending(CrossDevicePacketEntry::createdTimeMs)
            val (outgoing, others) = live.partition { isSentBy(it.manifest, self) }
            // The status is not tested on purpose: a shipped phone's CLAIMED is read as PENDING here.
            val incoming = others.filter { entry -> isOfferedTo(entry.manifest, self) && entry.packetId !in received }
            return CrossDeviceQueueView(incoming = incoming, outgoing = outgoing)
        }

        private fun isOfferedTo(manifest: CrossDevicePacketManifest, self: LocalDeviceIdentity): Boolean =
            (manifest.targetDeviceId == null && manifest.targetDeviceName == null) ||
                ReceiveCrossDevicePacketUseCase.isTargetedAt(manifest, self)

        private fun isSentBy(manifest: CrossDevicePacketManifest, self: LocalDeviceIdentity): Boolean =
            manifest.senderDeviceId?.let { it == self.deviceId } ?: (manifest.senderDeviceName == self.displayName)
    }
}
