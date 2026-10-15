package com.sza.fastmediasorter.domain.usecase.transfer

import com.sza.fastmediasorter.core.util.rethrowIfCancellation
import com.sza.fastmediasorter.domain.model.transfer.CrossDevicePacketEntry
import com.sza.fastmediasorter.domain.model.transfer.CrossDevicePacketManifest
import com.sza.fastmediasorter.domain.model.transfer.CrossDevicePayloadKind
import com.sza.fastmediasorter.domain.model.transfer.CrossDeviceReceiveOutcome
import com.sza.fastmediasorter.domain.model.transfer.CrossDeviceTransferOption
import com.sza.fastmediasorter.domain.model.transfer.DriveTransferFailure
import com.sza.fastmediasorter.domain.model.transfer.PacketIntegrityFailure
import com.sza.fastmediasorter.domain.model.transfer.TransferDataKind
import com.sza.fastmediasorter.domain.port.IncomingFileResult
import com.sza.fastmediasorter.domain.port.IncomingTransferFileSink
import com.sza.fastmediasorter.domain.port.LocalDeviceIdentity
import com.sza.fastmediasorter.domain.transfer.CrossDeviceTransferRepository
import com.sza.fastmediasorter.domain.transfer.DriveChannelSettings
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

/**
 * S3040: download an offered packet, apply it, then take it off the queue.
 *
 * Removal happens last on purpose: a packet deleted before its payload is applied cannot be
 * retried, and the user would be left with neither the file nor the offer.
 *
 * S4231: every file is checked against its manifest before it is placed, and a failed check removes
 * what this attempt placed and leaves the packet for another try (contract item CN). Only a packet
 * targeted at this device, or one the person asked to delete, is deleted; an untargeted packet stays
 * for the other devices and no manifest is ever rewritten (item CR).
 */
class ReceiveCrossDevicePacketUseCase @Inject constructor(
    private val repository: CrossDeviceTransferRepository,
    private val applyTransferPayload: ApplyTransferPayloadUseCase,
    private val fileSink: IncomingTransferFileSink,
    private val settings: DriveChannelSettings
) {

    suspend operator fun invoke(
        entry: CrossDevicePacketEntry,
        self: LocalDeviceIdentity,
        option: CrossDeviceTransferOption
    ): Result<CrossDeviceReceiveOutcome> = runCatching {
        Timber.d("S4231: receive a Drive queue packet")
        val outcome = when (entry.manifest.payloadKind) {
            CrossDevicePayloadKind.SETTINGS -> applySettings(entry)
            CrossDevicePayloadKind.MEDIA_FILES, CrossDevicePayloadKind.FILES -> writeFiles(entry)
            else -> throw DriveTransferFailure("packet ${entry.packetId} is not offered to a person")
        }
        settings.markReceived(entry.packetId)
        if (isTargetedAt(entry.manifest, self) || option == CrossDeviceTransferOption.ACCEPT_AND_DELETE) {
            repository.deletePacket(entry)
                .onFailure { it.rethrowIfCancellation() }
                .onFailure { Timber.w("Received packet %s stays in the queue", entry.packetId) }
        }
        outcome
    }.onFailure { it.rethrowIfCancellation() }

    private suspend fun applySettings(entry: CrossDevicePacketEntry): CrossDeviceReceiveOutcome {
        val fileName = entry.manifest.fileNames.firstOrNull()
            ?: throw DriveTransferFailure("packet ${entry.packetId} declares no settings file")
        val bytes = repository.openPayload(entry, fileName).getOrThrow().use { it.readBytes() }
        applyTransferPayload(TransferDataKind.SETTINGS, bytes).getOrThrow()
        return CrossDeviceReceiveOutcome(entry.packetId, emptyList(), settingsApplied = true)
    }

    private suspend fun writeFiles(entry: CrossDevicePacketEntry): CrossDeviceReceiveOutcome {
        val placed = mutableListOf<String>()
        var complete = false
        try {
            entry.manifest.fileNames.forEach { name -> placed += receiveOne(entry, name) }
            complete = true
        } finally {
            if (!complete) withContext(NonCancellable) { placed.forEach { fileSink.discard(it) } }
        }
        if (placed.isEmpty()) throw DriveTransferFailure("no file of packet ${entry.packetId} could be stored")
        return CrossDeviceReceiveOutcome(entry.packetId, placed, settingsApplied = false)
    }

    private suspend fun receiveOne(entry: CrossDevicePacketEntry, name: String): String {
        val described = entry.manifest.files.firstOrNull { it.name == name }
        val result = repository.openPayload(entry, name).getOrThrow().use { stream ->
            fileSink.receive(name, described?.size, described?.sha256, stream)
        }
        return when (result) {
            is IncomingFileResult.Placed -> result.path
            is IncomingFileResult.Rejected -> throw PacketIntegrityFailure(name)
        }
    }

    companion object {
        /** `targetDeviceId` decides when present; the name is the v1 fallback (contract item CN). */
        fun isTargetedAt(manifest: CrossDevicePacketManifest, self: LocalDeviceIdentity): Boolean =
            manifest.targetDeviceId?.let { it == self.deviceId }
                ?: (manifest.targetDeviceName != null && manifest.targetDeviceName == self.displayName)
    }
}
