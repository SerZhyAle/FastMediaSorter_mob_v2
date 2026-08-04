package com.sza.fastmediasorter.data.remote.ftp

import com.sza.fastmediasorter.core.util.rethrowIfCancellation
import com.sza.fastmediasorter.domain.usecase.ByteProgressCallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPFile
import timber.log.Timber
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.SocketTimeoutException

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

    suspend fun readFileBytesRange(
        remotePath: String,
        offset: Long,
        length: Long
    ): Result<ByteArray> = withContext(Dispatchers.IO) {
        synchronized(mutex) {
            try {
                val client = getClient() ?: return@withContext Result.failure(
                    IllegalStateException("Not connected. Call connect() first.")
                )
                val bytes = try {
                    client.setRestartOffset(offset)
                    client.retrieveFileStream(remotePath)?.use { inputStream ->
                        val buffer = ByteArray(length.toInt())
                        var totalRead = 0
                        while (totalRead < length) {
                            val read = inputStream.read(buffer, totalRead, (length - totalRead).toInt())
                            if (read == -1) break
                            totalRead += read
                        }
                        if (!safeCompletePendingCommand(client, "readFileBytesRange(passive)")) {
                            return@withContext Result.failure(
                                IOException("FTP command failed after retrieving range")
                            )
                        }
                        if (totalRead < length) buffer.copyOf(totalRead) else buffer
                    } ?: return@withContext Result.failure(IOException("Failed to open file stream: $remotePath"))
                } catch (e: SocketTimeoutException) {
                    Timber.w(e, "FTP passive mode timeout during range read, switching to active mode")
                    client.enterLocalActiveMode()
                    Timber.d("FTP retrying range read in active mode: $remotePath")
                    try {
                        client.setRestartOffset(offset)
                        client.retrieveFileStream(remotePath)?.use { inputStream ->
                            val buffer = ByteArray(length.toInt())
                            var totalRead = 0
                            while (totalRead < length) {
                                val read = inputStream.read(buffer, totalRead, (length - totalRead).toInt())
                                if (read == -1) break
                                totalRead += read
                            }
                            if (!safeCompletePendingCommand(client, "readFileBytesRange(active)")) {
                                return@withContext Result.failure(
                                    IOException("FTP command failed after retrieving range (active mode)")
                                )
                            }
                            if (totalRead < length) buffer.copyOf(totalRead) else buffer
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
                Timber.w(e, "FTP read bytes range failed: $remotePath offset=$offset length=$length")
                Result.failure(e)
            } catch (e: Exception) {
                e.rethrowIfCancellation()
                Timber.w(e, "FTP read bytes range error: $remotePath offset=$offset length=$length")
                Result.failure(e)
            }
        }
    }

    suspend fun downloadFile(
        remotePath: String,
        outputStream: OutputStream,
        fileSize: Long = 0L,
        @Suppress("UNUSED_PARAMETER") progressCallback: ByteProgressCallback? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        synchronized(mutex) {
            try {
                val client = getClient() ?: return@withContext Result.failure(
                    IllegalStateException("Not connected. Call connect() first.")
                )
                Timber.d("FTP downloading: $remotePath (size=$fileSize bytes)")
                val success = try {
                    client.retrieveFile(remotePath, outputStream)
                } catch (e: SocketTimeoutException) {
                    Timber.w(e, "FTP passive mode timeout, switching to active mode for download")
                    client.enterLocalActiveMode()
                    Timber.d("FTP retrying download in active mode: $remotePath")
                    try {
                        client.retrieveFile(remotePath, outputStream)
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
                } catch (e: Exception) {
                    e.rethrowIfCancellation()
                    Timber.e(e, "FTP download error during retrieveFile: $remotePath")
                    return@withContext Result.failure(
                        IOException("FTP download failed: ${e.message}", e)
                    )
                }
                if (!success) {
                    return@withContext Result.failure(
                        IOException("FTP download failed: ${client.replyString}")
                    )
                }
                Timber.i("FTP download success: $remotePath")
                Result.success(Unit)
            } catch (e: IOException) {
                Timber.w(e, "FTP download failed: $remotePath")
                Result.failure(e)
            } catch (e: Exception) {
                e.rethrowIfCancellation()
                Timber.w(e, "FTP download error: $remotePath")
                Result.failure(e)
            }
        }
    }

    suspend fun uploadFile(
        remotePath: String,
        inputStream: InputStream,
        fileSize: Long = 0L,
        @Suppress("UNUSED_PARAMETER") progressCallback: ByteProgressCallback? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val client = getClient() ?: return@withContext Result.failure(
                IllegalStateException("Not connected. Call connect() first.")
            )
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
            val success = client.storeFile(remotePath, inputStream)
            if (!success) {
                return@withContext Result.failure(
                    IOException("FTP upload failed: ${client.replyString}")
                )
            }
            Timber.i("FTP upload success: $remotePath")
            Result.success(Unit)
        } catch (e: IOException) {
            Timber.e(e, "FTP upload failed: $remotePath")
            Result.failure(e)
        } catch (e: Exception) {
            e.rethrowIfCancellation()
            Timber.e(e, "FTP upload error: $remotePath")
            Result.failure(e)
        }
    }

    suspend fun deleteFile(remotePath: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val client = getClient() ?: return@withContext Result.failure(
                IllegalStateException("Not connected. Call connect() first.")
            )
            Timber.d("FTP deleting: $remotePath")
            val success = client.deleteFile(remotePath)
            if (!success) {
                return@withContext Result.failure(
                    IOException("FTP delete failed: ${client.replyString}")
                )
            }
            Timber.i("FTP delete success: $remotePath")
            Result.success(Unit)
        } catch (e: IOException) {
            Timber.e(e, "FTP delete failed: $remotePath")
            Result.failure(e)
        } catch (e: Exception) {
            e.rethrowIfCancellation()
            Timber.e(e, "FTP delete error: $remotePath")
            Result.failure(e)
        }
    }

    suspend fun deleteDirectory(remotePath: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val client = getClient() ?: return@withContext Result.failure(
                IllegalStateException("Not connected. Call connect() first.")
            )
            Timber.d("FTP deleting directory: $remotePath")
            val files = client.listFiles(remotePath)
            files.forEach { file ->
                val fullPath = "$remotePath/${file.name}"
                if (file.isDirectory) {
                    deleteDirectory(fullPath).getOrThrow()
                } else {
                    deleteFile(fullPath).getOrThrow()
                }
            }
            val success = client.removeDirectory(remotePath)
            if (!success) {
                return@withContext Result.failure(
                    IOException("FTP remove directory failed: ${client.replyString}")
                )
            }
            Timber.i("FTP delete directory success: $remotePath")
            Result.success(Unit)
        } catch (e: IOException) {
            Timber.e(e, "FTP delete directory failed: $remotePath")
            Result.failure(e)
        } catch (e: Exception) {
            e.rethrowIfCancellation()
            Timber.e(e, "FTP delete directory error: $remotePath")
            Result.failure(e)
        }
    }

    suspend fun renameFile(oldPath: String, newName: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val client = getClient() ?: return@withContext Result.failure(
                IllegalStateException("Not connected. Call connect() first.")
            )
            val directory = oldPath.substringBeforeLast('/', "")
            val newPath = when {
                directory.isNotEmpty() -> "$directory/$newName"
                oldPath.startsWith("/") -> "/$newName"
                else -> newName
            }
            Timber.d("FTP renaming: $oldPath → $newPath")
            val success = client.rename(oldPath, newPath)
            if (!success) {
                return@withContext Result.failure(
                    IOException("FTP rename failed: ${client.replyString}")
                )
            }
            Timber.i("FTP rename success: $newPath")
            Result.success(Unit)
        } catch (e: IOException) {
            Timber.e(e, "FTP rename failed: $oldPath")
            Result.failure(e)
        } catch (e: Exception) {
            e.rethrowIfCancellation()
            Timber.e(e, "FTP rename error: $oldPath")
            Result.failure(e)
        }
    }

    suspend fun moveFile(oldPath: String, newPath: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val client = getClient() ?: return@withContext Result.failure(
                IllegalStateException("Not connected. Call connect() first.")
            )
            Timber.d("FTP moving: $oldPath → $newPath")
            val success = client.rename(oldPath, newPath)
            if (!success) {
                return@withContext Result.failure(
                    IOException("FTP move failed: ${client.replyString}")
                )
            }
            Timber.i("FTP move success: $newPath")
            Result.success(Unit)
        } catch (e: IOException) {
            Timber.e(e, "FTP move failed: $oldPath → $newPath")
            Result.failure(e)
        } catch (e: Exception) {
            e.rethrowIfCancellation()
            Timber.e(e, "FTP move error: $oldPath → $newPath")
            Result.failure(e)
        }
    }

    suspend fun createDirectory(remotePath: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val client = getClient() ?: return@withContext Result.failure(
                IllegalStateException("Not connected. Call connect() first.")
            )
            Timber.d("FTP creating directory: $remotePath")
            val success = client.makeDirectory(remotePath)
            if (!success) {
                return@withContext Result.failure(
                    IOException("FTP create directory failed: ${client.replyString}")
                )
            }
            Timber.i("FTP directory created: $remotePath")
            Result.success(Unit)
        } catch (e: IOException) {
            Timber.e(e, "FTP create directory failed: $remotePath")
            Result.failure(e)
        } catch (e: Exception) {
            e.rethrowIfCancellation()
            Timber.e(e, "FTP create directory error: $remotePath")
            Result.failure(e)
        }
    }

    suspend fun directoryExists(remotePath: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val client = getClient() ?: return@withContext Result.failure(
                IllegalStateException("Not connected. Call connect() first.")
            )
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
