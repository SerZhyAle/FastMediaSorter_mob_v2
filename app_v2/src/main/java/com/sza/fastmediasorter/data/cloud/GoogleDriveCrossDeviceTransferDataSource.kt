package com.sza.fastmediasorter.data.cloud

import com.sza.fastmediasorter.data.cloud.helpers.GoogleDriveAppDataApi
import com.sza.fastmediasorter.domain.model.transfer.CrossDevicePacketManifest
import com.sza.fastmediasorter.domain.model.transfer.DriveTransferFailure
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S3040: the `cross_device_transfer` queue inside the app's private Google Drive `appDataFolder`.
 *
 * One packet is one folder named after its `packetId`, holding its payload files beside a
 * `manifest.json`. A folder per packet is what makes "accept and delete" a single delete call
 * instead of a per-file sweep that can half-succeed.
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

    /** Every packet folder currently in the queue, newest ordering left to the caller. */
    suspend fun listPacketFolders(queueFolderId: String): Result<List<CloudFile>> =
        when (val listed = appData.listChildren(queueFolderId)) {
            is CloudResult.Error -> Result.failure(DriveTransferFailure(listed.message))
            is CloudResult.Success -> Result.success(listed.data.filter { it.isFolder })
        }

    /** Write one payload file into [packetFolderId]. */
    suspend fun uploadPayloadFile(
        packetFolderId: String,
        fileName: String,
        content: ByteArray
    ): Result<Unit> =
        unitOf(appData.uploadFile(fileName, packetFolderId, PAYLOAD_MIME_TYPE, content))

    /** Write the manifest that makes the packet visible to the other devices. */
    suspend fun writeManifest(packetFolderId: String, manifest: CrossDevicePacketManifest): Result<Unit> =
        unitOf(
            appData.uploadFile(
                fileName = CrossDevicePacketManifest.MANIFEST_FILE_NAME,
                parentFolderId = packetFolderId,
                mimeType = MANIFEST_MIME_TYPE,
                content = CrossDevicePacketManifest.toJson(manifest).toByteArray()
            )
        )

    /**
     * Read the manifest of [packetFolderId].
     *
     * `null` covers both a packet whose manifest was not uploaded yet and one written by a build
     * this one cannot read - neither is an error the user can act on, and both are skipped by the
     * queue rather than surfaced.
     */
    suspend fun readManifest(packetFolderId: String): Result<CrossDevicePacketManifest?> =
        downloadPayloadFile(packetFolderId, CrossDevicePacketManifest.MANIFEST_FILE_NAME)
            .map { CrossDevicePacketManifest.fromJson(it.decodeToString()) }

    /** Read one payload file of [packetFolderId] by its manifest-declared name. */
    suspend fun downloadPayloadFile(packetFolderId: String, fileName: String): Result<ByteArray> =
        when (val found = appData.findEntry(fileName, packetFolderId)) {
            is CloudResult.Error -> Result.failure(DriveTransferFailure(found.message))
            is CloudResult.Success ->
                found.data
                    ?.let { bytesOf(appData.downloadFile(it.id)) }
                    ?: Result.failure(DriveTransferFailure("$fileName is not in packet $packetFolderId"))
        }

    /** Delete a whole packet folder, payload and manifest together. */
    suspend fun deletePacketFolder(packetFolderId: String): Result<Unit> =
        when (val deleted = appData.deleteEntry(packetFolderId)) {
            is CloudResult.Error -> Result.failure(DriveTransferFailure(deleted.message))
            is CloudResult.Success -> Result.success(Unit)
        }

    private fun idOf(result: CloudResult<CloudFile>): Result<String> =
        when (result) {
            is CloudResult.Error -> Result.failure(DriveTransferFailure(result.message))
            is CloudResult.Success -> Result.success(result.data.id)
        }

    private fun unitOf(result: CloudResult<CloudFile>): Result<Unit> =
        when (result) {
            is CloudResult.Error -> Result.failure(DriveTransferFailure(result.message))
            is CloudResult.Success -> Result.success(Unit)
        }

    private fun bytesOf(result: CloudResult<ByteArray>): Result<ByteArray> =
        when (result) {
            is CloudResult.Error -> Result.failure(DriveTransferFailure(result.message))
            is CloudResult.Success -> Result.success(result.data)
        }

    companion object {
        const val QUEUE_FOLDER_NAME = "cross_device_transfer"
        private const val PAYLOAD_MIME_TYPE = "application/octet-stream"
        private const val MANIFEST_MIME_TYPE = "application/json"
    }
}
