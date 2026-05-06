package com.sza.fastmediasorter.data.remote.sftp

import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Semaphore

/**
 * SFTP session + channel pool for SftpClient.
 *
 * One [PooledConnection] per (host, port, user) holds a JSch [Session] plus a small bag of
 * [ChannelSftp]s. The pool serves both:
 *   - the suspending file-operation surface via [withConnection], which serializes per-channel
 *     work through a [Mutex] and rebuilds the session on hard failure;
 *   - the BLOCKING ExoPlayer DataSource path via [getConnectionForExoPlayer] /
 *     [releaseExoPlayerConnection], which uses a dedicated synchronized lock to avoid TOCTOU
 *     races with [invalidateConnection] (ML-008).
 *
 * Idle entries past [IDLE_TIMEOUT_MS] are reaped on the [cleanupScope] (ML-009).
 *
 * Extracted to keep SftpClient below the 1000-line cap.
 */
class SftpConnectionPool {

    private data class PooledConnection(
        val session: Session,
        val jsch: JSch,
        val channels: MutableList<ChannelSftp> = mutableListOf(),
        val channelMutexes: MutableList<Mutex> = mutableListOf(),
        val sessionMutex: Mutex = Mutex(),
        var lastUsed: Long = System.currentTimeMillis()
    )

    private data class ConnectionKey(val host: String, val port: Int, val username: String)

    private val connectionPool = ConcurrentHashMap<ConnectionKey, PooledConnection>()
    private val playbackConnectionPool = ConcurrentHashMap<ConnectionKey, PooledConnection>()
    private val connectionSemaphore = Semaphore(MAX_CONCURRENT_CONNECTIONS)
    private val poolMutex = Mutex()

    // Dedicated lock for ExoPlayer (blocking) path to avoid race with concurrent invalidateConnection() (ML-008)
    private val exoPlayerPoolLock = Any()

    // Cleanup scope for idle connection maintenance (ML-009)
    private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** ExoPlayer-facing wrapper. Both [session] and [channel] are owned by the pool — do not close. */
    data class ExoPlayerConnection(val session: Session, val channel: ChannelSftp)

    /**
     * Acquire a channel from the pool, run [block], and release. On channel failure the channel
     * is removed; on session failure the entire entry is invalidated and a single retry runs
     * against a fresh entry.
     */
    suspend fun <T> withConnection(
        info: SftpClient.SftpConnectionInfo,
        block: suspend (ChannelSftp) -> Result<T>
    ): Result<T> = withContext(Dispatchers.IO) {
        val key = ConnectionKey(info.host, info.port, info.username)

        try {
            connectionSemaphore.acquire()
            try {
                val pooled = getOrCreateConnection(key, info)
                pooled.lastUsed = System.currentTimeMillis()

                val (channel, mutex) = getOrCreateChannel(pooled, info)

                try {
                    // Serialize operations on the same channel to prevent race conditions
                    mutex.withLock { block(channel) }
                } catch (e: Exception) {
                    if (!channel.isConnected) {
                        Timber.w("SFTP channel lost, removing from pool: ${e.message}")
                        removeChannel(pooled, channel)
                    }

                    if (!pooled.session.isConnected) {
                        Timber.w("SFTP session lost, retrying: ${e.message}")
                        invalidateConnection(key)
                        val newPooled = getOrCreateConnection(key, info)
                        newPooled.lastUsed = System.currentTimeMillis()
                        val (newChannel, newMutex) = getOrCreateChannel(newPooled, info)
                        return@withContext newMutex.withLock { block(newChannel) }
                    }
                    throw e
                }
            } finally {
                connectionSemaphore.release()
            }
        } catch (e: CancellationException) {
            Timber.d("SFTP operation cancelled")
            throw e
        } catch (e: InterruptedException) {
            // Blocking Semaphore.acquire() throws InterruptedException when the coroutine is cancelled
            Timber.d("SFTP operation interrupted (coroutine cancelled)")
            Thread.currentThread().interrupt() // restore interrupt flag
            throw CancellationException("SFTP interrupted", e)
        } catch (e: Exception) {
            Timber.e(e, "SFTP operation failed")
            Result.failure(e)
        }
    }

    private suspend fun getOrCreateChannel(
        pooled: PooledConnection,
        info: SftpClient.SftpConnectionInfo
    ): Pair<ChannelSftp, Mutex> = pooled.sessionMutex.withLock {
        // First connected channel wins
        pooled.channels.forEachIndexed { index, channel ->
            if (channel.isConnected) {
                return@withLock channel to pooled.channelMutexes[index]
            }
        }

        // No available channels — create new one if under limit
        if (pooled.channels.size < MAX_CHANNELS_PER_SESSION) {
            val newChannel = pooled.session.openChannel("sftp") as ChannelSftp
            newChannel.connect(CONNECTION_TIMEOUT)
            val newMutex = Mutex()

            pooled.channels.add(newChannel)
            pooled.channelMutexes.add(newMutex)

            Timber.d("Created new SFTP channel (${pooled.channels.size}/${MAX_CHANNELS_PER_SESSION}) for ${info.host}")
            return@withLock newChannel to newMutex
        }

        // All channels in use — fall back to first one (callers wait on its mutex)
        Timber.d("All SFTP channels in use for ${info.host}, reusing first channel")
        pooled.channels[0] to pooled.channelMutexes[0]
    }

    private suspend fun removeChannel(pooled: PooledConnection, channel: ChannelSftp) {
        pooled.sessionMutex.withLock {
            val index = pooled.channels.indexOf(channel)
            if (index >= 0) {
                try {
                    channel.disconnect()
                } catch (e: Exception) {
                    Timber.w("Error disconnecting channel: ${e.message}")
                }
                pooled.channels.removeAt(index)
                pooled.channelMutexes.removeAt(index)
                Timber.d("Removed failed channel, ${pooled.channels.size} remaining")
            }
        }
    }

    private suspend fun getOrCreateConnection(
        key: ConnectionKey,
        info: SftpClient.SftpConnectionInfo
    ): PooledConnection {
        poolMutex.lock()
        try {
            val existing = connectionPool[key]
            if (existing != null && existing.session.isConnected) {
                if (existing.channels.any { it.isConnected }) {
                    return existing
                }
            }

            // Remove invalid connection if exists
            if (existing != null) {
                try {
                    existing.channels.forEach { it.disconnect() }
                    existing.session.disconnect()
                } catch (e: Exception) {
                    Timber.w("Error closing invalid connection: ${e.message}")
                }
                connectionPool.remove(key)
            }

            val jsch = JSch()
            applyIdentity(jsch, info, namePrefix = "key")

            val session = jsch.getSession(info.username, info.host, info.port)
            applyAuth(session, info)
            session.timeout = SOCKET_TIMEOUT
            session.connect(CONNECTION_TIMEOUT)

            val channel = session.openChannel("sftp") as ChannelSftp
            channel.connect(CONNECTION_TIMEOUT)

            val pooled = PooledConnection(
                session = session,
                jsch = jsch,
                channels = mutableListOf(channel),
                channelMutexes = mutableListOf(Mutex()),
                sessionMutex = Mutex()
            )
            connectionPool[key] = pooled

            Timber.d("Created new SFTP connection to ${info.host} with channel pool")
            return pooled
        } finally {
            poolMutex.unlock()
        }
    }

    /** Drop the pooled session for [info]. Public entry point for client-driven retry. */
    suspend fun invalidate(info: SftpClient.SftpConnectionInfo) {
        invalidateConnection(ConnectionKey(info.host, info.port, info.username))
    }

    private suspend fun invalidateConnection(key: ConnectionKey) {
        poolMutex.lock()
        try {
            connectionPool.remove(key)?.let { pooled ->
                try {
                    pooled.channels.forEach { channel ->
                        if (channel.isConnected) channel.disconnect()
                    }
                    pooled.session.disconnect()
                    Timber.d("Invalidated SFTP connection with ${pooled.channels.size} channels")
                } catch (e: Exception) {
                    Timber.w("Error closing invalidated connection: ${e.message}")
                }
            }
        } finally {
            poolMutex.unlock()
        }
    }

    private fun cleanupIdleConnections() {
        val now = System.currentTimeMillis()
        val keysToRemove = connectionPool.filter { (_, conn) ->
            now - conn.lastUsed > IDLE_TIMEOUT_MS
        }.keys

        if (keysToRemove.isEmpty()) return

        cleanupScope.launch {
            poolMutex.withLock {
                keysToRemove.forEach { key ->
                    connectionPool.remove(key)?.let { pooled ->
                        try {
                            pooled.channels.forEach { channel ->
                                if (channel.isConnected) channel.disconnect()
                            }
                            pooled.session.disconnect()
                            Timber.d("Closed idle SFTP connection to ${key.host} with ${pooled.channels.size} channels")
                        } catch (e: Exception) {
                            Timber.w("Error closing idle connection: ${e.message}")
                        }
                    }
                }
            }
        }
    }

    /**
     * Get a pooled SFTP connection for ExoPlayer DataSource. BLOCKING (not suspend) because
     * `DataSource.open()` is itself blocking. Caller must NOT close session or channel.
     */
    @Throws(IOException::class)
    fun getConnectionForExoPlayer(connectionInfo: SftpClient.SftpConnectionInfo): ExoPlayerConnection {
        val key = ConnectionKey(connectionInfo.host, connectionInfo.port, connectionInfo.username)

        try {
            connectionSemaphore.acquire()

            // Dedicated lock to eliminate TOCTOU race with invalidateConnection() (ML-008)
            synchronized(exoPlayerPoolLock) {
                val existing = playbackConnectionPool[key]
                if (existing != null && existing.session.isConnected) {
                    existing.lastUsed = System.currentTimeMillis()

                    synchronized(existing) {
                        existing.channels.firstOrNull { it.isConnected }?.let { channel ->
                            Timber.d("SFTP ExoPlayer: Reusing pooled connection to ${connectionInfo.host}")
                            return ExoPlayerConnection(existing.session, channel)
                        }

                        // No connected channel — create new one if under limit
                        if (existing.channels.size < MAX_CHANNELS_PER_SESSION) {
                            val newChannel = existing.session.openChannel("sftp") as ChannelSftp
                            newChannel.connect(CONNECTION_TIMEOUT)
                            existing.channels.add(newChannel)
                            existing.channelMutexes.add(Mutex())
                            Timber.d("SFTP ExoPlayer: Created new channel (${existing.channels.size}/$MAX_CHANNELS_PER_SESSION)")
                            return ExoPlayerConnection(existing.session, newChannel)
                        }

                        // All channels busy — fall back to first one (caller will wait on I/O)
                        Timber.d("SFTP ExoPlayer: All channels busy, reusing first channel")
                        return ExoPlayerConnection(existing.session, existing.channels[0])
                    }
                }

                Timber.d("SFTP ExoPlayer: Creating new pooled connection to ${connectionInfo.host}")

                val jsch = JSch()
                applyIdentity(jsch, connectionInfo, namePrefix = "exoplayer_key")

                val session = jsch.getSession(connectionInfo.username, connectionInfo.host, connectionInfo.port)
                applyAuth(session, connectionInfo)
                session.timeout = SOCKET_TIMEOUT
                session.connect(CONNECTION_TIMEOUT)

                val channel = session.openChannel("sftp") as ChannelSftp
                channel.connect(CONNECTION_TIMEOUT)

                val pooled = PooledConnection(
                    session = session,
                    jsch = jsch,
                    channels = mutableListOf(channel),
                    channelMutexes = mutableListOf(Mutex()),
                    sessionMutex = Mutex()
                )
                playbackConnectionPool[key] = pooled

                Timber.d("SFTP ExoPlayer: New connection added to pool for ${connectionInfo.host}")
                return ExoPlayerConnection(session, channel)
            }
        } catch (e: Exception) {
            connectionSemaphore.release()
            Timber.e(e, "SFTP ExoPlayer: Failed to get connection for ${connectionInfo.host}")
            throw IOException("Failed to establish SFTP connection: ${e.message}", e)
        }
    }

    private fun evictExoPlayerChannel(channel: ChannelSftp) {
        synchronized(exoPlayerPoolLock) {
            for (pooled in connectionPool.values) {
                val index = pooled.channels.indexOf(channel)
                if (index >= 0) {
                    try {
                        channel.disconnect()
                    } catch (e: Exception) {
                        Timber.w("SFTP ExoPlayer: Eviction disconnect failed: ${e.message}")
                    }
                    pooled.channels.removeAt(index)
                    pooled.channelMutexes.removeAt(index)
                    Timber.d("SFTP ExoPlayer: Evicted broken channel from pool")
                    return
                }
            }
            Timber.d("SFTP ExoPlayer: Eviction skipped — channel not in pool")
        }
    }

    /** Release semaphore slot after ExoPlayer is done. Triggers idle-pool sweep. */
    fun releaseExoPlayerConnection(channel: ChannelSftp? = null, broken: Boolean = false) {
        if (broken && channel != null) {
            evictExoPlayerChannel(channel)
        }
        connectionSemaphore.release()
        cleanupIdleConnections()
    }

    /**
     * Open an input stream against [remotePath] using a freshly opened SFTP channel on the
     * pooled session for [info]. The returned stream owns the channel — closing it disconnects.
     * Falls back to a fresh session if the existing one is dead or the channel fails to connect.
     */
    suspend fun openInputStream(
        info: SftpClient.SftpConnectionInfo,
        remotePath: String
    ): Result<java.io.InputStream> = withContext(Dispatchers.IO) {
        val key = ConnectionKey(info.host, info.port, info.username)
        try {
            connectionSemaphore.acquire()
            try {
                val pooled = getOrCreateConnection(key, info)
                pooled.lastUsed = System.currentTimeMillis()

                if (!pooled.session.isConnected) {
                    Timber.w("SFTP session disconnected, recreating")
                    connectionPool.remove(key)
                    val newPooled = getOrCreateConnection(key, info)
                    pooled.session.disconnect()
                    newPooled.lastUsed = System.currentTimeMillis()
                }

                val channel = pooled.session.openChannel("sftp") as ChannelSftp

                if (!channel.isConnected) {
                    try {
                        channel.connect(CONNECTION_TIMEOUT)
                    } catch (e: com.jcraft.jsch.JSchException) {
                        // Channel connection failed — recreate session and retry once
                        Timber.w(e, "SFTP channel connection failed, recreating session")
                        connectionPool.remove(key)
                        pooled.session.disconnect()

                        val newPooled = getOrCreateConnection(key, info)
                        val newChannel = newPooled.session.openChannel("sftp") as ChannelSftp
                        newChannel.connect(CONNECTION_TIMEOUT)

                        val stream = newChannel.get(remotePath)
                        val wrapper = object : java.io.FilterInputStream(stream) {
                            override fun close() {
                                try { super.close() } finally {
                                    try { newChannel.disconnect() } catch (e: Exception) {
                                        Timber.w("Error closing SFTP stream channel: ${e.message}")
                                    }
                                }
                            }
                        }
                        return@withContext Result.success(wrapper)
                    }
                }

                try {
                    val stream = channel.get(remotePath)
                    val wrapper = object : java.io.FilterInputStream(stream) {
                        override fun close() {
                            try { super.close() } finally {
                                try { channel.disconnect() } catch (e: Exception) {
                                    Timber.w("Error closing SFTP stream channel: ${e.message}")
                                }
                            }
                        }
                    }
                    Result.success(wrapper)
                } catch (e: Exception) {
                    channel.disconnect()
                    throw e
                }
            } finally {
                connectionSemaphore.release()
                cleanupIdleConnections()
            }
        } catch (e: Exception) {
            Timber.e(e, "SFTP openInputStream failed: $remotePath")
            Result.failure(e)
        }
    }

    /** Disconnect every pooled session. Call on app shutdown. */
    suspend fun disconnectAll() {
        poolMutex.lock()
        try {
            connectionPool.values.forEach { pooled ->
                try {
                    pooled.channels.forEach { channel ->
                        if (channel.isConnected) channel.disconnect()
                    }
                    pooled.session.disconnect()
                } catch (_: Exception) {}
            }
            connectionPool.clear()
        } finally {
            poolMutex.unlock()
        }
    }

    private fun applyIdentity(jsch: JSch, info: SftpClient.SftpConnectionInfo, namePrefix: String) {
        if (info.privateKey != null) {
            val name = "${namePrefix}_${System.currentTimeMillis()}"
            if (info.passphrase != null) {
                jsch.addIdentity(name, info.privateKey.toByteArray(), null, info.passphrase.toByteArray())
            } else {
                jsch.addIdentity(name, info.privateKey.toByteArray(), null, null)
            }
        }
    }

    private fun applyAuth(session: Session, info: SftpClient.SftpConnectionInfo) {
        if (info.privateKey != null) {
            if (info.passphrase != null) {
                session.userInfo = object : com.jcraft.jsch.UserInfo {
                    override fun getPassphrase(): String = info.passphrase
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
            session.setConfig(config)
        } else {
            session.setPassword(info.password)
            session.userInfo = object : com.jcraft.jsch.UserInfo {
                override fun getPassphrase(): String? = null
                override fun getPassword(): String = info.password
                override fun promptPassword(message: String?): Boolean = true
                override fun promptPassphrase(message: String?): Boolean = false
                override fun promptYesNo(message: String?): Boolean = true
                override fun showMessage(message: String?) {}
            }
            val config = java.util.Properties()
            config["StrictHostKeyChecking"] = "no"
            config["PreferredAuthentications"] = "keyboard-interactive,password"
            session.setConfig(config)
        }
    }

    companion object {
        private const val CONNECTION_TIMEOUT = 10_000
        private const val SOCKET_TIMEOUT = 30_000
        private const val MAX_CONCURRENT_CONNECTIONS = 15
        private const val MAX_CHANNELS_PER_SESSION = 5
        private const val IDLE_TIMEOUT_MS = 25_000L
    }
}
