package com.sza.fastmediasorter.data.network

import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Generic connection pool abstraction for network protocols (SMB/SFTP/FTP).
 *
 * Features:
 * - Thread-safe pooling with Mutex and ConcurrentHashMap
 * - Automatic idle connection cleanup (45s timeout)
 * - Connection validation before reuse
 * - Connection degradation tracking with auto-recovery
 * - Full reset on critical errors
 *
 * @param K Key type identifying connections (server, port, username, etc.)
 * @param C Connection type (SMBConnection, SFTP Session, FTPClient, etc.)
 * @param maxConnections Maximum concurrent connections
 * @param idleTimeoutMs Time before idle connections are cleaned (default 45s)
 */
abstract class BaseConnectionPool<K : Any, C : Any>(
    private val maxConnections: Int = 24,
    private val idleTimeoutMs: Long = 45000
) {
    /**
     * Pooled connection wrapper with last-used timestamp
     */
    data class PooledConnection<C>(
        val connection: C,
        var lastUsed: Long = System.currentTimeMillis()
    )

    // Connection pool storage
    protected val connectionPool = ConcurrentHashMap<K, PooledConnection<C>>()

    // Semaphore to limit concurrent connections
    private val connectionSemaphore = Semaphore(maxConnections)

    // Error tracking for degradation detection. Atomic because the semaphore admits up to
    // maxConnections callers at once, and a plain ++ on a volatile field loses strikes.
    private val consecutiveTimeouts = AtomicInteger(0)

    @Volatile
    private var lastSuccessfulOperation = System.currentTimeMillis()

    /**
     * Execute operation with connection pooling and auto-recovery.
     * Tries pooled connection first, falls back to fresh connection on failure.
     */
    protected suspend fun <T> withConnection(
        key: K,
        block: suspend (C) -> T
    ): T = connectionSemaphore.withPermit {
        // Reset timeout counter if enough time passed since last failure (1 minute idle = recovery)
        val timeSinceLastSuccess = System.currentTimeMillis() - lastSuccessfulOperation
        val idleStrikes = consecutiveTimeouts.get()
        if (idleStrikes > 0 && timeSinceLastSuccess > 60000) {
            Timber.d("${this::class.simpleName}: Resetting timeout counter after 60s idle (was: $idleStrikes)")
            consecutiveTimeouts.set(0)
            closeAllConnections()
            resetClients()
        }

        // Critical: If too many consecutive timeouts, force full reset
        val strikes = consecutiveTimeouts.get()
        if (strikes >= TIMEOUT_CRITICAL_THRESHOLD) {
            Timber.e("${this::class.simpleName}: CRITICAL - $strikes consecutive timeouts - forcing full reset")
            closeAllConnections()
            resetClients()
            consecutiveTimeouts.set(0)
        }

        // Attempt 1: Try pooled connection if exists and valid
        val pooled = connectionPool[key]
        if (pooled != null && isConnectionValid(pooled)) {
            pooled.lastUsed = System.currentTimeMillis()
            try {
                val result = block(pooled.connection)
                onSuccess()
                return@withPermit result
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                // Must precede the CancellationException catch: it is a subclass and would be shadowed.
                val timeouts = consecutiveTimeouts.incrementAndGet()
                Timber.d("${this::class.simpleName}: Pooled connection timeout (#$timeouts)")

                if (timeouts >= TIMEOUT_WARNING_THRESHOLD) {
                    Timber.w("${this::class.simpleName}: Connection degradation detected: $timeouts timeouts")
                }

                if (timeouts >= TIMEOUT_EVICT_THRESHOLD) {
                    removeConnection(key)
                }
                throw e
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Operation cancelled - keep connection alive
                Timber.d("${this::class.simpleName}: Pooled connection operation cancelled")
                throw e
            } catch (e: Exception) {
                // Check for InterruptedException
                val rootCause = generateSequence(e as Throwable) { it.cause }.lastOrNull()
                if (rootCause is InterruptedException) {
                    throw kotlinx.coroutines.CancellationException("Operation interrupted", e as Throwable)
                }

                // Pooled connection failed - remove and retry with fresh connection
                val isTimeout = e.toString().contains("TimeoutException", ignoreCase = true)
                if (isTimeout) {
                    Timber.w("${this::class.simpleName}: Pooled connection timed out (server session expired)")
                } else {
                    Timber.w(e, "${this::class.simpleName}: Pooled connection failed, retrying with fresh")
                }

                if (isTimeout || isCriticalError(e)) {
                    val failures = consecutiveTimeouts.incrementAndGet()
                    if (failures >= TIMEOUT_WARNING_THRESHOLD) {
                        Timber.w("${this::class.simpleName}: Connection degradation: $failures failures")
                    }
                }

                removeConnection(key)
                // Continue to create fresh connection below
            }
        }

        // Attempt 2: Create fresh connection
        try {
            val connection = createConnection(key)
            val newPooled = PooledConnection(connection)
            connectionPool[key] = newPooled

            val result = block(connection)
            onSuccess()
            result
        } catch (e: Exception) {
            // Check for critical errors that require full reset
            if (isCriticalError(e)) {
                Timber.e("${this::class.simpleName}: CRITICAL error - forcing full reset")
                closeAllConnections()
                resetClients()
                consecutiveTimeouts.set(0)
            } else {
                val failures = consecutiveTimeouts.incrementAndGet()
                if (failures >= TIMEOUT_WARNING_THRESHOLD) {
                    Timber.e("${this::class.simpleName}: Severely degraded: $failures failures - forcing reset")
                    closeAllConnections()
                    resetClients()
                    consecutiveTimeouts.set(0)
                } else if (failures > TIMEOUT_WARNING_THRESHOLD / 2) {
                    Timber.w("${this::class.simpleName}: Connection degradation: $failures failures")
                }
            }

            removeConnection(key)
            throw e
        }
    }

    private fun onSuccess() {
        consecutiveTimeouts.set(0)
        lastSuccessfulOperation = System.currentTimeMillis()
    }

    /**
     * Check if pooled connection is still valid (not too old)
     */
    private fun isConnectionValid(pooled: PooledConnection<C>): Boolean {
        val idleTime = System.currentTimeMillis() - pooled.lastUsed
        if (idleTime > idleTimeoutMs) {
            Timber.d("${this::class.simpleName}: Connection stale after ${idleTime}ms idle")
            return false
        }
        return isConnectionAlive(pooled.connection)
    }

    /**
     * Remove connection from pool and close it
     */
    private fun removeConnection(key: K) {
        connectionPool.remove(key)?.let { pooled ->
            try {
                closeConnection(pooled.connection)
            } catch (e: Exception) {
                Timber.w(e, "${this::class.simpleName}: Error closing pooled connection")
            }
        }
    }

    /**
     * Force close all connections in pool
     */
    protected fun closeAllConnections() {
        Timber.w("${this::class.simpleName}: Closing all ${connectionPool.size} pooled connections")
        val keys = connectionPool.keys.toList()
        keys.forEach { key ->
            removeConnection(key)
        }
    }

    /**
     * Quick cleanup: remove dead/idle connections without blocking
     */
    fun cleanupIdleConnections() {
        val now = System.currentTimeMillis()
        val keysToRemove = mutableListOf<K>()

        // Identify dead or idle connections
        connectionPool.entries.forEach { (key, pooled) ->
            val isIdle = (now - pooled.lastUsed) > idleTimeoutMs
            val isDead = !isConnectionAlive(pooled.connection)

            if (isDead || isIdle) {
                keysToRemove.add(key)
            }
        }

        keysToRemove.forEach { key ->
            connectionPool.remove(key)
            Timber.d("${this::class.simpleName}: Quick-removed idle/dead connection")
        }
    }

    /**
     * Clear all pooled connections (call on shutdown)
     */
    fun clearConnectionPool() {
        connectionPool.keys.toList().forEach { key ->
            removeConnection(key)
        }
    }

    /**
     * Full reset with connection pool cleanup (for manual refresh actions)
     */
    open fun forceFullReset() {
        Timber.i("${this::class.simpleName}: Force full reset requested")
        closeAllConnections()
        resetClients()
    }

    // Abstract methods to be implemented by protocol-specific pools

    /**
     * Create new connection for given key
     */
    protected abstract suspend fun createConnection(key: K): C

    /**
     * Check if connection is alive (non-blocking check)
     */
    protected abstract fun isConnectionAlive(connection: C): Boolean

    /**
     * Close connection gracefully
     */
    protected abstract fun closeConnection(connection: C)

    /**
     * Check if exception indicates critical error requiring full reset
     */
    protected abstract fun isCriticalError(exception: Exception): Boolean

    /**
     * Reset protocol clients (clear cached instances)
     */
    protected abstract fun resetClients()

    private companion object {
        // Thresholds for connection degradation
        const val TIMEOUT_WARNING_THRESHOLD = 5
        const val TIMEOUT_CRITICAL_THRESHOLD = 10
        const val TIMEOUT_EVICT_THRESHOLD = 3
    }
}
