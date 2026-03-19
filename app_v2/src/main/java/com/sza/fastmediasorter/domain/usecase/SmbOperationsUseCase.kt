package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.core.di.IoDispatcher
import com.sza.fastmediasorter.data.local.db.NetworkCredentialsEntity
import com.sza.fastmediasorter.data.network.SmbClient
import com.sza.fastmediasorter.data.network.model.SmbConnectionInfo
import com.sza.fastmediasorter.data.network.model.SmbResult
import com.sza.fastmediasorter.data.remote.ftp.FtpClient
import com.sza.fastmediasorter.data.remote.sftp.SftpClient
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.repository.NetworkCredentialsRepository
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
    private val credentialsRepository: NetworkCredentialsRepository,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher
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
            // Parse share name and path (e.g. "share\folder\subfolder" -> share="share", path="folder\subfolder")
            // This allows users to enter "share\subfolder" in the Share Name field
            val normalizedShareName = shareName.replace('\\', '/')
            val parts = normalizedShareName.split('/', limit = 2)
            val actualShareName = parts.getOrElse(0) { "" }
            val subPath = parts.getOrElse(1) { "" }

            val connectionInfo = SmbConnectionInfo(
                server = server,
                shareName = actualShareName,
                username = username,
                password = password,
                domain = domain,
                port = port
            )
            
            when (val result = smbClient.testConnection(connectionInfo, subPath)) {
                is SmbResult.Success -> Result.success(result.data)
                is SmbResult.Error -> {
                    // Augmented Debugging: If subpath failed, list the root to see what IS there
                    if (subPath.isNotEmpty()) {
                        Timber.w("SMB connection failed for subpath '$subPath'. Listing root share contents for debugging...")
                        try {
                            when (val listResult = smbClient.listFiles(connectionInfo, "")) {
                                is SmbResult.Success -> {
                                    val files = listResult.data.joinToString(", ") { "${it.name}${if(it.isDirectory) "/" else ""}" }
                                    Timber.d("SMB Share '${connectionInfo.shareName}' contains: $files")
                                    Timber.d("Looking for '$subPath' in above list.")
                                }
                                is SmbResult.Error -> {
                                    Timber.e("Could not list root share either: ${listResult.message}")
                                }
                            }
                        } catch (e: Exception) {
                            Timber.e(e, "Error during debug listing of share root")
                        }
                    }
                    Result.failure(
                        Exception(result.message, result.exception)
                    )
                }
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
                is SmbResult.Success -> Result.success(result.data)
                is SmbResult.Error -> Result.failure(
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
            val connectionInfo = SmbConnectionInfo(
                server = server,
                shareName = shareName,
                username = username,
                password = password,
                domain = domain,
                port = port
            )
            
            when (val result = smbClient.scanMediaFiles(connectionInfo, remotePath)) {
                is SmbResult.Success -> {
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
                is SmbResult.Error -> Result.failure(
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
            // Parse share name and path - only save the actual share name in credentials
            // The full path (including subfolder) will be stored in MediaResource.path
            // This allows users to enter "share\subfolder" in the Share Name field
            val normalizedShareName = shareName.replace('\\', '/')
            val parts = normalizedShareName.split('/', limit = 2)
            val actualShareName = parts.getOrElse(0) { "" }

            // Check if credentials already exist for this server+share combination
            val existingCredentials = credentialsRepository.getByServerAndShare(server, actualShareName)
            
            if (existingCredentials != null) {
                // Update existing credentials instead of creating new ones
                Timber.d("saveCredentials: Updating existing credentials for $server/$actualShareName (id=${existingCredentials.credentialId})")
                val updatedEntity = existingCredentials.copy(
                    username = username,
                    encryptedPassword = com.sza.fastmediasorter.data.local.db.CryptoHelper.encrypt(password) ?: "",
                    domain = domain,
                    port = port
                )
                credentialsRepository.update(updatedEntity)
                Result.success(existingCredentials.credentialId)
            } else {
                // Create new credentials
                val credentialId = UUID.randomUUID().toString()
                Timber.d("saveCredentials: Creating new credentials for $server/$actualShareName (id=$credentialId)")
                val entity = NetworkCredentialsEntity.create(
                    credentialId = credentialId,
                    type = "SMB",
                    server = server,
                    port = port,
                    username = username,
                    plaintextPassword = password,
                    domain = domain,
                    shareName = actualShareName
                )
                
                credentialsRepository.insert(entity)
                Result.success(credentialId)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to save SMB credentials")
            Result.failure(e)
        }
    }
    
    /**
     * Get SMB connection info from credentials ID
     */
    suspend fun getConnectionInfo(credentialsId: String): Result<SmbConnectionInfo> = 
        withContext(ioDispatcher) {
            try {
                val credentials = credentialsRepository.getByCredentialId(credentialsId)
                    ?: return@withContext Result.failure(Exception("Credentials not found"))
                
                if (credentials.type != "SMB") {
                    return@withContext Result.failure(Exception("Invalid credentials type"))
                }
                
                val connectionInfo = SmbConnectionInfo(
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
                val exception = connectionInfoResult.exceptionOrNull() 
                    ?: Exception("Unknown error getting connection info")
                Timber.e(exception, "Failed to get connection info for credentials: $credentialsId")
                return@withContext Result.failure(exception)
            }
            
            val connectionInfo = connectionInfoResult.getOrNull()
                ?: return@withContext Result.failure(Exception("Connection info is null after successful result"))
            
            when (val result = smbClient.listFiles(connectionInfo, remotePath)) {
                is SmbResult.Success -> {
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
                is SmbResult.Error -> Result.failure(
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
        
        // Exclude unsupported formats (AVIF, HEIF - not natively supported by Android)
        if (extension in setOf("avif", "heif", "heic")) {
            throw IllegalArgumentException("Unsupported file format: $extension")
        }
        
        return when (extension) {
            "jpg", "jpeg", "png", "bmp", "webp" -> MediaType.IMAGE
            "gif" -> MediaType.GIF
            "mp4", "mov", "avi", "mkv", "wmv", "flv", "webm" -> MediaType.VIDEO
            "mp3", "aac", "flac", "ogg", "m4a" -> MediaType.AUDIO
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
            // Check if credentials already exist for this host+port combination
            val existingCredentials = credentialsRepository.getByTypeServerAndPort("SFTP", host, port)
            
            if (existingCredentials != null) {
                // Update existing credentials instead of creating new ones
                Timber.d("saveSftpCredentials: Updating existing credentials for $host:$port (id=${existingCredentials.credentialId})")
                val updatedEntity = existingCredentials.copy(
                    username = username,
                    encryptedPassword = com.sza.fastmediasorter.data.local.db.CryptoHelper.encrypt(password) ?: "",
                    sshPrivateKey = privateKey
                )
                credentialsRepository.update(updatedEntity)
                Result.success(existingCredentials.credentialId)
            } else {
                // Create new credentials
                val credentialId = UUID.randomUUID().toString()
                Timber.d("saveSftpCredentials: Creating new credentials for $host:$port (id=$credentialId)")
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
                
                credentialsRepository.insert(entity)
                Result.success(credentialId)
            }
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
            // Check if credentials already exist for this host+port combination
            val existingCredentials = credentialsRepository.getByTypeServerAndPort("FTP", host, port)
            
            if (existingCredentials != null) {
                // Update existing credentials instead of creating new ones
                Timber.d("saveFtpCredentials: Updating existing credentials for $host:$port (id=${existingCredentials.credentialId})")
                val updatedEntity = existingCredentials.copy(
                    username = username,
                    encryptedPassword = com.sza.fastmediasorter.data.local.db.CryptoHelper.encrypt(password) ?: ""
                )
                credentialsRepository.update(updatedEntity)
                Result.success(existingCredentials.credentialId)
            } else {
                // Create new credentials
                val credentialId = UUID.randomUUID().toString()
                Timber.d("saveFtpCredentials: Creating new credentials for $host:$port (id=$credentialId)")
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
                
                credentialsRepository.insert(entity)
                Result.success(credentialId)
            }
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
                val credentials = credentialsRepository.getByCredentialId(credentialsId)
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
     * Get FTP credentials by credential ID
     */
    suspend fun getFtpCredentials(credentialsId: String): Result<NetworkCredentialsEntity> =
        withContext(ioDispatcher) {
            try {
                val credentials = credentialsRepository.getByCredentialId(credentialsId)
                    ?: return@withContext Result.failure(Exception("Credentials not found"))
                
                if (credentials.type != "FTP") {
                    return@withContext Result.failure(Exception("Invalid credentials type: expected FTP"))
                }
                
                Result.success(credentials)
            } catch (e: Exception) {
                Timber.e(e, "Failed to get FTP credentials")
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
            val connectionInfo = SftpClient.SftpConnectionInfo(
                host = host,
                port = port,
                username = username,
                password = password
            )
            
            val listResult = sftpClient.listFiles(connectionInfo, remotePath)
            
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
                val exception = credentialsResult.exceptionOrNull()
                    ?: Exception("Unknown error getting SFTP credentials")
                Timber.e(exception, "Failed to get SFTP credentials: $credentialsId")
                return@withContext Result.failure(exception)
            }
            
            val credentials = credentialsResult.getOrNull()
                ?: return@withContext Result.failure(Exception("SFTP credentials are null after successful result"))
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
    
    // ===== Trash Management =====
    
    /**
     * Check if network resource has trash folders
     * @return Pair<hasTrash, trashFolderNames>
     */
    suspend fun checkTrashFolders(
        type: com.sza.fastmediasorter.domain.model.ResourceType,
        credentialsId: String,
        path: String
    ): Result<Pair<Boolean, List<String>>> {
        return try {
            when (type) {
                com.sza.fastmediasorter.domain.model.ResourceType.SMB -> {
                    val connectionInfo = getConnectionInfo(credentialsId).getOrThrow()
                    
                    // Extract relative path from full URI if present
                    // path format: smb://server/share/subfolder
                    // connectionInfo.shareName: share
                    val relativePath = if (path.startsWith("smb://")) {
                        val withoutProtocol = path.substring(6) // server/share/subfolder
                        val parts = withoutProtocol.split('/', limit = 3)
                        // parts[0] = server
                        // parts[1] = share
                        // parts[2] = subfolder (optional)
                        if (parts.size > 2) parts[2] else ""
                    } else {
                        path // Fallback
                    }
                    
                    val listResult = smbClient.listFiles(connectionInfo, relativePath)
                    when (listResult) {
                        is SmbResult.Success -> {
                            val trashFolders = listResult.data
                                .filter { it.name.startsWith(".trash_") && it.isDirectory }
                                .map { it.name }
                            Result.success(Pair(trashFolders.isNotEmpty(), trashFolders))
                        }
                        is SmbResult.Error -> Result.failure(Exception(listResult.message))
                    }
                }
                com.sza.fastmediasorter.domain.model.ResourceType.SFTP -> {
                    val credentials = getSftpCredentials(credentialsId).getOrThrow()
                    val remotePath = path.substringAfter("://").substringAfter("/")
                    val connectionInfo = SftpClient.SftpConnectionInfo(
                        host = credentials.server,
                        port = credentials.port,
                        username = credentials.username,
                        password = credentials.password,
                        privateKey = credentials.sshPrivateKey
                    )
                    val files = sftpClient.listFiles(connectionInfo, remotePath).getOrDefault(emptyList())
                    val trashFolders = files.filter { it.substringAfterLast('/').startsWith(".trash_") }
                    Result.success(Pair(trashFolders.isNotEmpty(), trashFolders))
                }
                com.sza.fastmediasorter.domain.model.ResourceType.FTP -> {
                    val credentials = credentialsRepository.getByCredentialId(credentialsId) ?: throw Exception("Credentials not found")
                    val remotePath = path.substringAfter("://").substringAfter("/")
                    ftpClient.connect(
                        credentials.server, credentials.port, credentials.username, credentials.password
                    ).getOrThrow()
                    val files = ftpClient.listFiles(remotePath).getOrDefault(emptyList())
                    ftpClient.disconnect()
                    val trashFolders = files.filter { it.startsWith(".trash_") }
                    Result.success(Pair(trashFolders.isNotEmpty(), trashFolders))
                }
                else -> Result.success(Pair(false, emptyList()))
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to check trash folders")
            Result.failure(e)
        }
    }
    
    /**
     * Cleanup all trash folders on network resource
     * @return Number of deleted folders
     */
    suspend fun cleanupTrash(
        type: com.sza.fastmediasorter.domain.model.ResourceType,
        credentialsId: String,
        path: String
    ): Result<Int> {
        return try {
            val (hasTrash, trashFolders) = checkTrashFolders(type, credentialsId, path).getOrThrow()
            if (!hasTrash) return Result.success(0)
            
            var deletedCount = 0
            when (type) {
                com.sza.fastmediasorter.domain.model.ResourceType.SMB -> {
                    val connectionInfo = getConnectionInfo(credentialsId).getOrThrow()
                    
                    // Extract relative path from full URI if present
                    val relativePath = if (path.startsWith("smb://")) {
                        val withoutProtocol = path.substring(6)
                        val parts = withoutProtocol.split('/', limit = 3)
                        if (parts.size > 2) parts[2] else ""
                    } else {
                        path
                    }
                    
                    trashFolders.forEach { folderName ->
                        val remotePath = if (relativePath.isEmpty()) folderName else "$relativePath/$folderName"
                        when (smbClient.deleteDirectory(connectionInfo, remotePath)) {
                            is SmbResult.Success -> deletedCount++
                            is SmbResult.Error -> Timber.e("Failed to delete SMB trash: $folderName")
                        }
                    }
                }
                com.sza.fastmediasorter.domain.model.ResourceType.SFTP -> {
                    val credentials = getSftpCredentials(credentialsId).getOrThrow()
                    val remotePath = path.substringAfter("://").substringAfter("/")
                    val connectionInfo = SftpClient.SftpConnectionInfo(
                        host = credentials.server,
                        port = credentials.port,
                        username = credentials.username,
                        password = credentials.password,
                        privateKey = credentials.sshPrivateKey
                    )
                    trashFolders.forEach { folderName ->
                        val fullPath = if (remotePath.isEmpty()) folderName else "$remotePath/$folderName"
                        sftpClient.deleteDirectory(connectionInfo, fullPath).onSuccess { deletedCount++ }
                    }
                }
                com.sza.fastmediasorter.domain.model.ResourceType.FTP -> {
                    val credentials = credentialsRepository.getByCredentialId(credentialsId) ?: throw Exception("Credentials not found")
                    val remotePath = path.substringAfter("://").substringAfter("/")
                    ftpClient.connect(
                        credentials.server, credentials.port, credentials.username, credentials.password
                    ).getOrThrow()
                    trashFolders.forEach { folderName ->
                        val fullPath = if (remotePath.isEmpty()) folderName else "$remotePath/$folderName"
                        ftpClient.deleteDirectory(fullPath).onSuccess { deletedCount++ }
                    }
                    ftpClient.disconnect()
                }
                else -> {}
            }
            Result.success(deletedCount)
        } catch (e: Exception) {
            Timber.e(e, "Failed to cleanup trash")
            Result.failure(e)
        }
    }
    
    /**
     * Clear all pooled SMB/S/FTP connections
     * Should be called when refreshing resources or on connection issues
     */
    suspend fun clearAllConnectionPools() = withContext(ioDispatcher) {
        try {
            Timber.d("Clearing all network connection pools")
            smbClient.clearConnectionPool()
            sftpClient.disconnectAll()
            ftpClient.disconnect()
        } catch (e: Exception) {
            Timber.e(e, "Error clearing connection pools")
        }
    }
}
