package com.sza.fastmediasorter.wear.data.network.smb

import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.SmbConfig
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.connection.Connection
import com.hierynomus.smbj.session.Session
import com.hierynomus.smbj.share.DiskShare
import com.sza.fastmediasorter.wear.domain.model.NetworkSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.FilterInputStream
import java.io.InputStream
import java.util.EnumSet
import java.util.concurrent.TimeUnit

/**
 * SMB data source for accessing files on SMB/CIFS network shares.
 * Uses SMBJ library for SMB protocol communication.
 */
class SmbDataSource {
    
    private var connection: Connection? = null
    private var session: Session? = null
    private var share: DiskShare? = null
    
    // Store connection parameters for reconnection
    private var currentSource: NetworkSource? = null
    
    private val config = SmbConfig.builder()
        .withTimeout(30, TimeUnit.SECONDS)
        .withSoTimeout(30, TimeUnit.SECONDS)
        .build()
    
    private val client = SMBClient(config)
    
    /**
     * Connect to SMB server and authenticate.
     */
    suspend fun connect(source: NetworkSource): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                Timber.d("Connecting to SMB: ${source.server}:${source.port}")
                
                // Disconnect if already connected
                disconnect()
                
                // Store source for reconnection
                currentSource = source
                
                // Establish connection
                connection = client.connect(source.server, source.port)
                
                // Authenticate
                val authContext = AuthenticationContext(
                    source.username,
                    source.password.toCharArray(),
                    null // Domain (null for workgroup)
                )
                
                session = connection?.authenticate(authContext)
                
                // Connect to share
                if (source.shareName != null) {
                    share = session?.connectShare(source.shareName) as? DiskShare
                    Timber.d("Connected to share: ${source.shareName}")
                }
                
                Result.success(Unit)
            } catch (e: Exception) {
                Timber.e(e, "Failed to connect to SMB")
                disconnect()
                Result.failure(e)
            }
        }
    }
    
    /**
     * Ensure connection is alive, reconnect if needed.
     */
    private suspend fun ensureConnected(): Result<Unit> {
        return withContext(Dispatchers.IO) {
            // Check if connection is still alive
            val isAlive = try {
                connection?.isConnected == true && share != null
            } catch (e: Exception) {
                false
            }
            
            if (isAlive) {
                Timber.d("SMB connection is alive")
                return@withContext Result.success(Unit)
            }
            
            // Need to reconnect
            val source = currentSource
            if (source == null) {
                Timber.e("Cannot reconnect - no stored connection parameters")
                return@withContext Result.failure(IllegalStateException("Not connected to share"))
            }
            
            Timber.d("SMB connection lost, reconnecting...")
            connect(source)
        }
    }
    
    /**
     * Disconnect from SMB server.
     */
    suspend fun disconnect() {
        withContext(Dispatchers.IO) {
            try {
                share?.close()
                session?.close()
                connection?.close()
            } catch (e: Exception) {
                Timber.e(e, "Error disconnecting from SMB")
            } finally {
                share = null
                session = null
                connection = null
            }
        }
    }
    
    /**
     * List files in directory.
     * 
     * @param path Path relative to share root
     * @return List of file/directory names
     */
    suspend fun listFiles(path: String): Result<List<String>> {
        // Ensure connection is alive before attempting file operation
        val connectResult = ensureConnected()
        if (connectResult.isFailure) {
            return Result.failure(connectResult.exceptionOrNull() ?: IllegalStateException("Connection failed"))
        }
        
        return try {
            val currentShare = share ?: return Result.failure(
                IllegalStateException("Not connected to share")
            )
            
            val cleanPath = path.trim('/').replace('/', '\\')
            Timber.d("Listing files in: $cleanPath")
            
            val files = currentShare.list(cleanPath).map { it.fileName }
            
            Result.success(files)
        } catch (e: Exception) {
            Timber.e(e, "Failed to list files")
            Result.failure(e)
        }
    }
    
    /**
     * Get input stream for file.
     * 
     * @param path Path to file relative to share root
     * @return InputStream for reading file content
     */
    suspend fun getFileStream(path: String): Result<InputStream> {
        // Ensure connection is alive before attempting file operation
        val connectResult = ensureConnected()
        if (connectResult.isFailure) {
            return Result.failure(connectResult.exceptionOrNull() ?: IllegalStateException("Connection failed"))
        }
        
        return try {
            val currentShare = share ?: return Result.failure(
                IllegalStateException("Not connected to share")
            )
            
            val cleanPath = path.trim('/').trim('\\')
            Timber.d("Opening file: $cleanPath")
            
            // Open file with read access using proper SMBJ API
            val file = currentShare.openFile(
                cleanPath,
                EnumSet.of(AccessMask.GENERIC_READ),
                null,
                SMB2ShareAccess.ALL,
                SMB2CreateDisposition.FILE_OPEN,
                null
            )
            
            // S1304: closing only the stream leaked the smbj File handle - one open SMB2 handle per
            // viewed media file, held by the server until the session died. Tie the handle's
            // lifetime to the stream the caller actually closes.
            val inputStream = object : FilterInputStream(file.inputStream) {
                override fun close() {
                    try {
                        super.close()
                    } finally {
                        runCatching { file.close() }
                            .onFailure { Timber.w(it, "Failed to close SMB file handle for $cleanPath") }
                    }
                }
            }

            Result.success(inputStream)
        } catch (e: Exception) {
            Timber.e(e, "Failed to open file stream")
            Result.failure(e)
        }
    }
    
    /**
     * Check if currently connected.
     */
    fun isConnected(): Boolean {
        return connection?.isConnected == true && share != null
    }
    
    /**
     * Get file size in bytes.
     */
    suspend fun getFileSize(path: String): Result<Long> {
        // Ensure connection is alive before attempting file operation
        val connectResult = ensureConnected()
        if (connectResult.isFailure) {
            return Result.failure(connectResult.exceptionOrNull() ?: IllegalStateException("Connection failed"))
        }
        
        return try {
            val currentShare = share ?: return Result.failure(
                IllegalStateException("Not connected to share")
            )
            
            val cleanPath = path.trim('/').trim('\\')
            val file = currentShare.openFile(
                cleanPath,
                EnumSet.of(AccessMask.GENERIC_READ),
                null,
                SMB2ShareAccess.ALL,
                SMB2CreateDisposition.FILE_OPEN,
                null
            )
            
            file.use { smbFile ->
                val size = smbFile.fileInformation?.standardInformation?.endOfFile ?: 0L
                Result.success(size)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to get file size")
            Result.failure(e)
        }
    }
    
    /**
     * Get file information (name, is directory, size, modified time).
     */
    data class FileInfo(
        val name: String,
        val isDirectory: Boolean,
        val size: Long,
        val modifiedTime: Long
    )
    
    suspend fun getFileInfo(path: String): Result<FileInfo> {
        // Ensure connection is alive before attempting file operation
        val connectResult = ensureConnected()
        if (connectResult.isFailure) {
            return Result.failure(connectResult.exceptionOrNull() ?: IllegalStateException("Connection failed"))
        }
        
        return try {
            val currentShare = share ?: return Result.failure(
                IllegalStateException("Not connected to share")
            )
            
            val cleanPath = path.trim('/').trim('\\')
            val file = currentShare.openFile(
                cleanPath,
                EnumSet.of(AccessMask.GENERIC_READ),
                null,
                SMB2ShareAccess.ALL,
                SMB2CreateDisposition.FILE_OPEN,
                null
            )
            
            file.use { smbFile ->
                // Get file information - simplified for compatibility
                val fileName = smbFile.fileName
                val size = try {
                    smbFile.fileInformation?.standardInformation?.endOfFile ?: 0L
                } catch (e: Exception) {
                    0L
                }
                
                val fileInfo = FileInfo(
                    name = fileName,
                    isDirectory = fileName.startsWith("\\") && fileName.endsWith("\\"), // Heuristic
                    size = size,
                    modifiedTime = System.currentTimeMillis()
                )
                Result.success(fileInfo)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to get file info")
            Result.failure(e)
        }
    }
}
