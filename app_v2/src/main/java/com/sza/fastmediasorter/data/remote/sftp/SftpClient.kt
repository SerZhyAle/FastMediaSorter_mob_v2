package com.sza.fastmediasorter.data.remote.sftp

import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSch
import com.jcraft.jsch.JSchException
import com.jcraft.jsch.Session
import com.jcraft.jsch.SftpATTRS
import com.sza.fastmediasorter.core.util.InputStreamExt.copyToWithProgress
import com.sza.fastmediasorter.domain.usecase.ByteProgressCallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.IOException
import java.io.OutputStream
import java.util.Vector
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
 * Low-level SFTP client wrapper using JSch library
 * JSch has built-in KEX implementations (including ECDH) without requiring EC KeyPairGenerator from BouncyCastle
 * This solves Android BouncyCastle limitations with modern SSH servers
 */
@Singleton
class SftpClient @Inject constructor() {

    private var session: Session? = null
    private var channel: ChannelSftp? = null
    private val jsch = JSch()

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
            
            Timber.d("SFTP connecting to $host:$port with password...")
            
            val newSession = jsch.getSession(username, host, port)
            newSession.setPassword(password)
            
            // UserInfo for keyboard-interactive authentication with password
            newSession.userInfo = object : com.jcraft.jsch.UserInfo {
                override fun getPassphrase(): String? = null
                override fun getPassword(): String = password
                override fun promptPassword(message: String?): Boolean = true
                override fun promptPassphrase(message: String?): Boolean = false
                override fun promptYesNo(message: String?): Boolean = true
                override fun showMessage(message: String?) {}
            }
            
            // Disable strict host key checking (accept all host keys)
            val config = java.util.Properties()
            config["StrictHostKeyChecking"] = "no"
            // Try keyboard-interactive first (for servers that require it), then password
            config["PreferredAuthentications"] = "keyboard-interactive,password"
            newSession.setConfig(config)
            
            // Set timeouts
            newSession.timeout = 4000 // 4 seconds for socket operations
            newSession.connect(4000) // 4 seconds connection timeout
            
            val newChannel = newSession.openChannel("sftp") as ChannelSftp
            newChannel.connect(4000)
            
            session = newSession
            channel = newChannel
            
            Timber.d("SFTP connected to $host:$port as $username")
            Result.success(Unit)
        } catch (e: JSchException) {
            Timber.e(e, "SFTP connection failed: $host:$port")
            disconnect()
            Result.failure(IOException("SFTP connection failed: ${e.message}", e))
        } catch (e: Exception) {
            Timber.e(e, "SFTP connection error: $host:$port")
            disconnect()
            Result.failure(e)
        }
    }
    
    /**
     * Connect to SFTP server with SSH private key authentication
     * @param host Server hostname or IP address
     * @param port Server port (default 22)
     * @param username Username for authentication
     * @param privateKey SSH private key in PEM format
     * @param passphrase Optional passphrase for encrypted private key
     * @return Result with Unit on success or exception on failure
     */
    suspend fun connectWithPrivateKey(
        host: String,
        port: Int = 22,
        username: String,
        privateKey: String,
        passphrase: String? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            disconnect() // Ensure clean state
            
            Timber.d("SFTP connecting to $host:$port with private key...")
            
            // Add private key to JSch
            if (passphrase != null) {
                jsch.addIdentity("key", privateKey.toByteArray(), null, passphrase.toByteArray())
            } else {
                jsch.addIdentity("key", privateKey.toByteArray(), null, null)
            }
            
            val newSession = jsch.getSession(username, host, port)
            
            // UserInfo for passphrase prompts if key is encrypted
            if (passphrase != null) {
                newSession.userInfo = object : com.jcraft.jsch.UserInfo {
                    override fun getPassphrase(): String = passphrase
                    override fun getPassword(): String? = null
                    override fun promptPassword(message: String?): Boolean = false
                    override fun promptPassphrase(message: String?): Boolean = true
                    override fun promptYesNo(message: String?): Boolean = true
                    override fun showMessage(message: String?) {}
                }
            }
            
            // Disable strict host key checking (accept all host keys)
            val config = java.util.Properties()
            config["StrictHostKeyChecking"] = "no"
            // Public key authentication only
            config["PreferredAuthentications"] = "publickey"
            newSession.setConfig(config)
            
            // Set timeouts
            newSession.timeout = 4000 // 4 seconds for socket operations
            newSession.connect(4000) // 4 seconds connection timeout
            
            val newChannel = newSession.openChannel("sftp") as ChannelSftp
            newChannel.connect(4000)
            
            session = newSession
            channel = newChannel
            
            Timber.d("SFTP connected to $host:$port as $username with private key")
            Result.success(Unit)
        } catch (e: JSchException) {
            Timber.e(e, "SFTP private key connection failed: $host:$port")
            disconnect()
            Result.failure(IOException("SFTP private key connection failed: ${e.message}", e))
        } catch (e: Exception) {
            Timber.e(e, "SFTP private key connection error: $host:$port")
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
            val ch = channel ?: return@withContext Result.failure(
                IllegalStateException("Not connected. Call connect() first.")
            )
            
            @Suppress("UNCHECKED_CAST")
            val entries = ch.ls(remotePath) as Vector<ChannelSftp.LsEntry>
            val files = entries.mapNotNull { entry ->
                // Skip . and .. entries
                if (entry.filename == "." || entry.filename == "..") {
                    null
                } else {
                    if (remotePath.endsWith("/")) {
                        remotePath + entry.filename
                    } else {
                        "$remotePath/${entry.filename}"
                    }
                }
            }
            
            Timber.d("SFTP listed ${files.size} files in $remotePath")
            Result.success(files)
        } catch (e: Exception) {
            Timber.e(e, "SFTP list files failed: $remotePath")
            Result.failure(e)
        }
    }

    /**
     * Test connection to SFTP server with private key
     * @param host Server hostname or IP address
     * @param port Server port (default 22)
     * @param username Username for authentication
     * @param privateKey SSH private key in PEM format
     * @param passphrase Optional passphrase for encrypted private key
     * @return Result with true on success or exception on failure
     */
    suspend fun testConnectionWithPrivateKey(
        host: String,
        port: Int = 22,
        username: String,
        privateKey: String,
        passphrase: String? = null
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val testJsch = JSch()
            
            // Add private key to test JSch instance
            if (passphrase != null) {
                testJsch.addIdentity("key", privateKey.toByteArray(), null, passphrase.toByteArray())
            } else {
                testJsch.addIdentity("key", privateKey.toByteArray(), null, null)
            }
            
            val testSession = testJsch.getSession(username, host, port)
            
            // UserInfo for passphrase prompts
            if (passphrase != null) {
                testSession.userInfo = object : com.jcraft.jsch.UserInfo {
                    override fun getPassphrase(): String = passphrase
                    override fun getPassword(): String? = null
                    override fun promptPassword(message: String?): Boolean = false
                    override fun promptPassphrase(message: String?): Boolean = true
                    override fun promptYesNo(message: String?): Boolean = true
                    override fun showMessage(message: String?) {}
                }
            }
            
            val config = java.util.Properties()
            config["StrictHostKeyChecking"] = "no"
            config["PreferredAuthentications"] = "publickey"
            testSession.setConfig(config)
            
            testSession.timeout = 10000 // 10 seconds
            testSession.connect(10000)
            
            val testChannel = testSession.openChannel("sftp") as ChannelSftp
            testChannel.connect(10000)
            
            // Test listing root directory
            testChannel.ls("/")
            
            testChannel.disconnect()
            testSession.disconnect()
            
            Timber.d("SFTP test connection with private key successful: $host:$port")
            Result.success(true)
        } catch (e: JSchException) {
            Timber.e(e, "SFTP test connection with private key failed: $host:$port")
            Result.failure(IOException("SFTP test connection with private key failed: ${e.message}", e))
        } catch (e: Exception) {
            Timber.e(e, "SFTP test connection with private key error: $host:$port")
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
            val testSession = jsch.getSession(username, host, port)
            testSession.setPassword(password)
            
            // UserInfo for keyboard-interactive authentication with password
            testSession.userInfo = object : com.jcraft.jsch.UserInfo {
                override fun getPassphrase(): String? = null
                override fun getPassword(): String = password
                override fun promptPassword(message: String?): Boolean = true
                override fun promptPassphrase(message: String?): Boolean = false
                override fun promptYesNo(message: String?): Boolean = true
                override fun showMessage(message: String?) {}
            }
            
            val config = java.util.Properties()
            config["StrictHostKeyChecking"] = "no"
            // Try keyboard-interactive first, then password
            config["PreferredAuthentications"] = "keyboard-interactive,password"
            testSession.setConfig(config)
            
            testSession.timeout = 10000 // 10 seconds
            testSession.connect(10000)
            
            val testChannel = testSession.openChannel("sftp") as ChannelSftp
            testChannel.connect(10000)
            
            // Test listing root directory
            testChannel.ls("/")
            
            testChannel.disconnect()
            testSession.disconnect()
            
            Timber.d("SFTP test connection successful: $host:$port")
            Result.success(true)
        } catch (e: JSchException) {
            Timber.e(e, "SFTP test connection failed: $host:$port")
            Result.failure(IOException("SFTP test connection failed: ${e.message}", e))
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
            val ch = channel ?: return@withContext Result.failure(
                IllegalStateException("Not connected. Call connect() first.")
            )
            
            ch.get(remotePath).use { inputStream ->
                val bytes = if (maxBytes < Long.MAX_VALUE) {
                    val buffer = ByteArray(maxBytes.toInt())
                    val bytesRead = inputStream.read(buffer, 0, maxBytes.toInt())
                    buffer.copyOf(bytesRead)
                } else {
                    inputStream.readBytes()
                }
                Timber.d("SFTP read ${bytes.size} bytes from $remotePath")
                Result.success(bytes)
            }
        } catch (e: Exception) {
            Timber.e(e, "SFTP read file bytes failed: $remotePath")
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
        outputStream: OutputStream,
        fileSize: Long = 0,
        progressCallback: ByteProgressCallback? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val ch = channel ?: return@withContext Result.failure(
                IllegalStateException("Not connected. Call connect() first.")
            )
            
            ch.get(remotePath).use { inputStream ->
                if (progressCallback != null && fileSize > 0) {
                    inputStream.copyToWithProgress(outputStream, fileSize, progressCallback)
                } else {
                    inputStream.copyTo(outputStream)
                }
            }
            
            Timber.d("SFTP downloaded file: $remotePath")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "SFTP download file failed: $remotePath")
            Result.failure(e)
        }
    }

    /**
     * Upload file to SFTP server from byte array
     * @param remotePath Full path to remote file
     * @param data Byte array to upload
     * @return Result with Unit on success or exception on failure
     */
    suspend fun uploadFile(
        remotePath: String,
        data: ByteArray
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val ch = channel ?: return@withContext Result.failure(
                IllegalStateException("Not connected. Call connect() first.")
            )
            
            data.inputStream().use { inputStream ->
                ch.put(inputStream, remotePath)
            }
            
            Timber.d("SFTP uploaded file: $remotePath (${data.size} bytes)")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "SFTP upload file failed: $remotePath")
            Result.failure(e)
        }
    }

    /**
     * Get file attributes (size, dates, type)
     * @param remotePath Full path to remote file/directory
     * @return Result with SftpFileAttributes or exception on failure
     */
    suspend fun stat(remotePath: String): Result<SftpFileAttributes> = withContext(Dispatchers.IO) {
        try {
            val ch = channel ?: return@withContext Result.failure(
                IllegalStateException("Not connected. Call connect() first.")
            )
            
            val attrs = ch.stat(remotePath)
            val attributes = SftpFileAttributes(
                size = attrs.size,
                modifiedDate = attrs.mTime * 1000L, // Convert seconds to milliseconds
                accessDate = attrs.aTime * 1000L,
                isDirectory = attrs.isDir
            )
            
            Timber.d("SFTP stat: $remotePath - size=${attributes.size}, isDir=${attributes.isDirectory}")
            Result.success(attributes)
        } catch (e: Exception) {
            Timber.e(e, "SFTP stat failed: $remotePath")
            Result.failure(e)
        }
    }

    /**
     * Check if path exists
     * @param remotePath Full path to check
     * @return Result with true if exists, false if not exists, or exception on error
     */
    suspend fun exists(remotePath: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val ch = channel ?: return@withContext Result.failure(
                IllegalStateException("Not connected. Call connect() first.")
            )
            
            ch.stat(remotePath)
            Result.success(true)
        } catch (e: com.jcraft.jsch.SftpException) {
            if (e.id == ChannelSftp.SSH_FX_NO_SUCH_FILE) {
                Result.success(false)
            } else {
                Timber.e(e, "SFTP exists check failed: $remotePath")
                Result.failure(e)
            }
        } catch (e: Exception) {
            Timber.e(e, "SFTP exists check error: $remotePath")
            Result.failure(e)
        }
    }

    /**
     * Create directory
     * @param remotePath Full path to directory
     * @return Result with Unit on success or exception on failure
     */
    suspend fun mkdir(remotePath: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val ch = channel ?: return@withContext Result.failure(
                IllegalStateException("Not connected. Call connect() first.")
            )
            
            ch.mkdir(remotePath)
            Timber.d("SFTP created directory: $remotePath")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "SFTP mkdir failed: $remotePath")
            Result.failure(e)
        }
    }

    /**
     * Delete file
     * @param remotePath Full path to file
     * @return Result with Unit on success or exception on failure
     */
    suspend fun deleteFile(remotePath: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val ch = channel ?: return@withContext Result.failure(
                IllegalStateException("Not connected. Call connect() first.")
            )
            
            ch.rm(remotePath)
            Timber.d("SFTP deleted file: $remotePath")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "SFTP delete file failed: $remotePath")
            Result.failure(e)
        }
    }

    /**
     * Delete directory (must be empty)
     * @param remotePath Full path to directory
     * @return Result with Unit on success or exception on failure
     */
    suspend fun deleteDirectory(remotePath: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val ch = channel ?: return@withContext Result.failure(
                IllegalStateException("Not connected. Call connect() first.")
            )
            
            ch.rmdir(remotePath)
            Timber.d("SFTP deleted directory: $remotePath")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "SFTP delete directory failed: $remotePath")
            Result.failure(e)
        }
    }

    /**
     * Rename/move file or directory
     * @param oldPath Current path
     * @param newPath New path
     * @return Result with Unit on success or exception on failure
     */
    suspend fun rename(oldPath: String, newPath: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val ch = channel ?: return@withContext Result.failure(
                IllegalStateException("Not connected. Call connect() first.")
            )
            
            ch.rename(oldPath, newPath)
            Timber.d("SFTP renamed: $oldPath -> $newPath")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "SFTP rename failed: $oldPath -> $newPath")
            Result.failure(e)
        }
    }

    /**
     * Rename file (convenience method for rename with new name only)
     * @param oldPath Current full path
     * @param newName New filename only (not full path)
     * @return Result with Unit on success or exception on failure
     */
    suspend fun renameFile(oldPath: String, newName: String): Result<Unit> {
        val parentPath = oldPath.substringBeforeLast('/')
        val newPath = if (parentPath.isEmpty()) newName else "$parentPath/$newName"
        return rename(oldPath, newPath)
    }

    /**
     * Create directory (alias for mkdir)
     */
    suspend fun createDirectory(remotePath: String): Result<Unit> = mkdir(remotePath)

    /**
     * Get file attributes (alias for stat with different return type for compatibility)
     */
    suspend fun getFileAttributes(remotePath: String): Result<SftpFileAttributes> = stat(remotePath)

    /**
     * Disconnect from SFTP server
     */
    suspend fun disconnect() = withContext(Dispatchers.IO) {
        try {
            channel?.disconnect()
            session?.disconnect()
            channel = null
            session = null
            Timber.d("SFTP disconnected")
        } catch (e: Exception) {
            Timber.e(e, "Error during SFTP disconnect")
        }
    }

    /**
     * Check if currently connected
     */
    fun isConnected(): Boolean {
        return session?.isConnected == true && channel?.isConnected == true
    }
}
