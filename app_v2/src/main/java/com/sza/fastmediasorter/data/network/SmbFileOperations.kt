package com.sza.fastmediasorter.data.network

import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.share.DiskShare
import com.sza.fastmediasorter.core.util.InputStreamExt.copyToWithProgress
import com.sza.fastmediasorter.core.util.rethrowIfCancellation
import com.sza.fastmediasorter.data.network.model.SmbConnectionInfo
import com.sza.fastmediasorter.data.network.model.SmbFileInfo
import com.sza.fastmediasorter.data.network.model.SmbResult
import com.sza.fastmediasorter.domain.usecase.ByteProgressCallback
import kotlinx.coroutines.CancellationException
import timber.log.Timber
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.EnumSet
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SMB file operations handler - extracted from SmbClient.
 * 
 * Responsibilities:
 * - File CRUD operations (upload, download, delete, rename, move)
 * - Directory operations (create, delete)
 * - File metadata queries (exists, getFileInfo)
 * - Stream operations (openInputStream, readBytes, readPartial)
 * 
 * All operations use SmbConnectionManager for connection pooling and health tracking.
 */
@Singleton
class SmbFileOperations @Inject constructor(
    private val connectionManager: SmbConnectionManager
) {

    /**
     * Download file from SMB to local output stream.
     * @param progressCallback Optional callback for progress updates (bytes transferred)
     */
    suspend fun downloadFile(
        connectionInfo: SmbConnectionInfo,
        remotePath: String,
        outputStream: OutputStream,
        progressCallback: ByteProgressCallback? = null
    ): SmbResult<Unit> {
        return try {
            connectionManager.withConnection(connectionInfo) { share ->
                val file = share.openFile(
                    remotePath.trim('/', '\\'),
                    EnumSet.of(AccessMask.GENERIC_READ),
                    null,
                    SMB2ShareAccess.ALL,
                    SMB2CreateDisposition.FILE_OPEN,
                    null
                )

                file.use { smbFile ->
                    smbFile.inputStream.use { rawInput ->
                        // S0247 graduated: 64 KiB BufferedInputStream wrap on raw SMBJ stream.
                        val fileSize = smbFile.fileInformation.standardInformation.endOfFile
                        val input = BufferedInputStream(rawInput, 65_536)
                        if (progressCallback != null) {
                            input.copyToWithProgress(
                                output = outputStream,
                                totalBytes = fileSize,
                                progressCallback = progressCallback
                            )
                        } else {
                            input.copyTo(outputStream)
                        }
                    }
                }
                SmbResult.Success(Unit)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Failed to download file from SMB")
            SmbResult.Error("Failed to download file: ${e.message}", e)
        }
    }
    
    /**
     * Read entire file content as byte array.
     * WARNING: Use only for small files (< 10MB) to avoid memory issues.
     * @param maxBytes Maximum number of bytes to read (default: 10MB limit)
     */
    suspend fun readFileBytes(
        connectionInfo: SmbConnectionInfo,
        remotePath: String,
        maxBytes: Long = 10 * 1024 * 1024 // 10MB default limit
    ): SmbResult<ByteArray> {
        return try {
            connectionManager.withConnection(connectionInfo) { share ->
                val file = share.openFile(
                    remotePath.trim('/', '\\'),
                    EnumSet.of(AccessMask.GENERIC_READ),
                    null,
                    SMB2ShareAccess.ALL,
                    SMB2CreateDisposition.FILE_OPEN,
                    null
                )
                
                file.use { smbFile ->
                    smbFile.inputStream.use { input ->
                        val bytes = if (maxBytes < Long.MAX_VALUE) {
                            // Read in 64KB chunks for better throughput
                            val buffer = ByteArrayOutputStream(maxBytes.toInt().coerceAtMost(256 * 1024))
                            val chunk = ByteArray(64 * 1024) // 64KB chunks
                            var bytesRead = 0L
                            
                            while (bytesRead < maxBytes) {
                                val toRead = minOf(chunk.size.toLong(), maxBytes - bytesRead).toInt()
                                val read = input.read(chunk, 0, toRead)
                                if (read == -1) break
                                buffer.write(chunk, 0, read)
                                bytesRead += read
                            }
                            buffer.toByteArray()
                        } else {
                            input.readBytes()
                        }
                        SmbResult.Success(bytes)
                    }
                }
            }
        } catch (e: CancellationException) {
            // Normal behavior when coroutine is cancelled (e.g., Coil cancels image fetch during RecyclerView scroll)
            // Re-throw to propagate cancellation properly without logging as error
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Failed to read file bytes from SMB")
            SmbResult.Error("Failed to read file: ${e.message}", e)
        }
    }
    
    /**
     * Read partial file content (byte range).
     * Used for text file previews, PDF page extraction, etc.
     * @param offset Start position (0-based)
     * @param length Number of bytes to read
     */
    suspend fun readPartialFile(
        connectionInfo: SmbConnectionInfo,
        remotePath: String,
        offset: Long = 0L,
        length: Int = 8192,
        allowRetry: Boolean = true
    ): SmbResult<ByteArray> {
        return try {
            connectionManager.withConnection(connectionInfo, allowRetry = allowRetry) { share ->
                val file = share.openFile(
                    remotePath.trim('/', '\\'),
                    EnumSet.of(AccessMask.GENERIC_READ),
                    null,
                    SMB2ShareAccess.ALL,
                    SMB2CreateDisposition.FILE_OPEN,
                    null
                )
                
                file.use { smbFile ->
                    val fileSize = smbFile.fileInformation.standardInformation.endOfFile
                    val actualOffset = offset.coerceIn(0L, fileSize)
                    val actualLength = (fileSize - actualOffset).coerceIn(0L, length.toLong()).toInt()
                    
                    if (actualLength == 0) {
                        return@withConnection SmbResult.Success(ByteArray(0))
                    }
                    
                    val buffer = ByteArray(actualLength)
                    var totalRead = 0
                    
                    smbFile.inputStream.use { input ->
                        input.skip(actualOffset)
                        
                        while (totalRead < actualLength) {
                            val bytesRead = input.read(buffer, totalRead, actualLength - totalRead)
                            if (bytesRead == -1) break
                            totalRead += bytesRead
                        }
                    }
                    
                    // Return only the bytes actually read
                    if (totalRead < actualLength) {
                        SmbResult.Success(buffer.copyOf(totalRead))
                    } else {
                        SmbResult.Success(buffer)
                    }
                }
            }
        } catch (e: CancellationException) {
            // Normal behavior when coroutine is cancelled (e.g., video thumbnail extraction timeout)
            // Re-throw to propagate cancellation properly without logging as error
            throw e
        } catch (e: Exception) {
            // Log without stack trace for expected interruptions
            if (e is InterruptedException || e.cause is InterruptedException) {
                Timber.w("SMB partial read interrupted (expected during cancellation): ${e.message}")
            } else {
                Timber.e(e, "Failed to read partial file from SMB")
            }
            SmbResult.Error("Failed to read partial file: ${e.message}", e)
        }
    }
    
    /**
     * Read specific byte range from file.
     * Alias for readPartialFile with different parameter names.
     */
    suspend fun readFileBytesRange(
        connectionInfo: SmbConnectionInfo,
        remotePath: String,
        start: Long,
        length: Long,
        allowRetry: Boolean = true
    ): SmbResult<ByteArray> {
        if (length > Int.MAX_VALUE) {
            return SmbResult.Error("Range too large: $length bytes", Exception("Length exceeds Int.MAX_VALUE"))
        }
        return readPartialFile(connectionInfo, remotePath, start, length.toInt(), allowRetry = allowRetry)
    }
    
    /**
     * Upload file from local input stream to SMB.
     * @param progressCallback Optional callback for progress updates (bytes transferred)
     */
    suspend fun uploadFile(
        connectionInfo: SmbConnectionInfo,
        remotePath: String,
        localInputStream: InputStream,
        fileSize: Long = 0L,
        progressCallback: ByteProgressCallback? = null
    ): SmbResult<Unit> {
        return try {
            connectionManager.withConnection(connectionInfo) { share ->
                // Ensure parent directory exists
                val cleanPath = remotePath.trim('/', '\\')
                val parentDir = cleanPath.substringBeforeLast('\\').substringBeforeLast('/')
                if (parentDir.isNotEmpty() && parentDir != cleanPath) {
                    if (!share.folderExists(parentDir)) {
                        Timber.d("SmbFileOperations.uploadFile: Creating parent directory recursively: $parentDir")
                        try {
                            ensureSmbDirectoryExists(share, parentDir)
                        } catch (e: Exception) {
                            Timber.w(e, "SmbFileOperations.uploadFile: Failed to create parent dir, trying anyway")
                        }
                    }
                }
                
                val file = share.openFile(
                    cleanPath,
                    EnumSet.of(AccessMask.GENERIC_WRITE),
                    null,
                    SMB2ShareAccess.ALL,
                    SMB2CreateDisposition.FILE_OVERWRITE_IF,
                    null
                )

                file.use { smbFile ->
                    smbFile.outputStream.use { rawOutput ->
                        // S0247 graduated: 64 KiB BufferedOutputStream wrap on raw SMBJ stream.
                        val output = BufferedOutputStream(rawOutput, 65_536)
                        if (progressCallback != null) {
                            localInputStream.copyToWithProgress(
                                output = output,
                                totalBytes = fileSize,
                                progressCallback = progressCallback
                            )
                        } else {
                            localInputStream.copyTo(output)
                        }
                        output.flush()
                    }
                }
                SmbResult.Success(Unit)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Failed to upload file to SMB")
            SmbResult.Error("Failed to upload file: ${e.message}", e)
        }
    }
    
    /**
     * Delete file on SMB share.
     */
    suspend fun deleteFile(
        connectionInfo: SmbConnectionInfo,
        remotePath: String
    ): SmbResult<Unit> {
        Timber.d("SmbFileOperations.deleteFile: START - remotePath='$remotePath'")
        Timber.d("SmbFileOperations.deleteFile: Connection - server=${connectionInfo.server}, share=${connectionInfo.shareName}, port=${connectionInfo.port}")
        Timber.d("SmbFileOperations.deleteFile: Credentials - username=${connectionInfo.username}, domain=${connectionInfo.domain}")
        
        return try {
            connectionManager.withConnection(connectionInfo) { share ->
                Timber.d("SmbFileOperations.deleteFile: Share connected, checking if file exists...")
                
                // Check if file exists before deleting
                val exists = try {
                    share.fileExists(remotePath.trim('/', '\\'))
                } catch (e: Exception) {
                    Timber.w(e, "SmbFileOperations.deleteFile: Failed to check file existence")
                    false
                }
                
                if (!exists) {
                    Timber.e("SmbFileOperations.deleteFile: File does not exist: $remotePath")
                    return@withConnection SmbResult.Error("File not found: $remotePath", Exception("File does not exist"))
                }
                
                Timber.d("SmbFileOperations.deleteFile: File exists, attempting to delete...")
                
                try {
                    share.rm(remotePath.trim('/', '\\'))
                    Timber.i("SmbFileOperations.deleteFile: SUCCESS - File deleted: $remotePath")
                    SmbResult.Success(Unit)
                } catch (deleteEx: Exception) {
                    Timber.e(deleteEx, "SmbFileOperations.deleteFile: FAILED - Exception during rm() call")
                    Timber.e("SmbFileOperations.deleteFile: Delete error type: ${deleteEx.javaClass.name}")
                    Timber.e("SmbFileOperations.deleteFile: Delete error message: ${deleteEx.message}")
                    deleteEx.cause?.let { cause ->
                        Timber.e("SmbFileOperations.deleteFile: Cause: ${cause.javaClass.name} - ${cause.message}")
                    }
                    SmbResult.Error("Delete operation failed: ${deleteEx.message}", deleteEx)
                }
            }
        } catch (e: Exception) {
            e.rethrowIfCancellation()
            Timber.e(e, "SmbFileOperations.deleteFile: EXCEPTION - Failed to establish connection or execute delete")
            Timber.e("SmbFileOperations.deleteFile: Exception type: ${e.javaClass.name}")
            Timber.e("SmbFileOperations.deleteFile: Exception message: ${e.message}")
            e.cause?.let { cause ->
                Timber.e("SmbFileOperations.deleteFile: Cause: ${cause.javaClass.name} - ${cause.message}")
            }
            SmbResult.Error("Failed to delete file: ${e.message}", e)
        }
    }
    
    /**
     * Delete directory recursively on SMB share.
     */
    suspend fun deleteDirectory(
        connectionInfo: SmbConnectionInfo,
        remotePath: String
    ): SmbResult<Unit> {
        Timber.d("SmbFileOperations.deleteDirectory: START - remotePath='$remotePath'")
        
        return try {
            connectionManager.withConnection(connectionInfo) { share ->
                if (!share.fileExists(remotePath.trim('/', '\\'))) {
                    Timber.w("SmbFileOperations.deleteDirectory: Directory does not exist: $remotePath")
                    return@withConnection SmbResult.Success(Unit)
                }
                
                try {
                    share.rmdir(remotePath.trim('/', '\\'), true)
                    Timber.i("SmbFileOperations.deleteDirectory: SUCCESS - Directory deleted: $remotePath")
                    SmbResult.Success(Unit)
                } catch (deleteEx: Exception) {
                    Timber.e(deleteEx, "SmbFileOperations.deleteDirectory: FAILED - ${deleteEx.message}")
                    SmbResult.Error("Delete directory failed: ${deleteEx.message}", deleteEx)
                }
            }
        } catch (e: Exception) {
            e.rethrowIfCancellation()
            Timber.e(e, "SmbFileOperations.deleteDirectory: EXCEPTION - ${e.message}")
            SmbResult.Error("Failed to delete directory: ${e.message}", e)
        }
    }
    
    /**
     * Rename file or directory on SMB share.
     */
    suspend fun renameFile(
        connectionInfo: SmbConnectionInfo,
        oldPath: String,
        newPath: String
    ): SmbResult<Unit> {
        Timber.d("SmbFileOperations.renameFile: START - oldPath='$oldPath', newPath='$newPath'")
        
        return try {
            connectionManager.withConnection(connectionInfo) { share ->
                // Check if source exists
                if (!share.fileExists(oldPath.trim('/', '\\'))) {
                    return@withConnection SmbResult.Error("Source file not found: $oldPath", Exception("File does not exist"))
                }
                
                // Check if destination already exists
                if (share.fileExists(newPath.trim('/', '\\'))) {
                    return@withConnection SmbResult.Error("Destination already exists: $newPath", Exception("File already exists"))
                }
                
                val file = share.openFile(
                    oldPath.trim('/', '\\'),
                    EnumSet.of(AccessMask.DELETE),
                    null,
                    SMB2ShareAccess.ALL,
                    SMB2CreateDisposition.FILE_OPEN,
                    null
                )
                
                file.use { smbFile ->
                    smbFile.rename(newPath.trim('/', '\\'))
                    Timber.i("SmbFileOperations.renameFile: SUCCESS - Renamed '$oldPath' to '$newPath'")
                }
                
                SmbResult.Success(Unit)
            }
        } catch (e: Exception) {
            e.rethrowIfCancellation()
            Timber.e(e, "SmbFileOperations.renameFile: FAILED")
            SmbResult.Error("Failed to rename file: ${e.message}", e)
        }
    }
    
    /**
     * Move file between directories on SMB share.
     * Handles same-directory rename and cross-directory moves.
     */
    suspend fun moveFile(
        connectionInfo: SmbConnectionInfo,
        sourcePath: String,
        destinationPath: String
    ): SmbResult<Unit> {
        Timber.d("SmbFileOperations.moveFile: START - sourcePath='$sourcePath', destinationPath='$destinationPath'")
        
        return try {
            connectionManager.withConnection(connectionInfo) { share ->
                val trimmedSource = sourcePath.trim('/', '\\')
                val trimmedDest = destinationPath.trim('/', '\\')
                
                // Check if source exists
                if (!share.fileExists(trimmedSource)) {
                    return@withConnection SmbResult.Error("Source file not found: $sourcePath", Exception("File does not exist"))
                }
                
                // Check if destination already exists
                if (share.fileExists(trimmedDest)) {
                    return@withConnection SmbResult.Error("Destination already exists: $destinationPath", Exception("File already exists"))
                }
                
                // SMB2 rename/move operation
                val file = share.openFile(
                    trimmedSource,
                    EnumSet.of(AccessMask.DELETE),
                    null,
                    SMB2ShareAccess.ALL,
                    SMB2CreateDisposition.FILE_OPEN,
                    null
                )
                
                file.use { smbFile ->
                    smbFile.rename(trimmedDest, false) // replaceIfExists = false
                    Timber.i("SmbFileOperations.moveFile: SUCCESS - Moved '$sourcePath' to '$destinationPath'")
                }
                
                SmbResult.Success(Unit)
            }
        } catch (e: Exception) {
            e.rethrowIfCancellation()
            Timber.e(e, "SmbFileOperations.moveFile: FAILED")
            SmbResult.Error("Failed to move file: ${e.message}", e)
        }
    }
    
    /**
     * Create directory on SMB share.
     */
    suspend fun createDirectory(
        connectionInfo: SmbConnectionInfo,
        remotePath: String
    ): SmbResult<Unit> {
        return try {
            connectionManager.withConnection(connectionInfo) { share ->
                share.mkdir(remotePath.trim('/', '\\'))
                Timber.i("SmbFileOperations.createDirectory: Created directory: $remotePath")
                SmbResult.Success(Unit)
            }
        } catch (e: Exception) {
            e.rethrowIfCancellation()
            Timber.e(e, "Failed to create SMB directory")
            SmbResult.Error("Failed to create directory: ${e.message}", e)
        }
    }
    
    /**
     * Check if file or directory exists on SMB share.
     */
    suspend fun exists(
        connectionInfo: SmbConnectionInfo,
        remotePath: String
    ): SmbResult<Boolean> {
        return try {
            connectionManager.withConnection(connectionInfo) { share ->
                val exists = share.fileExists(remotePath.trim('/', '\\'))
                SmbResult.Success(exists)
            }
        } catch (e: Exception) {
            e.rethrowIfCancellation()
            Timber.e(e, "Failed to check SMB file existence")
            SmbResult.Error("Failed to check existence: ${e.message}", e)
        }
    }
    
    /**
     * Get detailed file information (metadata).
     */
    suspend fun getFileInfo(
        connectionInfo: SmbConnectionInfo,
        remotePath: String
    ): SmbResult<SmbFileInfo> {
        return try {
            connectionManager.withConnection(connectionInfo) { share ->
                val file = share.openFile(
                    remotePath.trim('/', '\\'),
                    EnumSet.of(AccessMask.GENERIC_READ),
                    null,
                    SMB2ShareAccess.ALL,
                    SMB2CreateDisposition.FILE_OPEN,
                    null
                )
                
                file.use { smbFile ->
                    val fileInfo = smbFile.fileInformation
                    val standardInfo = fileInfo.standardInformation
                    val basicInfo = fileInfo.basicInformation
                    
                    val fileName = remotePath.substringAfterLast('/', remotePath)
                    
                    SmbResult.Success(
                        SmbFileInfo(
                            name = fileName,
                            path = remotePath,
                            isDirectory = standardInfo.isDirectory,
                            size = standardInfo.endOfFile,
                            lastModified = basicInfo.lastWriteTime.toEpochMillis()
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.rethrowIfCancellation()
            Timber.e(e, "Failed to get SMB file info")
            SmbResult.Error("Failed to get file info: ${e.message}", e)
        }
    }
    
    /**
     * Open InputStream for reading file from SMB.
     * Caller is responsible for closing the stream.
     * The stream wrapper ensures the underlying SMB file handle is closed.
     */
    suspend fun openInputStream(
        connectionInfo: SmbConnectionInfo,
        remotePath: String
    ): SmbResult<InputStream> {
        return try {
            connectionManager.withConnection(connectionInfo) { share ->
                val file = share.openFile(
                    remotePath.trim('/', '\\'),
                    EnumSet.of(AccessMask.GENERIC_READ),
                    null,
                    SMB2ShareAccess.ALL,
                    SMB2CreateDisposition.FILE_OPEN,
                    null
                )
                
                // Return wrapper that closes the file when stream is closed
                val inputStream = object : java.io.FilterInputStream(file.inputStream) {
                    override fun close() {
                        try {
                            super.close()
                        } catch (e: Exception) {
                            Timber.w(e, "Error closing SMB input stream")
                        } finally {
                            try {
                                // CRITICAL FIX: Clear interruption status before closing file handle.
                                // If the thread is interrupted (e.g. Coil cancellation), smbj will fail to send
                                // the Close packet and might tear down the connection.
                                // We save the status to restore it later if needed, but for now we want the Close to succeed.
                                val interrupted = Thread.interrupted()
                                file.close()
                                if (interrupted) {
                                    Thread.currentThread().interrupt() // Restore status
                                }
                            } catch (e: Exception) {
                                Timber.w(e, "Error closing SMB file handle")
                            }
                        }
                    }
                }
                
                SmbResult.Success(inputStream)
            }
        } catch (e: Exception) {
            e.rethrowIfCancellation()
            Timber.e(e, "Failed to open SMB input stream")
            SmbResult.Error("Failed to open stream: ${e.message}", e)
        }
    }
    
    /**
     * Ensures directory exists on SMB share by recursively creating parent directories.
     */
    private fun ensureSmbDirectoryExists(share: DiskShare, path: String) {
        val parts = path.replace('\\', '/').split('/').filter { it.isNotEmpty() }
        var currentPath = ""
        for (part in parts) {
            currentPath = if (currentPath.isEmpty()) part else "$currentPath/$part"
            if (!share.folderExists(currentPath)) {
                Timber.d("ensureSmbDirectoryExists: Creating $currentPath")
                try {
                    share.mkdir(currentPath)
                    Timber.d("ensureSmbDirectoryExists: Successfully created $currentPath")
                } catch (e: Exception) {
                    // Check if directory was created despite exception (race condition)
                    if (!share.folderExists(currentPath)) {
                        Timber.e(e, "ensureSmbDirectoryExists: Failed to create $currentPath")
                        throw e
                    } else {
                        Timber.d("ensureSmbDirectoryExists: Directory $currentPath already exists")
                    }
                }
            }
        }
    }
}
