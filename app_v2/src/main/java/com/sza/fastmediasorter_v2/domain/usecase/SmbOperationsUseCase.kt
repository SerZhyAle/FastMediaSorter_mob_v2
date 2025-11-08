package com.sza.fastmediasorter_v2.domain.usecase

import com.sza.fastmediasorter_v2.core.di.IoDispatcher
import com.sza.fastmediasorter_v2.data.local.db.NetworkCredentialsDao
import com.sza.fastmediasorter_v2.data.local.db.NetworkCredentialsEntity
import com.sza.fastmediasorter_v2.data.network.SmbClient
import com.sza.fastmediasorter_v2.domain.model.MediaFile
import com.sza.fastmediasorter_v2.domain.model.MediaResource
import com.sza.fastmediasorter_v2.domain.model.MediaType
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

/**
 * Use case for SMB/CIFS network operations
 */
class SmbOperationsUseCase @Inject constructor(
    private val smbClient: SmbClient,
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
            val entity = NetworkCredentialsEntity(
                credentialId = credentialId,
                type = "SMB",
                server = server,
                port = port,
                username = username,
                password = password,
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
}
