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

/** Drive v3 multipart/related upload (metadata + content) helper. Returns the parsed JSON response body or null on HTTP failure. */
internal object GoogleDriveMultipartUploader {

    fun upload(
        driveUploadBase: String,
        token: String,
        fileName: String,
        mimeType: String,
        parentFolderId: String?,
        inputStream: InputStream,
        progressCallback: ((TransferProgress) -> Unit)?,
    ): JSONObject? {
        val metadata = JSONObject().apply {
            put("name", fileName)
            if (parentFolderId != null) put("parents", JSONArray().put(parentFolderId))
        }

        val boundary = "----FastMediaSorterBoundary${System.currentTimeMillis()}"
        val url = URL("$driveUploadBase/files?uploadType=multipart")
        val connection = url.openConnection() as HttpURLConnection
        connection.applyTimeouts(HttpTimeouts.STREAM_READ_MS)
        connection.requestMethod = "POST"
        connection.setRequestProperty("Authorization", "Bearer $token")
        connection.setRequestProperty("Content-Type", "multipart/related; boundary=$boundary")
        connection.doOutput = true

        return try {
            val outputStream = connection.outputStream
            outputStream.write("--$boundary\r\n".toByteArray())
            outputStream.write("Content-Type: application/json; charset=UTF-8\r\n\r\n".toByteArray())
            outputStream.write(metadata.toString().toByteArray())
            outputStream.write("\r\n".toByteArray())
            outputStream.write("--$boundary\r\n".toByteArray())
            outputStream.write("Content-Type: $mimeType\r\n\r\n".toByteArray())

            val buffer = ByteArray(65536)
            var bytesRead: Int
            var totalBytes = 0L
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
                totalBytes += bytesRead
                progressCallback?.invoke(TransferProgress(totalBytes, 0L))
            }

            outputStream.write("\r\n--$boundary--\r\n".toByteArray())
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
