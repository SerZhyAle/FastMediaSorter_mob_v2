package com.sza.fastmediasorter.data.transfer

import com.sza.fastmediasorter.data.network.SmbClient
import com.sza.fastmediasorter.utils.MediaStoreNotifier
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import com.sza.fastmediasorter.data.network.model.SmbConnectionInfo
import com.sza.fastmediasorter.data.network.model.SmbResult
import com.sza.fastmediasorter.domain.repository.NetworkCredentialsRepository
import com.sza.fastmediasorter.domain.transfer.FileTransferProvider
import com.sza.fastmediasorter.domain.usecase.ByteProgressCallback
import com.sza.fastmediasorter.utils.SmbPathUtils
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
    private val smbClient: SmbClient,
    private val credentialsRepository: NetworkCredentialsRepository
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
        val pathInfo = SmbPathUtils.parseSmbPath(path)
            ?: throw IllegalArgumentException("Invalid SMB path format: $path")
        return Triple(
            pathInfo.connectionInfo.server,
            pathInfo.connectionInfo.shareName,
            pathInfo.remotePath
        )
    }
    
    private suspend fun getConnectionInfo(path: String): SmbConnectionInfo {
        val pathInfo = SmbPathUtils.parseSmbPath(path)
            ?: throw IllegalArgumentException("Invalid SMB path format: $path")

        val server = pathInfo.connectionInfo.server
        val shareName = pathInfo.connectionInfo.shareName
        val credentials = resolveSmbCredentials(server, shareName)
            ?: throw IllegalStateException("No SMB credentials configured for '$server/$shareName'")

        return pathInfo.connectionInfo.copy(
            username = credentials.username,
            password = credentials.password,
            domain = credentials.domain
        )
    }

    private suspend fun resolveSmbCredentials(
        server: String,
        shareName: String
    ): com.sza.fastmediasorter.data.local.db.NetworkCredentialsEntity? {
        val normalizedShare = shareName.trim().trim('/', '\\')
        val firstSegment = normalizedShare.substringBefore('/', normalizedShare)

        val shareCandidates = linkedSetOf<String>().apply {
            add(shareName)
            if (normalizedShare.isNotEmpty()) {
                add(normalizedShare)
            }
            if (firstSegment.isNotEmpty()) {
                add(firstSegment)
            }
        }

        for (candidate in shareCandidates) {
            val candidateCredentials = credentialsRepository.getByServerAndShare(server, candidate)
            if (candidateCredentials != null) {
                Timber.d("SmbTransferProvider: Credentials resolved for server='$server', share='$candidate'")
                return candidateCredentials
            }
        }

        val hostCredentials = credentialsRepository.getCredentialsByHost(server)
        if (hostCredentials != null && hostCredentials.type.equals("SMB", ignoreCase = true)) {
            Timber.w("SmbTransferProvider: Share credentials not found for '$server/$shareName', using host SMB credentials")
            return hostCredentials
        }

        Timber.w("SmbTransferProvider: No SMB credentials found for '$server/$shareName'")
        return null
    }
    
    override suspend fun downloadFile(
        sourcePath: String,
        destinationFile: File,
        onProgress: ((Long, Long) -> Unit)?
    ): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext try {
            val (_, _, remotePath) = parseSmbPath(sourcePath)
            val connectionInfo = getConnectionInfo(sourcePath)

            val fileSize = when (val fileInfoResult = smbClient.getFileInfo(connectionInfo, remotePath)) {
                is SmbResult.Success -> fileInfoResult.data.size
                is SmbResult.Error -> {
                    Timber.w(fileInfoResult.exception, "SMB file size lookup failed, fallback to unknown size: ${fileInfoResult.message}")
                    0L
                }
            }
            
            destinationFile.outputStream().use { output ->
                when (val result = smbClient.downloadFile(
                    connectionInfo = connectionInfo,
                    remotePath = remotePath,
                    localOutputStream = output,
                    fileSize = fileSize,
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
