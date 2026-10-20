package com.sza.fastmediasorter.data.transfer.drive

import com.sza.fastmediasorter.core.util.rethrowIfCancellation
import com.sza.fastmediasorter.domain.model.transfer.ClipKind
import com.sza.fastmediasorter.domain.model.transfer.CrossDevicePacketEntry
import com.sza.fastmediasorter.domain.model.transfer.CrossDevicePacketExpiry
import com.sza.fastmediasorter.domain.model.transfer.CrossDevicePacketManifest
import com.sza.fastmediasorter.domain.model.transfer.CrossDevicePayloadKind
import com.sza.fastmediasorter.domain.model.transfer.CrossDeviceQueueSnapshot
import com.sza.fastmediasorter.domain.model.transfer.DriveChannelOff
import com.sza.fastmediasorter.domain.model.transfer.OutgoingPacketFile
import com.sza.fastmediasorter.domain.port.IncomingFileResult
import com.sza.fastmediasorter.domain.port.IncomingTransferFileSink
import com.sza.fastmediasorter.domain.port.LocalDeviceIdentitySource
import com.sza.fastmediasorter.domain.transfer.CrossDeviceTransferRepository
import com.sza.fastmediasorter.domain.transfer.DriveChannelSettings
import com.sza.fastmediasorter.domain.transfer.DriveClipboardValue
import kotlinx.coroutines.flow.first
import java.io.InputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S4231: the clipboard via Google Drive (contract DEVICE-EXCHANGE item CP) - one untargeted `CLIPBOARD`
 * packet is the account's value, the one with the latest Drive `createdTime`, and it never expires.
 * Drive's clock decides "latest", so phones with different clocks agree on the value.
 */
@Singleton
class DriveClipboardChannel @Inject constructor(
    private val repository: CrossDeviceTransferRepository,
    private val settings: DriveChannelSettings,
    private val identity: LocalDeviceIdentitySource,
    private val sink: IncomingTransferFileSink,
) {

    suspend fun put(kind: ClipKind, name: String, mimeType: String, size: Long, open: () -> InputStream): Result<Unit> {
        if (!isOn()) return Result.failure(DriveChannelOff())
        val self = identity.current()
        val fileName = if (kind == ClipKind.TEXT) TEXT_FILE_NAME else name
        val manifest = CrossDevicePacketManifest(
            packetId = UUID.randomUUID().toString().replace("-", ""),
            createdAtEpochMs = System.currentTimeMillis(),
            senderDeviceName = self.displayName,
            targetDeviceName = null,
            payloadKind = CrossDevicePayloadKind.CLIPBOARD,
            fileNames = listOf(fileName),
            totalSizeBytes = size,
            schemaVersion = CrossDevicePacketManifest.SCHEMA_V2,
            senderDeviceId = self.deviceId
        )
        val file = OutgoingPacketFile(fileName, size, mimeType, kind, open)
        return repository.publishPacket(manifest, listOf(file)).map { written ->
            // The sender removes the older values it can see once its own manifest landed.
            repository.listQueue().getOrNull()?.let { snapshot -> deleteOlderThan(snapshot, written.packetId) }
            Unit
        }
    }

    suspend fun latest(): Result<DriveClipboardValue?> =
        if (!isOn()) {
            Result.failure(DriveChannelOff())
        } else {
            repository.listQueue().map { snapshot ->
                valueOf(snapshot)?.let { DriveClipboardValue(it, it.manifest.files.firstOrNull()?.clipKind) }
            }
        }

    /** Take the value's one file through the integrity-checking sink; the value itself is never deleted. */
    suspend fun receive(value: DriveClipboardValue): Result<IncomingFileResult> = runCatching {
        val name = value.entry.manifest.fileNames.single()
        val described = value.entry.manifest.files.firstOrNull { it.name == name }
        repository.openPayload(value.entry, name).getOrThrow().use { stream ->
            sink.receive(name, described?.size, described?.sha256, stream)
        }
    }.onFailure { it.rethrowIfCancellation() }

    /**
     * Delete the clipboard packets that are not the value and were created more than 10 minutes before it,
     * so a read in progress is not cut (item CP). Run on every poll by any device with the channel on.
     */
    suspend fun cleanup(snapshot: CrossDeviceQueueSnapshot) {
        val value = valueOf(snapshot) ?: return
        clipboards(snapshot)
            .filter { it.packetId != value.packetId }
            .filter { value.createdTimeMs - it.createdTimeMs > CrossDevicePacketExpiry.CLIPBOARD_GRACE_MS }
            .forEach { repository.deletePacket(it) }
    }

    private suspend fun deleteOlderThan(snapshot: CrossDeviceQueueSnapshot, ownPacketId: String) {
        val own = clipboards(snapshot).firstOrNull { it.packetId == ownPacketId } ?: return
        clipboards(snapshot)
            .filter { it.packetId != ownPacketId && it.createdTimeMs < own.createdTimeMs }
            .forEach { repository.deletePacket(it) }
    }

    private suspend fun isOn(): Boolean = settings.enabled.first() && settings.clipboardEnabled.first()

    companion object {
        const val TEXT_FILE_NAME = "clipboard.txt"

        private fun clipboards(snapshot: CrossDeviceQueueSnapshot): List<CrossDevicePacketEntry> =
            snapshot.entries.filter { it.manifest.payloadKind == CrossDevicePayloadKind.CLIPBOARD }

        /** The committed clipboard packet with the latest Drive `createdTime`. */
        fun valueOf(snapshot: CrossDeviceQueueSnapshot): CrossDevicePacketEntry? =
            clipboards(snapshot).maxByOrNull { it.createdTimeMs }
    }
}
