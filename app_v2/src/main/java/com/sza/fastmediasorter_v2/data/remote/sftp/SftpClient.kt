package com.sza.fastmediasorter_v2.data.remote.sftp

import com.sza.fastmediasorter_v2.core.util.InputStreamExt.copyToWithProgress
import com.sza.fastmediasorter_v2.domain.usecase.ByteProgressCallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.schmizz.sshj.DefaultConfig
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.sftp.SFTPClient
import net.schmizz.sshj.transport.verification.PromiscuousVerifier
import org.bouncycastle.jce.provider.BouncyCastleProvider
import timber.log.Timber
import java.io.IOException
import java.security.Security
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Data class for file attributes retrieved via SFTP stat()
 */
data class SftpFileAttributes(
    val size: Long,
    val modifiedDate: Long, // Unix timestamp in milliseconds
    val accessDate: Long,   // Unix timestamp in milliseconds
    val isDirectory: Boolean
)

/**
 * Low-level SFTP client wrapper using SSHJ library
 * Handles SFTP connection, authentication and file operations
 */
@Singleton
class SftpClient @Inject constructor() {

    init {
        // Ensure BouncyCastle provider is registered
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(BouncyCastleProvider())
            Timber.d("Registered BouncyCastle security provider")
        }
    }

    private var sshClient: SSHClient? = null
    private var sftpClient: SFTPClient? = null

    /**
     * Connect to SFTP server with password authentication
     * @param host Server hostname or IP address
     * @param port Server port (default 22)
     * @param username Username for authentication
     * @param password Password for authentication
     * @return Result with Unit on success or exception on failure
     */
    suspend fun connect(
        host: String,
        port: Int = 22,
        username: String,
        password: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            disconnect() // Ensure clean state
            
            // Use default config - Curve25519 will fail but fallback to other algorithms
            val config = DefaultConfig()
            
            Timber.d("SFTP creating SSHClient with default config")
            val client = SSHClient(config)
            client.addHostKeyVerifier(PromiscuousVerifier()) // Accept all host keys (security risk in production)
            
            // Set connection and socket timeout to 4 seconds (default is much longer)
            client.connectTimeout = 4000 // 4 seconds
            client.timeout = 4000 // 4 seconds for socket operations
            
            Timber.d("SFTP connecting to $host:$port...")
            
            // Try connection - if Curve25519 fails, SSHJ will automatically try other algorithms
            try {
                client.connect(host, port)
            } catch (e: Exception) {
                // If connection fails with X25519 error, server may be forcing Curve25519
                // Unfortunately we can't disable it without modifying SSHJ library
                Timber.e(e, "SFTP first connection attempt failed, may need X25519 support")
                throw e
            }
            
            client.authPassword(username, password)
            
            sshClient = client
            sftpClient = client.newSFTPClient()
            
            Timber.d("SFTP connected to $host:$port as $username")
            Result.success(Unit)
        } catch (e: IOException) {
            Timber.e(e, "SFTP connection failed: $host:$port")
            disconnect()
            Result.failure(e)
        } catch (e: Exception) {
            Timber.e(e, "SFTP connection error: $host:$port")
            disconnect()
            Result.failure(e)
        }
    }

    /**
     * List files and directories in remote path
     * @param remotePath Remote directory path (default "/")
     * @return Result with list of file paths or exception on failure
     */
    suspend fun listFiles(remotePath: String = "/"): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            val client = sftpClient ?: return@withContext Result.failure(
                IllegalStateException("Not connected. Call connect() first.")
            )
            
            val files = client.ls(remotePath).mapNotNull { fileEntry ->
                // Skip . and .. entries
                if (fileEntry.name == "." || fileEntry.name == "..") {
                    null
                } else {
                    fileEntry.path
                }
            }
            
            Timber.d("SFTP listed ${files.size} files in $remotePath")
            Result.success(files)
        } catch (e: IOException) {
            Timber.e(e, "SFTP list files failed: $remotePath")
            Result.failure(e)
        } catch (e: Exception) {
            Timber.e(e, "SFTP list files error: $remotePath")
            Result.failure(e)
        }
    }

    /**
     * Test connection to SFTP server
     * @param host Server hostname or IP address
     * @param port Server port (default 22)
     * @param username Username for authentication
     * @param password Password for authentication
     * @return Result with true on success or exception on failure
     */
    suspend fun testConnection(
        host: String,
        port: Int = 22,
        username: String,
        password: String
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val testClient = SSHClient()
            testClient.addHostKeyVerifier(PromiscuousVerifier())
            
            // Set connection and socket timeout to 10 seconds
            testClient.connectTimeout = 10000 // 10 seconds
            testClient.timeout = 10000 // 10 seconds for socket operations
            
            testClient.connect(host, port)
            testClient.authPassword(username, password)
            
            // Test listing root directory
            val testSftp = testClient.newSFTPClient()
            testSftp.ls("/")
            testSftp.close()
            testClient.disconnect()
            
            Timber.d("SFTP test connection successful: $host:$port")
            Result.success(true)
        } catch (e: IOException) {
            Timber.e(e, "SFTP test connection failed: $host:$port")
            Result.failure(e)
        } catch (e: Exception) {
            Timber.e(e, "SFTP test connection error: $host:$port")
            Result.failure(e)
        }
    }

    /**
     * Read file bytes from SFTP server (useful for thumbnails and image loading)
     * @param remotePath Full path to remote file
     * @param maxBytes Maximum bytes to read (default: read entire file)
     * @return Result with ByteArray or exception on failure
     */
    suspend fun readFileBytes(
        remotePath: String,
        maxBytes: Long = Long.MAX_VALUE
    ): Result<ByteArray> = withContext(Dispatchers.IO) {
        try {
            val client = sftpClient ?: return@withContext Result.failure(
                IllegalStateException("Not connected. Call connect() first.")
            )
            
            val remoteFile = client.open(remotePath)
            remoteFile.use { file ->
                file.RemoteFileInputStream().use { inputStream ->
                    val bytes = if (maxBytes < Long.MAX_VALUE) {
                        inputStream.readNBytes(maxBytes.toInt())
                    } else {
                        inputStream.readBytes()
                    }
                    Timber.d("SFTP read ${bytes.size} bytes from $remotePath")
                    Result.success(bytes)
                }
            }
        } catch (e: IOException) {
            Timber.e(e, "SFTP read file bytes failed: $remotePath")
            Result.failure(e)
        } catch (e: Exception) {
            Timber.e(e, "SFTP read file bytes error: $remotePath")
            Result.failure(e)
        }
    }

    /**
     * Download file from SFTP server to OutputStream
     * @param remotePath Full path to remote file
     * @param outputStream OutputStream to write downloaded data
     * @param fileSize Size of the file to download (for progress tracking), 0 if unknown
     * @param progressCallback Optional callback for tracking download progress
     * @return Result with Unit on success or exception on failure
     */
    suspend fun downloadFile(
        remotePath: String,
        outputStream: java.io.OutputStream,
        fileSize: Long = 0L,
        progressCallback: ByteProgressCallback? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val client = sftpClient ?: return@withContext Result.failure(
                IllegalStateException("Not connected. Call connect() first.")
            )
            
            Timber.d("SFTP downloading: $remotePath (size=$fileSize bytes)")
            
            val remoteFile = client.open(remotePath)
            remoteFile.use { file ->
                file.RemoteFileInputStream().use { inputStream ->
                    if (progressCallback != null && fileSize > 0L) {
                        inputStream.copyToWithProgress(outputStream, fileSize, progressCallback)
                    } else {
                        inputStream.copyTo(outputStream)
                    }
                }
            }
            
            Timber.i("SFTP download success: $remotePath")
            Result.success(Unit)
        } catch (e: IOException) {
            Timber.e(e, "SFTP download failed: $remotePath")
            Result.failure(e)
        } catch (e: Exception) {
            Timber.e(e, "SFTP download error: $remotePath")
            Result.failure(e)
        }
    }

    /**
     * Upload file to SFTP server from InputStream
     * @param remotePath Full path where file should be uploaded
     * @param inputStream InputStream to read data from
     * @param fileSize Size of the file to upload (for progress tracking), 0 if unknown
     * @param progressCallback Optional callback for tracking upload progress
     * @return Result with Unit on success or exception on failure
     */
    suspend fun uploadFile(
        remotePath: String,
        inputStream: java.io.InputStream,
        fileSize: Long = 0L,
        progressCallback: ByteProgressCallback? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val client = sftpClient ?: return@withContext Result.failure(
                IllegalStateException("Not connected. Call connect() first.")
            )
            
            Timber.d("SFTP uploading: $remotePath (size=$fileSize bytes)")
            
            val remoteFile = client.open(remotePath, 
                java.util.EnumSet.of(
                    net.schmizz.sshj.sftp.OpenMode.WRITE,
                    net.schmizz.sshj.sftp.OpenMode.CREAT,
                    net.schmizz.sshj.sftp.OpenMode.TRUNC
                )
            )
            
            remoteFile.use { file ->
                file.RemoteFileOutputStream().use { outputStream ->
                    if (progressCallback != null && fileSize > 0L) {
                        inputStream.copyToWithProgress(outputStream, fileSize, progressCallback)
                    } else {
                        inputStream.copyTo(outputStream)
                    }
                }
            }
            
            Timber.i("SFTP upload success: $remotePath")
            Result.success(Unit)
        } catch (e: IOException) {
            Timber.e(e, "SFTP upload failed: $remotePath")
            Result.failure(e)
        } catch (e: Exception) {
            Timber.e(e, "SFTP upload error: $remotePath")
            Result.failure(e)
        }
    }

    /**
     * Delete file on SFTP server
     * @param remotePath Full path to file to delete
     * @return Result with Unit on success or exception on failure
     */
    suspend fun deleteFile(remotePath: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val client = sftpClient ?: return@withContext Result.failure(
                IllegalStateException("Not connected. Call connect() first.")
            )
            
            Timber.d("SFTP deleting: $remotePath")
            client.rm(remotePath)
            Timber.i("SFTP delete success: $remotePath")
            Result.success(Unit)
        } catch (e: IOException) {
            Timber.e(e, "SFTP delete failed: $remotePath")
            Result.failure(e)
        } catch (e: Exception) {
            Timber.e(e, "SFTP delete error: $remotePath")
            Result.failure(e)
        }
    }

    /**
     * Rename file on SFTP server
     * @param oldPath Current file path
     * @param newName New filename (without path)
     * @return Result with Unit on success or exception on failure
     */
    suspend fun renameFile(oldPath: String, newName: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val client = sftpClient ?: return@withContext Result.failure(
                IllegalStateException("Not connected. Call connect() first.")
            )
            
            // Extract directory and construct new path
            val directory = oldPath.substringBeforeLast('/', "")
            val newPath = if (directory.isEmpty()) newName else "$directory/$newName"
            
            Timber.d("SFTP renaming: $oldPath → $newPath")
            
            // Check if target exists
            try {
                client.stat(newPath)
                // If stat succeeds, file exists
                return@withContext Result.failure(IOException("File '$newName' already exists"))
            } catch (e: IOException) {
                // File doesn't exist, proceed with rename
            }
            
            client.rename(oldPath, newPath)
            Timber.i("SFTP rename success: $newPath")
            Result.success(Unit)
        } catch (e: IOException) {
            Timber.e(e, "SFTP rename failed: $oldPath")
            Result.failure(e)
        } catch (e: Exception) {
            Timber.e(e, "SFTP rename error: $oldPath")
            Result.failure(e)
        }
    }

    /**
     * Get file attributes (size, dates) via SFTP stat()
     * @param remotePath Full path to remote file
     * @return Result with SftpFileAttributes or exception on failure
     */
    suspend fun getFileAttributes(remotePath: String): Result<SftpFileAttributes> = withContext(Dispatchers.IO) {
        try {
            val client = sftpClient ?: return@withContext Result.failure(
                IllegalStateException("Not connected. Call connect() first.")
            )
            
            val attrs = client.stat(remotePath)
            
            val attributes = SftpFileAttributes(
                size = attrs.size,
                modifiedDate = attrs.mtime * 1000L, // Convert Unix seconds to milliseconds
                accessDate = attrs.atime * 1000L,   // Convert Unix seconds to milliseconds
                isDirectory = attrs.type == net.schmizz.sshj.sftp.FileMode.Type.DIRECTORY
            )
            
            Result.success(attributes)
        } catch (e: IOException) {
            Timber.e(e, "SFTP get file attributes failed: $remotePath")
            Result.failure(e)
        } catch (e: Exception) {
            Timber.e(e, "SFTP get file attributes error: $remotePath")
            Result.failure(e)
        }
    }

    /**
     * Disconnect from SFTP server and cleanup resources
     */
    suspend fun disconnect() = withContext(Dispatchers.IO) {
        try {
            sftpClient?.close()
            sshClient?.disconnect()
            Timber.d("SFTP disconnected")
        } catch (e: Exception) {
            Timber.w(e, "SFTP disconnect error (non-critical)")
        } finally {
            sftpClient = null
            sshClient = null
        }
    }

    /**
     * Check if currently connected
     */
    fun isConnected(): Boolean {
        return sshClient?.isConnected == true && sftpClient != null
    }
}
