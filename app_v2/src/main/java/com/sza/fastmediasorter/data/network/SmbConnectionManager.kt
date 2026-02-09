package com.sza.fastmediasorter.data.network

import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.SmbConfig
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.connection.Connection
import com.hierynomus.smbj.session.Session
import com.hierynomus.smbj.share.DiskShare
import com.sza.fastmediasorter.core.network.NetworkStateMonitor
import com.sza.fastmediasorter.data.network.model.ConnectionKey
import com.sza.fastmediasorter.data.network.model.SmbConnectionInfo
import com.sza.fastmediasorter.data.network.model.SmbResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import timber.log.Timber
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages SMB connection pooling, lifecycle, and health tracking.
 * 
 * Responsibilities:
 * - Connection pool management with automatic cleanup
 * - Normal vs. degraded client configuration
 * - Connection health tracking (timeout counter)
 * - Semaphore-based concurrent connection limiting
 * - Connection validation and retry logic
 * 
 * Extracted from SmbClient to improve maintainability and testability.
 */
@Singleton
class SmbConnectionManager @Inject constructor(
    private val networkStateMonitor: NetworkStateMonitor
) {
    
    init {
        // Register for network change notifications to handle WiFi reconnections
        networkStateMonitor.registerCallback(object : NetworkStateMonitor.NetworkChangeCallback {
            override fun onNetworkChanged() {
                handleNetworkReconnect()
            }
            
            override fun onNetworkLost() {
                handleNetworkLost()
            }
        })
    }
    
    companion object {
        // Normal timeouts (healthy connection)
        private const val CONNECTION_TIMEOUT_MS = 5000L // 5 seconds
        private const val READ_TIMEOUT_MS = 90000L // 90 seconds - increased for large folder listing
        private const val WRITE_TIMEOUT_MS = 60000L // 60 seconds
        
        // Degraded timeouts (poor connection)
        private const val CONNECTION_TIMEOUT_DEGRADED_MS = 15000L // 15 seconds
        private const val READ_TIMEOUT_DEGRADED_MS = 120000L // 120 seconds - extended for degraded connection
        
        // Timeout for no-response detection (connection appears dead)
        private const val NO_RESPONSE_TIMEOUT_MS = 15000L // 15 seconds
        
        // Retry configuration
        private const val MAX_RETRY_ATTEMPTS = 3
        private const val RETRY_DELAY_MS = 1000L
        private const val MAX_CONCURRENT_CONNECTIONS = 16 // Reduced from 24 to prevent credit exhaustion
        
        // Connection staleness thresholds
        private const val CONNECTION_STALE_THRESHOLD_MS = 2 * 60 * 1000L // 2 minutes - mark as stale (reduced from 4)
        private const val CONNECTION_FORCE_RESET_MS = 3 * 60 * 1000L // 3 minutes - force full reset (reduced from 5)
        
        // Timeout degradation tracking
        private const val TIMEOUT_WARNING_THRESHOLD = 5
        private const val TIMEOUT_CRITICAL_THRESHOLD = 20
        
        @Volatile
        private var consecutiveTimeouts = 0
        
        @Volatile
        private var lastSuccessfulOperation = System.currentTimeMillis()
    }
    
    // Connection pool
    data class PooledConnection(
        val connection: Connection,
        val session: Session,
        val share: DiskShare,
        var lastUsed: Long = System.currentTimeMillis(),
        val usageCount: AtomicInteger = AtomicInteger(0),
        val isPendingClose: AtomicBoolean = AtomicBoolean(false)
    )
    
    private val connectionPool = ConcurrentHashMap<ConnectionKey, PooledConnection>()
    private val connectionSemaphore = Semaphore(MAX_CONCURRENT_CONNECTIONS)
    
    // Lazy initialization of SMB clients
    private val normalConfig by lazy {
        SmbConfig.builder()
            .withTimeout(CONNECTION_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .withSoTimeout(READ_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .withMultiProtocolNegotiate(true)
            .withReadBufferSize(65536) // 64KB read buffer
            .withWriteBufferSize(65536) // 64KB write buffer
            .withTransactBufferSize(4280) // Standard transact buffer size
            .build()
    }
    
    private val degradedConfig by lazy {
        SmbConfig.builder()
            .withTimeout(CONNECTION_TIMEOUT_DEGRADED_MS, TimeUnit.MILLISECONDS)
            .withSoTimeout(READ_TIMEOUT_DEGRADED_MS, TimeUnit.MILLISECONDS)
            .withMultiProtocolNegotiate(true)
            .withReadBufferSize(32768) // 32KB read buffer (smaller for degraded)
            .withWriteBufferSize(32768) // 32KB write buffer (smaller for degraded)
            .withTransactBufferSize(4280) // Standard transact buffer size
            .build()
    }
    
    @Volatile
    private var normalClient: SMBClient? = null
    
    @Volatile
    private var degradedClient: SMBClient? = null
    
    /**
     * Get or create normal (healthy connection) SMB client.
     */
    private fun getNormalClient(): SMBClient {
        return normalClient ?: synchronized(this) {
            normalClient ?: SMBClient(normalConfig).also { normalClient = it }
        }
    }
    
    /**
     * Get or create degraded (poor connection) SMB client with extended timeouts.
     */
    private fun getDegradedClient(): SMBClient {
        return degradedClient ?: synchronized(this) {
            degradedClient ?: SMBClient(degradedConfig).also { degradedClient = it }
        }
    }
    
    /**
     * Select appropriate SMB client based on connection health.
     * Uses degraded client when ConnectionThrottleManager reports degradation.
     */
    fun getClient(server: String, port: Int): SMBClient {
        val resourceKey = "smb://$server:$port"
        val isDegraded = ConnectionThrottleManager.isDegraded(
            ConnectionThrottleManager.ProtocolLimits.SMB,
            resourceKey
        )
        return if (isDegraded) getDegradedClient() else getNormalClient()
    }
    
    /**
     * Execute block with a pooled SMB connection.
     * Handles connection pooling, retry logic, and health tracking.
     */
    suspend fun <T> withConnection(
        connectionInfo: SmbConnectionInfo,
        block: suspend (DiskShare) -> SmbResult<T>
    ): SmbResult<T> = connectionSemaphore.withPermit {
        val key = ConnectionKey(
            server = connectionInfo.server,
            port = connectionInfo.port,
            shareName = connectionInfo.shareName,
            username = connectionInfo.username,
            domain = connectionInfo.domain
        )
        
        // Reset timeout counter and connections after idle period
        val timeSinceLastSuccess = System.currentTimeMillis() - lastSuccessfulOperation
        if (timeSinceLastSuccess > CONNECTION_FORCE_RESET_MS) {
            Timber.d("Idle for ${timeSinceLastSuccess}ms - closing all connections (server likely closed them)")
            closeAllConnections()
            resetClients()
            consecutiveTimeouts = 0
        } else if (consecutiveTimeouts > 0 && timeSinceLastSuccess > 60000) {
            Timber.d("Resetting timeout counter after ${timeSinceLastSuccess}ms idle (was: $consecutiveTimeouts)")
            consecutiveTimeouts = 0
        }
        
        // Force reset after critical threshold
        if (consecutiveTimeouts >= TIMEOUT_CRITICAL_THRESHOLD) {
            Timber.e("CRITICAL: $consecutiveTimeouts consecutive timeouts - forcing full reset")
            closeAllConnections()
            resetClients()
            consecutiveTimeouts = 0
        }
        
        // Attempt 1: Try pooled connection
        val pooled = connectionPool[key]
        if (pooled != null && isConnectionValid(pooled)) {
            pooled.lastUsed = System.currentTimeMillis()
            
            // Track usage
            pooled.usageCount.incrementAndGet()
            
            try {
                val result = block(pooled.share)
                onSuccess()
                return@withPermit result
            } catch (e: CancellationException) {
                Timber.d("Pooled connection cancelled: ${e::class.simpleName}")
                throw e
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                handleTimeout(key, pooled)
                throw e
            } catch (e: Exception) {
                handlePooledConnectionFailure(key, pooled, e)
                // Continue to create fresh connection below
            } finally {
                // Release usage and check for pending close
                val count = pooled.usageCount.decrementAndGet()
                if (count == 0 && pooled.isPendingClose.get()) {
                    Timber.d("Closing pending connection after use (key=${key.server})")
                    closeConnectionAsync(pooled)
                }
            }
        }
        
        // Attempt 2: Create fresh connection with retry and degraded timeout
        var freshConnectionAttempts = 0
        var lastException: Exception? = null
        
        while (freshConnectionAttempts < 2) {
            freshConnectionAttempts++
            try {
                val newPooled = createFreshConnection(connectionInfo, useDegradedTimeout = true)
                
                // Track usage for fresh connection
                newPooled.usageCount.incrementAndGet()
                
                try {
                    val result = block(newPooled.share)
                    onSuccess()
                    return@withPermit result
                } finally {
                    // Release usage and check for pending close
                    val count = newPooled.usageCount.decrementAndGet()
                    if (count == 0 && newPooled.isPendingClose.get()) {
                        Timber.d("Closing pending fresh connection after use")
                        closeConnectionAsync(newPooled)
                    }
                }
            } catch (e: Exception) {
                lastException = e
                if (freshConnectionAttempts < 2) {
                    Timber.w("Fresh connection attempt $freshConnectionAttempts failed, retrying with longer timeout")
                    delay(RETRY_DELAY_MS)
                }
            }
        }
        
        // If we get here, both attempts failed - return error
        handleFreshConnectionFailure(key, connectionInfo, lastException ?: Exception("Unknown SMB connection error"))
    }
    
    /**
     * Create a new SMB connection and add to pool.
     * @param useDegradedTimeout if true, use extended timeouts for recovery scenarios
     */
    private suspend fun createFreshConnection(
        connectionInfo: SmbConnectionInfo,
        useDegradedTimeout: Boolean = false
    ): PooledConnection {
        val startTime = System.currentTimeMillis()
        // Use degraded client for recovery after timeout to get extended timeouts
        val client = if (useDegradedTimeout) getDegradedClient() else getClient(connectionInfo.server, connectionInfo.port)
        val connection = client.connect(connectionInfo.server, connectionInfo.port)
        val connectTime = System.currentTimeMillis() - startTime
        Timber.d("SMB connect to ${connectionInfo.server}:${connectionInfo.port} took ${connectTime}ms (degraded=$useDegradedTimeout)")
        
        val finalDomain = connectionInfo.domain.trim().ifEmpty { null }
        Timber.d("SMB Auth: user='${connectionInfo.username}', domain='$finalDomain', pwdLen=${connectionInfo.password.length}")
        
        val authContext = if (connectionInfo.username.isEmpty()) {
            AuthenticationContext.anonymous()
        } else {
            AuthenticationContext(
                connectionInfo.username,
                connectionInfo.password.toCharArray(),
                finalDomain
            )
        }
        
        val authStartTime = System.currentTimeMillis()
        val session = connection.authenticate(authContext)
        val authTime = System.currentTimeMillis() - authStartTime
        Timber.d("SMB authenticate took ${authTime}ms")
        
        val shareStartTime = System.currentTimeMillis()
        val share = session.connectShare(connectionInfo.shareName) as DiskShare
        val shareTime = System.currentTimeMillis() - shareStartTime
        Timber.d("SMB connect to share ${connectionInfo.shareName} took ${shareTime}ms")
        
        // Store in pool
        val key = ConnectionKey(
            server = connectionInfo.server,
            port = connectionInfo.port,
            shareName = connectionInfo.shareName,
            username = connectionInfo.username,
            domain = connectionInfo.domain
        )
        val newPooled = PooledConnection(connection, session, share)
        connectionPool[key] = newPooled
        
        return newPooled
    }
    
    /**
     * Check if pooled connection is still valid.
     * Validates both idle time and actual connection state.
     */
    private fun isConnectionValid(pooled: PooledConnection): Boolean {
        return try {
            val idleTime = System.currentTimeMillis() - pooled.lastUsed
            
            // Check if connection has been idle too long
            if (idleTime > CONNECTION_STALE_THRESHOLD_MS) {
                Timber.d("Connection stale after ${idleTime}ms idle (threshold: ${CONNECTION_STALE_THRESHOLD_MS}ms)")
                return false
            }
            
            // Validate actual connection state
            val isConnected = pooled.connection.isConnected &&
                pooled.session.connection.isConnected &&
                pooled.share.isConnected
            
            if (!isConnected) {
                Timber.d("Connection validation failed - connection no longer active")
            }
            
            isConnected
        } catch (e: Exception) {
            Timber.d("Connection validation failed with exception: ${e.message}")
            false
        }
    }
    
    /**
     * Handle successful operation.
     */
    private fun onSuccess() {
        consecutiveTimeouts = 0
        lastSuccessfulOperation = System.currentTimeMillis()
    }
    
    /**
     * Handle timeout from pooled connection.
     */
    private fun handleTimeout(key: ConnectionKey, pooled: PooledConnection) {
        consecutiveTimeouts++
        Timber.d("Pooled connection timeout (#$consecutiveTimeouts)")
        
        if (consecutiveTimeouts >= TIMEOUT_WARNING_THRESHOLD) {
            Timber.w("SMB degradation: $consecutiveTimeouts consecutive timeouts")
        }
        
        // After 3 timeouts, force fresh reconnect
        if (consecutiveTimeouts >= 3) {
            connectionPool.remove(key)
            closeConnectionAsync(pooled)
        }
    }
    
    /**
     * Handle failure from pooled connection.
     */
    private fun handlePooledConnectionFailure(
        key: ConnectionKey,
        pooled: PooledConnection,
        e: Exception
    ) {
        // Check for InterruptedException
        val rootCause = generateSequence(e as Throwable) { it.cause }.lastOrNull()
        if (rootCause is InterruptedException) {
            throw kotlinx.coroutines.CancellationException("Operation interrupted", e as Throwable)
        }
        
        // Check for SMB credits exhaustion
        val errorMessage = e.message ?: ""
        val isCreditError = errorMessage.contains("Not enough credits", ignoreCase = true) ||
                           errorMessage.contains("STATUS_INSUFF_SERVER_RESOURCES", ignoreCase = true)
        
        // Check for protocol-level parsing errors (corrupted connection)
        val isProtocolError = errorMessage.contains("Invalid uint32", ignoreCase = true) ||
                             errorMessage.contains("Invalid uint64", ignoreCase = true) ||
                             errorMessage.contains("Invalid SMB", ignoreCase = true) ||
                             e is IllegalArgumentException && errorMessage.contains("value:")
        
        if (isCreditError || isProtocolError) {
            val errorType = if (isCreditError) "CREDITS EXHAUSTED" else "PROTOCOL PARSE ERROR"
            Timber.e("SMB $errorType on pooled connection - forcing connection reset")
            // Close all connections to this server to free up credits/clean corrupted state
            connectionPool.entries.removeIf { (poolKey, pooledConn) ->
                if (poolKey.server == key.server) {
                    closeConnectionAsync(pooledConn)
                    true
                } else {
                    false
                }
            }
        }
        
        val isTimeout = e.toString().contains("TimeoutException", ignoreCase = true)
        if (isTimeout) {
            Timber.w("Pooled connection timed out (server session expired)")
            consecutiveTimeouts++
        } else {
            Timber.w(e, "Pooled connection failed, retrying with fresh")
        }
        
        // Track failures
        if (e is com.hierynomus.smbj.common.SMBRuntimeException || isTimeout) {
            if (!isTimeout) consecutiveTimeouts++
            if (consecutiveTimeouts >= TIMEOUT_WARNING_THRESHOLD) {
                Timber.w("SMB degradation: $consecutiveTimeouts consecutive failures")
            }
        }
        
        // Remove and close async
        connectionPool.remove(key)
        closeConnectionAsync(pooled)
    }
    
    /**
     * Handle failure when creating fresh connection.
     */
    @Suppress("UNUSED_PARAMETER")
    private fun <T> handleFreshConnectionFailure(
        key: ConnectionKey,
        connectionInfo: SmbConnectionInfo,
        e: Exception
    ): SmbResult<T> {
        val errorMessage = e.message ?: ""
        
        // Check for SMB credits exhaustion
        val isCreditError = errorMessage.contains("Not enough credits", ignoreCase = true) ||
                           errorMessage.contains("STATUS_INSUFF_SERVER_RESOURCES", ignoreCase = true)
        
        // Check for protocol-level parsing errors (corrupted connection)
        val isProtocolError = errorMessage.contains("Invalid uint32", ignoreCase = true) ||
                             errorMessage.contains("Invalid uint64", ignoreCase = true) ||
                             errorMessage.contains("Invalid SMB", ignoreCase = true) ||
                             e is IllegalArgumentException && errorMessage.contains("value:")
        
        if (isCreditError || isProtocolError) {
            val errorType = if (isCreditError) "CREDITS EXHAUSTED" else "PROTOCOL PARSE ERROR"
            Timber.e("SMB $errorType - forcing connection reset for ${connectionInfo.server}")
            // Close all connections to this server to free up credits/clean corrupted state
            connectionPool.entries.removeIf { (poolKey, pooled) ->
                if (poolKey.server == connectionInfo.server) {
                    closeConnectionAsync(pooled)
                    true
                } else {
                    false
                }
            }
            // Small delay to let server recover
            Thread.sleep(500)
        }
        
        val errorDetail = buildString {
            append("SMB connection failed: $errorMessage")
            e.cause?.let { cause ->
                append(" (cause: ${cause.javaClass.simpleName}: ${cause.message})")
            }
        }
        Timber.w(e, errorDetail)
        
        // Check for critical socket errors
        val isCriticalError = e.cause?.let { cause ->
            cause is java.net.SocketException && 
            (cause.message?.contains("Software caused connection abort") == true ||
             cause.message?.contains("Connection reset") == true ||
             cause.message?.contains("Broken pipe") == true)
        } ?: false
        
        if (isCriticalError) {
            Timber.e("CRITICAL socket error - forcing full reset")
            closeAllConnections()
            resetClients()
            consecutiveTimeouts = 0
        } else {
            // Track timeouts
            if (e is kotlinx.coroutines.TimeoutCancellationException || 
                e is com.hierynomus.smbj.common.SMBRuntimeException) {
                consecutiveTimeouts++
                if (consecutiveTimeouts >= TIMEOUT_WARNING_THRESHOLD) {
                    Timber.e("SMB severely degraded: $consecutiveTimeouts failures - full reset")
                    closeAllConnections()
                    resetClients()
                    consecutiveTimeouts = 0
                } else if (consecutiveTimeouts > TIMEOUT_WARNING_THRESHOLD / 2) {
                    Timber.w("SMB degradation: $consecutiveTimeouts timeouts")
                }
            }
        }
        
        removeConnection(key)
        return SmbResult.Error(getUserFriendlyMessage(e), e)
    }
    
    /**
     * Remove connection from pool and close it.
     */
    private fun removeConnection(key: ConnectionKey) {
        connectionPool.remove(key)?.let { pooled ->
            try {
                pooled.share.close()
                pooled.session.close()
                pooled.connection.close()
            } catch (e: Exception) {
                Timber.w(e, "Error closing pooled connection")
            }
        }
    }
    
    /**
     * Close connection asynchronously (don't block caller).
     */
    private fun closeConnectionAsync(pooled: PooledConnection) {
        // Mark as pending close so it won't be reused and will be closed after current use
        pooled.isPendingClose.set(true)
        
        CoroutineScope(Dispatchers.IO).launch {
            // Only close if not in use
            if (pooled.usageCount.get() == 0) {
                try {
                    pooled.share.close()
                    pooled.session.close()
                    pooled.connection.close()
                    Timber.d("Connection closed successfully")
                } catch (e: Exception) {
                    // Ignore errors during forced cleanup
                }
            } else {
                Timber.d("Connection marked for close but currently in use (count=${pooled.usageCount.get()}). Will be closed after use.")
            }
        }
    }
    
    /**
     * Close all connections in pool.
     */
    /**
     * Close all pooled connections.
     */
    private fun closeAllConnections() {
        val keys = connectionPool.keys().toList()
        keys.forEach { key ->
            removeConnection(key)
        }
        Timber.d("Closed all ${keys.size} pooled connections")
    }
    
    /**
     * Clear connection pool (public API).
     * Used when refreshing resources or on connection issues.
     */
    fun clearConnectionPool() {
        closeAllConnections()
    }
    
    /**
     * Reset SMB clients (force recreation on next use).
     */
    private fun resetClients() {
        try {
            normalClient?.close()
            degradedClient?.close()
        } catch (e: Exception) {
            Timber.w(e, "Error closing SMB clients during reset")
        } finally {
            normalClient = null
            degradedClient = null
            Timber.d("SMB clients reset")
        }
    }
    
    /**     * Force full reset: close all connections and reset clients.
     * Used when user manually refreshes or encounters persistent issues.
     */
    fun forceFullReset() {
        closeAllConnections()
        resetClients()
    }
    
    /**     * Convert exception to user-friendly error message.
     */
    private fun getUserFriendlyMessage(e: Exception): String {
        return when {
            e.message?.contains("Unknown host", ignoreCase = true) == true ->
                "Cannot resolve server address. Check server name/IP."
            e.message?.contains("Connection refused", ignoreCase = true) == true ->
                "Connection refused. Check if SMB service is running."
            e.message?.contains("Connection timed out", ignoreCase = true) == true ->
                "Connection timed out. Check network and server availability."
            e.message?.contains("Authentication failed", ignoreCase = true) == true ->
                "Authentication failed. Check username and password."
            e.message?.contains("Access denied", ignoreCase = true) == true ->
                "Access denied. Check share permissions."
            e is kotlinx.coroutines.TimeoutCancellationException ->
                "Operation timed out. Server may be overloaded or network slow."
            else -> e.message ?: "Unknown error"
        }
    }
    
    /**
     * Get connection for ExoPlayer (synchronous blocking call).
     * Used by SmbDataSource which runs in ExoPlayer's thread pool.
     * Attempts to reuse pooled connection, creates new if needed.
     * 
     * IMPORTANT: Caller must NOT close the returned objects - they are managed by the pool.
     * ExoPlayer should only use the DiskShare for file operations.
     */
    fun getConnectionForExoPlayer(connectionInfo: SmbConnectionInfo): PooledConnection {
        val key = ConnectionKey(
            server = connectionInfo.server,
            port = connectionInfo.port,
            shareName = connectionInfo.shareName,
            username = connectionInfo.username,
            domain = connectionInfo.domain
        )
        
        // Try pooled connection first
        val pooled = connectionPool[key]
        if (pooled != null && isConnectionValid(pooled)) {
            pooled.lastUsed = System.currentTimeMillis()
            Timber.d("SmbConnectionManager: Reusing pooled connection for ExoPlayer")
            return pooled
        }
        
        // Create fresh connection (blocking)
        Timber.d("SmbConnectionManager: Creating fresh connection for ExoPlayer")
        try {
            val client = getClient(connectionInfo.server, connectionInfo.port)
            val connection = client.connect(connectionInfo.server, connectionInfo.port)
            
            val finalDomain = connectionInfo.domain.trim().ifEmpty { null }
            val authContext = if (connectionInfo.username.isEmpty()) {
                AuthenticationContext.anonymous()
            } else {
                AuthenticationContext(
                    connectionInfo.username,
                    connectionInfo.password.toCharArray(),
                    finalDomain
                )
            }
            
            val session = connection.authenticate(authContext)
            val share = session.connectShare(connectionInfo.shareName) as DiskShare
            
            // Store in pool
            val newPooled = PooledConnection(connection, session, share)
            connectionPool[key] = newPooled
            
            onSuccess()
            return newPooled
        } catch (e: Exception) {
            Timber.e(e, "SmbConnectionManager: Failed to create connection for ExoPlayer")
            throw IOException("Failed to connect to SMB: ${e.message}", e)
        }
    }
    
    /**
     * Handle network reconnection (e.g., WiFi reconnect with new IP).
     * Invalidates all pooled connections as they may be broken.
     */
    private fun handleNetworkReconnect() {
        Timber.w("SmbConnectionManager: Network reconnected - invalidating all SMB connections")
        closeAllConnections()
        // Don't reset clients - they will work with new network
        // Just clear the pool so new connections will be created
        consecutiveTimeouts = 0
        lastSuccessfulOperation = System.currentTimeMillis()
    }
    
    /**
     * Handle network loss (e.g., WiFi disconnected, airplane mode).
     * Closes all connections immediately.
     */
    private fun handleNetworkLost() {
        Timber.w("SmbConnectionManager: Network lost - closing all SMB connections")
        closeAllConnections()
    }
    
    /**
     * Close all resources and cleanup.
     */
    fun close() {
        closeAllConnections()
        resetClients()
    }
}
