@file:Suppress("DEPRECATION")

package com.sza.fastmediasorter.data.remote.ftp

import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPReply
import timber.log.Timber
import java.io.IOException
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Semaphore

/**
 * ExoPlayer FTP connection management for FtpClient.
 *
 * Each ExoPlayer DataSource gets a dedicated FTPClient instance — sharing a single client
 * across concurrent thumbnail extraction threads has been observed causing protocol-reply
 * interleaving (e.g. PASV expects 227 but receives NOOP 200). The semaphore caps total
 * concurrent FTP sockets system-wide.
 *
 * The idle pool tracking remains intact for parity with the previous implementation but is
 * currently dormant — `getConnectionForExoPlayer` always creates a fresh client.
 *
 * Extracted to keep FtpClient below the 1000-line cap.
 */
class FtpExoPlayerPool {

    private val connectionPool = ConcurrentHashMap<ConnectionKey, PooledFtpConnection>()
    private val connectionSemaphore = Semaphore(MAX_CONCURRENT_CONNECTIONS)
    private val poolMutex = Any()

    /** ExoPlayer-facing connection info (full credentials carry through to each acquire). */
    data class FtpConnectionInfo(
        val host: String,
        val port: Int = 21,
        val username: String,
        val password: String
    )

    /** Wrapper handed back to ExoPlayer DataSources. The client is owned by the pool — do not disconnect. */
    data class ExoPlayerFtpConnection(val client: FTPClient)

    private data class ConnectionKey(val host: String, val port: Int, val username: String)

    private data class PooledFtpConnection(
        val client: FTPClient,
        var lastUsed: Long = System.currentTimeMillis()
    )

    /**
     * Get a connection for an ExoPlayer DataSource. BLOCKING (not suspend) because
     * `DataSource.open()` is itself blocking. Caller must NOT close the returned client —
     * use [releaseExoPlayerConnection] when done.
     */
    @Throws(IOException::class)
    fun getConnectionForExoPlayer(connectionInfo: FtpConnectionInfo): ExoPlayerFtpConnection {
        try {
            connectionSemaphore.acquire()

            // Each ExoPlayer consumer needs its own FTPClient. Sharing causes protocol-reply
            // interleaving across concurrent thumbnail/probe threads.
            Timber.d("FTP ExoPlayer: Creating dedicated connection to ${connectionInfo.host}")

            val client = FTPClient()
            client.connectTimeout = CONNECT_TIMEOUT
            client.defaultTimeout = SOCKET_TIMEOUT
            client.setDataTimeout(SOCKET_TIMEOUT)
            client.controlKeepAliveTimeout = Duration.ofSeconds(KEEPALIVE_TIMEOUT).seconds

            client.connect(connectionInfo.host, connectionInfo.port)

            val replyCode = client.replyCode
            if (!FTPReply.isPositiveCompletion(replyCode)) {
                client.disconnect()
                throw IOException("FTP server refused connection. Reply code: $replyCode")
            }

            if (!client.login(connectionInfo.username, connectionInfo.password)) {
                client.disconnect()
                throw IOException("FTP authentication failed for user: ${connectionInfo.username}")
            }

            client.enterLocalPassiveMode()
            client.setFileType(FTP.BINARY_FILE_TYPE)
            client.controlEncoding = "UTF-8"

            return ExoPlayerFtpConnection(client)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IOException("Interrupted while waiting for FTP connection", e)
        } catch (e: IOException) {
            connectionSemaphore.release()
            throw e
        } catch (e: Exception) {
            connectionSemaphore.release()
            Timber.e(e, "FTP ExoPlayer: Failed to get connection for ${connectionInfo.host}")
            throw IOException("Failed to establish FTP connection: ${e.message}", e)
        }
    }

    /** Complete pending command + logout + disconnect, then release the semaphore slot. */
    fun releaseExoPlayerConnection(client: FTPClient?) {
        try {
            // FTP requires completePendingCommand after stream operations.
            client?.completePendingCommand()
        } catch (e: Exception) {
            Timber.w("FTP ExoPlayer: completePendingCommand failed (non-critical): ${e.message}")
        }

        try {
            if (client?.isConnected == true) {
                try {
                    client.logout()
                } catch (e: Exception) {
                    Timber.w("FTP ExoPlayer: logout failed (ignored): ${e.message}")
                }
                client.disconnect()
            }
        } catch (e: Exception) {
            Timber.w("FTP ExoPlayer: disconnect failed (ignored): ${e.message}")
        }

        connectionSemaphore.release()
    }

    /** Walk the idle pool and disconnect entries past [IDLE_TIMEOUT_MS]. */
    fun cleanupIdleFtpConnections() {
        val now = System.currentTimeMillis()
        val keysToRemove = connectionPool.filter { (_, conn) ->
            now - conn.lastUsed > IDLE_TIMEOUT_MS
        }.keys

        if (keysToRemove.isEmpty()) return

        synchronized(poolMutex) {
            keysToRemove.forEach { key ->
                connectionPool.remove(key)?.let { pooled ->
                    try {
                        if (pooled.client.isConnected) {
                            pooled.client.logout()
                            pooled.client.disconnect()
                        }
                        Timber.d("FTP: Closed idle connection to ${key.host}")
                    } catch (e: Exception) {
                        Timber.w("FTP: Error closing idle connection: ${e.message}")
                    }
                }
            }
        }
    }

    companion object {
        private const val CONNECT_TIMEOUT = 10_000
        private const val SOCKET_TIMEOUT = 30_000
        private const val KEEPALIVE_TIMEOUT = 15L
        private const val MAX_CONCURRENT_CONNECTIONS = 10
        private const val IDLE_TIMEOUT_MS = 25_000L
    }
}
