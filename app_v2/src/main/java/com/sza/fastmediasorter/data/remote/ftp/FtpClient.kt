@file:Suppress("DEPRECATION")

package com.sza.fastmediasorter.data.remote.ftp

import com.sza.fastmediasorter.data.network.IdleDisconnectPolicy
import com.sza.fastmediasorter.domain.usecase.ByteProgressCallback
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
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Low-level FTP client wrapper using Apache Commons Net.
 * Handles connection lifecycle and authentication; file operations delegate to
 * [FtpConnectedOperations], standalone operations to [FtpStandaloneOperations],
 * and ExoPlayer pooling to [FtpExoPlayerPool].
 *
 * Thread-safe: uses mutex for synchronized access to [FTPClient].
 */
@Singleton
class FtpClient @Inject constructor(
    private val reachabilityGate: com.sza.fastmediasorter.core.network.NetworkReachabilityGate,
    private val lifecycleBootstrapper: dagger.Lazy<com.sza.fastmediasorter.data.network.lifecycle.NetworkLifecycleBootstrapper>,
    private val idleDisconnectPolicy: IdleDisconnectPolicy,
) {

    private var ftpClient: FTPClient? = null
    private val mutex = Any()
    private val trackedTransportKeys = ConcurrentHashMap.newKeySet<String>()

    @Volatile
    private var currentTransportKey: String? = null

    companion object {
        private const val CONNECT_TIMEOUT = 10000
        private const val SOCKET_TIMEOUT = 30000
        private const val KEEPALIVE_TIMEOUT = 15L
        private const val MAX_CONCURRENT_CONNECTIONS = 10
        private const val IDLE_TIMEOUT_MS = 30_000L
    }

    private val exoPlayerPool = FtpExoPlayerPool()
    private val connectedOps = FtpConnectedOperations(getClient = { ftpClient }, mutex = mutex)

    // region ExoPlayer pool

    /** S0195: trigger network lifecycle bootstrap on first FTP use. */
    @Throws(IOException::class)
    fun getConnectionForExoPlayer(connectionInfo: FtpExoPlayerPool.FtpConnectionInfo): FtpExoPlayerPool.ExoPlayerFtpConnection {
        lifecycleBootstrapper.get().ensureInitialized()
        reachabilityGate.requireAnyNetwork("FTP")
        val transportKey = transportKey(connectionInfo.host, connectionInfo.port, connectionInfo.username)
        trackedTransportKeys.add(transportKey)
        idleDisconnectPolicy.touch(transportKey)
        return exoPlayerPool.getConnectionForExoPlayer(connectionInfo).also {
            idleDisconnectPolicy.arm(transportKey, IDLE_TIMEOUT_MS) {
                cleanupIdleFtpConnections()
            }
        }
    }

    fun releaseExoPlayerConnection(client: FTPClient?) =
        exoPlayerPool.releaseExoPlayerConnection(client)

    fun cleanupIdleFtpConnections() = exoPlayerPool.cleanupIdleFtpConnections()

    // endregion

    // region Connection lifecycle

    /** S0195: trigger network lifecycle bootstrap on first FTP use. */
    suspend fun connect(
        host: String,
        port: Int = 21,
        username: String,
        password: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            lifecycleBootstrapper.get().ensureInitialized()
            reachabilityGate.requireAnyNetwork("FTP")
            disconnect()
            val client = FTPClient()
            client.connectTimeout = CONNECT_TIMEOUT
            client.defaultTimeout = SOCKET_TIMEOUT
            client.setDataTimeout(SOCKET_TIMEOUT)
            client.controlKeepAliveTimeout = Duration.ofSeconds(KEEPALIVE_TIMEOUT).seconds
            // S0212: encoding MUST be set before connect - Apache Commons Net
            // captures the control-channel encoding inside _connectAction_().
            client.applyUtf8Encoding()
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
                return@withContext Result.failure(IOException("FTP authentication failed"))
            }
            client.enterLocalPassiveMode()
            client.setFileType(FTP.BINARY_FILE_TYPE)
            // S0212: negotiate UTF-8 filename interpretation on RFC 2640 servers.
            client.enableUtf8Mode()
            ftpClient = client
            rememberTransportKey(transportKey(host, port, username))
            armCurrentTransport()
            Timber.d("FTP connected to $host:$port (hasUser=${username.isNotBlank()}, passive mode)")
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

    suspend fun disconnect() = disconnectInternal(disarmTrackedTimers = true)

    private suspend fun disconnectInternal(disarmTrackedTimers: Boolean) = withContext(Dispatchers.IO) {
        try {
            if (disarmTrackedTimers) {
                disarmTrackedTransports()
            }
            ftpClient?.let { client ->
                if (client.isConnected) {
                    val originalTimeout = client.soTimeout
                    try {
                        client.soTimeout = 1000
                        client.logout()
                    } catch (e: java.net.SocketTimeoutException) {
                        Timber.d("FTP logout timeout (ignored)")
                    } catch (e: Exception) {
                        Timber.d(e, "FTP logout error (ignored)")
                    } finally {
                        try {
                            client.soTimeout = originalTimeout
                        } catch (e: Exception) {
                            // Socket may be null/closed - ignore
                        }
                    }
                    client.disconnect()
                }
            }
            Timber.d("FTP disconnected")
        } catch (e: Exception) {
            Timber.w(e, "FTP disconnect error (non-critical)")
        } finally {
            ftpClient = null
            currentTransportKey = null
        }
    }

    fun isConnected(): Boolean = ftpClient?.isConnected == true

    // endregion

    // region Persistent-connection operations (delegate to FtpConnectedOperations)

    suspend fun listFilesWithMetadata(
        remotePath: String = "/",
        recursive: Boolean = true
    ): Result<List<FTPFile>> = withTrackedConnectedOperation {
        connectedOps.listFilesWithMetadata(remotePath, recursive)
    }

    suspend fun listFilesWithMetadataPaged(
        remotePath: String = "/",
        offset: Int = 0,
        limit: Int = 50,
        recursive: Boolean = true
    ): Result<List<FTPFile>> = withTrackedConnectedOperation {
        connectedOps.listFilesWithMetadataPaged(remotePath, offset, limit, recursive)
    }

    suspend fun listFiles(remotePath: String = "/"): Result<List<String>> =
        withTrackedConnectedOperation { connectedOps.listFiles(remotePath) }

    suspend fun readFileBytes(
        remotePath: String,
        maxBytes: Long = Long.MAX_VALUE
    ): Result<ByteArray> = withTrackedConnectedOperation {
        connectedOps.readFileBytes(remotePath, maxBytes)
    }

    suspend fun readFileBytesRange(
        remotePath: String,
        offset: Long,
        length: Long
    ): Result<ByteArray> = withTrackedConnectedOperation {
        connectedOps.readFileBytesRange(remotePath, offset, length)
    }

    suspend fun downloadFile(
        remotePath: String,
        outputStream: OutputStream,
        fileSize: Long = 0L,
        progressCallback: ByteProgressCallback? = null
    ): Result<Unit> = withTrackedConnectedOperation {
        connectedOps.downloadFile(remotePath, outputStream, fileSize, progressCallback)
    }

    suspend fun uploadFile(
        remotePath: String,
        inputStream: InputStream,
        fileSize: Long = 0L,
        progressCallback: ByteProgressCallback? = null
    ): Result<Unit> = withTrackedConnectedOperation {
        connectedOps.uploadFile(remotePath, inputStream, fileSize, progressCallback)
    }

    suspend fun deleteFile(remotePath: String): Result<Unit> = withTrackedConnectedOperation {
        connectedOps.deleteFile(remotePath)
    }

    suspend fun deleteDirectory(remotePath: String): Result<Unit> = withTrackedConnectedOperation {
        connectedOps.deleteDirectory(remotePath)
    }

    suspend fun renameFile(oldPath: String, newName: String): Result<Unit> =
        withTrackedConnectedOperation { connectedOps.renameFile(oldPath, newName) }

    suspend fun moveFile(oldPath: String, newPath: String): Result<Unit> =
        withTrackedConnectedOperation { connectedOps.moveFile(oldPath, newPath) }

    suspend fun createDirectory(remotePath: String): Result<Unit> =
        withTrackedConnectedOperation { connectedOps.createDirectory(remotePath) }

    suspend fun directoryExists(remotePath: String): Result<Boolean> =
        withTrackedConnectedOperation { connectedOps.directoryExists(remotePath) }

    // endregion

    // region Standalone operations (each creates its own connection)

    suspend fun testConnection(
        host: String,
        port: Int = 21,
        username: String,
        password: String
    ): Result<Boolean> = FtpStandaloneOperations.testConnection(host, port, username, password)

    suspend fun uploadFileWithNewConnection(
        host: String,
        port: Int,
        username: String,
        password: String,
        remotePath: String,
        inputStream: InputStream,
        fileSize: Long = 0L,
        progressCallback: ByteProgressCallback? = null
    ): Result<Unit> = FtpStandaloneOperations.uploadFile(
        host, port, username, password, remotePath, inputStream, fileSize, progressCallback
    )

    suspend fun deleteFileWithNewConnection(
        host: String,
        port: Int,
        username: String,
        password: String,
        remotePath: String
    ): Result<Unit> = FtpStandaloneOperations.deleteFile(host, port, username, password, remotePath)

    suspend fun renameFileWithNewConnection(
        host: String,
        port: Int,
        username: String,
        password: String,
        oldPath: String,
        newName: String
    ): Result<Unit> = FtpStandaloneOperations.renameFile(host, port, username, password, oldPath, newName)

    suspend fun createDirectoryWithNewConnection(
        host: String,
        port: Int,
        username: String,
        password: String,
        remotePath: String
    ): Result<Unit> = FtpStandaloneOperations.createDirectory(host, port, username, password, remotePath)

    suspend fun existsWithNewConnection(
        host: String,
        port: Int,
        username: String,
        password: String,
        remotePath: String
    ): Result<Boolean> = FtpStandaloneOperations.exists(host, port, username, password, remotePath)

    suspend fun readFileBytesWithNewConnection(
        host: String,
        port: Int,
        username: String,
        password: String,
        remotePath: String,
        maxBytes: Long = Long.MAX_VALUE
    ): Result<ByteArray> = FtpStandaloneOperations.readFileBytes(
        host, port, username, password, remotePath, maxBytes
    )

    suspend fun downloadFileWithNewConnection(
        host: String,
        port: Int,
        username: String,
        password: String,
        remotePath: String,
        outputStream: OutputStream,
        fileSize: Long = 0L,
        progressCallback: ByteProgressCallback? = null
    ): Result<Unit> = FtpStandaloneOperations.downloadFile(
        host, port, username, password, remotePath, outputStream, fileSize, progressCallback
    )

    suspend fun openInputStream(
        host: String,
        port: Int,
        username: String,
        password: String,
        remotePath: String
    ): Result<InputStream> = FtpStandaloneOperations.openInputStream(host, port, username, password, remotePath)

    // endregion

    private fun transportKey(host: String, port: Int, username: String): String {
        return "ftp@$host:$port:$username"
    }

    private fun rememberTransportKey(transportKey: String) {
        trackedTransportKeys.add(transportKey)
        currentTransportKey = transportKey
    }

    private fun armCurrentTransport() {
        currentTransportKey?.let { transportKey ->
            idleDisconnectPolicy.arm(transportKey, IDLE_TIMEOUT_MS) {
                disconnect()
            }
        }
    }

    private suspend fun <T> withTrackedConnectedOperation(
        block: suspend () -> Result<T>,
    ): Result<T> {
        currentTransportKey?.let(idleDisconnectPolicy::touch)
        val result = block()
        if (result.isSuccess) {
            armCurrentTransport()
        }
        return result
    }

    private fun disarmTrackedTransports() {
        trackedTransportKeys.forEach(idleDisconnectPolicy::disarm)
        trackedTransportKeys.clear()
    }
}
