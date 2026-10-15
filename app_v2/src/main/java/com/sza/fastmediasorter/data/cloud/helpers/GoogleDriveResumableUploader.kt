package com.sza.fastmediasorter.data.cloud.helpers

import com.sza.fastmediasorter.core.network.HttpTimeouts
import com.sza.fastmediasorter.core.network.applyTimeouts
import com.sza.fastmediasorter.core.util.JitteredBackoff
import com.sza.fastmediasorter.core.util.rethrowIfCancellation
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import org.json.JSONObject
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * S4231: Drive's resumable upload from a stream (contract DEVICE-EXCHANGE item CN: a payload above 5 MiB
 * is never sent whole from memory and an interrupted upload may resume).
 *
 * At most one chunk is held in memory. The chunk is kept until Drive confirms it, because Drive may
 * confirm fewer bytes than were sent and the source stream cannot be rewound.
 */
internal object GoogleDriveResumableUploader {

    /** A multiple of 256 KiB, as Drive requires for every chunk but the last. */
    const val CHUNK_BYTES: Int = 32 * 256 * 1024

    private const val HTTP_RESUME_INCOMPLETE = 308
    private const val MAX_TRANSIENT_RETRIES = 6

    /** Open an upload session for [metadata]; returns the session URI. */
    fun initiate(uploadBase: String, token: String, metadata: JSONObject, size: Long, mimeType: String): String {
        val url = URL("$uploadBase/files?uploadType=resumable&fields=$FILE_FIELDS")
        val connection = url.openConnection() as HttpURLConnection
        connection.applyTimeouts()
        connection.requestMethod = "POST"
        connection.setRequestProperty("Authorization", "Bearer $token")
        connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
        connection.setRequestProperty("X-Upload-Content-Type", mimeType)
        connection.setRequestProperty("X-Upload-Content-Length", size.toString())
        connection.doOutput = true
        return try {
            connection.outputStream.bufferedWriter().use { it.write(metadata.toString()) }
            val code = connection.responseCode
            val location = connection.getHeaderField("Location")
            if (code in HTTP_OK_MIN..HTTP_OK_MAX && location != null) {
                location
            } else {
                val body = connection.errorStream?.bufferedReader()?.use { it.readText() }
                throw DriveAppDataFailure.classify(code, body)
            }
        } finally {
            connection.disconnect()
        }
    }

    /** Send [size] bytes of [source] into [sessionUri]; returns the created file's JSON. */
    suspend fun upload(
        sessionUri: String,
        token: String,
        source: InputStream,
        size: Long,
        onProgress: ((Long) -> Unit)? = null,
    ): JSONObject {
        val session = Session(sessionUri, token, size)
        val buffer = ByteArray(minOf(CHUNK_BYTES.toLong(), size.coerceAtLeast(1L)).toInt())
        var offset = 0L
        var result: JSONObject? = if (size == 0L) session.finishEmpty() else null
        while (result == null) {
            currentCoroutineContext().ensureActive()
            val length = readChunk(source, buffer)
            if (length <= 0) throw IOException("source ended at $offset of $size bytes")
            result = session.sendChunk(buffer, length, offset)
            offset += length
            onProgress?.invoke(offset)
        }
        return result
    }

    private fun readChunk(source: InputStream, buffer: ByteArray): Int {
        var filled = 0
        while (filled < buffer.size) {
            val read = source.read(buffer, filled, buffer.size - filled)
            if (read < 0) break
            filled += read
        }
        return filled
    }

    private sealed interface PutOutcome {
        data class Complete(val json: JSONObject) : PutOutcome

        /** Drive holds bytes up to, not including, [confirmed]. */
        data class Incomplete(val confirmed: Long) : PutOutcome
    }

    private class Session(private val sessionUri: String, private val token: String, private val size: Long) {

        private val backoff = JitteredBackoff()

        fun finishEmpty(): JSONObject =
            (put(ByteArray(0), 0, 0, "bytes */0") as? PutOutcome.Complete)?.json
                ?: throw IOException("Drive did not finish an empty upload")

        /** Send one chunk starting at [offset]; returns the file JSON once the last byte is in. */
        suspend fun sendChunk(buffer: ByteArray, length: Int, offset: Long): JSONObject? {
            var sentFrom = 0
            var attempts = 0
            var done: JSONObject? = null
            while (done == null && sentFrom < length) {
                val outcome = runCatching { putRange(buffer, sentFrom, length, offset) }
                    .getOrElse { error -> recover(error, ++attempts) }
                when (outcome) {
                    is PutOutcome.Complete -> done = outcome.json
                    is PutOutcome.Incomplete -> sentFrom = resumeIndex(outcome.confirmed, offset)
                }
            }
            return done
        }

        // Bytes before this chunk are gone from memory, so Drive confirming less than [offset] cannot be repaired.
        private fun resumeIndex(confirmed: Long, offset: Long): Int {
            if (confirmed < offset) throw IOException("Drive dropped bytes before $offset (holds $confirmed)")
            return (confirmed - offset).toInt()
        }

        private fun putRange(buffer: ByteArray, from: Int, length: Int, offset: Long): PutOutcome {
            val start = offset + from
            val end = offset + length - 1
            return put(buffer, from, length - from, "bytes $start-$end/$size")
        }

        // A transient failure waits, then asks Drive how much it holds, so the next PUT resumes there.
        private suspend fun recover(error: Throwable, attempt: Int): PutOutcome {
            error.rethrowIfCancellation()
            val failure = error as? DriveAppDataFailure ?: DriveAppDataFailure.Network(error)
            if (!failure.isTransient || attempt > MAX_TRANSIENT_RETRIES) throw failure
            delay(backoff.nextDelayMs())
            return put(ByteArray(0), 0, 0, "bytes */$size")
        }

        private fun put(buffer: ByteArray, from: Int, count: Int, contentRange: String): PutOutcome {
            val connection = URL(sessionUri).openConnection() as HttpURLConnection
            connection.applyTimeouts(HttpTimeouts.uploadReadTimeoutMs(count.toLong()))
            connection.instanceFollowRedirects = false
            connection.requestMethod = "PUT"
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("Content-Range", contentRange)
            connection.doOutput = true
            connection.setFixedLengthStreamingMode(count)
            return try {
                connection.outputStream.use { it.write(buffer, from, count) }
                outcomeOf(connection)
            } catch (io: IOException) {
                throw io as? DriveAppDataFailure ?: DriveAppDataFailure.Network(io)
            } finally {
                connection.disconnect()
            }
        }

        private fun outcomeOf(connection: HttpURLConnection): PutOutcome {
            val code = connection.responseCode
            return when (code) {
                in HTTP_OK_MIN..HTTP_OK_MAX ->
                    PutOutcome.Complete(JSONObject(connection.inputStream.bufferedReader().use { it.readText() }))
                HTTP_RESUME_INCOMPLETE -> PutOutcome.Incomplete(confirmedFrom(connection.getHeaderField("Range")))
                else -> throw DriveAppDataFailure.classify(
                    code,
                    connection.errorStream?.bufferedReader()?.use { it.readText() }
                )
            }
        }

        // "bytes=0-1048575" -> 1048576; no header means Drive holds nothing yet.
        private fun confirmedFrom(range: String?): Long =
            range?.substringAfterLast('-')?.toLongOrNull()?.plus(1) ?: 0L
    }

    private const val HTTP_OK_MIN = 200
    private const val HTTP_OK_MAX = 299
    private const val FILE_FIELDS = "id,name,mimeType,size,createdTime,modifiedTime,parents"
}
