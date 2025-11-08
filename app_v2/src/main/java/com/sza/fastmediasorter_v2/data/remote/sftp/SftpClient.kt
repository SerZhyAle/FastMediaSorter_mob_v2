package com.sza.fastmediasorter_v2.data.remote.sftp

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.sftp.SFTPClient
import net.schmizz.sshj.transport.verification.PromiscuousVerifier
import timber.log.Timber
import java.io.IOException

/**
 * Low-level SFTP client wrapper using SSHJ library
 * Handles SFTP connection, authentication and file operations
 */
class SftpClient {

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
            
            val client = SSHClient()
            client.addHostKeyVerifier(PromiscuousVerifier()) // Accept all host keys (security risk in production)
            client.connect(host, port)
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
