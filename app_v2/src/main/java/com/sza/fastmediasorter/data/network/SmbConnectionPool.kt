package com.sza.fastmediasorter.data.network

import com.hierynomus.smbj.connection.Connection
import com.hierynomus.smbj.session.Session
import com.hierynomus.smbj.share.DiskShare
import com.sza.fastmediasorter.data.network.model.ConnectionKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Tracks which subsystem originally created the pooled connection.
 * ExoPlayer must not reuse a SCANNER connection — directory-scan sessions have been
 * observed silently dropping their TCP socket between scan completion and playback
 * start; SMBJ's isConnected flag cannot detect that and the subsequent openFile()
 * blocks indefinitely inside the socket write.
 *
 * S0061 Phase 04: BACKGROUND_WORKER identifies connections opened by WorkManager tasks.
 * These are preserved when the UI moves to background (unlike SCANNER/PLAYER which are
 * closed on [SmbConnectionPool.closeAllExceptWorker]).
 */
enum class ConnectionConsumer { SCANNER, PLAYER, BACKGROUND_WORKER }

/**
 * Pooled SMB connection holding the SMBJ tri-layer (Connection / Session / DiskShare)
 * plus usage metadata. Closed by [SmbConnectionPool] in cascade order share → session
 * → connection so a dead transport on one layer does not block the rest.
 */
data class PooledConnection(
    val connection: Connection,
    val session: Session,
    val share: DiskShare,
    var lastUsed: Long = System.currentTimeMillis(),
    val usageCount: AtomicInteger = AtomicInteger(0),
    val isPendingClose: AtomicBoolean = AtomicBoolean(false),
    val consumer: ConnectionConsumer = ConnectionConsumer.SCANNER
)

/**
 * Owns the SMB connection map and the cascade-close sequence.
 * Extracted from [SmbConnectionManager] in S0061 phase 01 to bring the manager
 * back below the 1000-LOC limit and to localise pool ownership in one place.
 *
 * No retry/diagnostic policy lives here — that stays in [SmbConnectionManager].
 */
class SmbConnectionPool {

    private val map = ConcurrentHashMap<ConnectionKey, PooledConnection>()
    private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun get(key: ConnectionKey): PooledConnection? = map[key]

    fun put(key: ConnectionKey, pooled: PooledConnection) {
        map[key] = pooled
    }

    /**
     * Remove the entry without closing it. The caller is responsible for closing
     * the returned PooledConnection (use [removeAndCloseSync] / [removeAndCloseAsync]
     * if you need the close behaviour bundled in).
     */
    fun remove(key: ConnectionKey): PooledConnection? = map.remove(key)

    /**
     * Remove the entry and close it synchronously in cascade order.
     * Each layer is closed independently so a dead transport on one step does not
     * prevent the remaining resources from being released.
     */
    fun removeAndCloseSync(key: ConnectionKey): PooledConnection? {
        val pooled = map.remove(key) ?: return null
        closeCascade(pooled)
        return pooled
    }

    /**
     * Remove the entry and schedule its close on the cleanup scope.
     * Honours [PooledConnection.usageCount] — if a consumer is still using the
     * connection, the close is deferred until the usage count returns to zero.
     */
    fun removeAndCloseAsync(key: ConnectionKey): PooledConnection? {
        val pooled = map.remove(key) ?: return null
        closeConnectionAsync(pooled)
        return pooled
    }

    /**
     * Schedule [pooled] for asynchronous close without removing anything from the map.
     * Useful when the pool entry has already been dropped via [remove] but the close
     * still needs to honour outstanding usage counts.
     */
    fun closeConnectionAsync(pooled: PooledConnection) {
        pooled.isPendingClose.set(true)
        cleanupScope.launch {
            if (pooled.usageCount.get() == 0) {
                try {
                    pooled.share.close()
                    pooled.session.close()
                    pooled.connection.close()
                    Timber.d("Connection closed successfully")
                } catch (_: Exception) {
                    // Swallow — forced cleanup, nothing to recover here.
                }
            } else {
                Timber.d("Connection marked for close but currently in use (count=${pooled.usageCount.get()}). Will be closed after use.")
            }
        }
    }

    /**
     * Snapshot the current pool entries. Safe to iterate while concurrent modifications
     * occur on the underlying map. Used by remove-matching helpers in the manager.
     */
    fun snapshot(): List<Map.Entry<ConnectionKey, PooledConnection>> = map.entries.toList()

    /**
     * Iterate every pool entry whose key matches [predicate]; remove and asynchronously
     * close it. Returns the number of entries removed. The matched entries are removed
     * via the same atomic [ConcurrentHashMap.remove] used by [removeAndCloseAsync] so a
     * concurrent call cannot resurrect them.
     */
    fun removeMatchingAndCloseAsync(predicate: (ConnectionKey) -> Boolean): Int {
        var count = 0
        for (entry in snapshot()) {
            if (predicate(entry.key)) {
                if (map.remove(entry.key) != null) {
                    closeConnectionAsync(entry.value)
                    count++
                }
            }
        }
        return count
    }

    /**
     * Remove every entry from the pool and schedule async close.
     */
    fun closeAll() {
        val keys = map.keys().toList()
        keys.forEach { key ->
            map.remove(key)?.let { closeConnectionAsync(it) }
        }
        Timber.d("Initiated async close of ${keys.size} pooled connections")
    }

    /**
     * S0061 Phase 04: Close all UI-owned connections (SCANNER + PLAYER) while preserving
     * connections tagged as BACKGROUND_WORKER. Called when the app moves to background
     * via [SmbConnectionManager.closeUiConnections].
     */
    fun closeAllExceptWorker() {
        var count = 0
        for (entry in snapshot()) {
            if (entry.value.consumer != ConnectionConsumer.BACKGROUND_WORKER) {
                if (map.remove(entry.key) != null) {
                    closeConnectionAsync(entry.value)
                    count++
                }
            }
        }
        Timber.d("Closed $count UI SMB connections (background lifecycle)")
    }

    /** Synchronous cascade close used by [removeAndCloseSync]. */
    private fun closeCascade(pooled: PooledConnection) {
        try {
            pooled.share.close()
        } catch (e: Exception) {
            Timber.w("SMB share close error (likely dead connection): ${e.message}")
        }
        try {
            pooled.session.close()
        } catch (e: Exception) {
            Timber.w("SMB session close error (likely dead connection): ${e.message}")
        }
        try {
            pooled.connection.close()
        } catch (e: Exception) {
            Timber.w("SMB connection close error (likely dead connection): ${e.message}")
        }
    }
}
