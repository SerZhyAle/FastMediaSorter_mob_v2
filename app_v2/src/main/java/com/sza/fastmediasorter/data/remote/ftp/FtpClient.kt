@file:Suppress("DEPRECATION")

package com.sza.fastmediasorter.data.remote.ftp

import com.sza.fastmediasorter.core.util.rethrowIfCancellation
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
import java.util.concurrent.atomic.AtomicInteger
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
    private val lifecycleBootstrapper:
    dagger.Lazy<com.sza.fastmediasorter.data.network.lifecycle.NetworkLifecycleBootstrapper>,
    private val idleDisconnectPolicy: IdleDisconnectPolicy,
) {

    // Published and detached under [stateLock]; a detached client is closed under [mutex], so it is
    // never torn down beneath an operation that is still using it.
    @Volatile
    private var sharedClient: FTPClient? = null
    private val mutex = Any()

    // Separate from [mutex], which a transfer holds for its whole duration: starting an operation or
    // detaching the client must not wait for a running download.
    private val stateLock = Any()
    private val trackedTransportKeys = ConcurrentHashMap.newKeySet<String>()

    // S1297: connected-mode operations share ONE FTPClient and nothing refreshed the idle timer
    // while a transfer was streaming, so any download/upload/recursive listing longer than
    // IDLE_TIMEOUT_MS was killed mid-flight by the idle callback (partial file, failed copy).
    private val inFlightOperations = AtomicInteger(0)

    @Volatile
    private var currentTransportKey: String? = null

    companion object {
        private const val CONNECT_TIMEOUT = 10000
        private const val SOCKET_TIMEOUT = 30000
        private const val KEEPALIVE_TIMEOUT = 15L
        private const val IDLE_TIMEOUT_MS = 30_000L
    }

    private val exoPlayerPool = FtpExoPlayerPool()
    private val connectedOps = FtpConnectedOperations(getClient = { sharedClient }, mutex = mutex)

    // region ExoPlayer pool

    /** S0195: trigger network lifecycle bootstrap on first FTP use. */
    @Throws(IOException::class)
    fun getConnectionForExoPlayer(
        connectionInfo: FtpExoPlayerPool.FtpConnectionInfo
    ): FtpExoPlayerPool.ExoPlayerFtpConnection {
        lifecycleBootstrapper.get().ensureInitialized()
        reachabilityGate.requireAnyNetwork("FTP")
        val transportKey = transportKey(connectionInfo.host, connectionInfo.port, connectionInfo.username)
        trackedTransportKeys.add(transportKey)
        idleDisconnectPolicy.touch(transportKey)
        // ExoPlayer FTP connections are created fresh per acquire and disconnected in
        // releaseExoPlayerConnection; they are never pooled, so there is no idle pool to arm/sweep.
        return exoPlayerPool.getConnectionForExoPlayer(connectionInfo)
    }

    fun releaseExoPlayerConnection(client: FTPClient?) =
        exoPlayerPool.releaseExoPlayerConnection(client)

    // endregion

    // region Connection lifecycle

    /** S0195: trigger network lifecycle bootstrap on first FTP use. */
    suspend fun connect(
        host: String,
        port: Int = 21,
        username: String,
        password: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        var pending: FTPClient? = null
        try {
            lifecycleBootstrapper.get().ensureInitialized()
            reachabilityGate.requireAnyNetwork("FTP")
            disconnect()
            val client = FTPClient()
            pending = client
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
            synchronized(stateLock) {
                sharedClient = client
                rememberTransportKey(transportKey(host, port, username))
            }
            pending = null
            armCurrentTransport()
            Timber.d("FTP connected to $host:$port (hasUser=${username.isNotBlank()}, passive mode)")
            Result.success(Unit)
        } catch (e: IOException) {
            Timber.e(e, "FTP connection failed: $host:$port")
            pending?.let(::disconnectQuietly)
            Result.failure(e)
        } catch (e: Exception) {
            e.rethrowIfCancellation()
            Timber.e(e, "FTP connection error: $host:$port")
            pending?.let(::disconnectQuietly)
            Result.failure(e)
        }
    }

    /** A client that failed half-way through connect is not published, so only this path can close it. */
    private fun disconnectQuietly(client: FTPClient) {
        try {
            if (client.isConnected) client.disconnect()
        } catch (e: IOException) {
            Timber.d(e, "FTP disconnect of a failed connect (ignored)")
        }
    }

    suspend fun disconnect() = disconnectInternal(disarmTrackedTimers = true)

    private suspend fun disconnectInternal(disarmTrackedTimers: Boolean) = withContext(Dispatchers.IO) {
        if (disarmTrackedTimers) {
            disarmTrackedTransports()
        }
        val detached = synchronized(stateLock) { detachClientLocked() }
        if (detached != null) closeClient(detached)
    }

    private fun detachClientLocked(): FTPClient? {
        val client = sharedClient
        sharedClient = null
        currentTransportKey = null
        return client
    }

    /** Waits for an operation still inside [mutex] instead of closing its socket mid-command. */
    private fun closeClient(client: FTPClient) = synchronized(mutex) {
        try {
            if (client.isConnected) {
                val originalTimeout = client.soTimeout
                try {
                    client.soTimeout = 1000
                    client.logout()
                } catch (e: java.net.SocketTimeoutException) {
                    Timber.d("FTP logout timeout (ignored)")
                } catch (e: Exception) {
                    e.rethrowIfCancellation()
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
            Timber.d("FTP disconnected")
        } catch (e: Exception) {
            e.rethrowIfCancellation()
            Timber.w(e, "FTP disconnect error (non-critical)")
        }
    }

    fun isConnected(): Boolean = sharedClient?.isConnected == true

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
        host,
        port,
        username,
        password,
        remotePath,
        inputStream,
        fileSize,
        progressCallback
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
        host,
        port,
        username,
        password,
        remotePath,
        maxBytes
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
        host,
        port,
        username,
        password,
        remotePath,
        outputStream,
        fileSize,
        progressCallback
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
                onIdleTimeout()
            }
        }
    }

    /**
     * S1297: never disconnect the shared client out from under a running operation. A long transfer
     * or recursive listing keeps the control socket legitimately busy well past the idle window, so
     * the timer is re-armed instead and the connection closes on a later, genuinely idle tick.
     */
    private suspend fun onIdleTimeout() {
        // Check and detach under the same lock an operation start takes, so no operation can begin
        // between "nothing in flight" and the client going away.
        Timber.d("S3740: FTP idle timeout checks in-flight operations")
        var inFlight = 0
        val detached = synchronized(stateLock) {
            inFlight = inFlightOperations.get()
            if (inFlight > 0) null else detachClientLocked()
        }
        if (inFlight > 0) {
            Timber.d("FTP idle timeout deferred - %d operation(s) in flight", inFlight)
            armCurrentTransport()
            return
        }
        disarmTrackedTransports()
        if (detached != null) withContext(Dispatchers.IO) { closeClient(detached) }
    }

    private suspend fun <T> withTrackedConnectedOperation(
        block: suspend () -> Result<T>,
    ): Result<T> {
        currentTransportKey?.let(idleDisconnectPolicy::touch)
        synchronized(stateLock) { inFlightOperations.incrementAndGet() }
        val result = try {
            block()
        } finally {
            inFlightOperations.decrementAndGet()
        }
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
