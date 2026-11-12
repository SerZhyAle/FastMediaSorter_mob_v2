package com.sza.fastmediasorter.data.cloud.helpers

import com.sza.fastmediasorter.data.cloud.TransferProgress
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream

/**
 * S4554: picks the Drive upload protocol by size for the visible Drive.
 *
 * A body above 5 MiB goes through the resumable protocol - one chunk in memory, an interrupted chunk
 * resumed where Drive stopped - which INBOX-PACKET 2.2 and DEVICE-EXCHANGE item CN require of a large
 * payload; a smaller or unknown-size body keeps the single multipart request.
 */
internal object GoogleDriveUploadRouter {

    const val RESUMABLE_THRESHOLD_BYTES: Long = 5L * 1024L * 1024L

    suspend fun upload(
        driveUploadBase: String,
        token: String,
        target: DriveUploadTarget,
        inputStream: InputStream,
        progressCallback: ((TransferProgress) -> Unit)?,
    ): JSONObject? {
        if (target.fileSize <= RESUMABLE_THRESHOLD_BYTES) {
            return GoogleDriveMultipartUploader.upload(driveUploadBase, token, target, inputStream, progressCallback)
        }
        val metadata = JSONObject().put("name", target.fileName)
        target.parentFolderId?.let { metadata.put("parents", JSONArray().put(it)) }
        val session = GoogleDriveResumableUploader.initiate(
            uploadBase = driveUploadBase,
            token = token,
            metadata = metadata,
            size = target.fileSize,
            mimeType = target.mimeType
        )
        return GoogleDriveResumableUploader.upload(session, token, inputStream, target.fileSize) { sent ->
            progressCallback?.invoke(TransferProgress(sent, target.fileSize))
        }
    }
}
