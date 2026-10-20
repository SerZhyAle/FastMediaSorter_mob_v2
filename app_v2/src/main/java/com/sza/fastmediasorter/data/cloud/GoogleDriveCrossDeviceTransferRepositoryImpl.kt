package com.sza.fastmediasorter.data.cloud

import com.sza.fastmediasorter.core.util.rethrowIfCancellation
import com.sza.fastmediasorter.data.cloud.helpers.DriveAppDataEntry
import com.sza.fastmediasorter.data.cloud.helpers.DriveAppDataFailure
import com.sza.fastmediasorter.domain.model.transfer.CrossDevicePacketEntry
import com.sza.fastmediasorter.domain.model.transfer.CrossDevicePacketExpiry
import com.sza.fastmediasorter.domain.model.transfer.CrossDevicePacketFile
import com.sza.fastmediasorter.domain.model.transfer.CrossDevicePacketManifest
import com.sza.fastmediasorter.domain.model.transfer.CrossDeviceQueueSnapshot
import com.sza.fastmediasorter.domain.model.transfer.DriveDailyLimit
import com.sza.fastmediasorter.domain.model.transfer.DrivePacketNames
import com.sza.fastmediasorter.domain.model.transfer.DriveQueueFull
import com.sza.fastmediasorter.domain.model.transfer.DriveQuota
import com.sza.fastmediasorter.domain.model.transfer.DriveSignInRequired
import com.sza.fastmediasorter.domain.model.transfer.DriveTemporarilyUnavailable
import com.sza.fastmediasorter.domain.model.transfer.DriveTransferFailure
import com.sza.fastmediasorter.domain.model.transfer.OutgoingPacketFile
import com.sza.fastmediasorter.domain.model.transfer.PacketNotInQueue
import com.sza.fastmediasorter.domain.transfer.CrossDeviceTransferRepository
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S3040: the cross-device packet queue on top of the Drive AppData data source.
 *
 * A packet the queue cannot read - no manifest yet, or one from a newer build - is skipped rather
 * than reported: the receiver's list must stay usable while a sender is still uploading.
 *
 * S4231: a manifest never changes once written (receivers no longer rewrite it), so it is read once per
 * packet folder and cached for later listings; the poll then costs one listing call plus the new packets.
 */
@Singleton
class GoogleDriveCrossDeviceTransferRepositoryImpl @Inject constructor(
    private val dataSource: GoogleDriveCrossDeviceTransferDataSource
) : CrossDeviceTransferRepository {

    private val manifestCache = ConcurrentHashMap<String, CrossDevicePacketManifest>()

    override suspend fun publishPacket(
        manifest: CrossDevicePacketManifest,
        files: List<OutgoingPacketFile>,
        onProgress: ((Long) -> Unit)?
    ): Result<CrossDevicePacketManifest> = guarded {
        val queueFolderId = dataSource.ensureQueueFolder().getOrThrow()
        val packetFolderId = dataSource.ensurePacketFolder(queueFolderId, manifest.packetId).getOrThrow()
        var committed = false
        try {
            val described = uploadAll(packetFolderId, files, onProgress)
            val written = manifest.copy(
                fileNames = described.map { it.name },
                files = described,
                totalSizeBytes = described.sumOf { it.size }
            )
            // Manifest last: until it lands the packet is invisible to every reader.
            dataSource.writeManifest(packetFolderId, written).getOrThrow()
            committed = true
            written
        } finally {
            // A sender whose upload failed or was cancelled deletes its own folder at once (item CR).
            if (!committed) withContext(NonCancellable) { dataSource.deletePacketFolder(packetFolderId) }
        }
    }

    override suspend fun listQueue(): Result<CrossDeviceQueueSnapshot> = guarded {
        val queueFolderId = dataSource.ensureQueueFolder().getOrThrow()
        val listing = dataSource.listQueue(queueFolderId).getOrThrow()
        val folders = listing.entries.filter { it.isFolder && DrivePacketNames.isSafePacketId(it.name) }
        manifestCache.keys.retainAll(folders.mapTo(HashSet()) { it.id })
        val entries = mutableListOf<CrossDevicePacketEntry>()
        val abandoned = mutableListOf<String>()
        folders.forEach { folder ->
            when (val read = manifestOf(folder)) {
                is ManifestRead.Found ->
                    entries += CrossDevicePacketEntry(read.manifest, folder.id, folder.createdTimeMs)
                ManifestRead.Absent ->
                    if (CrossDevicePacketExpiry.isAbandonedUpload(folder.createdTimeMs, listing.serverDateMs)) {
                        abandoned += folder.id
                    }
                ManifestRead.Unreadable -> Unit
            }
        }
        CrossDeviceQueueSnapshot(entries, abandoned, listing.serverDateMs)
    }

    override suspend fun openPayload(entry: CrossDevicePacketEntry, fileName: String): Result<InputStream> = guarded {
        if (fileName !in entry.manifest.fileNames) throw DriveTransferFailure("$fileName is not in ${entry.packetId}")
        val file = dataSource.listPacketFiles(entry.folderId).getOrThrow().firstOrNull { it.name == fileName }
            ?: throw PacketNotInQueue(entry.packetId)
        dataSource.openFile(file.id).getOrThrow()
    }

    override suspend fun deletePacket(entry: CrossDevicePacketEntry): Result<Unit> = guarded {
        dataSource.deletePacketFolder(entry.folderId).getOrThrow()
        manifestCache.remove(entry.folderId)
        Unit
    }

    override suspend fun purgeExpired(snapshot: CrossDeviceQueueSnapshot): Result<Int> = guarded {
        val expired = snapshot.entries
            .filter { CrossDevicePacketExpiry.isExpired(it, snapshot.serverNowMs) }
            .map { it.folderId }
        (expired + snapshot.abandonedFolderIds).count { folderId ->
            manifestCache.remove(folderId)
            dataSource.deletePacketFolder(folderId).isSuccess
        }
    }

    override suspend fun readStorageQuota(): Result<DriveQuota> = guarded {
        val quota = dataSource.readStorageQuota().getOrThrow()
        DriveQuota(limitBytes = quota.limitBytes, usageBytes = quota.usageBytes)
    }

    private suspend fun uploadAll(
        packetFolderId: String,
        files: List<OutgoingPacketFile>,
        onProgress: ((Long) -> Unit)?
    ): List<CrossDevicePacketFile> {
        var sentBefore = 0L
        return files.map { file ->
            val hashing = HashingInputStream(file.open())
            hashing.use { stream ->
                dataSource.uploadPayload(packetFolderId, file.name, file.mimeType, file.size, stream).getOrThrow()
            }
            if (hashing.count != file.size) throw IOException("${file.name} changed size while it was sent")
            sentBefore += file.size
            onProgress?.invoke(sentBefore)
            CrossDevicePacketFile(file.name, file.size, hashing.hexDigest(), file.mimeType, file.clipKind)
        }
    }

    private suspend fun manifestOf(folder: DriveAppDataEntry): ManifestRead {
        val cached = manifestCache[folder.id]
        return if (cached != null) ManifestRead.Found(cached) else readManifestOf(folder)
    }

    private suspend fun readManifestOf(folder: DriveAppDataEntry): ManifestRead {
        val files = dataSource.listPacketFiles(folder.id)
            .onFailure { it.rethrowIfCancellation() }
            .getOrElse { error ->
                Timber.w("Cross-device queue: packet %s not listed (%s)", folder.name, error.javaClass.simpleName)
                return ManifestRead.Unreadable
            }
        val hasManifest = files.any { it.name == CrossDevicePacketManifest.MANIFEST_FILE_NAME }
        val manifest = if (hasManifest) {
            dataSource.readManifest(files).onFailure { it.rethrowIfCancellation() }.getOrNull()
                ?.takeIf { it.packetId == folder.name }
                ?.also { manifestCache[folder.id] = it }
        } else {
            null
        }
        return when {
            manifest != null -> ManifestRead.Found(manifest)
            hasManifest -> ManifestRead.Unreadable
            else -> ManifestRead.Absent
        }
    }

    private sealed interface ManifestRead {
        data class Found(val manifest: CrossDevicePacketManifest) : ManifestRead

        data object Absent : ManifestRead

        data object Unreadable : ManifestRead
    }

    /** Counts and hashes the bytes the upload actually read, so the manifest describes what Drive holds. */
    private class HashingInputStream(source: InputStream) : FilterInputStream(source) {
        private val digest = MessageDigest.getInstance("SHA-256")
        var count = 0L
            private set

        override fun read(): Int = super.read().also { byte ->
            if (byte >= 0) {
                digest.update(byte.toByte())
                count++
            }
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
            super.read(buffer, offset, length).also { read ->
                if (read > 0) {
                    digest.update(buffer, offset, read)
                    count += read
                }
            }

        fun hexDigest(): String = digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * `runCatching` with the one exception a coroutine must never absorb, and Drive's failure classes
     * mapped to the domain's.
     *
     * Every call here is a cloud round trip on a screen the user can leave: a plain `runCatching`
     * would turn the resulting cancellation into `Result.failure` and the UI would report a failed
     * transfer for a screen that is already gone.
     */
    private suspend fun <T> guarded(block: suspend () -> T): Result<T> =
        runCatching { block() }
            .onFailure { it.rethrowIfCancellation() }
            .fold(onSuccess = { Result.success(it) }, onFailure = { Result.failure(toDomain(it)) })

    private fun toDomain(error: Throwable): Throwable = when (error) {
        is DriveAppDataFailure.AuthRequired -> DriveSignInRequired()
        is DriveAppDataFailure.DailyLimit -> DriveDailyLimit()
        is DriveAppDataFailure.StorageFull -> DriveQueueFull(freeBytes = null)
        is DriveAppDataFailure ->
            if (error.isTransient) DriveTemporarilyUnavailable(error) else DriveTransferFailure(error.message.orEmpty())
        else -> error
    }
}
