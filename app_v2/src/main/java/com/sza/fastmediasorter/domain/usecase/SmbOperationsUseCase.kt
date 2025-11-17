package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.core.di.IoDispatcher
import com.sza.fastmediasorter.data.local.db.NetworkCredentialsDao
import com.sza.fastmediasorter.data.local.db.NetworkCredentialsEntity
import com.sza.fastmediasorter.data.network.SmbClient
import com.sza.fastmediasorter.data.remote.ftp.FtpClient
import com.sza.fastmediasorter.data.remote.sftp.SftpClient
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.MediaType
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

/**
 * Use case for SMB/CIFS, SFTP and FTP network operations
 */
class SmbOperationsUseCase @Inject constructor(
    private val smbClient: SmbClient,
    private val sftpClient: SftpClient,
    private val ftpClient: FtpClient,
    private val credentialsDao: NetworkCredentialsDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    
    /**
     * Test SMB connection with given credentials
     */
    suspend fun testConnection(
        server: String,
        shareName: String,
        username: String,
        password: String,
        domain: String = "",
        port: Int = 445
    ): Result<String> = withContext(ioDispatcher) {
        try {
            val connectionInfo = SmbClient.SmbConnectionInfo(
                server = server,
                shareName = shareName,
                username = username,
                password = password,
                domain = domain,
                port = port
            )
            
            when (val result = smbClient.testConnection(connectionInfo)) {
                is SmbClient.SmbResult.Success -> Result.success(result.data)
                is SmbClient.SmbResult.Error -> Result.failure(
                    Exception(result.message, result.exception)
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "SMB test connection failed")
            Result.failure(e)
        }
    }
    
    /**
     * List available shares on SMB server
     */
    suspend fun listShares(
        server: String,
        username: String = "",
        password: String = "",
        domain: String = "",
        port: Int = 445
    ): Result<List<String>> = withContext(ioDispatcher) {
        try {
            when (val result = smbClient.listShares(server, username, password, domain, port)) {
                is SmbClient.SmbResult.Success -> Result.success(result.data)
                is SmbClient.SmbResult.Error -> Result.failure(
                    Exception(result.message, result.exception)
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to list SMB shares")
            Result.failure(e)
        }
    }
    
    /**
     * Scan SMB folder for media files
     */
    suspend fun scanMediaFiles(
        server: String,
        shareName: String,
        remotePath: String = "",
        username: String,
        password: String,
        domain: String = "",
        port: Int = 445
    ): Result<List<MediaFile>> = withContext(ioDispatcher) {
        try {
            val connectionInfo = SmbClient.SmbConnectionInfo(
                server = server,
                shareName = shareName,
                username = username,
                password = password,
                domain = domain,
                port = port
            )
            
            when (val result = smbClient.scanMediaFiles(connectionInfo, remotePath)) {
                is SmbClient.SmbResult.Success -> {
                    val mediaFiles = result.data.map { smbFile ->
                        MediaFile(
                            name = smbFile.name,
                            path = smbFile.path,
                            type = detectMediaType(smbFile.name),
                            size = smbFile.size,
                            createdDate = smbFile.lastModified
                        )
                    }
                    Result.success(mediaFiles)
                }
                is SmbClient.SmbResult.Error -> Result.failure(
                    Exception(result.message, result.exception)
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to scan SMB media files")
            Result.failure(e)
        }
    }
    
    /**
     * Save SMB credentials to database
     */
    suspend fun saveCredentials(
        server: String,
        shareName: String,
        username: String,
        password: String,
        domain: String = "",
        port: Int = 445
    ): Result<String> = withContext(ioDispatcher) {
        try {
            val credentialId = UUID.randomUUID().toString()
            val entity = NetworkCredentialsEntity.create(
                credentialId = credentialId,
                type = "SMB",
                server = server,
                port = port,
                username = username,
                plaintextPassword = password,
                domain = domain,
                shareName = shareName
            )
            
            credentialsDao.insert(entity)
            Result.success(credentialId)
        } catch (e: Exception) {
            Timber.e(e, "Failed to save SMB credentials")
            Result.failure(e)
        }
    }
    
    /**
     * Get SMB connection info from credentials ID
     */
    suspend fun getConnectionInfo(credentialsId: String): Result<SmbClient.SmbConnectionInfo> = 
        withContext(ioDispatcher) {
            try {
                val credentials = credentialsDao.getCredentialsById(credentialsId)
                    ?: return@withContext Result.failure(Exception("Credentials not found"))
                
                if (credentials.type != "SMB") {
                    return@withContext Result.failure(Exception("Invalid credentials type"))
                }
                
                val connectionInfo = SmbClient.SmbConnectionInfo(
                    server = credentials.server,
                    shareName = credentials.shareName ?: "",
                    username = credentials.username,
                    password = credentials.password,
                    domain = credentials.domain,
                    port = credentials.port
                )
                
                Result.success(connectionInfo)
            } catch (e: Exception) {
                Timber.e(e, "Failed to get SMB connection info")
                Result.failure(e)
            }
        }
    
    /**
     * List files in SMB directory
     */
    suspend fun listFiles(
        resource: MediaResource,
        remotePath: String = ""
    ): Result<List<MediaFile>> = withContext(ioDispatcher) {
        try {
            val credentialsId = resource.credentialsId
                ?: return@withContext Result.failure(Exception("No credentials for resource"))
            
            val connectionInfoResult = getConnectionInfo(credentialsId)
            if (connectionInfoResult.isFailure) {
                return@withContext Result.failure(connectionInfoResult.exceptionOrNull()!!)
            }
            
            val connectionInfo = connectionInfoResult.getOrNull()!!
            
            when (val result = smbClient.listFiles(connectionInfo, remotePath)) {
                is SmbClient.SmbResult.Success -> {
                    val mediaFiles = result.data
                        .filter { !it.isDirectory }
                        .map { smbFile ->
                            MediaFile(
                                name = smbFile.name,
                                path = smbFile.path,
                                type = detectMediaType(smbFile.name),
                                size = smbFile.size,
                                createdDate = smbFile.lastModified
                            )
                        }
                    Result.success(mediaFiles)
                }
                is SmbClient.SmbResult.Error -> Result.failure(
                    Exception(result.message, result.exception)
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to list SMB files")
            Result.failure(e)
        }
    }
    
    /**
     * Detect media type from file extension
     */
    private fun detectMediaType(fileName: String): MediaType {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        return when (extension) {
            "jpg", "jpeg", "png", "bmp", "webp" -> MediaType.IMAGE
            "gif" -> MediaType.GIF
            "mp4", "mov", "avi", "mkv", "wmv", "flv", "webm" -> MediaType.VIDEO
            "mp3", "wav", "aac", "flac", "ogg", "m4a" -> MediaType.AUDIO
            else -> MediaType.IMAGE // Default
        }
    }
    
    // ========== SFTP Operations ==========
    
    /**
     * Test SFTP connection with given credentials (password or private key)
     */
    suspend fun testSftpConnection(
        host: String,
        port: Int = 22,
        username: String,
        password: String,
        privateKey: String? = null,
        keyPassphrase: String? = null
    ): Result<String> = withContext(ioDispatcher) {
        try {
            val result = if (privateKey != null) {
                sftpClient.testConnectionWithPrivateKey(host, port, username, privateKey, keyPassphrase)
            } else {
                sftpClient.testConnection(host, port, username, password)
            }
            
            if (result.isSuccess) {
                val authMethod = if (privateKey != null) "private key" else "password"
                Result.success("SFTP connection successful to $host:$port using $authMethod")
            } else {
                Result.failure(result.exceptionOrNull() ?: Exception("SFTP connection failed"))
            }
        } catch (e: Exception) {
            Timber.e(e, "SFTP test connection failed")
            Result.failure(e)
        }
    }
    
    /**
     * Save SFTP credentials to database (password or private key)
     */
    suspend fun saveSftpCredentials(
        host: String,
        port: Int = 22,
        username: String,
        password: String,
        privateKey: String? = null
    ): Result<String> = withContext(ioDispatcher) {
        try {
            val credentialId = UUID.randomUUID().toString()
            val entity = NetworkCredentialsEntity.create(
                credentialId = credentialId,
                type = "SFTP",
                server = host,
                port = port,
                username = username,
                plaintextPassword = password,
                domain = "", // Not used for SFTP
                shareName = null, // Not used for SFTP
                sshPrivateKey = privateKey // SSH private key (encrypted)
            )
            
            credentialsDao.insert(entity)
            Result.success(credentialId)
        } catch (e: Exception) {
            Timber.e(e, "Failed to save SFTP credentials")
            Result.failure(e)
        }
    }
    
    /**
     * Test FTP connection with given credentials
     */
    suspend fun testFtpConnection(
        host: String,
        port: Int = 21,
        username: String,
        password: String
    ): Result<String> = withContext(ioDispatcher) {
        try {
            val result = ftpClient.testConnection(host, port, username, password)
            if (result.isSuccess) {
                Result.success("FTP connection successful to $host:$port (passive mode)")
            } else {
                Result.failure(result.exceptionOrNull() ?: Exception("FTP connection failed"))
            }
        } catch (e: Exception) {
            Timber.e(e, "FTP test connection failed")
            Result.failure(e)
        }
    }
    
    /**
     * Save FTP credentials to database
     */
    suspend fun saveFtpCredentials(
        host: String,
        port: Int = 21,
        username: String,
        password: String
    ): Result<String> = withContext(ioDispatcher) {
        try {
            val credentialId = UUID.randomUUID().toString()
            val entity = NetworkCredentialsEntity.create(
                credentialId = credentialId,
                type = "FTP",
                server = host,
                port = port,
                username = username,
                plaintextPassword = password,
                domain = "", // Not used for FTP
                shareName = null // Not used for FTP
            )
            
            credentialsDao.insert(entity)
            Result.success(credentialId)
        } catch (e: Exception) {
            Timber.e(e, "Failed to save FTP credentials")
            Result.failure(e)
        }
    }
    
    /**
     * Get SFTP credentials by credential ID
     */
    suspend fun getSftpCredentials(credentialsId: String): Result<NetworkCredentialsEntity> =
        withContext(ioDispatcher) {
            try {
                val credentials = credentialsDao.getCredentialsById(credentialsId)
                    ?: return@withContext Result.failure(Exception("Credentials not found"))
                
                if (credentials.type != "SFTP") {
                    return@withContext Result.failure(Exception("Invalid credentials type: expected SFTP"))
                }
                
                Result.success(credentials)
            } catch (e: Exception) {
                Timber.e(e, "Failed to get SFTP credentials")
                Result.failure(e)
            }
        }
    
    /**
     * List files in SFTP directory
     */
    suspend fun listSftpFiles(
        host: String,
        port: Int = 22,
        username: String,
        password: String,
        remotePath: String = "/"
    ): Result<List<MediaFile>> = withContext(ioDispatcher) {
        try {
            val connectResult = sftpClient.connect(host, port, username, password)
            if (connectResult.isFailure) {
                return@withContext Result.failure(connectResult.exceptionOrNull() ?: Exception("Connection failed"))
            }
            
            val listResult = sftpClient.listFiles(remotePath)
            sftpClient.disconnect()
            
            if (listResult.isFailure) {
                return@withContext Result.failure(listResult.exceptionOrNull() ?: Exception("List files failed"))
            }
            
            val filePaths = listResult.getOrNull() ?: emptyList()
            val mediaFiles = filePaths.map { filePath ->
                val fileName = filePath.substringAfterLast('/')
                MediaFile(
                    name = fileName,
                    path = filePath,
                    type = detectMediaType(fileName),
                    size = 0L, // Size not available without additional stat() call
                    createdDate = System.currentTimeMillis() // Date not available without additional stat() call
                )
            }
            
            Result.success(mediaFiles)
        } catch (e: Exception) {
            sftpClient.disconnect()
            Timber.e(e, "Failed to list SFTP files")
            Result.failure(e)
        }
    }
    
    /**
     * List files in SFTP directory using saved credentials
     */
    suspend fun listSftpFilesWithCredentials(
        credentialsId: String,
        remotePath: String = "/"
    ): Result<List<MediaFile>> = withContext(ioDispatcher) {
        try {
            val credentialsResult = getSftpCredentials(credentialsId)
            if (credentialsResult.isFailure) {
                return@withContext Result.failure(credentialsResult.exceptionOrNull()!!)
            }
            
            val credentials = credentialsResult.getOrNull()!!
            listSftpFiles(
                host = credentials.server,
                port = credentials.port,
                username = credentials.username,
                password = credentials.password,
                remotePath = remotePath
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to list SFTP files with credentials")
            Result.failure(e)
        }
    }
}
