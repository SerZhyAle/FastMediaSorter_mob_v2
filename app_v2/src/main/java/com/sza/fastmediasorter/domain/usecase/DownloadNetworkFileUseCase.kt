package com.sza.fastmediasorter.domain.usecase

import android.content.Context
import com.sza.fastmediasorter.core.util.PathUtils
import com.sza.fastmediasorter.data.network.SmbClient
import com.sza.fastmediasorter.data.network.model.SmbConnectionInfo
import com.sza.fastmediasorter.data.network.model.SmbResult
import com.sza.fastmediasorter.data.remote.ftp.FtpClient
import com.sza.fastmediasorter.data.remote.sftp.SftpClient
import com.sza.fastmediasorter.domain.repository.NetworkCredentialsRepository
import com.sza.fastmediasorter.utils.MediaStoreNotifier
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

/**
 * UseCase for downloading network files (SMB/S/FTP) to local storage
 */
class DownloadNetworkFileUseCase @Inject constructor(
    private val smbClient: SmbClient,
    private val sftpClient: SftpClient,
    private val ftpClient: FtpClient,
    private val credentialsRepository: NetworkCredentialsRepository,
    @ApplicationContext private val context: Context
) {
    
    /**
     * Download file to local storage with progress callback
     * @param remotePath Full path (smb://, sftp://, ftp://)
     * @param targetFile Target file on local storage
     * @param progressCallback Callback for progress updates (0-100)
     * @return true if successful, false otherwise
     */
    suspend fun execute(
        remotePath: String,
        targetFile: File,
        progressCallback: ((Int) -> Unit)? = null
    ): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            val success = when {
                remotePath.startsWith("smb://") || remotePath.startsWith("smb:/") -> downloadSmbFile(remotePath, targetFile, progressCallback)
                remotePath.startsWith("sftp://") || remotePath.startsWith("sftp:/") -> downloadSftpFile(remotePath, targetFile, progressCallback)
                remotePath.startsWith("ftp://") || remotePath.startsWith("ftp:/") -> downloadFtpFile(remotePath, targetFile)
                else -> {
                    Timber.e("Unsupported protocol: $remotePath")
                    false
                }
            }
            
            if (success) {
                MediaStoreNotifier.notifyFile(context, targetFile.absolutePath, "download")
            }
            
            success
        } catch (e: Exception) {
            Timber.e(e, "Error downloading file: $remotePath")
            false
        }
    }
    
    private suspend fun downloadSmbFile(
        remotePath: String,
        targetFile: File,
        progressCallback: ((Int) -> Unit)?
    ): Boolean {
        // Parse SMB URL: smb://server/share/path/to/file
        val uri = PathUtils.safeParseUri(remotePath)
        val server = uri.host ?: return false
        val pathSegments = uri.pathSegments
        if (pathSegments.isEmpty()) return false
        
        val shareName = pathSegments[0]
        val filePathInShare = pathSegments.drop(1).joinToString("/")
        
        // Get credentials
        val credentials = credentialsRepository.getByServerAndShare(server, shareName)
            ?: return false
        
        val connectionInfo = SmbConnectionInfo(
            server = server,
            port = credentials.port,
            shareName = shareName,
            username = credentials.username,
            password = credentials.password,
            domain = credentials.domain
        )
        
        val progressCallbackAdapter = if (progressCallback != null) {
            object : ByteProgressCallback {
                override suspend fun onProgress(bytesTransferred: Long, totalBytes: Long, speedBytesPerSecond: Long) {
                    if (totalBytes > 0) {
                        val percentage = ((bytesTransferred * 100) / totalBytes).toInt()
                        progressCallback(percentage)
                    }
                }
            }
        } else null
        
        val result = FileOutputStream(targetFile).use { outputStream ->
            smbClient.downloadFile(
                connectionInfo = connectionInfo,
                remotePath = filePathInShare,
                localOutputStream = outputStream,
                fileSize = 0L,
                progressCallback = progressCallbackAdapter
            )
        }
        
        return when (result) {
            is SmbResult.Success -> true
            is SmbResult.Error -> false
        }
    }
    
    private suspend fun downloadSftpFile(
        remotePath: String,
        targetFile: File,
        progressCallback: ((Int) -> Unit)?
    ): Boolean {
        // Parse SFTP URL: sftp://server:port/path/to/file
        val uri = PathUtils.safeParseUri(remotePath)
        val server = uri.host ?: return false
        val port = uri.port.takeIf { it > 0 } ?: 22
        val filePath = uri.path ?: return false
        
        // Get credentials
        val credentials = credentialsRepository.getByTypeServerAndPort("sftp", server, port)
            ?: return false
        
        val connectionInfo = SftpClient.SftpConnectionInfo(
            host = server,
            port = port,
            username = credentials.username,
            password = credentials.password
        )
        
        val progressCallbackAdapter = if (progressCallback != null) {
            object : ByteProgressCallback {
                override suspend fun onProgress(bytesTransferred: Long, totalBytes: Long, speedBytesPerSecond: Long) {
                    if (totalBytes > 0) {
                        val percentage = ((bytesTransferred * 100) / totalBytes).toInt()
                        progressCallback(percentage)
                    }
                }
            }
        } else null
        
        val result = FileOutputStream(targetFile).use { outputStream ->
            sftpClient.downloadFile(
                connectionInfo = connectionInfo,
                remotePath = filePath,
                outputStream = outputStream,
                fileSize = 0L,
                progressCallback = progressCallbackAdapter
            )
        }
        
        return result.isSuccess
    }
    
    private suspend fun downloadFtpFile(
        remotePath: String,
        targetFile: File
    ): Boolean {
        // Parse FTP URL: ftp://server:port/path/to/file
        val uri = PathUtils.safeParseUri(remotePath)
        val server = uri.host ?: return false
        val port = uri.port.takeIf { it > 0 } ?: 21
        val filePath = uri.path ?: return false
        
        // Get credentials
        val credentials = credentialsRepository.getByTypeServerAndPort("ftp", server, port)
            ?: return false
        
        // Connect to FTP
        val connectResult = ftpClient.connect(
            host = server,
            port = port,
            username = credentials.username,
            password = credentials.password
        )
        
        if (connectResult.isFailure) {
            return false
        }
        
        val result = FileOutputStream(targetFile).use { outputStream ->
            ftpClient.downloadFile(
                remotePath = filePath,
                outputStream = outputStream,
                fileSize = 0L,
                progressCallback = null // FTP client doesn't use progress callback
            )
        }
        
        ftpClient.disconnect()
        
        return result.isSuccess
    }
}
