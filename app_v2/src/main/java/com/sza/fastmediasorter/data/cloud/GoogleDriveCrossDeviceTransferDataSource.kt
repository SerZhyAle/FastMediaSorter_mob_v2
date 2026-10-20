package com.sza.fastmediasorter.data.cloud

import com.sza.fastmediasorter.data.cloud.helpers.DriveAppDataEntry
import com.sza.fastmediasorter.data.cloud.helpers.DriveAppDataFailure
import com.sza.fastmediasorter.data.cloud.helpers.DriveAppDataListing
import com.sza.fastmediasorter.data.cloud.helpers.DriveStorageQuota
import com.sza.fastmediasorter.data.cloud.helpers.GoogleDriveAppDataApi
import com.sza.fastmediasorter.domain.model.transfer.CrossDevicePacketManifest
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S3040: the `cross_device_transfer` queue inside the app's private Google Drive `appDataFolder`.
 *
 * One packet is one folder named after its `packetId`, holding its payload files beside a
 * `manifest.json`. A folder per packet is what makes "accept and delete" a single delete call
 * instead of a per-file sweep that can half-succeed.
 *
 * S4231: every call answers `Result` with a [DriveAppDataFailure] on failure, so the layers above can
 * tell a rate limit from the daily limit from a lost sign-in.
 */
@Singleton
class GoogleDriveCrossDeviceTransferDataSource @Inject constructor(
    private val appData: GoogleDriveAppDataApi
) {

    /** Find or create the queue folder itself; every other call needs its id. */
    suspend fun ensureQueueFolder(): Result<String> =
        idOf(appData.ensureFolder(QUEUE_FOLDER_NAME))

    /** Find or create the folder holding [packetId]. */
    suspend fun ensurePacketFolder(queueFolderId: String, packetId: String): Result<String> =
        idOf(appData.ensureFolder(packetId, queueFolderId))

    /** Every entry of the queue folder with Drive's timestamps, and Drive's clock. */
    suspend fun listQueue(queueFolderId: String): Result<DriveAppDataListing> =
        appData.listChildrenDetailed(queueFolderId)

    /** The payload files and manifest inside one packet folder. */
    suspend fun listPacketFiles(packetFolderId: String): Result<List<DriveAppDataEntry>> =
        appData.listChildrenDetailed(packetFolderId).map { it.entries }

    /** Stream one payload file into [packetFolderId]. */
    suspend fun uploadPayload(
        packetFolderId: String,
        fileName: String,
        mimeType: String,
        size: Long,
        source: InputStream
    ): Result<Unit> = appData.uploadStream(fileName, packetFolderId, mimeType, size, source).map { }

    /** Write the manifest that makes the packet visible to the other devices. */
    suspend fun writeManifest(packetFolderId: String, manifest: CrossDevicePacketManifest): Result<Unit> {
        val bytes = CrossDevicePacketManifest.toJson(manifest).toByteArray()
        return appData.uploadStream(
            fileName = CrossDevicePacketManifest.MANIFEST_FILE_NAME,
            parentFolderId = packetFolderId,
            mimeType = MANIFEST_MIME_TYPE,
            size = bytes.size.toLong(),
            source = bytes.inputStream()
        ).map { }
    }

    /**
     * Read the manifest among [files] of one packet folder.
     *
     * `null` covers both a packet whose manifest was not uploaded yet and one written by a build
     * this one cannot read - neither is an error the user can act on, and both are skipped by the
     * queue rather than surfaced.
     */
    suspend fun readManifest(files: List<DriveAppDataEntry>): Result<CrossDevicePacketManifest?> {
        val manifest = files.firstOrNull { it.name == CrossDevicePacketManifest.MANIFEST_FILE_NAME }
            ?: return Result.success(null)
        return openFile(manifest.id).mapCatching { stream ->
            CrossDevicePacketManifest.fromJson(stream.use { it.readBytes().decodeToString() })
        }
    }

    /** Open one Drive file of a packet as a stream; the caller closes it. */
    suspend fun openFile(fileId: String): Result<InputStream> = appData.openDownload(fileId)

    /** Delete a whole packet folder, payload and manifest together. */
    suspend fun deletePacketFolder(packetFolderId: String): Result<Unit> =
        when (val deleted = appData.deleteEntry(packetFolderId)) {
            is CloudResult.Error -> Result.failure(failureOf(deleted))
            is CloudResult.Success -> Result.success(Unit)
        }

    suspend fun readStorageQuota(): Result<DriveStorageQuota> = appData.readStorageQuota()

    private fun idOf(result: CloudResult<CloudFile>): Result<String> =
        when (result) {
            is CloudResult.Error -> Result.failure(failureOf(result))
            is CloudResult.Success -> Result.success(result.data.id)
        }

    private fun failureOf(error: CloudResult.Error): DriveAppDataFailure =
        error.cause as? DriveAppDataFailure ?: DriveAppDataFailure.Network(error.cause)

    companion object {
        const val QUEUE_FOLDER_NAME = "cross_device_transfer"
        private const val MANIFEST_MIME_TYPE = "application/json"
    }
}
