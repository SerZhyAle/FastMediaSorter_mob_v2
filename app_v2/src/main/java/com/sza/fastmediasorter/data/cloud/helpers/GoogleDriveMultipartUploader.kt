package com.sza.fastmediasorter.data.cloud.helpers

import com.sza.fastmediasorter.core.network.HttpTimeouts
import com.sza.fastmediasorter.core.network.applyTimeouts
import com.sza.fastmediasorter.data.cloud.TransferProgress
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/** What a single Drive upload is about to write: the destination identity plus the body size, 0 when unknown. */
internal data class DriveUploadTarget(
    val fileName: String,
    val mimeType: String,
    val parentFolderId: String?,
    val fileSize: Long,
)

/** Drive v3 multipart/related upload (metadata + content) helper. Returns the parsed JSON response body or null on HTTP failure. */
internal object GoogleDriveMultipartUploader {

    private const val COPY_BUFFER_BYTES = 65536

    fun upload(
        driveUploadBase: String,
        token: String,
        target: DriveUploadTarget,
        inputStream: InputStream,
        progressCallback: ((TransferProgress) -> Unit)?,
    ): JSONObject? {
        val fileName = target.fileName
        val fileSize = target.fileSize
        val metadata = JSONObject().apply {
            put("name", fileName)
            target.parentFolderId?.let { put("parents", JSONArray().put(it)) }
        }

        val boundary = "----FastMediaSorterBoundary${System.currentTimeMillis()}"
        // Materialised once so the declared content length and the bytes actually written cannot drift apart.
        val preamble = (
            "--$boundary\r\n" +
                "Content-Type: application/json; charset=UTF-8\r\n\r\n" +
                metadata.toString() +
                "\r\n" +
                "--$boundary\r\n" +
                "Content-Type: ${target.mimeType}\r\n\r\n"
            ).toByteArray()
        val epilogue = "\r\n--$boundary--\r\n".toByteArray()

        val url = URL("$driveUploadBase/files?uploadType=multipart")
        val connection = url.openConnection() as HttpURLConnection
        val declaredLength = if (fileSize > 0L) preamble.size + fileSize + epilogue.size else 0L
        connection.applyTimeouts(HttpTimeouts.uploadReadTimeoutMs(declaredLength))
        connection.requestMethod = "POST"
        connection.setRequestProperty("Authorization", "Bearer $token")
        connection.setRequestProperty("Content-Type", "multipart/related; boundary=$boundary")
        connection.doOutput = true
        // S1361: without a declared length HttpURLConnection buffers the whole body in memory, which
        // risks an OOM on large media and lets the socket buffer absorb the upload so the response
        // read starts - and its budget expires - before the bytes have left the device.
        if (declaredLength > 0L) connection.setFixedLengthStreamingMode(declaredLength)
        return try {
            val outputStream = connection.outputStream
            outputStream.write(preamble)

            val buffer = ByteArray(COPY_BUFFER_BYTES)
            var bytesRead: Int
            var totalBytes = 0L
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
                totalBytes += bytesRead
                progressCallback?.invoke(TransferProgress(totalBytes, fileSize))
            }

            outputStream.write(epilogue)
            outputStream.flush()

            val responseCode = connection.responseCode
            if (responseCode in 200..299) {
                JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            } else {
                connection.errorStream?.bufferedReader()?.use { it.readText() }?.also {
                    Timber.w("Drive upload HTTP $responseCode: $it")
                }
                null
            }
        } finally {
            connection.disconnect()
        }
    }
}
