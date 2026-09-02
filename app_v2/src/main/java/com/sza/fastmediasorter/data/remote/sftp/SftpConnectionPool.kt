package com.sza.fastmediasorter.data.remote.sftp

import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import com.sza.fastmediasorter.core.util.rethrowIfCancellation
import com.sza.fastmediasorter.utils.SshFingerprintNormalizer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock

/** Purpose of an SFTP channel within a pooled session (S0113: unified-session). */
enum class ChannelPurpose { PLAYBACK, FILE_OPS }

/**
 * SFTP session + channel pool for SftpClient.
 *
 * One [PooledConnection] per (host, port, user) holds a single JSch [Session] and a list of
 * [PooledChannel]s partitioned by [ChannelPurpose]. The unified design (S0113 Phase 04) eliminates
 * the two-SSH-session problem - PLAYBACK and FILE_OPS channels share one session per account.
 *
 * Active borrowers (PLAYBACK streams and FILE_OPS blocks) are tracked via
 * [PooledConnection.activeBorrowCount] so idle cleanup and invalidation never evict a session
 * while a caller is mid-block (S0113 Phase 01, S0219 Pillar B).
 *
 * Extracted to keep SftpClient below the 1000-line cap.
 */
class SftpConnectionPool {

    /** Single SFTP channel with its serialization mutex and declared purpose. */
    data class PooledChannel(
        val channel: ChannelSftp,
        val mutex: Mutex,
        val purpose: ChannelPurpose
    ) {
        // S2319: a PLAYBACK borrow lives from open() to close() - a whole track - so [mutex] cannot
        // serialize it the way FILE_OPS does without parking the next borrower for minutes. The
        // claim flag instead makes the pool refuse a channel another reader is still streaming
        // from. Declared outside the primary constructor so it stays out of equals/hashCode, which
        // pooledChannels.remove(target) relies on.
        val playbackClaimed: AtomicBoolean = AtomicBoolean(false)
    }

    private class PooledConnection(
        val session: Session,
        val jsch: JSch,
        // S0866: CopyOnWriteArrayList - this list is mutated/iterated from four independent lock
        // domains (sessionMutex, openChannelLock, poolMutex, and lock-free evict/release paths) that
        // do not mutually exclude each other. A thread-safe collection removes the CME/lost-update
        // risk at the list level; sessionMutex/openChannelLock still guard their own per-purpose
        // count-then-add invariants (FILE_OPS vs PLAYBACK slot limits), which is orthogonal.
        val pooledChannels: MutableList<PooledChannel> = CopyOnWriteArrayList(),
        val sessionMutex: Mutex = Mutex(),
        // Guards session.openChannel() across both suspend and blocking callers (Research #2)
        val openChannelLock: ReentrantLock = ReentrantLock(),
        var lastUsed: Long = System.currentTimeMillis(),
        // Non-zero while any borrower (PLAYBACK stream or FILE_OPS block) holds this pooled
        // session; idle cleanup and invalidation both honor this counter (S0219 Pillar B).
        val activeBorrowCount: AtomicInteger = AtomicInteger(0)
    )

    // S0046: expectedFingerprint is part of the pool key so a pinned and an unpinned session for
    // the same host:port:user are pooled separately and never share a host-key verification policy.
    private data class ConnectionKey(
        val host: String,
        val port: Int,
        val username: String,
        val expectedFingerprint: String? = null
    )

    /** Unified pool - one entry per (host, port, user) for both PLAYBACK and FILE_OPS. */
    // S0866: session-map mutation guard. Both the suspend path (getOrCreateSession,
    // invalidateSession, disconnectAll) and the blocking ExoPlayer path
    // (getOrCreateSessionBlocking) use this SAME monitor - previously the suspend path used a
    // kotlinx.coroutines Mutex while the blocking path used synchronized(pooledSessions), two
    // disjoint lock domains that did not exclude each other (TOCTOU: both could see existing==null
    // and both create+overwrite a session for the same key, leaking the loser's JSch session).
    // Safe as a plain monitor because every critical section below is synchronous JSch/map work
    // with zero suspension points - no coroutine ever suspends while holding it.
    private val pooledSessions = ConcurrentHashMap<ConnectionKey, PooledConnection>()

    // S1296: per-host creation locks. session.connect() blocks for up to CONNECTION_TIMEOUT, and
    // running it under the shared pooledSessions monitor made every other host queue behind one
    // unreachable server. Map mutation still happens on that monitor, so the S0866 TOCTOU
    // guarantee holds; only the SSH handshake moved out from under it.
    private val sessionCreationLocks = ConcurrentHashMap<ConnectionKey, Any>()
    private val connectionFailureCache = SftpConnectionFailureCache()

    // S1296: which pooled session a live PLAYBACK borrow belongs to. Release used to resolve the
    // owner by scanning pooledSessions, which fails once invalidateSession removes the entry while
    // the borrow is live - the decrement was skipped and the deferred disconnect never ran, leaking
    // the JSch session, its socket and its keep-alive thread until process death.
    private val playbackOwners = ConcurrentHashMap<ChannelSftp, PooledConnection>()

    private val connectionSemaphore = Semaphore(MAX_CONCURRENT_CONNECTIONS)
    private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var sweepJob: Job? = null

    /** ExoPlayer-facing wrapper. Both [session] and [channel] are owned by the pool - do not close. */
    data class ExoPlayerConnection(val session: Session, val channel: ChannelSftp)

    // ── Suspend path (FILE_OPS) ──────────────────────────────────────────────────────────────────

    suspend fun <T> withConnection(
        info: SftpClient.SftpConnectionInfo,
        block: suspend (ChannelSftp) -> Result<T>
    ): Result<T> = withContext(Dispatchers.IO) {
        val key = ConnectionKey(info.host, info.port, info.username, info.expectedFingerprint)
        try {
            connectionSemaphore.acquire()
            try {
                val pooled = getOrCreateSession(key, info)
                pooled.lastUsed = System.currentTimeMillis()
                val pc = getOrCreateFileOpsChannel(pooled, info)
                // S0219 Pillar B: track FILE_OPS borrow so invalidation defers disconnect until
                // the last borrower releases rather than disconnecting a session mid-block.
                pooled.activeBorrowCount.incrementAndGet()
                // Tracks the connection actually holding the borrow for the deferred-disconnect check.
                // Swaps to newPooled on retry so the finally decrements the correct counter.
                var actualRetryBorrowed: PooledConnection? = null
                try {
                    try {
                        pc.mutex.withLock { block(pc.channel) }
                    } catch (e: Exception) {
                        // Cancellation must never reach the reconnect branches below: a torn-down
                        // channel/session looks exactly like a dead transport, so a cancelled block
                        // would be silently re-run on a freshly opened session.
                        e.rethrowIfCancellation()
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
                            newPooled.activeBorrowCount.incrementAndGet()
                            actualRetryBorrowed = newPooled
                            return@withContext newPc.mutex.withLock { block(newPc.channel) }
                        }
                        // S0147: silent TCP drop - isConnected flags stay true but transport is dead.
                        // S0205: skip retry when the coroutine is being cancelled - "inputstream is
                        // closed" can arrive from ConnectionThrottle teardown, not only dead TCP.
                        if (isDeadTransportException(e)) {
                            ensureActive() // throws CancellationException if scope is being cancelled
                            Timber.w("SFTP [FILE_OPS] dead transport detected (${e.message}), reconnecting")
                            removeChannel(pooled, pc.channel)
                            invalidateSession(key)
                            val newPooled = getOrCreateSession(key, info)
                            newPooled.lastUsed = System.currentTimeMillis()
                            val newPc = getOrCreateFileOpsChannel(newPooled, info)
                            newPooled.activeBorrowCount.incrementAndGet()
                            actualRetryBorrowed = newPooled
                            return@withContext newPc.mutex.withLock { block(newPc.channel) }
                        }
                        throw e
                    }
                } finally {
                    // S0219: decrement original borrow. If session was invalidated (not in map) and
                    // nobody else holds it, disconnect it now (deferred-disconnect path).
                    val remaining = pooled.activeBorrowCount.decrementAndGet()
                    if (remaining == 0 && !pooledSessions.containsValue(pooled)) {
                        disconnectOrphan(pooled)
                    }
                    // Decrement retry borrow if a new session was acquired mid-block.
                    actualRetryBorrowed?.let { rp ->
                        val retryRemaining = rp.activeBorrowCount.decrementAndGet()
                        if (retryRemaining == 0 && !pooledSessions.containsValue(rp)) {
                            disconnectOrphan(rp)
                        }
                    }
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
            // S1027: concise cause instead of a full stack trace - this fires per file, and a
            // read-only source floods the log with identical 18-line traces (e.g. "permission
            // denied" on delete). The caller classifies and surfaces the failure with context.
            Timber.w("SFTP [FILE_OPS] operation failed: ${e.message}")
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
        ensurePeriodicSweepRunning()
        // S1296: handshake under a per-host lock, map work under the shared monitor.
        synchronized(creationLock(key)) {
            synchronized(pooledSessions) {
                val existing = pooledSessions[key]
                if (existing != null && existing.session.isConnected) return existing

                if (existing != null) {
                    try {
                        existing.pooledChannels.forEach { try { it.channel.disconnect() } catch (_: Exception) {} }
                        existing.session.disconnect()
                    } catch (e: Exception) { Timber.w("Error closing stale session: ${e.message}") }
                    pooledSessions.remove(key)
                }
            }
            // S1651: after the live-session check, so a healthy pooled session is never rejected by
            // a negative record left behind by an earlier attempt on a since-recovered endpoint.
            failFastIfRecentlyUnreachable(info)

            val jsch = JSch()
            applyIdentity(jsch, info, namePrefix = "key")
            val session = jsch.getSession(info.username, info.host, info.port)
            applyAuth(session, info)
            session.timeout = SOCKET_TIMEOUT
            // Keep-alive lets JSch's own thread detect a half-open socket and fail the session,
            // unblocking a parked ls without waiting on external invalidation. Conservative
            // interval/count keeps the cost negligible for healthy sessions.
            session.setServerAliveInterval(SERVER_ALIVE_INTERVAL_MS)
            session.setServerAliveCountMax(SERVER_ALIVE_COUNT_MAX)
            try {
                session.connect(CONNECTION_TIMEOUT)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                recordUnreachable(info, e)
                throw e
            }
            clearUnreachable(info)

            val pooled = PooledConnection(session = session, jsch = jsch)
            val firstCh = openChannelSafe(pooled)
            pooled.pooledChannels.add(PooledChannel(firstCh, Mutex(), ChannelPurpose.FILE_OPS))
            synchronized(pooledSessions) { pooledSessions[key] = pooled }
            Timber.d("SFTP new session for ${info.host}")
            return pooled
        }
    }

    private fun creationLock(key: ConnectionKey): Any = sessionCreationLocks.computeIfAbsent(key) { Any() }

    /**
     * S1651: both handshake sites call this under [creationLock], so temp cleanup, listing, paging
     * and counting share one proven socket-connect refusal instead of each waiting out its own
     * CONNECTION_TIMEOUT. Rethrows the original throwable, leaving caller messaging unchanged.
     */
    private fun failFastIfRecentlyUnreachable(info: SftpClient.SftpConnectionInfo) {
        val recent = connectionFailureCache.recentFailure(info) ?: return
        // Owner ask: a reused refusal must stay distinguishable from a real connection attempt.
        Timber.i("SFTP connect skipped for ${info.host}:${info.port} - recent connect failure reused")
        throw recent
    }

    private fun recordUnreachable(info: SftpClient.SftpConnectionInfo, cause: Throwable) {
        connectionFailureCache.record(info, cause)
    }

    private fun clearUnreachable(info: SftpClient.SftpConnectionInfo) {
        connectionFailureCache.clear(info)
    }

    suspend fun invalidate(info: SftpClient.SftpConnectionInfo) {
        clearUnreachable(info)
        invalidateSession(ConnectionKey(info.host, info.port, info.username, info.expectedFingerprint))
    }

    private suspend fun invalidateSession(key: ConnectionKey) {
        synchronized(pooledSessions) {
            pooledSessions.remove(key)?.let { pooled ->
                // S0219 Pillar B: session is removed from the map immediately so no new checkout
                // can see it. If active borrowers still hold a reference, defer the actual
                // disconnect - the last borrower's finally block calls disconnectOrphan instead.
                val deferred = pooled.activeBorrowCount.get() > 0
                if (!deferred) {
                    try {
                        pooled.pooledChannels.forEach { try { it.channel.disconnect() } catch (_: Exception) {} }
                        pooled.session.disconnect()
                        Timber.d("SFTP invalidated session with ${pooled.pooledChannels.size} channels")
                    } catch (e: Exception) { Timber.w("Error closing invalidated session: ${e.message}") }
                } else {
                    val activeBorrows = pooled.activeBorrowCount.get()
                    Timber.d(
                        "SFTP invalidate deferred for ${key.host} (activeBorrow=$activeBorrows) - " +
                            "last borrower will disconnect"
                    )
                }
            }
        }
    }

    /** Disconnect all channels and the session of an orphaned [PooledConnection].
     * Called from withConnection's finally block when the last borrower releases a session
     * that was already removed from [pooledSessions] by [invalidateSession] (S0219 Pillar B). */
    private fun disconnectOrphan(pooled: PooledConnection) {
        pooled.pooledChannels.forEach { pc ->
            try { pc.channel.disconnect() } catch (e: Exception) {
                Timber.w("SFTP orphan channel disconnect error: ${e.message}")
            }
        }
        try { pooled.session.disconnect() } catch (e: Exception) {
            Timber.w("SFTP orphan session disconnect error: ${e.message}")
        }
        Timber.d("SFTP orphan session disconnected after last borrower released")
    }

    // ── Idle cleanup ─────────────────────────────────────────────────────────────────────────────

    private fun cleanupIdleConnections() {
        val now = System.currentTimeMillis()
        val keysToRemove = pooledSessions.filter { (_, conn) ->
            val shouldEvict = now - conn.lastUsed > IDLE_TIMEOUT_MS && conn.activeBorrowCount.get() == 0
            if (!shouldEvict && now - conn.lastUsed > IDLE_TIMEOUT_MS) {
                Timber.d("SFTP [PLAYBACK] idle cleanup skipped - active=${conn.activeBorrowCount.get()}")
            }
            shouldEvict
        }.keys
        if (keysToRemove.isEmpty()) return

        cleanupScope.launch {
            synchronized(pooledSessions) {
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

    private fun ensurePeriodicSweepRunning() {
        if (sweepJob?.isActive == true) return
        synchronized(this) {
            if (sweepJob?.isActive == true) return
            sweepJob = cleanupScope.launch {
                while (isActive) {
                    delay(IDLE_TIMEOUT_MS)
                    Timber.d("SftpConnectionPool.periodicSweep: tick")
                    runCatching { cleanupIdleConnections() }
                        .onFailure { Timber.w(it, "SftpConnectionPool.periodicSweep: cleanup failed") }
                }
            }
        }
    }

    private fun stopPeriodicSweep() {
        synchronized(this) {
            sweepJob?.cancel()
            sweepJob = null
        }
    }

    internal fun isPeriodicSweepActive(): Boolean = sweepJob?.isActive == true

    internal fun startPeriodicSweepForTest() {
        ensurePeriodicSweepRunning()
    }

    internal fun stopPeriodicSweepForTest() {
        stopPeriodicSweep()
    }

    /**
     * S1651 test seam: replays the handshake outcome hooks the creation paths use, so the
     * pool-level cooldown and its recovery boundaries are provable without a live SSH server.
     */
    internal fun applyHandshakeOutcomeForTest(info: SftpClient.SftpConnectionInfo, failure: Throwable?) {
        if (failure != null) {
            recordUnreachable(info, failure)
        } else {
            clearUnreachable(info)
        }
    }

    /** S1651 test seam: the pre-handshake guard both creation paths run under [creationLock]. */
    internal fun failFastIfRecentlyUnreachableForTest(info: SftpClient.SftpConnectionInfo) {
        failFastIfRecentlyUnreachable(info)
    }

    // ── Blocking path (ExoPlayer / PLAYBACK) ────────────────────────────────────────────────────

    @Throws(IOException::class)
    fun getConnectionForExoPlayer(connectionInfo: SftpClient.SftpConnectionInfo): ExoPlayerConnection {
        val key = ConnectionKey(
            connectionInfo.host,
            connectionInfo.port,
            connectionInfo.username,
            connectionInfo.expectedFingerprint
        )
        try {
            connectionSemaphore.acquire()
            val pooled = getOrCreateSessionBlocking(key, connectionInfo)
            pooled.lastUsed = System.currentTimeMillis()

            // openChannelLock serializes session.openChannel() across both blocking and suspend callers
            pooled.openChannelLock.lock()
            try {
                val claimed = claimIdlePlaybackChannel(pooled.pooledChannels)
                if (claimed != null) {
                    return borrowPlayback(pooled, claimed.channel, "reuse", connectionInfo.host)
                }

                val playbackCount = pooled.pooledChannels.count { it.purpose == ChannelPurpose.PLAYBACK }
                if (playbackCount < MAX_PLAYBACK_CHANNELS) {
                    val ch = pooled.session.openChannel("sftp") as ChannelSftp
                    ch.connect(CONNECTION_TIMEOUT)
                    val opened = PooledChannel(ch, Mutex(), ChannelPurpose.PLAYBACK)
                    opened.playbackClaimed.set(true)
                    pooled.pooledChannels.add(opened)
                    Timber.d(
                        "SFTP [PLAYBACK] new channel (total=${pooled.pooledChannels.size}/$MAX_CHANNELS_PER_SESSION)"
                    )
                    return borrowPlayback(pooled, ch, "new channel", connectionInfo.host)
                }

                // S2319: this used to hand the first PLAYBACK channel to a second borrower. A JSch
                // ChannelSftp is one request/response queue over one stream, so the two readers
                // corrupted each other - stat() returned an empty "0:" status and the stream close
                // threw IndexOutOfBoundsException. Refusing is recoverable: ExoPlayer retries the
                // load against a slot the outgoing source has since released.
                refusePlaybackSharing(connectionInfo.host, playbackCount)
            } finally {
                pooled.openChannelLock.unlock()
            }
        } catch (e: InterruptedException) {
            // ExoPlayer's Loader.release() interrupts its worker thread; the semaphore acquire
            // then throws InterruptedException. This is orderly cancellation during player teardown,
            // not a connection failure - log at DEBUG, restore the interrupt flag, propagate as
            // InterruptedIOException so callers (SftpDataSource.read) can short-circuit instead of
            // retrying a doomed reconnect.
            connectionSemaphore.release()
            Thread.currentThread().interrupt()
            Timber.d("SFTP [PLAYBACK] acquire cancelled (player teardown) host=${connectionInfo.host}")
            throw InterruptedIOException("SFTP connection acquire cancelled").apply { initCause(e) }
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
        ensurePeriodicSweepRunning()
        // S1296: the SSH handshake below can block for CONNECTION_TIMEOUT. Holding the shared
        // pooledSessions monitor across it froze every other host's lookup behind one dead server,
        // so only the map read/write stays on that monitor; creation is serialized per host.
        synchronized(creationLock(key)) {
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
            }
            // S1651: PLAYBACK is the pool's second handshake site, so it shares the same cooldown -
            // otherwise a start-playback attempt still pays a full CONNECTION_TIMEOUT on a host
            // FILE_OPS has just proven unreachable.
            failFastIfRecentlyUnreachable(info)

            val jsch = JSch()
            applyIdentity(jsch, info, namePrefix = "exoplayer_key")
            val session = jsch.getSession(info.username, info.host, info.port)
            applyAuth(session, info)
            session.timeout = SOCKET_TIMEOUT
            // Keep-alive lets JSch's own thread detect a half-open socket and fail the session,
            // unblocking a parked ls without waiting on external invalidation. Conservative
            // interval/count keeps the cost negligible for healthy sessions.
            session.setServerAliveInterval(SERVER_ALIVE_INTERVAL_MS)
            session.setServerAliveCountMax(SERVER_ALIVE_COUNT_MAX)
            try {
                session.connect(CONNECTION_TIMEOUT)
            } catch (e: Exception) {
                recordUnreachable(info, e)
                throw e
            }
            clearUnreachable(info)
            val pooled = PooledConnection(session = session, jsch = jsch)
            synchronized(pooledSessions) { pooledSessions[key] = pooled }
            Timber.d("SFTP [PLAYBACK] new unified session created - host=${info.host}")
            return pooled
        }
    }

    /**
     * S2319: refuses a borrow rather than letting two readers share one channel. Kept out of
     * [getConnectionForExoPlayer] so that function stays within its throw budget.
     */
    private fun refusePlaybackSharing(host: String, playbackCount: Int): Nothing {
        Timber.w("SFTP [PLAYBACK] all $playbackCount slot(s) busy for $host - refusing to share")
        throw IOException("All SFTP playback channels are busy")
    }

    /**
     * S2319: claims one PLAYBACK slot for an exclusive borrow, or returns null when every connected
     * slot is already streaming. Internal so the exclusivity contract is provable in a unit test -
     * the defect it fixes only reproduced against a live server during a track transition.
     */
    internal fun claimIdlePlaybackChannel(channels: List<PooledChannel>): PooledChannel? {
        for (pc in channels) {
            val free = pc.purpose == ChannelPurpose.PLAYBACK &&
                pc.channel.isConnected &&
                pc.playbackClaimed.compareAndSet(false, true)
            if (free) return pc
        }
        return null
    }

    /** S2319: hands a PLAYBACK slot back so the next borrower can claim it. */
    internal fun releasePlaybackClaim(channels: List<PooledChannel>, channel: ChannelSftp) {
        channels.firstOrNull { it.channel === channel }?.playbackClaimed?.set(false)
    }

    /** Register a PLAYBACK borrow so [releaseExoPlayerConnection] can always find its owner (S1296). */
    private fun borrowPlayback(
        pooled: PooledConnection,
        channel: ChannelSftp,
        how: String,
        host: String
    ): ExoPlayerConnection {
        pooled.activeBorrowCount.incrementAndGet()
        playbackOwners[channel] = pooled
        val claimedSlots = pooled.pooledChannels.count {
            it.purpose == ChannelPurpose.PLAYBACK && it.playbackClaimed.get()
        }
        Timber.d("S2319: playback borrow how=$how claimedSlots=$claimedSlots")
        Timber.d("SFTP [PLAYBACK] acquired ($how) - active=${pooled.activeBorrowCount.get()} host=$host")
        return ExoPlayerConnection(pooled.session, channel)
    }

    fun releaseExoPlayerConnection(channel: ChannelSftp? = null, broken: Boolean = false) {
        // S1296: the borrow registry survives invalidateSession removing the entry from the map,
        // so the counter is always decremented and an orphaned session is actually disconnected -
        // the same deferred-disconnect contract withConnection's finally implements.
        val owner = channel?.let { ch ->
            playbackOwners[ch] ?: pooledSessions.values.find { p -> p.pooledChannels.any { it.channel == ch } }
        } ?: pooledSessions.values.firstOrNull()

        // S2319: free the exclusivity claim first - until it is cleared the next track's open()
        // sees no idle slot and is refused, which would turn the shared-channel corruption into a
        // permanently unusable channel.
        if (channel != null && owner != null) releasePlaybackClaim(owner.pooledChannels, channel)

        // Decrement before eviction so channel can still be found in the pool
        val remaining = owner?.activeBorrowCount?.updateAndGet { maxOf(0, it - 1) }
        if (owner != null && remaining == 0) {
            channel?.let(playbackOwners::remove)
            if (!pooledSessions.containsValue(owner)) {
                Timber.d("SFTP [PLAYBACK] last borrower released an invalidated session - disconnecting")
                disconnectOrphan(owner)
            }
        }
        if (broken && channel != null) evictPlaybackChannel(channel)
        connectionSemaphore.release()
        cleanupIdleConnections()
    }

    private fun evictPlaybackChannel(channel: ChannelSftp) {
        playbackOwners.remove(channel)
        pooledSessions.values.forEach { pooled ->
            val target = pooled.pooledChannels.firstOrNull { it.channel == channel } ?: return@forEach
            // S2319: covers the borrow whose owner lookup missed - this scan walks every session,
            // so the claim is cleared even when releaseExoPlayerConnection could not resolve one.
            target.playbackClaimed.set(false)
            try { channel.disconnect() } catch (e: Exception) {
                Timber.w("SFTP [PLAYBACK] eviction disconnect: ${e.message}")
            }
            pooled.pooledChannels.remove(target)
            Timber.d("SFTP [PLAYBACK] evicted broken channel, ${pooled.pooledChannels.size} remaining")
            return
        }
        Timber.d("SFTP [PLAYBACK] eviction skipped - channel not in pool")
    }

    // ── InputStream (own-channel, not pooled) ────────────────────────────────────────────────────

    suspend fun openInputStream(
        info: SftpClient.SftpConnectionInfo,
        remotePath: String
    ): Result<java.io.InputStream> = withContext(Dispatchers.IO) {
        val key = ConnectionKey(info.host, info.port, info.username, info.expectedFingerprint)
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
                } catch (e: Exception) {
                    channel.disconnect()
                    throw e
                }
            } finally {
                connectionSemaphore.release()
                cleanupIdleConnections()
            }
        } catch (e: Exception) {
            e.rethrowIfCancellation()
            Timber.e(e, "SFTP openInputStream failed: $remotePath")
            Result.failure(e)
        }
    }

    // ── Disconnect all ───────────────────────────────────────────────────────────────────────────

    suspend fun disconnectAll() {
        connectionFailureCache.clearAll()
        synchronized(pooledSessions) {
            stopPeriodicSweep()
            pooledSessions.values.forEach { pooled ->
                try {
                    pooled.pooledChannels.forEach { try { it.channel.disconnect() } catch (_: Exception) {} }
                    pooled.session.disconnect()
                } catch (_: Exception) {}
            }
            pooledSessions.clear()
        }
    }

    /**
     * Fire-and-forget force-reset for a network handover, reaching parity with
     * SmbConnectionManager.handleNetworkReconnect. Runs on the pool's own IO scope so the
     * ConnectivityManager callback thread is never blocked. Delegates to the unconditional
     * [disconnectAll] (socket close) - never the borrow-deferring [invalidate] - because a scan
     * parked in a blocking listing never reaches its finally until the socket is closed.
     */
    fun disconnectAllOnNetworkChange() {
        cleanupScope.launch { disconnectAll() }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────────────────────

    /** Thread-safe openChannel - serializes via [PooledConnection.openChannelLock] (Research #2). */
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
        val strictHostKeyChecking = installPinnedHostKeyOrPermissive(session, info)
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
            config["StrictHostKeyChecking"] = strictHostKeyChecking
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
            config["StrictHostKeyChecking"] = strictHostKeyChecking
            config["PreferredAuthentications"] = "keyboard-interactive,password"
            session.setConfig(config)
        }
    }

    /**
     * S0046: when [info].expectedFingerprint is a parseable SHA256 fingerprint, install a pinned
     * host-key repository and return "yes" so JSch consults it and aborts on a CHANGED verdict
     * before auth. Returns the permissive "no" when no fingerprint is set, or when the configured
     * fingerprint is unparseable (logged at warn, no key bytes) so a release-time misconfiguration
     * degrades to the prior behaviour instead of crashing the connection.
     */
    private fun installPinnedHostKeyOrPermissive(session: Session, info: SftpClient.SftpConnectionInfo): String {
        val fingerprint = info.expectedFingerprint ?: return "no"
        val canonical = SshFingerprintNormalizer.canonical(fingerprint)
        if (canonical == null) {
            Timber.w("SFTP host-key pin ignored: unparseable fingerprint for host=${info.host}")
            return "no"
        }
        session.setHostKeyRepository(PinnedHostKeyRepository(canonical))
        return "yes"
    }

    /**
     * Returns true iff [e] is a dead-transport IOException - i.e. the JSch session's underlying
     * TCP socket is silently broken while JSch's isConnected flags still report true (S0147).
     * SFTP-protocol errors ([com.jcraft.jsch.SftpException]) are not IOExceptions, so they never
     * match here.
     */
    private fun isDeadTransportException(e: Exception): Boolean = isDeadTransport(e)

    companion object {
        private const val CONNECTION_TIMEOUT = 10_000
        private const val SOCKET_TIMEOUT = 30_000

        // SSH keep-alive: ~30 s (interval x countMax) to drop a dead transport, comfortably under
        // the SftpMediaScanner scan watchdog so this cleaner recovery fires first.
        private const val SERVER_ALIVE_INTERVAL_MS = 15_000
        private const val SERVER_ALIVE_COUNT_MAX = 2
        private const val MAX_CONCURRENT_CONNECTIONS = 15
        private const val MAX_CHANNELS_PER_SESSION = 5 // total across all purposes (Research #1)

        // S2319: two, because an ExoPlayer track transition opens the next source before it closes
        // the previous one - with a single slot the overlap forced both readers onto one channel.
        private const val MAX_PLAYBACK_CHANNELS = 2 // reserved for ExoPlayer streaming

        // S2319: three, so the two purposes still sum to MAX_CHANNELS_PER_SESSION. FILE_OPS work is
        // already serialized per channel by PooledChannel.mutex, so the slot it gives up costs
        // parallelism only, never correctness.
        private const val MAX_FILE_OPS_CHANNELS = 3 // for suspend file operations
        private const val IDLE_TIMEOUT_MS = 30_000L

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

        /**
         * True iff [e] is a dead-transport IOException (silent TCP drop / stale pooled session
         * after a long scan). Shared with callers that run their own retry loops (e.g.
         * [SftpClient.downloadFile]) so a stale session is recovered by an immediate reconnect
         * instead of waiting out an exponential backoff (S0466).
         */
        internal fun isDeadTransport(e: Throwable?): Boolean {
            if (e !is IOException) return false
            val msg = e.message?.lowercase() ?: return false
            return DEAD_TRANSPORT_MESSAGES.any { msg.contains(it) }
        }
    }
}
