package com.sza.fastmediasorter.data.remote.ftp

import androidx.annotation.WorkerThread
import com.sza.fastmediasorter.core.util.rethrowIfCancellation
import com.sza.fastmediasorter.domain.usecase.ByteProgressCallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPFile
import timber.log.Timber
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicLong

/** Stateful FTP operations that require an active connection from [FtpClient]. */
class FtpConnectedOperations(
    private val getClient: () -> FTPClient?,
    private val mutex: Any
) {

    suspend fun listFilesWithMetadata(
        remotePath: String = "/",
        recursive: Boolean = true
    ): Result<List<FTPFile>> = withContext(Dispatchers.IO) {
        synchronized(mutex) {
            try {
                val client = getClient() ?: return@withContext Result.failure(
                    IllegalStateException("Not connected. Call connect() first.")
                )
                val allFiles = mutableListOf<FTPFile>()
                if (recursive) {
                    FtpDirectoryScanner.listFilesWithMetadataRecursive(client, remotePath, allFiles)
                } else {
                    FtpDirectoryScanner.listFilesWithMetadataSingleLevel(client, remotePath, allFiles)
                }
                Timber.d("FTP listed ${allFiles.size} files with metadata in $remotePath (recursive=$recursive)")
                Result.success(allFiles)
            } catch (e: IOException) {
                Timber.e(e, "FTP list files with metadata failed: $remotePath")
                Result.failure(e)
            } catch (e: Exception) {
                e.rethrowIfCancellation()
                Timber.e(e, "FTP list files with metadata error: $remotePath")
                Result.failure(e)
            }
        }
    }

    suspend fun listFilesWithMetadataPaged(
        remotePath: String = "/",
        offset: Int = 0,
        limit: Int = 50,
        recursive: Boolean = true
    ): Result<List<FTPFile>> = withContext(Dispatchers.IO) {
        synchronized(mutex) {
            try {
                val client = getClient() ?: return@withContext Result.failure(
                    IllegalStateException("Not connected. Call connect() first.")
                )
                if (limit <= 0) return@withContext Result.success(emptyList())
                val safeOffset = offset.coerceAtLeast(0)
                val results = mutableListOf<FTPFile>()
                if (recursive) {
                    val pagingState = FtpDirectoryScanner.MetadataPagingState(offset = safeOffset, limit = limit)
                    FtpDirectoryScanner.listFilesWithMetadataRecursivePaged(client, remotePath, results, pagingState)
                } else {
                    val allFiles = mutableListOf<FTPFile>()
                    FtpDirectoryScanner.listFilesWithMetadataSingleLevel(client, remotePath, allFiles)
                    allFiles.drop(safeOffset).take(limit).forEach { results.add(it) }
                }
                Timber.d(
                    "FTP listFilesWithMetadataPaged: path=$remotePath, offset=$safeOffset, limit=$limit, recursive=$recursive, returned=${results.size}"
                )
                Result.success(results)
            } catch (e: IOException) {
                Timber.e(e, "FTP paged list files with metadata failed: $remotePath")
                Result.failure(e)
            } catch (e: Exception) {
                e.rethrowIfCancellation()
                Timber.e(e, "FTP paged list files with metadata error: $remotePath")
                Result.failure(e)
            }
        }
    }

    suspend fun listFiles(remotePath: String = "/"): Result<List<String>> = withContext(Dispatchers.IO) {
        synchronized(mutex) {
            try {
                val client = getClient() ?: return@withContext Result.failure(
                    IllegalStateException("Not connected. Call connect() first.")
                )
                val files = try {
                    Timber.d("FTP listing files in passive mode: $remotePath")
                    val ftpFiles = client.listFiles(remotePath)
                    ftpFiles.mapNotNull { ftpFile ->
                        if (ftpFile.name == "." || ftpFile.name == "..") null else ftpFile.name
                    }
                } catch (e: SocketTimeoutException) {
                    Timber.w(e, "FTP passive mode timeout, switching to active mode")
                    client.enterLocalActiveMode()
                    Timber.d("FTP retrying listFiles in active mode: $remotePath")
                    val ftpFiles = try {
                        client.listFiles(remotePath)
                    } finally {
                        try {
                            client.enterLocalPassiveMode()
                            Timber.d("FTP switched back to passive mode")
                        } catch (ignored: Exception) {
                            Timber.w(ignored, "Failed to switch back to passive mode")
                        }
                    }
                    ftpFiles.mapNotNull { ftpFile ->
                        if (ftpFile.name == "." || ftpFile.name == "..") null else ftpFile.name
                    }
                }
                Timber.d("FTP listed ${files.size} files in $remotePath")
                Result.success(files)
            } catch (e: IOException) {
                Timber.e(e, "FTP list files failed: $remotePath")
                Result.failure(e)
            } catch (e: Exception) {
                e.rethrowIfCancellation()
                Timber.e(e, "FTP list files error: $remotePath")
                Result.failure(e)
            }
        }
    }

    suspend fun readFileBytes(
        remotePath: String,
        maxBytes: Long = Long.MAX_VALUE
    ): Result<ByteArray> = withContext(Dispatchers.IO) {
        synchronized(mutex) {
            try {
                val client = getClient() ?: return@withContext Result.failure(
                    IllegalStateException("Not connected. Call connect() first.")
                )
                val bytes = try {
                    client.retrieveFileStream(remotePath)?.use { inputStream ->
                        // S0206: readBoundedAndAbort reads exactly maxBytesInt bytes, sends ABOR
                        // if cap is reached, and calls completePendingCommand internally.
                        // Full-read path (no limit) retains original byte-for-byte contract.
                        if (maxBytes < Long.MAX_VALUE) {
                            val maxBytesInt = maxBytes.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                            val result = readBoundedAndAbort(client, inputStream, maxBytesInt, "readFileBytes(passive)")
                            Timber.d("FTP bounded read: ${result.bytes.size}b from $remotePath (abort=${result.abortInvoked}, completeOk=${result.completeOk})")
                            result.bytes
                        } else {
                            val allBytes = inputStream.readBytes()
                            if (!safeCompletePendingCommand(client, "readFileBytes(passive)")) {
                                return@withContext Result.failure(
                                    IOException("FTP command failed after retrieving file")
                                )
                            }
                            Timber.d("FTP read ${allBytes.size} bytes from $remotePath")
                            allBytes
                        }
                    } ?: return@withContext Result.failure(IOException("Failed to open file stream: $remotePath"))
                } catch (e: SocketTimeoutException) {
                    Timber.w(e, "FTP passive mode timeout during read, switching to active mode")
                    client.enterLocalActiveMode()
                    Timber.d("FTP retrying read in active mode: $remotePath")
                    try {
                        // S0206: same bounded-read logic for active mode fallback.
                        client.retrieveFileStream(remotePath)?.use { inputStream ->
                            if (maxBytes < Long.MAX_VALUE) {
                                val maxBytesInt = maxBytes.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                                val result = readBoundedAndAbort(client, inputStream, maxBytesInt, "readFileBytes(active)")
                                Timber.d("FTP bounded read (active): ${result.bytes.size}b from $remotePath (abort=${result.abortInvoked}, completeOk=${result.completeOk})")
                                result.bytes
                            } else {
                                val allBytes = inputStream.readBytes()
                                if (!safeCompletePendingCommand(client, "readFileBytes(active)")) {
                                    return@withContext Result.failure(
                                        IOException("FTP command failed after retrieving file (active mode)")
                                    )
                                }
                                allBytes
                            }
                        } ?: return@withContext Result.failure(IOException("Failed to open file stream (active mode): $remotePath"))
                    } catch (active: Exception) {
                        active.rethrowIfCancellation()
                        // Behind NAT the active-mode data socket is null/unreachable; Apache commons-net
                        // throws a raw NPE/SocketException here. Fail cleanly instead of letting it escape.
                        Timber.w(active, "FTP active-mode data connection failed (likely NAT-blocked): $remotePath")
                        return@withContext Result.failure(
                            IOException("FTP active-mode data connection failed: $remotePath", active)
                        )
                    } finally {
                        try {
                            client.enterLocalPassiveMode()
                            Timber.d("FTP switched back to passive mode")
                        } catch (ignored: Exception) {
                            Timber.w(ignored, "Failed to switch back to passive mode")
                        }
                    }
                }
                Result.success(bytes)
            } catch (e: IOException) {
                Timber.w(e, "FTP read file bytes failed: $remotePath")
                Result.failure(e)
            } catch (e: Exception) {
                e.rethrowIfCancellation()
                Timber.w(e, "FTP read file bytes error: $remotePath")
                Result.failure(e)
            }
        }
    }

    suspend fun downloadFile(
        remotePath: String,
        outputStream: OutputStream,
        fileSize: Long = 0L,
        progressCallback: ByteProgressCallback? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        withProgress(fileSize, progressCallback) { counter ->
            val target = counter?.let { FtpProgressOutputStream(outputStream, it) } ?: outputStream
            synchronized(mutex) { downloadLocked(remotePath, target, fileSize) }
        }
    }

    private fun downloadLocked(remotePath: String, outputStream: OutputStream, fileSize: Long): Result<Unit> {
        val client = getClient() ?: return notConnected()
        Timber.d("FTP downloading: $remotePath (size=$fileSize bytes)")
        val result = try {
            retrieveResult(client, remotePath, outputStream)
        } catch (e: SocketTimeoutException) {
            Timber.w(e, "FTP passive mode timeout, switching to active mode for download")
            retrieveInActiveMode(client, remotePath, outputStream)
        } catch (e: Exception) {
            e.rethrowIfCancellation()
            Timber.e(e, "FTP download error during retrieveFile: $remotePath")
            Result.failure(IOException("FTP download failed: ${e.message}", e))
        }
        if (result.isSuccess) Timber.i("FTP download success: $remotePath")
        return result
    }

    private fun retrieveResult(client: FTPClient, remotePath: String, outputStream: OutputStream): Result<Unit> =
        if (client.retrieveFile(remotePath, outputStream)) {
            Result.success(Unit)
        } else {
            Result.failure(IOException("FTP download failed: ${client.replyString}"))
        }

    private fun retrieveInActiveMode(client: FTPClient, remotePath: String, outputStream: OutputStream): Result<Unit> {
        client.enterLocalActiveMode()
        Timber.d("FTP retrying download in active mode: $remotePath")
        return try {
            retrieveResult(client, remotePath, outputStream)
        } catch (active: Exception) {
            active.rethrowIfCancellation()
            // Behind NAT the active-mode data socket is null/unreachable; Apache commons-net
            // throws a raw NPE/SocketException here. Fail cleanly instead of letting it escape.
            Timber.w(active, "FTP active-mode data connection failed (likely NAT-blocked): $remotePath")
            Result.failure(IOException("FTP active-mode data connection failed: $remotePath", active))
        } finally {
            try {
                client.enterLocalPassiveMode()
                Timber.d("FTP switched back to passive mode")
            } catch (ignored: Exception) {
                Timber.w(ignored, "Failed to switch back to passive mode")
            }
        }
    }

    suspend fun uploadFile(
        remotePath: String,
        inputStream: InputStream,
        fileSize: Long = 0L,
        progressCallback: ByteProgressCallback? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        withProgress(fileSize, progressCallback) { counter ->
            val source = counter?.let { CountingInputStream(inputStream, it) } ?: inputStream
            synchronized(mutex) { uploadLocked(remotePath, source, fileSize) }
        }
    }

    private fun uploadLocked(remotePath: String, inputStream: InputStream, fileSize: Long): Result<Unit> = try {
        val client = getClient() ?: return notConnected()
        Timber.d("FTP uploading: $remotePath (size=$fileSize bytes)")
        val parentDir = remotePath.substringBeforeLast('/')
        if (parentDir.isNotEmpty() && parentDir != remotePath) {
            try {
                if (!client.changeWorkingDirectory(parentDir)) {
                    Timber.d("FTP: Creating parent directory: $parentDir")
                    client.makeDirectory(parentDir)
                }
                client.changeWorkingDirectory("/")
            } catch (e: Exception) {
                e.rethrowIfCancellation()
                Timber.w(e, "FTP: Failed to create parent dir, trying upload anyway")
            }
        }
        if (client.storeFile(remotePath, inputStream)) {
            Timber.i("FTP upload success: $remotePath")
            Result.success(Unit)
        } else {
            Result.failure(IOException("FTP upload failed: ${client.replyString}"))
        }
    } catch (e: IOException) {
        Timber.e(e, "FTP upload failed: $remotePath")
        Result.failure(e)
    } catch (e: Exception) {
        e.rethrowIfCancellation()
        Timber.e(e, "FTP upload error: $remotePath")
        Result.failure(e)
    }

    suspend fun deleteFile(remotePath: String): Result<Unit> = locked("FTP delete", remotePath) { client ->
        Timber.d("FTP deleting: $remotePath")
        if (!client.deleteFile(remotePath)) throw IOException("FTP delete failed: ${client.replyString}")
        Timber.i("FTP delete success: $remotePath")
    }

    suspend fun deleteDirectory(remotePath: String): Result<Unit> =
        locked("FTP delete directory", remotePath) { client ->
            Timber.d("FTP deleting directory: $remotePath")
            removeTree(client, remotePath)
            Timber.i("FTP delete directory success: $remotePath")
        }

    /** Recursive delete on the caller's already-locked client; one control socket, no re-entry. */
    private fun removeTree(client: FTPClient, remotePath: String) {
        client.listFiles(remotePath).forEach { file ->
            if (file.name == "." || file.name == "..") return@forEach
            val fullPath = "$remotePath/${file.name}"
            if (file.isDirectory) {
                removeTree(client, fullPath)
            } else if (!client.deleteFile(fullPath)) {
                throw IOException("FTP delete failed: ${client.replyString}")
            }
        }
        if (!client.removeDirectory(remotePath)) {
            throw IOException("FTP remove directory failed: ${client.replyString}")
        }
    }

    suspend fun renameFile(oldPath: String, newName: String): Result<Unit> = locked("FTP rename", oldPath) { client ->
        val directory = oldPath.substringBeforeLast('/', "")
        val newPath = when {
            directory.isNotEmpty() -> "$directory/$newName"
            oldPath.startsWith("/") -> "/$newName"
            else -> newName
        }
        Timber.d("FTP renaming: $oldPath -> $newPath")
        if (!client.rename(oldPath, newPath)) throw IOException("FTP rename failed: ${client.replyString}")
        Timber.i("FTP rename success: $newPath")
    }

    suspend fun moveFile(oldPath: String, newPath: String): Result<Unit> =
        locked("FTP move", "$oldPath -> $newPath") { client ->
            Timber.d("FTP moving: $oldPath -> $newPath")
            if (!client.rename(oldPath, newPath)) throw IOException("FTP move failed: ${client.replyString}")
            Timber.i("FTP move success: $newPath")
        }

    suspend fun createDirectory(remotePath: String): Result<Unit> =
        locked("FTP create directory", remotePath) { client ->
            Timber.d("FTP creating directory: $remotePath")
            if (!client.makeDirectory(remotePath)) {
                throw IOException("FTP create directory failed: ${client.replyString}")
            }
            Timber.i("FTP directory created: $remotePath")
        }

    suspend fun directoryExists(remotePath: String): Result<Boolean> = withContext(Dispatchers.IO) {
        synchronized(mutex) {
            try {
                val client = getClient() ?: return@withContext notConnected()
                val currentDir = client.printWorkingDirectory()
                val success = client.changeWorkingDirectory(remotePath)
                if (success) {
                    client.changeWorkingDirectory(currentDir)
                }
                Result.success(success)
            } catch (e: IOException) {
                Timber.w(e, "FTP directory exists check failed: $remotePath")
                Result.success(false)
            } catch (e: Exception) {
                e.rethrowIfCancellation()
                Timber.w(e, "FTP directory exists check error: $remotePath")
                Result.success(false)
            }
        }
    }

    /**
     * Every command on the shared client runs under [mutex]: listing, reading and downloading
     * already did, and a mutation issued beside them interleaved commands and replies on the one
     * control socket.
     */
    @WorkerThread
    private suspend fun locked(
        label: String,
        subject: String,
        action: (FTPClient) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        synchronized(mutex) {
            try {
                val client = getClient() ?: return@withContext notConnected()
                action(client)
                Result.success(Unit)
            } catch (e: IOException) {
                Timber.e(e, "$label failed: $subject")
                Result.failure(e)
            } catch (e: Exception) {
                e.rethrowIfCancellation()
                Timber.e(e, "$label error: $subject")
                Result.failure(e)
            }
        }
    }

    /**
     * The transfer blocks inside [mutex], so progress is polled from a sibling coroutine instead of
     * being reported from the copy loop.
     */
    private suspend fun <T> withProgress(
        fileSize: Long,
        callback: ByteProgressCallback?,
        transfer: (AtomicLong?) -> Result<T>
    ): Result<T> {
        if (callback == null) return transfer(null)
        val counter = AtomicLong(0)
        val total = fileSize.coerceAtLeast(0L)
        return coroutineScope {
            val emitter = launch {
                while (isActive) {
                    callback.onProgress(counter.get(), total, 0L)
                    delay(PROGRESS_POLL_MS)
                }
            }
            val result = try {
                transfer(counter)
            } finally {
                emitter.cancel()
            }
            emitter.join()
            val transferred = counter.get()
            if (result.isSuccess) callback.onProgress(transferred, if (total > 0) total else transferred, 0L)
            result
        }
    }

    private fun <T> notConnected(): Result<T> =
        Result.failure(IllegalStateException("Not connected. Call connect() first."))

    private class CountingInputStream(delegate: InputStream, private val counter: AtomicLong) :
        FilterInputStream(delegate) {
        override fun read(): Int = super.read().also { if (it >= 0) counter.incrementAndGet() }

        override fun read(b: ByteArray, off: Int, len: Int): Int =
            super.read(b, off, len).also { if (it > 0) counter.addAndGet(it.toLong()) }
    }

    private companion object {
        const val PROGRESS_POLL_MS = 100L
    }
}
