package com.sza.fastmediasorter.domain.usecase.transfer

import com.sza.fastmediasorter.domain.model.transfer.CrossDevicePacketManifest
import com.sza.fastmediasorter.domain.model.transfer.CrossDevicePayloadKind
import com.sza.fastmediasorter.domain.model.transfer.CrossDeviceReceiveOutcome
import com.sza.fastmediasorter.domain.model.transfer.CrossDeviceTransferOption
import com.sza.fastmediasorter.domain.model.transfer.DriveTransferFailure
import com.sza.fastmediasorter.domain.model.transfer.TransferDataKind
import com.sza.fastmediasorter.domain.port.IncomingTransferFileSink
import com.sza.fastmediasorter.domain.transfer.CrossDeviceTransferRepository
import javax.inject.Inject

/**
 * S3040: download an offered packet, apply it, then claim it.
 *
 * The claim happens last on purpose: a packet deleted before its payload is applied cannot be
 * retried, and the user would be left with neither the file nor the offer.
 */
class ReceiveCrossDevicePacketUseCase @Inject constructor(
    private val repository: CrossDeviceTransferRepository,
    private val applyTransferPayload: ApplyTransferPayloadUseCase,
    private val fileSink: IncomingTransferFileSink
) {

    suspend operator fun invoke(
        manifest: CrossDevicePacketManifest,
        option: CrossDeviceTransferOption
    ): Result<CrossDeviceReceiveOutcome> = runCatching {
        val outcome = when (manifest.payloadKind) {
            CrossDevicePayloadKind.SETTINGS -> applySettings(manifest)
            CrossDevicePayloadKind.MEDIA_FILES -> writeFiles(manifest)
        }
        repository.claimPacket(
            packetId = manifest.packetId,
            deleteFromCloud = option == CrossDeviceTransferOption.ACCEPT_AND_DELETE
        ).getOrThrow()
        outcome
    }

    private suspend fun applySettings(manifest: CrossDevicePacketManifest): CrossDeviceReceiveOutcome {
        val fileName = manifest.fileNames.firstOrNull()
            ?: throw DriveTransferFailure("packet ${manifest.packetId} declares no settings file")
        val bytes = repository.fetchPayload(manifest.packetId, fileName).getOrThrow()
        applyTransferPayload(TransferDataKind.SETTINGS, bytes).getOrThrow()
        return CrossDeviceReceiveOutcome(manifest.packetId, emptyList(), settingsApplied = true)
    }

    private suspend fun writeFiles(manifest: CrossDevicePacketManifest): CrossDeviceReceiveOutcome {
        val written = manifest.fileNames.mapNotNull { fileName ->
            val bytes = repository.fetchPayload(manifest.packetId, fileName).getOrThrow()
            fileSink.write(fileName, bytes)
        }
        if (written.isEmpty()) {
            throw DriveTransferFailure("no file of packet ${manifest.packetId} could be stored")
        }
        return CrossDeviceReceiveOutcome(manifest.packetId, written, settingsApplied = false)
    }
}
