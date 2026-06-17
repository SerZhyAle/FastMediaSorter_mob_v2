@file:Suppress("DEPRECATION")

package com.sza.fastmediasorter.data.remote.ftp

import com.sza.fastmediasorter.domain.usecase.ByteProgressCallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPReply
import timber.log.Timber
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.SocketTimeoutException
import java.time.Duration
import java.util.concurrent.atomic.AtomicLong

/**
 * Stateless FTP operations: every call opens a fresh [FTPClient], performs the operation,
 * then disconnects. Used both during AddResource testing (no pooled state to leak) and from
 * the file-operation handlers (each operation runs against an independent connection so
 * thread-safety is not a concern).
 *
 * Extracted to keep FtpClient below the 1000-line cap.
 */
object FtpStandaloneOperations {

    private const val CONNECT_TIMEOUT = 10_000
    private const val SOCKET_TIMEOUT = 30_000
    private const val KEEPALIVE_TIMEOUT = 15L
    private const val PROGRESS_POLL_MS = 100L

    /** Test connection by connecting + login + listing root. */
    suspend fun testConnection(
        host: String,
        port: Int = 21,
        username: String,
        password: String
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val testClient = FTPClient().apply { applyTimeouts() }
            // S0212: encoding MUST be set before connect - see FtpEncodingSupport.
            testClient.applyUtf8Encoding()

            testClient.connect(host, port)

            val replyCode = testClient.replyCode
            if (!FTPReply.isPositiveCompletion(replyCode)) {
                testClient.disconnect()
                return@withContext Result.failure(
                    IOException("FTP server refused connection. Reply code: $replyCode")
                )
            }

            if (!testClient.login(username, password)) {
                testClient.disconnect()
                return@withContext Result.failure(
                    IOException("FTP authentication failed for user: $username")
                )
            }

            testClient.enterLocalPassiveMode()
            // S0212: negotiate UTF-8 filename interpretation on RFC 2640 servers.
            testClient.enableUtf8Mode()
            testClient.listFiles("/")

            testClient.logout()
            testClient.disconnect()

            Timber.d("FTP test connection successful: $host:$port")
            Result.success(true)
        } catch (e: IOException) {
            Timber.e(e, "FTP test connection failed: $host:$port")
            Result.failure(e)
        } catch (e: Exception) {
            Timber.e(e, "FTP test connection error: $host:$port")
            Result.failure(e)
        }
    }

    suspend fun uploadFile(
        host: String,
        port: Int,
        username: String,
        password: String,
        remotePath: String,
        inputStream: InputStream,
        @Suppress("UNUSED_PARAMETER") fileSize: Long = 0L,
        @Suppress("UNUSED_PARAMETER") progressCallback: ByteProgressCallback? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val tempClient = FTPClient().apply { applyTimeouts() }
        // S0212: encoding MUST be set before connect - see FtpEncodingSupport.
        tempClient.applyUtf8Encoding()
        try {
            tempClient.connect(host, port)

            val replyCode = tempClient.replyCode
            if (!FTPReply.isPositiveCompletion(replyCode)) {
                tempClient.disconnect()
                return@withContext Result.failure(
                    IOException("FTP server refused connection. Reply code: $replyCode")
                )
            }

            if (!tempClient.login(username, password)) {
                tempClient.disconnect()
                return@withContext Result.failure(
                    IOException("FTP authentication failed for user: $username")
                )
            }

            tempClient.enterLocalPassiveMode()
            tempClient.setFileType(FTP.BINARY_FILE_TYPE)
            // S0212: negotiate UTF-8 filename interpretation on RFC 2640 servers.
            tempClient.enableUtf8Mode()

            val parentDir = remotePath.substringBeforeLast('/', "")
            if (parentDir.isNotEmpty()) {
                ensureRemoteDirectoryExists(tempClient, parentDir)
            }

            Timber.d("FTP temp connection: uploading $remotePath")

            val success = tempClient.storeFile(remotePath, inputStream)
            if (success) {
                Timber.i("FTP temp connection upload success: $remotePath")
                Result.success(Unit)
            } else {
                val message = "FTP storeFile returned false: $remotePath"
                Timber.e(message)
                Result.failure(IOException(message))
            }
        } catch (e: Exception) {
            Timber.e(e, "FTP temp connection upload failed: $remotePath")
            Result.failure(e)
        } finally {
            tempClient.disconnectQuietly()
        }
    }

    suspend fun deleteFile(
        host: String,
        port: Int,
        username: String,
        password: String,
        remotePath: String
    ): Result<Unit> = executeWithNewConnection(host, port, username, password) { client ->
        Timber.d("FTP temp connection: deleting $remotePath")
        val success = client.deleteFile(remotePath)
        if (success) {
            Timber.i("FTP temp connection delete success: $remotePath")
            Result.success(Unit)
        } else {
            Result.failure(IOException("FTP delete failed: ${client.replyString}"))
        }
    }

    suspend fun renameFile(
        host: String,
        port: Int,
        username: String,
        password: String,
        oldPath: String,
        newName: String
    ): Result<Unit> = executeWithNewConnection(host, port, username, password) { client ->
        val directory = oldPath.substringBeforeLast('/', "")
        val newPath = when {
            directory.isNotEmpty() -> "$directory/$newName"
            oldPath.startsWith("/") -> "/$newName"
            else -> newName
        }

        Timber.d("FTP temp connection: renaming $oldPath → $newPath")
        val success = client.rename(oldPath, newPath)
        if (success) {
            Timber.i("FTP temp connection rename success: $newPath")
            Result.success(Unit)
        } else {
            Result.failure(IOException("FTP rename failed: ${client.replyString}"))
        }
    }

    suspend fun createDirectory(
        host: String,
        port: Int,
        username: String,
        password: String,
        remotePath: String
    ): Result<Unit> = executeWithNewConnection(host, port, username, password) { client ->
        Timber.d("FTP temp connection: creating directory $remotePath")
        val success = client.makeDirectory(remotePath)
        if (success) {
            Timber.i("FTP temp connection create directory success: $remotePath")
            Result.success(Unit)
        } else {
            Result.failure(IOException("FTP create directory failed: ${client.replyString}"))
        }
    }

    suspend fun exists(
        host: String,
        port: Int,
        username: String,
        password: String,
        remotePath: String
    ): Result<Boolean> = executeWithNewConnection(host, port, username, password) { client ->
        try {
            val files = client.listFiles(remotePath)
            Result.success(files != null && files.isNotEmpty())
        } catch (e: Exception) {
            Timber.w(e, "FTP exists check failed for $remotePath")
            Result.success(false)
        }
    }

    suspend fun readFileBytes(
        host: String,
        port: Int,
        username: String,
        password: String,
        remotePath: String,
        maxBytes: Long = Long.MAX_VALUE
    ): Result<ByteArray> = executeWithNewConnection(host, port, username, password) { client ->
        try {
            // S0206: bounded path is split out so readBoundedAndAbort can own ABOR +
            // completePendingCommand. Full-read path (no limit) retains original contract.
            if (maxBytes < Long.MAX_VALUE) {
                val maxBytesInt = maxBytes.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                val stream = client.retrieveFileStream(remotePath)
                    ?: throw SocketTimeoutException("Failed to open stream (force retry)")
                val result = stream.use { readBoundedAndAbort(client, it, maxBytesInt, "standalone.readFileBytes(passive)") }
                Timber.d("FTP standalone bounded read: ${result.bytes.size}b from $remotePath (abort=${result.abortInvoked}, completeOk=${result.completeOk})")
                return@executeWithNewConnection Result.success(result.bytes)
            }
            val bytes = client.retrieveFileStream(remotePath)?.use { stream ->
                stream.readBytes()
            }

            if (bytes != null) {
                if (!safeCompletePendingCommand(client, "standalone.readFileBytes(passive)")) {
                    throw IOException("FTP command failed after retrieving file")
                }
                Result.success(bytes)
            } else {
                throw SocketTimeoutException("Failed to open stream (force retry)")
            }
        } catch (e: SocketTimeoutException) {
            Timber.w(e, "FTP passive mode timeout/error, switching to active mode")
            client.enterLocalActiveMode()
            try {
                // S0206: same bounded/full-read split for active mode fallback.
                if (maxBytes < Long.MAX_VALUE) {
                    val maxBytesInt = maxBytes.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                    val stream = client.retrieveFileStream(remotePath)
                        ?: return@executeWithNewConnection Result.failure(
                            IOException("Failed to open file stream (active mode): $remotePath")
                        )
                    val result = stream.use { readBoundedAndAbort(client, it, maxBytesInt, "standalone.readFileBytes(active)") }
                    Timber.d("FTP standalone bounded read (active): ${result.bytes.size}b from $remotePath (abort=${result.abortInvoked}, completeOk=${result.completeOk})")
                    return@executeWithNewConnection Result.success(result.bytes)
                }
                // Full-read path (no limit).
                val bytes = client.retrieveFileStream(remotePath)?.use { stream ->
                    stream.readBytes()
                } ?: return@executeWithNewConnection Result.failure(
                    IOException("Failed to open file stream (active mode): $remotePath")
                )

                if (!safeCompletePendingCommand(client, "standalone.readFileBytes(active)")) {
                    return@executeWithNewConnection Result.failure(
                        IOException("FTP command failed (active mode)")
                    )
                }
                Result.success(bytes)
            } catch (e2: Exception) {
                Result.failure(e2)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun downloadFile(
        host: String,
        port: Int,
        username: String,
        password: String,
        remotePath: String,
        outputStream: OutputStream,
        fileSize: Long = 0L,
        progressCallback: ByteProgressCallback? = null
    ): Result<Unit> = executeWithNewConnection(host, port, username, password) { tempClient ->
        try {
            // Resolve the remote size up front so the counting stream can report determinate
            // progress; falls back through SIZE/MLST, directory listing, then the caller hint (S0496).
            val total = tempClient.mlistFile(remotePath)?.size?.takeIf { it > 0 }
                ?: tempClient.listFiles(remotePath)?.firstOrNull()?.size?.takeIf { it > 0 }
                ?: fileSize.takeIf { it > 0 }
                ?: -1L
            Timber.d("S0496: FTP standalone download total=$total remote=$remotePath")
            val counter = AtomicLong(0)
            val countingOut = FtpProgressOutputStream(outputStream, counter)
            coroutineScope {
                val emitter = launch {
                    while (isActive) {
                        progressCallback?.onProgress(counter.get(), total, 0L)
                        if (total in 1..counter.get()) break
                        delay(PROGRESS_POLL_MS)
                    }
                }
                val success = tempClient.retrieveFile(remotePath, countingOut)
                emitter.cancelAndJoin()
                val transferred = counter.get()
                progressCallback?.onProgress(transferred, if (total > 0) total else transferred, 0L)
                if (success) {
                    Result.success(Unit)
                } else {
                    Result.failure(IOException("FTP download failed: ${tempClient.replyString}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Open an InputStream for [remotePath] over a fresh connection. The returned stream owns
     * the connection - closing the stream completes the pending FTP command and disconnects.
     */
    suspend fun openInputStream(
        host: String,
        port: Int,
        username: String,
        password: String,
        remotePath: String
    ): Result<InputStream> = withContext(Dispatchers.IO) {
        val tempClient = FTPClient().apply { applyTimeouts() }
        // S0212: encoding MUST be set before connect - see FtpEncodingSupport.
        tempClient.applyUtf8Encoding()
        try {
            tempClient.connect(host, port)

            val replyCode = tempClient.replyCode
            if (!FTPReply.isPositiveCompletion(replyCode)) {
                tempClient.disconnect()
                return@withContext Result.failure(
                    IOException("FTP server refused connection. Reply code: $replyCode")
                )
            }

            if (!tempClient.login(username, password)) {
                tempClient.disconnect()
                return@withContext Result.failure(
                    IOException("FTP authentication failed for user: $username")
                )
            }

            tempClient.enterLocalPassiveMode()
            tempClient.setFileType(FTP.BINARY_FILE_TYPE)
            // S0212: negotiate UTF-8 filename interpretation on RFC 2640 servers.
            tempClient.enableUtf8Mode()

            val stream = tempClient.retrieveFileStream(remotePath)
            if (stream == null) {
                tempClient.disconnect()
                return@withContext Result.failure(IOException("Failed to open FTP stream: $remotePath"))
            }

            // Wrapper that closes the underlying connection when the caller closes the stream.
            val wrapper = object : java.io.FilterInputStream(stream) {
                override fun close() {
                    try {
                        super.close()
                        if (!tempClient.completePendingCommand()) {
                            Timber.w("FTP completePendingCommand failed after stream close")
                        }
                    } catch (e: Exception) {
                        Timber.w(e, "Error closing FTP stream")
                    } finally {
                        try {
                            if (tempClient.isConnected) {
                                tempClient.logout()
                                tempClient.disconnect()
                            }
                        } catch (e: Exception) {
                            Timber.w("Error disconnecting FTP client")
                        }
                    }
                }
            }

            Result.success(wrapper)
        } catch (e: Exception) {
            try { if (tempClient.isConnected) tempClient.disconnect() } catch (_: Exception) {}
            Timber.e(e, "FTP openInputStream failed: $remotePath")
            Result.failure(e)
        }
    }

    /** Recursively `mkdir` the parent chain of [remotePath]. Race-tolerant (errors swallowed). */
    fun ensureRemoteDirectoryExists(client: FTPClient, remotePath: String) {
        if (remotePath.isEmpty()) return

        val parts = remotePath.trim('/').split('/')
        var currentPath = ""

        for (part in parts) {
            currentPath = if (currentPath.isEmpty()) part else "$currentPath/$part"
            try {
                val created = client.makeDirectory(currentPath)
                if (created) {
                    Timber.d("FTP: Created directory: $currentPath")
                }
            } catch (e: Exception) {
                Timber.d("FTP: Directory exists or error: $currentPath")
            }
        }
    }

    private suspend fun <T> executeWithNewConnection(
        host: String,
        port: Int,
        username: String,
        password: String,
        block: suspend (FTPClient) -> Result<T>
    ): Result<T> = withContext(Dispatchers.IO) {
        val tempClient = FTPClient().apply { applyTimeouts() }
        // S0212: encoding MUST be set before connect - see FtpEncodingSupport.
        tempClient.applyUtf8Encoding()
        try {
            tempClient.connect(host, port)

            val replyCode = tempClient.replyCode
            if (!FTPReply.isPositiveCompletion(replyCode)) {
                tempClient.disconnect()
                return@withContext Result.failure(
                    IOException("FTP server refused connection. Reply code: $replyCode")
                )
            }

            if (!tempClient.login(username, password)) {
                tempClient.disconnect()
                return@withContext Result.failure(
                    IOException("FTP authentication failed for user: $username")
                )
            }

            tempClient.enterLocalPassiveMode()
            tempClient.setFileType(FTP.BINARY_FILE_TYPE)
            // S0212: negotiate UTF-8 filename interpretation on RFC 2640 servers.
            tempClient.enableUtf8Mode()

            block(tempClient)
        } catch (e: Exception) {
            Timber.e(e, "FTP temp connection operation failed")
            Result.failure(e)
        } finally {
            tempClient.disconnectQuietly()
        }
    }

    private fun FTPClient.applyTimeouts() {
        connectTimeout = CONNECT_TIMEOUT
        defaultTimeout = SOCKET_TIMEOUT
        setDataTimeout(SOCKET_TIMEOUT)
        controlKeepAliveTimeout = Duration.ofSeconds(KEEPALIVE_TIMEOUT).seconds
    }

    private fun FTPClient.disconnectQuietly() {
        try {
            if (isConnected) {
                soTimeout = 1000
                try { logout() } catch (_: Exception) {}
                disconnect()
            }
        } catch (_: Exception) {}
    }
}
