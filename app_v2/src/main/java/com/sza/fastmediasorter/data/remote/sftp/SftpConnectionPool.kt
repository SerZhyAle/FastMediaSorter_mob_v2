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
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock

/** Purpose of an SFTP channel within a pooled session (S0113: unified-session). */
enum class ChannelPurpose { PLAYBACK, FILE_OPS }

/**
 * SFTP session + channel pool for SftpClient.
 *
 * One [PooledConnection] per (host, port, user) holds a single JSch [Session] and a list of
 * [PooledChannel]s partitioned by [ChannelPurpose]. The unified design (S0113 Phase 04) eliminates
 * the two-SSH-session problem — PLAYBACK and FILE_OPS channels share one session per account.
 *
 * Active PLAYBACK streams are tracked via [PooledConnection.activeStreamCount] so idle cleanup
 * never evicts a session that ExoPlayer is reading from (S0113 Phase 01).
 *
 * Extracted to keep SftpClient below the 1000-line cap.
 */
class SftpConnectionPool {

    /** Single SFTP channel with its serialization mutex and declared purpose. */
    data class PooledChannel(
        val channel: ChannelSftp,
        val mutex: Mutex,
        val purpose: ChannelPurpose
    )

    private class PooledConnection(
        val session: Session,
        val jsch: JSch,
        val pooledChannels: MutableList<PooledChannel> = mutableListOf(),
        val sessionMutex: Mutex = Mutex(),
        // Guards session.openChannel() across both suspend and blocking callers (Research #2)
        val openChannelLock: ReentrantLock = ReentrantLock(),
        var lastUsed: Long = System.currentTimeMillis(),
        // Non-zero while ExoPlayer is actively streaming; idle cleanup skips such sessions
        val activeStreamCount: AtomicInteger = AtomicInteger(0)
    )

    private data class ConnectionKey(val host: String, val port: Int, val username: String)

    /** Unified pool — one entry per (host, port, user) for both PLAYBACK and FILE_OPS. */
    private val pooledSessions = ConcurrentHashMap<ConnectionKey, PooledConnection>()
    private val connectionSemaphore = Semaphore(MAX_CONCURRENT_CONNECTIONS)
    private val poolMutex = Mutex()
    private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** ExoPlayer-facing wrapper. Both [session] and [channel] are owned by the pool — do not close. */
    data class ExoPlayerConnection(val session: Session, val channel: ChannelSftp)

    // ── Suspend path (FILE_OPS) ──────────────────────────────────────────────────────────────────

    suspend fun <T> withConnection(
        info: SftpClient.SftpConnectionInfo,
        block: suspend (ChannelSftp) -> Result<T>
    ): Result<T> = withContext(Dispatchers.IO) {
        val key = ConnectionKey(info.host, info.port, info.username)
        try {
            connectionSemaphore.acquire()
            try {
                val pooled = getOrCreateSession(key, info)
                pooled.lastUsed = System.currentTimeMillis()
                val pc = getOrCreateFileOpsChannel(pooled, info)
                try {
                    pc.mutex.withLock { block(pc.channel) }
                } catch (e: Exception) {
                    if (!pc.channel.isConnected) {
                        Timber.w("SFTP [FILE_OPS] channel lost: ${e.message}")
                        removeChannel(pooled, pc.channel)
                    }
                    if (!pooled.session.isConnected) {
                        Timber.w("SFTP [FILE_OPS] session lost, retrying: ${e.message}")
                        invalidateSession(key)
                        val newPooled = getOrCreateSession(key, info)
                        newPooled.lastUsed = System.currentTimeMillis()
                        val newPc = getOrCreateFileOpsChannel(newPooled, info)
                        return@withContext newPc.mutex.withLock { block(newPc.channel) }
                    }
                    // S0147: silent TCP drop — isConnected flags stay true but transport is dead
                    if (isDeadTransportException(e)) {
                        Timber.w("SFTP [FILE_OPS] dead transport detected (${e.message}), reconnecting")
                        removeChannel(pooled, pc.channel)
                        invalidateSession(key)
                        val newPooled = getOrCreateSession(key, info)
                        newPooled.lastUsed = System.currentTimeMillis()
                        val newPc = getOrCreateFileOpsChannel(newPooled, info)
                        return@withContext newPc.mutex.withLock { block(newPc.channel) }
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
            Timber.d("SFTP operation interrupted (coroutine cancelled)")
            Thread.currentThread().interrupt()
            throw CancellationException("SFTP interrupted", e)
        } catch (e: Exception) {
            Timber.e(e, "SFTP [FILE_OPS] operation failed")
            Result.failure(e)
        }
    }

    private suspend fun getOrCreateFileOpsChannel(
        pooled: PooledConnection,
        info: SftpClient.SftpConnectionInfo
    ): PooledChannel = pooled.sessionMutex.withLock {
        pooled.pooledChannels
            .firstOrNull { it.purpose == ChannelPurpose.FILE_OPS && it.channel.isConnected }
            ?.let { return@withLock it }

        val fileOpsCount = pooled.pooledChannels.count { it.purpose == ChannelPurpose.FILE_OPS }
        if (fileOpsCount < MAX_FILE_OPS_CHANNELS) {
            val ch = openChannelSafe(pooled)
            val pc = PooledChannel(ch, Mutex(), ChannelPurpose.FILE_OPS)
            pooled.pooledChannels.add(pc)
            Timber.d("SFTP [FILE_OPS] new channel (total=${pooled.pooledChannels.size}) for ${info.host}")
            return@withLock pc
        }

        Timber.d("SFTP [FILE_OPS] all channels busy for ${info.host}, reusing first")
        pooled.pooledChannels.first { it.purpose == ChannelPurpose.FILE_OPS }
    }

    private suspend fun removeChannel(pooled: PooledConnection, channel: ChannelSftp) {
        pooled.sessionMutex.withLock {
            val target = pooled.pooledChannels.firstOrNull { it.channel == channel } ?: return@withLock
            try { channel.disconnect() } catch (e: Exception) {
                Timber.w("Error disconnecting channel: ${e.message}")
            }
            pooled.pooledChannels.remove(target)
            Timber.d("SFTP removed failed channel, ${pooled.pooledChannels.size} remaining")
        }
    }

    private suspend fun getOrCreateSession(
        key: ConnectionKey,
        info: SftpClient.SftpConnectionInfo
    ): PooledConnection {
        poolMutex.lock()
        try {
            val existing = pooledSessions[key]
            if (existing != null && existing.session.isConnected) return existing

            if (existing != null) {
                try {
                    existing.pooledChannels.forEach { try { it.channel.disconnect() } catch (_: Exception) {} }
                    existing.session.disconnect()
                } catch (e: Exception) { Timber.w("Error closing stale session: ${e.message}") }
                pooledSessions.remove(key)
            }

            val jsch = JSch()
            applyIdentity(jsch, info, namePrefix = "key")
            val session = jsch.getSession(info.username, info.host, info.port)
            applyAuth(session, info)
            session.timeout = SOCKET_TIMEOUT
            session.connect(CONNECTION_TIMEOUT)

            val pooled = PooledConnection(session = session, jsch = jsch)
            val firstCh = openChannelSafe(pooled)
            pooled.pooledChannels.add(PooledChannel(firstCh, Mutex(), ChannelPurpose.FILE_OPS))
            pooledSessions[key] = pooled
            Timber.d("SFTP new session for ${info.host}")
            return pooled
        } finally {
            poolMutex.unlock()
        }
    }

    suspend fun invalidate(info: SftpClient.SftpConnectionInfo) {
        invalidateSession(ConnectionKey(info.host, info.port, info.username))
    }

    private suspend fun invalidateSession(key: ConnectionKey) {
        poolMutex.lock()
        try {
            pooledSessions.remove(key)?.let { pooled ->
                // Defer session disconnect if ExoPlayer is actively streaming on it
                if (pooled.activeStreamCount.get() == 0) {
                    try {
                        pooled.pooledChannels.forEach { try { it.channel.disconnect() } catch (_: Exception) {} }
                        pooled.session.disconnect()
                        Timber.d("SFTP invalidated session with ${pooled.pooledChannels.size} channels")
                    } catch (e: Exception) { Timber.w("Error closing invalidated session: ${e.message}") }
                } else {
                    Timber.d("SFTP [FILE_OPS] session invalidated but PLAYBACK active — deferred disconnect")
                }
            }
        } finally {
            poolMutex.unlock()
        }
    }

    // ── Idle cleanup ─────────────────────────────────────────────────────────────────────────────

    private fun cleanupIdleConnections() {
        val now = System.currentTimeMillis()
        val keysToRemove = pooledSessions.filter { (_, conn) ->
            val shouldEvict = now - conn.lastUsed > IDLE_TIMEOUT_MS && conn.activeStreamCount.get() == 0
            if (!shouldEvict && now - conn.lastUsed > IDLE_TIMEOUT_MS) {
                Timber.d("SFTP [PLAYBACK] idle cleanup skipped — active=${conn.activeStreamCount.get()}")
            }
            shouldEvict
        }.keys
        if (keysToRemove.isEmpty()) return

        cleanupScope.launch {
            poolMutex.withLock {
                keysToRemove.forEach { key ->
                    pooledSessions.remove(key)?.let { pooled ->
                        try {
                            pooled.pooledChannels.forEach { try { it.channel.disconnect() } catch (_: Exception) {} }
                            pooled.session.disconnect()
                            Timber.d("SFTP closed idle session for ${key.host}")
                        } catch (e: Exception) { Timber.w("SFTP error closing idle session: ${e.message}") }
                    }
                }
            }
        }
    }

    // ── Blocking path (ExoPlayer / PLAYBACK) ────────────────────────────────────────────────────

    @Throws(IOException::class)
    fun getConnectionForExoPlayer(connectionInfo: SftpClient.SftpConnectionInfo): ExoPlayerConnection {
        val key = ConnectionKey(connectionInfo.host, connectionInfo.port, connectionInfo.username)
        try {
            connectionSemaphore.acquire()
            val pooled = getOrCreateSessionBlocking(key, connectionInfo)
            pooled.lastUsed = System.currentTimeMillis()

            // openChannelLock serializes session.openChannel() across both blocking and suspend callers
            pooled.openChannelLock.lock()
            try {
                val existing = pooled.pooledChannels
                    .firstOrNull { it.purpose == ChannelPurpose.PLAYBACK && it.channel.isConnected }
                if (existing != null) {
                    pooled.activeStreamCount.incrementAndGet()
                    Timber.d("SFTP [PLAYBACK] acquired (reuse) — active=${pooled.activeStreamCount.get()} host=${connectionInfo.host}")
                    return ExoPlayerConnection(pooled.session, existing.channel)
                }

                val playbackCount = pooled.pooledChannels.count { it.purpose == ChannelPurpose.PLAYBACK }
                if (playbackCount < MAX_PLAYBACK_CHANNELS) {
                    val ch = pooled.session.openChannel("sftp") as ChannelSftp
                    ch.connect(CONNECTION_TIMEOUT)
                    pooled.pooledChannels.add(PooledChannel(ch, Mutex(), ChannelPurpose.PLAYBACK))
                    Timber.d("SFTP [PLAYBACK] new channel (total=${pooled.pooledChannels.size}/$MAX_CHANNELS_PER_SESSION)")
                    pooled.activeStreamCount.incrementAndGet()
                    Timber.d("SFTP [PLAYBACK] acquired (new channel) — active=${pooled.activeStreamCount.get()} host=${connectionInfo.host}")
                    return ExoPlayerConnection(pooled.session, ch)
                }

                // All PLAYBACK slots taken — reuse first (caller waits on I/O)
                val fallback = pooled.pooledChannels.first { it.purpose == ChannelPurpose.PLAYBACK }
                Timber.d("SFTP [PLAYBACK] all slots busy, reusing first channel")
                pooled.activeStreamCount.incrementAndGet()
                Timber.d("SFTP [PLAYBACK] acquired (fallback) — active=${pooled.activeStreamCount.get()} host=${connectionInfo.host}")
                return ExoPlayerConnection(pooled.session, fallback.channel)
            } finally {
                pooled.openChannelLock.unlock()
            }
        } catch (e: Exception) {
            connectionSemaphore.release()
            Timber.e(e, "SFTP [PLAYBACK] failed to get connection for ${connectionInfo.host}")
            throw IOException("Failed to establish SFTP connection: ${e.message}", e)
        }
    }

    /** Blocking (non-suspend) session lookup/creation for the ExoPlayer path. */
    private fun getOrCreateSessionBlocking(
        key: ConnectionKey,
        info: SftpClient.SftpConnectionInfo
    ): PooledConnection {
        // Synchronized on pooledSessions to avoid TOCTOU on session creation from the blocking path
        synchronized(pooledSessions) {
            val existing = pooledSessions[key]
            if (existing != null && existing.session.isConnected) return existing

            if (existing != null) {
                try {
                    existing.pooledChannels.forEach { try { it.channel.disconnect() } catch (_: Exception) {} }
                    existing.session.disconnect()
                } catch (e: Exception) { Timber.w("Error closing stale PLAYBACK session: ${e.message}") }
                pooledSessions.remove(key)
            }

            val jsch = JSch()
            applyIdentity(jsch, info, namePrefix = "exoplayer_key")
            val session = jsch.getSession(info.username, info.host, info.port)
            applyAuth(session, info)
            session.timeout = SOCKET_TIMEOUT
            session.connect(CONNECTION_TIMEOUT)
            val pooled = PooledConnection(session = session, jsch = jsch)
            pooledSessions[key] = pooled
            Timber.d("SFTP [PLAYBACK] new unified session created — host=${info.host}")
            return pooled
        }
    }

    fun releaseExoPlayerConnection(channel: ChannelSftp? = null, broken: Boolean = false) {
        // Decrement before eviction so channel can still be found in the pool
        if (channel != null) {
            pooledSessions.values
                .find { pooled -> pooled.pooledChannels.any { it.channel == channel } }
                ?.activeStreamCount
                ?.updateAndGet { maxOf(0, it - 1) }
        } else {
            pooledSessions.values.firstOrNull()
                ?.activeStreamCount
                ?.updateAndGet { maxOf(0, it - 1) }
        }
        if (broken && channel != null) evictPlaybackChannel(channel)
        connectionSemaphore.release()
        cleanupIdleConnections()
    }

    private fun evictPlaybackChannel(channel: ChannelSftp) {
        Timber.d("S0047: evictPlaybackChannel — removing broken ExoPlayer channel from pool")
        pooledSessions.values.forEach { pooled ->
            val target = pooled.pooledChannels.firstOrNull { it.channel == channel } ?: return@forEach
            try { channel.disconnect() } catch (e: Exception) {
                Timber.w("SFTP [PLAYBACK] eviction disconnect: ${e.message}")
            }
            pooled.pooledChannels.remove(target)
            Timber.d("SFTP [PLAYBACK] evicted broken channel, ${pooled.pooledChannels.size} remaining")
            return
        }
        Timber.d("SFTP [PLAYBACK] eviction skipped — channel not in pool")
    }

    // ── InputStream (own-channel, not pooled) ────────────────────────────────────────────────────

    suspend fun openInputStream(
        info: SftpClient.SftpConnectionInfo,
        remotePath: String
    ): Result<java.io.InputStream> = withContext(Dispatchers.IO) {
        val key = ConnectionKey(info.host, info.port, info.username)
        try {
            connectionSemaphore.acquire()
            try {
                val pooled = getOrCreateSession(key, info)
                pooled.lastUsed = System.currentTimeMillis()

                if (!pooled.session.isConnected) {
                    Timber.w("SFTP session disconnected, recreating")
                    pooledSessions.remove(key)
                    getOrCreateSession(key, info).lastUsed = System.currentTimeMillis()
                }

                val channel = pooled.session.openChannel("sftp") as ChannelSftp
                if (!channel.isConnected) {
                    try {
                        channel.connect(CONNECTION_TIMEOUT)
                    } catch (e: com.jcraft.jsch.JSchException) {
                        Timber.w(e, "SFTP channel connect failed, recreating session")
                        pooledSessions.remove(key)
                        pooled.session.disconnect()
                        val newPooled = getOrCreateSession(key, info)
                        val newChannel = newPooled.session.openChannel("sftp") as ChannelSftp
                        newChannel.connect(CONNECTION_TIMEOUT)
                        val stream = newChannel.get(remotePath)
                        return@withContext Result.success(object : java.io.FilterInputStream(stream) {
                            override fun close() {
                                try { super.close() } finally {
                                    try { newChannel.disconnect() } catch (e: Exception) {
                                        Timber.w("Error closing SFTP stream channel: ${e.message}")
                                    }
                                }
                            }
                        })
                    }
                }

                try {
                    val stream = channel.get(remotePath)
                    Result.success(object : java.io.FilterInputStream(stream) {
                        override fun close() {
                            try { super.close() } finally {
                                try { channel.disconnect() } catch (e: Exception) {
                                    Timber.w("Error closing SFTP stream channel: ${e.message}")
                                }
                            }
                        }
                    })
                } catch (e: Exception) { channel.disconnect(); throw e }
            } finally {
                connectionSemaphore.release()
                cleanupIdleConnections()
            }
        } catch (e: Exception) {
            Timber.e(e, "SFTP openInputStream failed: $remotePath")
            Result.failure(e)
        }
    }

    // ── Disconnect all ───────────────────────────────────────────────────────────────────────────

    suspend fun disconnectAll() {
        poolMutex.lock()
        try {
            pooledSessions.values.forEach { pooled ->
                try {
                    pooled.pooledChannels.forEach { try { it.channel.disconnect() } catch (_: Exception) {} }
                    pooled.session.disconnect()
                } catch (_: Exception) {}
            }
            pooledSessions.clear()
        } finally {
            poolMutex.unlock()
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────────────────────

    /** Thread-safe openChannel — serializes via [PooledConnection.openChannelLock] (Research #2). */
    private fun openChannelSafe(pooled: PooledConnection): ChannelSftp {
        pooled.openChannelLock.lock()
        return try {
            val ch = pooled.session.openChannel("sftp") as ChannelSftp
            ch.connect(CONNECTION_TIMEOUT)
            ch
        } finally {
            pooled.openChannelLock.unlock()
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

    /**
     * Returns true iff [e] is a dead-transport IOException — i.e. the JSch session's underlying
     * TCP socket is silently broken while JSch's isConnected flags still report true (S0147).
     * SFTP-protocol errors ([com.jcraft.jsch.SftpException]) are not IOExceptions, so they never
     * match here.
     */
    private fun isDeadTransportException(e: Exception): Boolean {
        if (e !is IOException) return false
        val msg = e.message?.lowercase() ?: return false
        return DEAD_TRANSPORT_MESSAGES.any { msg.contains(it) }
    }

    companion object {
        private const val CONNECTION_TIMEOUT = 10_000
        private const val SOCKET_TIMEOUT = 30_000
        private const val MAX_CONCURRENT_CONNECTIONS = 15
        private const val MAX_CHANNELS_PER_SESSION = 5   // total across all purposes (Research #1)
        private const val MAX_PLAYBACK_CHANNELS = 1      // reserved for ExoPlayer streaming
        private const val MAX_FILE_OPS_CHANNELS = 4      // for suspend file operations
        private const val IDLE_TIMEOUT_MS = 25_000L

        /**
         * Lowercase substrings of IOException messages that indicate a dead JSch transport
         * (silent TCP drop, half-open connection) rather than an SFTP-protocol error.
         * S0147: extend this list when new signals are confirmed in field logs or JSch source.
         *
         * Sources:
         *  - "inputstream is closed"  → Channel.getInputStream() when io.in == null (field-confirmed)
         *  - "channel is not opened"  → Channel.checkConnected() when _isConnected = false after drop
         *  - "broken pipe"            → java.net.SocketException from OS when writing to dead socket
         */
        internal val DEAD_TRANSPORT_MESSAGES = listOf(
            "inputstream is closed",
            "channel is not opened",
            "broken pipe"
        )
    }
}
