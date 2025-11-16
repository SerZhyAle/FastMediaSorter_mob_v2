package com.sza.fastmediasorter_v2.data.remote.ftp

import com.sza.fastmediasorter_v2.domain.usecase.ByteProgressCallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPFile
import org.apache.commons.net.ftp.FTPReply
import timber.log.Timber
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.SocketTimeoutException
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Low-level FTP client wrapper using Apache Commons Net
 * Handles FTP connection, authentication and file operations with passive mode
 */
@Singleton
class FtpClient @Inject constructor() {

    private var ftpClient: FTPClient? = null

    /**
     * Connect to FTP server with password authentication
     * @param host Server hostname or IP address
     * @param port Server port (default 21)
     * @param username Username for authentication
     * @param password Password for authentication
     * @return Result with Unit on success or exception on failure
     */
    suspend fun connect(
        host: String,
        port: Int = 21,
        username: String,
        password: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            disconnect() // Ensure clean state
            
            val client = FTPClient()
            
            // Set connection and socket timeout to 15 seconds for unreliable networks
            client.connectTimeout = 15000
            client.defaultTimeout = 15000
            client.setDataTimeout(15000)
            client.controlKeepAliveTimeout = Duration.ofSeconds(10).seconds
            
            client.connect(host, port)
            
            val replyCode = client.replyCode
            if (!FTPReply.isPositiveCompletion(replyCode)) {
                client.disconnect()
                return@withContext Result.failure(
                    IOException("FTP server refused connection. Reply code: $replyCode")
                )
            }
            
            if (!client.login(username, password)) {
                client.disconnect()
                return@withContext Result.failure(
                    IOException("FTP authentication failed for user: $username")
                )
            }
            
            // Enable passive mode (required for most modern FTP servers behind NAT/firewall)
            client.enterLocalPassiveMode()
            
            // Set binary mode for file transfers
            client.setFileType(FTP.BINARY_FILE_TYPE)
            
            ftpClient = client
            
            Timber.d("FTP connected to $host:$port as $username (passive mode)")
            Result.success(Unit)
        } catch (e: IOException) {
            Timber.e(e, "FTP connection failed: $host:$port")
            disconnect()
            Result.failure(e)
        } catch (e: Exception) {
            Timber.e(e, "FTP connection error: $host:$port")
            disconnect()
            Result.failure(e)
        }
    }

    /**
     * List files and directories in remote path
     * @param remotePath Remote directory path (default "/")
     * @return Result with list of file names (not full paths) or exception on failure
     */
    suspend fun listFiles(remotePath: String = "/"): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            val client = ftpClient ?: return@withContext Result.failure(
                IllegalStateException("Not connected. Call connect() first.")
            )
            
            // Try passive mode first, fallback to active mode on timeout
            val files = try {
                Timber.d("FTP listing files in passive mode: $remotePath")
                val ftpFiles = client.listFiles(remotePath)
                
                // Filter out . and .. entries
                ftpFiles.mapNotNull { ftpFile ->
                    if (ftpFile.name == "." || ftpFile.name == "..") null else ftpFile.name
                }
            } catch (e: SocketTimeoutException) {
                Timber.w(e, "FTP passive mode timeout, switching to active mode")
                
                // Switch to active mode and retry
                client.enterLocalActiveMode()
                Timber.d("FTP retrying listFiles in active mode: $remotePath")
                
                val ftpFiles = try {
                    client.listFiles(remotePath)
                } finally {
                    // Switch back to passive for future operations
                    try { 
                        client.enterLocalPassiveMode() 
                        Timber.d("FTP switched back to passive mode")
                    } catch (ignored: Exception) {
                        Timber.w(ignored, "Failed to switch back to passive mode")
                    }
                }
                
                // Filter out . and .. entries
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
            Timber.e(e, "FTP list files error: $remotePath")
            Result.failure(e)
        }
    }

    /**
     * Test connection to FTP server
     * @param host Server hostname or IP address
     * @param port Server port (default 21)
     * @param username Username for authentication
     * @param password Password for authentication
     * @return Result with true on success or exception on failure
     */
    suspend fun testConnection(
        host: String,
        port: Int = 21,
        username: String,
        password: String
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val testClient = FTPClient()
            
            // Set connection and socket timeout to 15 seconds for unreliable networks
            testClient.connectTimeout = 15000
            testClient.defaultTimeout = 15000
            testClient.setDataTimeout(15000)
            testClient.controlKeepAliveTimeout = Duration.ofSeconds(10).seconds
            
            testClient.connect(host, port)
            
            val replyCode = testClient.replyCode
            if (!FTPReply.isPositiveCompletion(replyCode)) {
                testClient.disconnect()
                return@withContext Result.failure(
                    IOException("FTP server refused connection. Reply code: $replyCode")
                )
            }
            
            if (!testClient.login(username, password)) {
                testClient.disconnect()
                return@withContext Result.failure(
                    IOException("FTP authentication failed for user: $username")
                )
            }
            
            // Enable passive mode
            testClient.enterLocalPassiveMode()
            
            // Test listing root directory
            testClient.listFiles("/")
            
            testClient.logout()
            testClient.disconnect()
            
            Timber.d("FTP test connection successful: $host:$port")
            Result.success(true)
        } catch (e: IOException) {
            Timber.e(e, "FTP test connection failed: $host:$port")
            Result.failure(e)
        } catch (e: Exception) {
            Timber.e(e, "FTP test connection error: $host:$port")
            Result.failure(e)
        }
    }

    /**
     * Read file bytes from FTP server (useful for thumbnails and image loading)
     * @param remotePath Full path to remote file
     * @param maxBytes Maximum bytes to read (default: read entire file)
     * @return Result with ByteArray or exception on failure
     */
    suspend fun readFileBytes(
        remotePath: String,
        maxBytes: Long = Long.MAX_VALUE
    ): Result<ByteArray> = withContext(Dispatchers.IO) {
        try {
            val client = ftpClient ?: return@withContext Result.failure(
                IllegalStateException("Not connected. Call connect() first.")
            )
            
            client.retrieveFileStream(remotePath)?.use { inputStream ->
                val bytes = if (maxBytes < Long.MAX_VALUE) {
                    inputStream.readNBytes(maxBytes.toInt())
                } else {
                    inputStream.readBytes()
                }
                
                // Must complete transfer
                if (!client.completePendingCommand()) {
                    return@withContext Result.failure(
                        IOException("FTP command failed after retrieving file")
                    )
                }
                
                Timber.d("FTP read ${bytes.size} bytes from $remotePath")
                Result.success(bytes)
            } ?: Result.failure(IOException("Failed to open file stream: $remotePath"))
        } catch (e: IOException) {
            Timber.e(e, "FTP read file bytes failed: $remotePath")
            Result.failure(e)
        } catch (e: Exception) {
            Timber.e(e, "FTP read file bytes error: $remotePath")
            Result.failure(e)
        }
    }

    /**
     * Download file from FTP server to OutputStream
     * @param remotePath Full path to remote file
     * @param outputStream OutputStream to write downloaded data
     * @param fileSize Size of the file to download (for progress tracking), 0 if unknown
     * @param progressCallback Optional callback for tracking download progress
     * @return Result with Unit on success or exception on failure
     */
    suspend fun downloadFile(
        remotePath: String,
        outputStream: OutputStream,
        fileSize: Long = 0L,
        progressCallback: ByteProgressCallback? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val client = ftpClient ?: return@withContext Result.failure(
                IllegalStateException("Not connected. Call connect() first.")
            )
            
            Timber.d("FTP downloading: $remotePath (size=$fileSize bytes)")
            
            val success = client.retrieveFile(remotePath, outputStream)
            if (!success) {
                return@withContext Result.failure(
                    IOException("FTP download failed: ${client.replyString}")
                )
            }
            
            Timber.i("FTP download success: $remotePath")
            Result.success(Unit)
        } catch (e: IOException) {
            Timber.e(e, "FTP download failed: $remotePath")
            Result.failure(e)
        } catch (e: Exception) {
            Timber.e(e, "FTP download error: $remotePath")
            Result.failure(e)
        }
    }

    /**
     * Upload file to FTP server from InputStream
     * @param remotePath Full path where file should be uploaded
     * @param inputStream InputStream to read data from
     * @param fileSize Size of the file to upload (for progress tracking), 0 if unknown
     * @param progressCallback Optional callback for tracking upload progress
     * @return Result with Unit on success or exception on failure
     */
    suspend fun uploadFile(
        remotePath: String,
        inputStream: InputStream,
        fileSize: Long = 0L,
        progressCallback: ByteProgressCallback? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val client = ftpClient ?: return@withContext Result.failure(
                IllegalStateException("Not connected. Call connect() first.")
            )
            
            Timber.d("FTP uploading: $remotePath (size=$fileSize bytes)")
            
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
            Timber.e(e, "FTP upload error: $remotePath")
            Result.failure(e)
        }
    }

    /**
     * Delete file on FTP server
     * @param remotePath Full path to file to delete
     * @return Result with Unit on success or exception on failure
     */
    suspend fun deleteFile(remotePath: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val client = ftpClient ?: return@withContext Result.failure(
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
            Timber.e(e, "FTP delete error: $remotePath")
            Result.failure(e)
        }
    }

    /**
     * Rename file on FTP server
     * @param oldPath Current file path
     * @param newName New filename (without path)
     * @return Result with Unit on success or exception on failure
     */
    suspend fun renameFile(oldPath: String, newName: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val client = ftpClient ?: return@withContext Result.failure(
                IllegalStateException("Not connected. Call connect() first.")
            )
            
            // Extract directory and construct new path
            val directory = oldPath.substringBeforeLast('/', "")
            val newPath = if (directory.isEmpty()) "/$newName" else "$directory/$newName"
            
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
            Timber.e(e, "FTP rename error: $oldPath")
            Result.failure(e)
        }
    }
    
    /**
     * Create directory on FTP server
     * @param remotePath Full path to directory to create
     * @return Result with Unit or exception on failure
     */
    suspend fun createDirectory(remotePath: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val client = ftpClient ?: return@withContext Result.failure(
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
            Timber.e(e, "FTP create directory error: $remotePath")
            Result.failure(e)
        }
    }

    /**
     * Disconnect from FTP server and cleanup resources
     * Uses short timeout for logout to avoid SocketTimeoutException delays
     */
    suspend fun disconnect() = withContext(Dispatchers.IO) {
        try {
            ftpClient?.let { client ->
                if (client.isConnected) {
                    // Set short timeout for logout - don't wait for server response
                    val originalTimeout = client.soTimeout
                    try {
                        client.soTimeout = 1000 // 1 second instead of default 30
                        client.logout()
                    } catch (e: java.net.SocketTimeoutException) {
                        // Ignore logout timeout - server will close connection anyway
                        Timber.d("FTP logout timeout (ignored)")
                    } catch (e: Exception) {
                        // Other logout errors also non-critical
                        Timber.d(e, "FTP logout error (ignored)")
                    } finally {
                        // Restore original timeout before disconnect
                        client.soTimeout = originalTimeout
                    }
                    // Always close socket regardless of logout success
                    client.disconnect()
                }
            }
            Timber.d("FTP disconnected")
        } catch (e: Exception) {
            Timber.w(e, "FTP disconnect error (non-critical)")
        } finally {
            ftpClient = null
        }
    }

    /**
     * Check if currently connected
     */
    fun isConnected(): Boolean {
        return ftpClient?.isConnected == true
    }
}
