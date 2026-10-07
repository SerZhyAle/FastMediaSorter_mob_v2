package com.sza.fastmediasorter.data.cloud

import com.sza.fastmediasorter.data.cloud.helpers.GoogleDriveAppDataApi
import com.sza.fastmediasorter.data.remote.sftp.anywhere.SftpRendezvousCodec
import com.sza.fastmediasorter.data.remote.sftp.anywhere.SftpRendezvousCodec.fileName
import com.sza.fastmediasorter.domain.model.SftpRendezvousDevice
import com.sza.fastmediasorter.domain.model.SftpRendezvousRecord
import com.sza.fastmediasorter.domain.model.SftpRendezvousRequest
import com.sza.fastmediasorter.domain.model.SftpRendezvousResource
import com.sza.fastmediasorter.domain.model.SftpShareId
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The Drive channel of contract DEVICE-EXCHANGE section 8: one `fmsx-*.json` file per record, directly in
 * the app's private `appDataFolder`, where every product of the family looks for them. Only apps of the
 * same Google Cloud project on the same account can read the space, which is the whole access model.
 *
 * Every call answers with a [Result]; no Drive or network failure is thrown, so a caller on the
 * endpoint-resolution path can treat the channel as optional.
 */
@Singleton
class GoogleDriveSftpRendezvousDataSource @Inject constructor(
    private val appData: GoogleDriveAppDataApi,
) {

    /** One file: its Drive id, name and `modifiedTime` (0 when the listing gave none), and its record. */
    data class Entry<T : SftpRendezvousRecord>(
        val fileId: String,
        val name: String,
        val modifiedMs: Long,
        val record: T,
    )

    /** Everything the space holds right now, decoded; a file nobody here can read is left out. */
    data class Snapshot(
        val devices: List<Entry<SftpRendezvousDevice>>,
        val resources: List<Entry<SftpRendezvousResource>>,
        val requests: List<Entry<SftpRendezvousRequest>>,
    )

    /** Writes [device] over this device's previous device record. */
    suspend fun writeDevice(device: SftpRendezvousDevice): Result<Unit> =
        upload(fileName(SftpRendezvousCodec.TYPE_DEVICE, device.deviceId), device).map { }

    /** Writes [resource] over the previous record of the same resource. */
    suspend fun writeResource(resource: SftpRendezvousResource): Result<Unit> =
        upload(fileName(SftpRendezvousCodec.TYPE_RESOURCE, resource.resourceId), resource).map { }

    /** Leaves [request] under a fresh request id and answers the new file's Drive id. */
    suspend fun writeRequest(request: SftpRendezvousRequest): Result<String> =
        upload(fileName(SftpRendezvousCodec.TYPE_REQUEST, SftpShareId.generate()), request)

    /** Deletes the record of resource [resourceId]; an absent one is already gone. */
    suspend fun deleteResource(resourceId: String): Result<Unit> =
        when (val found = appData.findEntry(fileName(SftpRendezvousCodec.TYPE_RESOURCE, resourceId), ROOT)) {
            is CloudResult.Error -> failure(found.message)
            is CloudResult.Success -> found.data?.let { delete(it.id) } ?: Result.success(Unit)
        }

    /**
     * Lists the space and decodes every record. With [requestsOnly] the device and resource files are not
     * downloaded - the producer's poll pays one list call plus one read per pending request.
     */
    suspend fun snapshot(requestsOnly: Boolean = false): Result<Snapshot> =
        when (val listed = appData.listChildren(ROOT)) {
            is CloudResult.Error -> failure(listed.message)
            is CloudResult.Success -> Result.success(decode(listed.data.filterNot { it.isFolder }, requestsOnly))
        }

    suspend fun delete(fileId: String): Result<Unit> =
        when (val deleted = appData.deleteEntry(fileId)) {
            is CloudResult.Error -> failure(deleted.message)
            is CloudResult.Success -> Result.success(Unit)
        }

    private suspend fun upload(fileName: String, record: SftpRendezvousRecord): Result<String> =
        when (
            val uploaded = appData.uploadFile(
                fileName = fileName,
                parentFolderId = ROOT,
                mimeType = MIME_TYPE_JSON,
                content = SftpRendezvousCodec.encode(record).toByteArray(Charsets.UTF_8),
            )
        ) {
            is CloudResult.Error -> failure(uploaded.message)
            is CloudResult.Success -> Result.success(uploaded.data.id)
        }

    private suspend fun decode(files: List<CloudFile>, requestsOnly: Boolean): Snapshot {
        val wanted = files.filter { file ->
            file.name.endsWith(JSON_SUFFIX) &&
                (file.name.startsWith(requestPrefix) || (!requestsOnly && isDirectoryFile(file.name)))
        }
        val entries = wanted.mapNotNull { file ->
            read(file)?.let(SftpRendezvousCodec::decode)?.let { Entry(file.id, file.name, file.modifiedDate, it) }
        }
        return Snapshot(entries.typed(), entries.typed(), entries.typed())
    }

    private fun isDirectoryFile(name: String) = name.startsWith(devicePrefix) || name.startsWith(resourcePrefix)

    private inline fun <reified T : SftpRendezvousRecord> List<Entry<SftpRendezvousRecord>>.typed() =
        mapNotNull { entry -> (entry.record as? T)?.let { Entry(entry.fileId, entry.name, entry.modifiedMs, it) } }

    private suspend fun read(file: CloudFile): String? =
        (appData.downloadFile(file.id) as? CloudResult.Success)?.data
            ?.takeIf { it.size <= SftpRendezvousCodec.MAX_RECORD_BYTES }
            ?.toString(Charsets.UTF_8)

    private fun <T> failure(message: String): Result<T> = Result.failure(IOException(message))

    companion object {
        private const val ROOT = GoogleDriveAppDataApi.APP_DATA_FOLDER_ALIAS
        private val devicePrefix = SftpRendezvousCodec.filePrefix(SftpRendezvousCodec.TYPE_DEVICE)
        private val resourcePrefix = SftpRendezvousCodec.filePrefix(SftpRendezvousCodec.TYPE_RESOURCE)
        private val requestPrefix = SftpRendezvousCodec.filePrefix(SftpRendezvousCodec.TYPE_REQUEST)
        private const val JSON_SUFFIX = ".json"
        private const val MIME_TYPE_JSON = "application/json"
    }
}
