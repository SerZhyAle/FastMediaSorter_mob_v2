package com.sza.fastmediasorter.data.network.pool

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Semaphore

/**
 * Base connection pool implementation for network clients.
 * Provides thread-safe connection pooling with automatic cleanup of idle connections.
 * 
 * @param K Connection key type (identifies unique connections)
 * @param C Connection type (the actual connection object)
 * @param maxConnections Maximum number of concurrent connections allowed
 * @param idleTimeoutMs How long a connection can be idle before cleanup (milliseconds)
 * @param protocolName Protocol name for logging (e.g., "SMB", "SFTP", "FTP")
 */
abstract class BaseConnectionPool<K : Any, C : Any>(
    private val maxConnections: Int,
    private val idleTimeoutMs: Long,
    private val protocolName: String
) {
    
    /**
     * Wrapper for pooled connection with metadata.
     */
    protected data class PooledConnection<C>(
        val connection: C,
        var lastUsed: Long = System.currentTimeMillis()
    )
    
    protected val connectionPool = ConcurrentHashMap<K, PooledConnection<C>>()
    protected val connectionSemaphore = Semaphore(maxConnections)
    protected val poolMutex = Mutex()
    
    /**
     * Create a new connection for the given key.
     * Must be implemented by subclasses.
     */
    protected abstract suspend fun createConnection(key: K): C
    
    /**
     * Check if a connection is still valid.
     * Must be implemented by subclasses.
     */
    protected abstract fun isConnectionValid(connection: C): Boolean
    
    /**
     * Close a connection.
     * Must be implemented by subclasses.
     */
    protected abstract fun closeConnection(connection: C)
    
    /**
     * Execute a block with a connection from the pool.
     * Automatically acquires/releases connections and handles cleanup.
     */
    protected suspend fun <T> withConnection(key: K, block: suspend (C) -> T): T {
        // Try to acquire semaphore permit
        if (!connectionSemaphore.tryAcquire()) {
            // Pool full - cleanup idle connections first
            cleanupIdleConnectionsQuick()
            connectionSemaphore.acquire()
        }
        
        try {
            val connection = getOrCreateConnection(key)
            val result = block(connection)
            
            // Update last used timestamp
            poolMutex.withLock {
                connectionPool[key]?.lastUsed = System.currentTimeMillis()
            }
            
            return result
        } finally {
            connectionSemaphore.release()
        }
    }
    
    /**
     * Get existing connection or create new one.
     */
    private suspend fun getOrCreateConnection(key: K): C {
        poolMutex.withLock {
            val pooled = connectionPool[key]
            
            // Check if existing connection is valid
            if (pooled != null) {
                if (isConnectionValid(pooled.connection)) {
                    pooled.lastUsed = System.currentTimeMillis()
                    Timber.d("Reusing pooled $protocolName connection")
                    return pooled.connection
                } else {
                    // Connection invalid - remove and recreate
                    Timber.d("$protocolName connection stale, recreating")
                    connectionPool.remove(key)
                    try {
                        closeConnection(pooled.connection)
                    } catch (e: Exception) {
                        Timber.w(e, "Error closing stale $protocolName connection")
                    }
                }
            }
            
            // Create new connection
            val newConnection = createConnection(key)
            val newPooled = PooledConnection(newConnection)
            connectionPool[key] = newPooled
            Timber.d("Created new pooled $protocolName connection (pool size: ${connectionPool.size})")
            
            return newConnection
        }
    }
    
    /**
     * Remove a specific connection from pool.
     */
    protected fun invalidateConnection(key: K) {
        poolMutex.tryLock()
        try {
            connectionPool.remove(key)?.let { pooled ->
                try {
                    closeConnection(pooled.connection)
                    Timber.d("Invalidated $protocolName connection")
                } catch (e: Exception) {
                    Timber.w(e, "Error closing invalidated $protocolName connection")
                }
            }
        } finally {
            poolMutex.unlock()
        }
    }
    
    /**
     * Quick cleanup: remove dead/idle connections without blocking.
     */
    private fun cleanupIdleConnectionsQuick() {
        val now = System.currentTimeMillis()
        val keysToRemove = mutableListOf<K>()
        
        // Identify idle connections
        connectionPool.entries.forEach { (key, pooled) ->
            val isIdle = (now - pooled.lastUsed) > idleTimeoutMs
            val isDead = !isConnectionValid(pooled.connection)
            
            if (isDead || isIdle) {
                keysToRemove.add(key)
            }
        }
        
        // Remove without blocking
        keysToRemove.forEach { key ->
            connectionPool.remove(key)
        }
        
        if (keysToRemove.isNotEmpty()) {
            Timber.d("Quick-removed ${keysToRemove.size} idle/dead $protocolName connections")
        }
    }
    
    /**
     * Full cleanup of idle connections (with graceful close).
     */
    fun cleanupIdleConnections() {
        val now = System.currentTimeMillis()
        val keysToRemove = mutableListOf<K>()
        
        // Identify idle connections
        connectionPool.entries.forEach { (key, pooled) ->
            if ((now - pooled.lastUsed) > idleTimeoutMs) {
                keysToRemove.add(key)
            }
        }
        
        // Remove and close
        keysToRemove.forEach { key ->
            connectionPool.remove(key)?.let { pooled ->
                try {
                    closeConnection(pooled.connection)
                    Timber.d("Closed idle $protocolName connection")
                } catch (e: Exception) {
                    Timber.w(e, "Error closing idle $protocolName connection")
                }
            }
        }
    }
    
    /**
     * Close all connections in pool.
     */
    fun clearAllConnections() {
        Timber.w("Closing all ${connectionPool.size} pooled $protocolName connections")
        val keys = connectionPool.keys.toList()
        keys.forEach { key ->
            invalidateConnection(key)
        }
    }
    
    /**
     * Get current pool size.
     */
    fun getPoolSize(): Int = connectionPool.size
}
