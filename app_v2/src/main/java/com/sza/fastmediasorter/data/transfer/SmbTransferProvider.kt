package com.sza.fastmediasorter.data.transfer

import com.sza.fastmediasorter.data.network.SmbClient
import com.sza.fastmediasorter.utils.MediaStoreNotifier
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import com.sza.fastmediasorter.data.network.model.SmbConnectionInfo
import com.sza.fastmediasorter.data.network.model.SmbResult
import com.sza.fastmediasorter.domain.transfer.FileTransferProvider
import com.sza.fastmediasorter.domain.usecase.ByteProgressCallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SMB protocol implementation of FileTransferProvider.
 * Wraps SmbClient to provide unified file transfer interface.
 * 
 * Path format: "smb://server/share/path/to/file.ext"
 * Connection info extracted from NetworkCredentialsRepository using server+share.
 */
@Singleton
class SmbTransferProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val smbClient: SmbClient
) : FileTransferProvider {
    
    override val protocolName: String = "SMB"
    
    /**
     * Convert simple (Long, Long) -> Unit callback to ByteProgressCallback interface.
     */
    private fun adaptProgressCallback(onProgress: ((Long, Long) -> Unit)?): ByteProgressCallback? {
        return onProgress?.let { callback ->
            object : ByteProgressCallback {
                override suspend fun onProgress(bytesTransferred: Long, totalBytes: Long, speedBytesPerSecond: Long) {
                    callback(bytesTransferred, totalBytes)
                }
            }
        }
    }
    
    /**
     * Parse SMB path format: smb://server/share/path/to/file.ext
     * Returns Triple(server, shareName, remotePath)
     */
    private fun parseSmbPath(path: String): Triple<String, String, String> {
        require(path.startsWith("smb://")) { "Invalid SMB path format: $path" }
        
        val pathWithoutProtocol = path.substringAfter("smb://")
        val parts = pathWithoutProtocol.split("/", limit = 3)
        
        require(parts.size >= 2) { "SMB path must contain server and share: $path" }
        
        val server = parts[0]
        val shareName = parts[1]
        val remotePath = if (parts.size > 2) parts[2] else ""
        
        return Triple(server, shareName, remotePath)
    }
    
    /**
     * Get connection info from path.
     * TODO: Integrate with NetworkCredentialsRepository to fetch username/password/domain.
     * For now, uses empty credentials (requires anonymous access or saved credentials in SmbClient cache).
     */
    private fun getConnectionInfo(path: String): SmbConnectionInfo {
        val (server, shareName, _) = parseSmbPath(path)
        
        // TODO: Fetch credentials from NetworkCredentialsRepository
        // val credentials = credentialsRepository.getCredentials(server, shareName)
        
        return SmbConnectionInfo(
            server = server,
            shareName = shareName,
            username = "", // TODO: credentials.username
            password = "", // TODO: credentials.password
            domain = "",   // TODO: credentials.domain
            port = 445
        )
    }
    
    override suspend fun downloadFile(
        sourcePath: String,
        destinationFile: File,
        onProgress: ((Long, Long) -> Unit)?
    ): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext try {
            val (_, _, remotePath) = parseSmbPath(sourcePath)
            val connectionInfo = getConnectionInfo(sourcePath)
            
            destinationFile.outputStream().use { output ->
                when (val result = smbClient.downloadFile(
                    connectionInfo = connectionInfo,
                    remotePath = remotePath,
                    localOutputStream = output,
                    fileSize = 0L, // TODO: Get file size from getFileInfo first
                    progressCallback = adaptProgressCallback(onProgress)
                )) {
                    is SmbResult.Success -> {
                        MediaStoreNotifier.notifyFile(context, destinationFile.absolutePath, "smb-transfer-download")
                        Result.success(Unit)
                    }
                    is SmbResult.Error -> {
                        Timber.e(result.exception, "SMB download failed: ${result.message}")
                        Result.failure(result.exception ?: Exception(result.message))
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "SMB download exception")
            Result.failure(e)
        }
    }
    
    override suspend fun uploadFile(
        sourceFile: File,
        destinationPath: String,
        onProgress: ((Long, Long) -> Unit)?
    ): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext try {
            val (_, _, remotePath) = parseSmbPath(destinationPath)
            val connectionInfo = getConnectionInfo(destinationPath)
            
            sourceFile.inputStream().use { input ->
                when (val result = smbClient.uploadFile(
                    connectionInfo = connectionInfo,
                    remotePath = remotePath,
                    localInputStream = input,
                    fileSize = sourceFile.length(),
                    progressCallback = adaptProgressCallback(onProgress)
                )) {
                    is SmbResult.Success -> Result.success(Unit)
                    is SmbResult.Error -> {
                        Timber.e(result.exception, "SMB upload failed: ${result.message}")
                        Result.failure(result.exception ?: Exception(result.message))
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "SMB upload exception")
            Result.failure(e)
        }
    }
    
    override suspend fun deleteFile(path: String): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext try {
            val (_, _, remotePath) = parseSmbPath(path)
            val connectionInfo = getConnectionInfo(path)
            
            when (val result = smbClient.deleteFile(connectionInfo, remotePath)) {
                is SmbResult.Success -> Result.success(Unit)
                is SmbResult.Error -> {
                    Timber.e(result.exception, "SMB delete failed: ${result.message}")
                    Result.failure(result.exception ?: Exception(result.message))
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "SMB delete exception")
            Result.failure(e)
        }
    }
    
    override suspend fun renameFile(
        oldPath: String,
        newPath: String
    ): Result<String> = withContext(Dispatchers.IO) {
        return@withContext try {
            val (_, _, oldRemotePath) = parseSmbPath(oldPath)
            val (_, _, newRemotePath) = parseSmbPath(newPath)
            val connectionInfo = getConnectionInfo(oldPath)
            
            // Verify both paths are on same share
            val (oldServer, oldShare, _) = parseSmbPath(oldPath)
            val (newServer, newShare, _) = parseSmbPath(newPath)
            require(oldServer == newServer && oldShare == newShare) {
                "Cannot rename across different SMB shares"
            }
            
            when (val result = smbClient.renameFile(connectionInfo, oldRemotePath, newRemotePath)) {
                is SmbResult.Success -> Result.success(newPath)
                is SmbResult.Error -> {
                    Timber.e(result.exception, "SMB rename failed: ${result.message}")
                    Result.failure(result.exception ?: Exception(result.message))
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "SMB rename exception")
            Result.failure(e)
        }
    }
    
    override suspend fun moveFile(
        sourcePath: String,
        destinationPath: String
    ): Result<String> = withContext(Dispatchers.IO) {
        return@withContext try {
            val (_, _, sourceRemotePath) = parseSmbPath(sourcePath)
            val (_, _, destRemotePath) = parseSmbPath(destinationPath)
            val connectionInfo = getConnectionInfo(sourcePath)
            
            // Verify both paths are on same share (SMB move requires same share)
            val (srcServer, srcShare, _) = parseSmbPath(sourcePath)
            val (dstServer, dstShare, _) = parseSmbPath(destinationPath)
            require(srcServer == dstServer && srcShare == dstShare) {
                "Cannot move across different SMB shares. Use cross-protocol transfer instead."
            }
            
            when (val result = smbClient.moveFile(connectionInfo, sourceRemotePath, destRemotePath)) {
                is SmbResult.Success -> Result.success(destinationPath)
                is SmbResult.Error -> {
                    Timber.e(result.exception, "SMB move failed: ${result.message}")
                    Result.failure(result.exception ?: Exception(result.message))
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "SMB move exception")
            Result.failure(e)
        }
    }
    
    override suspend fun exists(path: String): Result<Boolean> = withContext(Dispatchers.IO) {
        return@withContext try {
            val (_, _, remotePath) = parseSmbPath(path)
            val connectionInfo = getConnectionInfo(path)
            
            when (val result = smbClient.exists(connectionInfo, remotePath)) {
                is SmbResult.Success -> Result.success(result.data)
                is SmbResult.Error -> {
                    // exists() returning false for missing files is not an error
                    Timber.d("SMB exists check: ${result.message}")
                    Result.success(false)
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "SMB exists exception")
            Result.failure(e)
        }
    }
    
    override suspend fun getFileInfo(path: String): Result<com.sza.fastmediasorter.domain.transfer.FileInfo> = withContext(Dispatchers.IO) {
        return@withContext try {
            val (_, _, remotePath) = parseSmbPath(path)
            val connectionInfo = getConnectionInfo(path)
            
            when (val result = smbClient.getFileInfo(connectionInfo, remotePath)) {
                is SmbResult.Success -> {
                    val info = result.data
                    Result.success(
                        com.sza.fastmediasorter.domain.transfer.FileInfo(
                            path = path,
                            name = info.name,
                            size = info.size,
                            lastModified = info.lastModified,
                            isDirectory = info.isDirectory
                        )
                    )
                }
                is SmbResult.Error -> {
                    Timber.e(result.exception, "SMB getFileInfo failed: ${result.message}")
                    Result.failure(result.exception ?: Exception(result.message))
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "SMB getFileInfo exception")
            Result.failure(e)
        }
    }
    
    override suspend fun createDirectory(path: String): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext try {
            val (_, _, remotePath) = parseSmbPath(path)
            val connectionInfo = getConnectionInfo(path)
            
            when (val result = smbClient.createDirectory(connectionInfo, remotePath)) {
                is SmbResult.Success -> Result.success(Unit)
                is SmbResult.Error -> {
                    Timber.e(result.exception, "SMB createDirectory failed: ${result.message}")
                    Result.failure(result.exception ?: Exception(result.message))
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "SMB createDirectory exception")
            Result.failure(e)
        }
    }
    
    override suspend fun isFile(path: String): Result<Boolean> = withContext(Dispatchers.IO) {
        return@withContext try {
            val infoResult = getFileInfo(path)
            if (infoResult.isSuccess) {
                val fileInfo = infoResult.getOrThrow()
                Result.success(!fileInfo.isDirectory)
            } else {
                Result.failure(infoResult.exceptionOrNull() ?: Exception("Failed to get file info"))
            }
        } catch (e: Exception) {
            Timber.e(e, "SMB isFile exception")
            Result.failure(e)
        }
    }
}
