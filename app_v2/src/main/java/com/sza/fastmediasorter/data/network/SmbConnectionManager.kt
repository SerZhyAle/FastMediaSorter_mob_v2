package com.sza.fastmediasorter.data.network

import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.SmbConfig
import com.hierynomus.smbj.auth.AuthenticationContext
import com.sza.fastmediasorter.BuildConfig
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
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import timber.log.Timber
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Callback interface for SMB connection reset notifications.
 * Allows UI layer to show feedback to user when auto-reset occurs.
 */
interface SmbResetCallback {
    /**
     * Called when SMB connections are automatically reset.
     * @param reason Human-readable reason for reset (for logging/debugging)
     */
    fun onAutoReset(reason: String)
}

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
        // SMBJ per-request SMB2 transaction timeout tiers.
        // withTimeout() in SmbConfig controls how long SMBJ's Promise waits for each SMB2 response
        // (QUERY_DIRECTORY, SESSION_SETUP, etc.). DiskShare.list() pages results, so total scan time
        // can be N × transactionTimeout before SMBRuntimeException(TimeoutException) is thrown.
        //
        // Tier selection is driven by NetworkSpeedTestUseCase results (readSpeedMbps):
        //   FAST  (> 100 Mbps): 5 s — fast network, slow QUERY_DIRECTORY means broken NAS
        //   MEDIUM (10–100 Mbps): 10 s — average home NAS / WiFi
        //   SLOW  (< 10 Mbps or no data): 20 s — conservative; also used when isDegraded
        private const val TRANSACTION_TIMEOUT_FAST_MS = 5_000L   // > 100 Mbps
        private const val TRANSACTION_TIMEOUT_MEDIUM_MS = 10_000L // 10–100 Mbps
        private const val TRANSACTION_TIMEOUT_SLOW_MS = 20_000L   // < 10 Mbps or unknown

        // Socket SO_TIMEOUT (controls streaming reads, e.g. file downloads).
        // Independent of transaction timeout — kept generous for large files.
        private const val READ_TIMEOUT_MS = 90_000L  // normal connections
        private const val READ_TIMEOUT_DEGRADED_MS = 120_000L // degraded connections
        private const val WRITE_TIMEOUT_MS = 60_000L

        // Legacy alias kept for createFreshConnection which still uses normal/degraded split.
        // Normal client reuses FAST config (good latency assumption for fresh connects).
        private const val CONNECTION_TIMEOUT_MS = TRANSACTION_TIMEOUT_FAST_MS
        private const val CONNECTION_TIMEOUT_DEGRADED_MS = TRANSACTION_TIMEOUT_SLOW_MS
        
        // Fast TCP pre-check before full SMBJ connect attempt
        private const val CONNECTIVITY_CHECK_TIMEOUT_MS = 3000 // 3 seconds — 2× observed worst-case RTT (~1071ms)
        
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
        
        // Auto-reset cooldown - prevent frequent resets
        private const val AUTO_RESET_COOLDOWN_MS = 30000L // 30 seconds
        
        @Volatile
        private var consecutiveTimeouts = 0
        
        @Volatile
        private var lastSuccessfulOperation = System.currentTimeMillis()
        
        @Volatile
        private var lastAutoResetTime = 0L
    }
    
    // Connection pool
    // Tracks which subsystem originally created the pooled connection.
    // ExoPlayer must not reuse a SCANNER connection: directory-scan sessions have been
    // observed silently dropping their TCP socket between scan completion and playback
    // start, and SMBJ's isConnected flag cannot detect that — the subsequent openFile()
    // then blocks indefinitely inside the socket write (see SmbDataSource.open watchdog).
    enum class ConnectionConsumer { SCANNER, PLAYER }

    data class PooledConnection(
        val connection: Connection,
        val session: Session,
        val share: DiskShare,
        var lastUsed: Long = System.currentTimeMillis(),
        val usageCount: AtomicInteger = AtomicInteger(0),
        val isPendingClose: AtomicBoolean = AtomicBoolean(false),
        val consumer: ConnectionConsumer = ConnectionConsumer.SCANNER
    )
    
    private val connectionPool = ConcurrentHashMap<ConnectionKey, PooledConnection>()
    private val connectionSemaphore = Semaphore(MAX_CONCURRENT_CONNECTIONS)
    // Cleanup scope for async connection closes (ML-009)
    private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    // Callback for auto-reset notifications
    @Volatile
    private var resetCallback: SmbResetCallback? = null
    
    // Lazy initialization of SMB clients — one per transaction-timeout tier.
    // Configs are immutable once built; tier selection happens in getClient().
    private val fastConfig by lazy {
        SmbConfig.builder()
            .withTimeout(TRANSACTION_TIMEOUT_FAST_MS, TimeUnit.MILLISECONDS)
            .withSoTimeout(READ_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .withMultiProtocolNegotiate(true)
            .withReadBufferSize(65536)
            .withWriteBufferSize(65536)
            .withTransactBufferSize(4280)
            .build()
    }

    private val mediumConfig by lazy {
        SmbConfig.builder()
            .withTimeout(TRANSACTION_TIMEOUT_MEDIUM_MS, TimeUnit.MILLISECONDS)
            .withSoTimeout(READ_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .withMultiProtocolNegotiate(true)
            .withReadBufferSize(65536)
            .withWriteBufferSize(65536)
            .withTransactBufferSize(4280)
            .build()
    }

    // normalConfig kept as alias to fastConfig so createFreshConnection (attempt 1) remains fast.
    private val normalConfig get() = fastConfig

    private val degradedConfig by lazy {
        SmbConfig.builder()
            .withTimeout(TRANSACTION_TIMEOUT_SLOW_MS, TimeUnit.MILLISECONDS)
            .withSoTimeout(READ_TIMEOUT_DEGRADED_MS, TimeUnit.MILLISECONDS)
            .withMultiProtocolNegotiate(true)
            .withReadBufferSize(32768) // smaller buffer for degraded connections
            .withWriteBufferSize(32768)
            .withTransactBufferSize(4280)
            .build()
    }
    
    @Volatile
    private var normalClient: SMBClient? = null

    @Volatile
    private var mediumClient: SMBClient? = null

    @Volatile
    private var degradedClient: SMBClient? = null
    
    /**
     * Get or create fast-tier SMB client (transaction timeout = 5 s).
     * Used for connections where speed test confirmed > 100 Mbps.
     */
    private fun getFastClient(): SMBClient {
        return normalClient ?: synchronized(this) {
            normalClient ?: SMBClient(fastConfig).also { normalClient = it }
        }
    }

    /**
     * Get or create medium-tier SMB client (transaction timeout = 10 s).
     * Used for connections where speed test confirmed 10–100 Mbps.
     */
    private fun getMediumClient(): SMBClient {
        return mediumClient ?: synchronized(this) {
            mediumClient ?: SMBClient(mediumConfig).also { mediumClient = it }
        }
    }

    /**
     * Get or create slow/degraded-tier SMB client (transaction timeout = 20 s).
     * Default when no speed test data exists or runtime degradation detected.
     */
    private fun getDegradedClient(): SMBClient {
        return degradedClient ?: synchronized(this) {
            degradedClient ?: SMBClient(degradedConfig).also { degradedClient = it }
        }
    }

    // Alias so createFreshConnection (attempt 1, non-degraded) continues to work.
    private fun getNormalClient() = getFastClient()

    /**
     * Select SMBJ client for [server]:[port] based on two signals:
     * 1. Runtime health: if [ConnectionThrottleManager] reports degradation, use max-patience client.
     * 2. Speed test data: pick tier (FAST / MEDIUM / SLOW) from last measured read speed.
     *
     * This means a WiFi7 user gets a 5 s QUERY_DIRECTORY timeout (unresponsive = broken immediately),
     * while a user on a slow 4G bridge gets 20 s of patience.
     */
    fun getClient(server: String, port: Int): SMBClient {
        val resourceKey = "smb://$server:$port"
        // Runtime degradation always wins — server is already struggling.
        if (ConnectionThrottleManager.isDegraded(ConnectionThrottleManager.ProtocolLimits.SMB, resourceKey)) {
            return getDegradedClient()
        }
        return when (ConnectionThrottleManager.getSmbjClientTier(resourceKey)) {
            ConnectionThrottleManager.SmbjClientTier.FAST   -> getFastClient()
            ConnectionThrottleManager.SmbjClientTier.MEDIUM -> getMediumClient()
            ConnectionThrottleManager.SmbjClientTier.SLOW   -> getDegradedClient()
        }
    }
    
    /**
     * Execute block with a pooled SMB connection.
     * Handles connection pooling, retry logic, and health tracking.
     */
    suspend fun <T> withConnection(
        connectionInfo: SmbConnectionInfo,
        allowRetry: Boolean = true,
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
        
        // Attempt 2: Create fresh connection (with optional retry)
        val maxAttempts = if (allowRetry) 2 else 1
        var freshConnectionAttempts = 0
        var lastException: Exception? = null

        while (freshConnectionAttempts < maxAttempts) {
            freshConnectionAttempts++
            try {
                // First attempt: always use normal timeouts (fast failure).
                // Degraded client (extended timeouts) only on retry — server proved reachable but slow.
                val newPooled = createFreshConnection(
                    connectionInfo,
                    useDegradedTimeout = freshConnectionAttempts > 1 && allowRetry,
                    skipConnectivityCheck = freshConnectionAttempts > 1
                )

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
                // An outer withTimeout/coroutine cancel propagates as CancellationException.
                // Retrying after cancellation is impossible — the scope is already cancelled.
                // Re-throw immediately so the cancellation is not silently swallowed, which
                // would cause delay() (the next suspension point) to throw again anyway.
                if (e is CancellationException) throw e

                lastException = e
                if (isNonRetriableConnectionError(e)) {
                    Timber.w("Fresh connection failed with non-retriable error, aborting retries: ${e.message}")
                    break
                }
                if (freshConnectionAttempts < maxAttempts) {
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
        useDegradedTimeout: Boolean = false,
        skipConnectivityCheck: Boolean = false
    ): PooledConnection {
        // Fast TCP pre-check before attempting full SMBJ connect.
        // Skipped on retry attempt — the first TCP ping already failed transiently;
        // SMBJ itself will time out via CONNECTION_TIMEOUT_DEGRADED_MS if the host is truly dead.
        if (!skipConnectivityCheck) {
            checkConnectivity(connectionInfo.server, connectionInfo.port, CONNECTIVITY_CHECK_TIMEOUT_MS)
        }

        val startTime = if (BuildConfig.DEBUG) System.currentTimeMillis() else 0L
        // Use degraded client for recovery after timeout to get extended timeouts
        val client = if (useDegradedTimeout) getDegradedClient() else getClient(connectionInfo.server, connectionInfo.port)
        val connection = client.connect(connectionInfo.server, connectionInfo.port)
        if (BuildConfig.DEBUG) {
            Timber.d("SMB connect to ${connectionInfo.server}:${connectionInfo.port} took ${System.currentTimeMillis() - startTime}ms (degraded=$useDegradedTimeout)")
        }
        
        val finalDomain = connectionInfo.domain.trim().ifEmpty { null }
        val pwdLen = connectionInfo.password.length
        Timber.d("SMB Auth: user='${connectionInfo.username}', hasDomain=${!finalDomain.isNullOrBlank()}, pwdLen=$pwdLen")
        if (connectionInfo.username.isNotEmpty() && pwdLen == 0) {
            Timber.e("SMB Auth: password is EMPTY for non-anonymous user '${connectionInfo.username}' – possible Keystore decryption failure")
        }
        
        val authContext = if (connectionInfo.username.isEmpty()) {
            AuthenticationContext.anonymous()
        } else {
            AuthenticationContext(
                connectionInfo.username,
                connectionInfo.password.toCharArray(),
                finalDomain
            )
        }
        
        val authStartTime = if (BuildConfig.DEBUG) System.currentTimeMillis() else 0L
        val session = try {
            connection.authenticate(authContext)
        } catch (e: com.hierynomus.mssmb2.SMBApiException) {
            Timber.e("SMB STATUS_LOGON_FAILURE for user='${connectionInfo.username}', pwdLen=$pwdLen, domain=${finalDomain ?: "<none>"}, server=${connectionInfo.server}:${connectionInfo.port}")
            throw e
        }
        if (BuildConfig.DEBUG) {
            Timber.d("SMB authenticate took ${System.currentTimeMillis() - authStartTime}ms")
        }
        
        val shareStartTime = if (BuildConfig.DEBUG) System.currentTimeMillis() else 0L
        val share = session.connectShare(connectionInfo.shareName) as DiskShare
        if (BuildConfig.DEBUG) {
            Timber.d("SMB connect to share ${connectionInfo.shareName} took ${System.currentTimeMillis() - shareStartTime}ms")
        }
        
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
        
        // Check if this is a configuration/authentication error (not a network issue)
        val isAuthError = errorMessage.contains("STATUS_LOGON_FAILURE", ignoreCase = true) ||
                         errorMessage.contains("Authentication failed", ignoreCase = true)
        val isAccessError = errorMessage.contains("STATUS_ACCESS_DENIED", ignoreCase = true) ||
                           errorMessage.contains("Access denied", ignoreCase = true)
        val isConfigError = errorMessage.contains("Unknown host", ignoreCase = true) ||
                           errorMessage.contains("Connection refused", ignoreCase = true)
        val isShareNotFound = errorMessage.contains("STATUS_BAD_NETWORK_NAME", ignoreCase = true) ||
                             errorMessage.contains("STATUS_BAD_NETWORK_PATH", ignoreCase = true)
        
        val isTimeout = e.toString().contains("TimeoutException", ignoreCase = true)
        
        if (isAuthError || isAccessError || isConfigError || isShareNotFound) {
            // Configuration/authentication errors - don't increment timeout counter
            Timber.w(e, "Pooled connection configuration error (not network issue)")
            // Auto-reset SMB connections for this server
            val resetReason = when {
                isAuthError -> "Authentication failure detected"
                isAccessError -> "Access denied detected"
                isShareNotFound -> "Share not found detected"
                else -> "Configuration error detected"
            }
            autoResetIfNeeded(resetReason)
        } else if (isTimeout) {
            Timber.w("Pooled connection timed out (server session expired)")
            consecutiveTimeouts++
            if (consecutiveTimeouts >= TIMEOUT_WARNING_THRESHOLD) {
                Timber.w("SMB degradation: $consecutiveTimeouts consecutive timeouts")
            }
        } else {
            // Other SMB errors - track as potential network issue
            Timber.w(e, "Pooled connection failed, retrying with fresh")
            if (e is com.hierynomus.smbj.common.SMBRuntimeException) {
                consecutiveTimeouts++
                if (consecutiveTimeouts >= TIMEOUT_WARNING_THRESHOLD) {
                    Timber.w("SMB degradation: $consecutiveTimeouts consecutive failures")
                }
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
        
        // Check if this is a configuration/authentication error (not a network issue)
        val isAuthError = errorMessage.contains("STATUS_LOGON_FAILURE", ignoreCase = true) ||
                         errorMessage.contains("Authentication failed", ignoreCase = true)
        val isAccessError = errorMessage.contains("STATUS_ACCESS_DENIED", ignoreCase = true) ||
                           errorMessage.contains("Access denied", ignoreCase = true)
        val isConfigError = errorMessage.contains("Unknown host", ignoreCase = true) ||
                           errorMessage.contains("Connection refused", ignoreCase = true)
        val isShareNotFound = errorMessage.contains("STATUS_BAD_NETWORK_NAME", ignoreCase = true) ||
                             errorMessage.contains("STATUS_BAD_NETWORK_PATH", ignoreCase = true)
        
        if (isCriticalError) {
            Timber.e("CRITICAL socket error - forcing full reset")
            closeAllConnections()
            resetClients()
            consecutiveTimeouts = 0
        } else if (isAuthError || isAccessError || isConfigError || isShareNotFound) {
            // Configuration/authentication errors - don't increment timeout counter
            Timber.w("Fresh connection configuration error (not network issue): $errorMessage")
            // Don't track these as consecutive failures - they indicate user config issues
            // Auto-reset SMB connections to clear stale sessions
            val resetReason = when {
                isAuthError -> "Authentication failure on new connection"
                isAccessError -> "Access denied on new connection"
                isShareNotFound -> "Share not found on new connection"
                else -> "Configuration error on new connection"
            }
            autoResetIfNeeded(resetReason)
        } else {
            // Track real network timeouts and failures
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
     * Each resource is closed independently so a dead transport on one
     * step does not prevent the remaining resources from being released.
     */
    private fun removeConnection(key: ConnectionKey) {
        connectionPool.remove(key)?.let { pooled ->
            try { pooled.share.close() } catch (e: Exception) {
                Timber.w("SMB share close error (likely dead connection): ${e.message}")
            }
            try { pooled.session.close() } catch (e: Exception) {
                Timber.w("SMB session close error (likely dead connection): ${e.message}")
            }
            try { pooled.connection.close() } catch (e: Exception) {
                Timber.w("SMB connection close error (likely dead connection): ${e.message}")
            }
        }
    }
    
    /**
     * Close connection asynchronously (don't block caller).
     */
    private fun closeConnectionAsync(pooled: PooledConnection) {
        // Mark as pending close so it won't be reused and will be closed after current use
        pooled.isPendingClose.set(true)
        
        cleanupScope.launch {  // Use managed scope (ML-009)
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
     * Close all pooled connections asynchronously.
     * Uses closeConnectionAsync to avoid blocking the caller when the server
     * has already dropped connections (idle timeout scenario).
     */
    private fun closeAllConnections() {
        val keys = connectionPool.keys().toList()
        keys.forEach { key ->
            connectionPool.remove(key)?.let { pooled ->
                closeConnectionAsync(pooled)
            }
        }
        Timber.d("Initiated async close of ${keys.size} pooled connections")
    }

    private fun isNonRetriableConnectionError(e: Exception): Boolean {
        val message = buildString {
            append(e.message ?: "")
            append(' ')
            append(e.cause?.message ?: "")
            append(' ')
            append(e.cause?.cause?.message ?: "")
        }

        val isAuthError = message.contains("STATUS_LOGON_FAILURE", ignoreCase = true) ||
            message.contains("Authentication failed", ignoreCase = true) ||
            message.contains("Logon failure", ignoreCase = true) ||
            message.contains("wrong password", ignoreCase = true) ||
            message.contains("invalid credential", ignoreCase = true)

        val isAccessError = message.contains("STATUS_ACCESS_DENIED", ignoreCase = true) ||
            message.contains("Access denied", ignoreCase = true)

        // Share/path doesn't exist on server — retrying will never help
        val isShareNotFound = message.contains("STATUS_BAD_NETWORK_NAME", ignoreCase = true) ||
            message.contains("STATUS_BAD_NETWORK_PATH", ignoreCase = true) ||
            message.contains("STATUS_OBJECT_NAME_NOT_FOUND", ignoreCase = true)

        val isConfigError = message.contains("Unknown host", ignoreCase = true) ||
            message.contains("Connection refused", ignoreCase = true) ||
            message.contains("No such host", ignoreCase = true)

        // TCP pre-check timeouts (SocketTimeoutException / "Server unreachable") are transient —
        // a brief latency spike (ARP, NIC wake-up, NAT) can cause them even when the server is reachable.
        // Only hard config/auth/share errors are truly non-retriable.

        return isAuthError || isAccessError || isConfigError || isShareNotFound
    }

    /**
     * Returns true if [e] is a broken-pipe or transport-level socket failure.
     * Used to detect a stale SMBJ-cached Connection that must be evicted before a retry.
     * SMBJ's isConnected flag does not detect TCP-level silent drops, so we check exception types.
     */
    private fun isTransportOrBrokenPipe(e: Exception): Boolean {
        var current: Throwable? = e
        var depth = 0
        while (current != null && depth < 5) {
            if (current is java.net.SocketException ||
                current is com.hierynomus.protocol.transport.TransportException) {
                return true
            }
            val msg = current.message?.lowercase() ?: ""
            if (msg.contains("broken pipe") || msg.contains("connection reset") ||
                msg.contains("transport") && msg.contains("socket")) {
                return true
            }
            current = current.cause
            depth++
        }
        return false
    }

    /**
     * Invalidate and close the pooled ExoPlayer connection for [connectionInfo].
     * Call this from SmbDataSource.open when a share operation fails with a transport error
     * on a connection that previously passed isConnectionValid() — SMBJ's isConnected flag
     * does not detect TCP-level silent drops until an actual write attempt is made.
     */
    fun invalidateExoPlayerConnection(connectionInfo: SmbConnectionInfo) {
        val key = ConnectionKey(
            server = connectionInfo.server,
            port = connectionInfo.port,
            shareName = connectionInfo.shareName,
            username = connectionInfo.username,
            domain = connectionInfo.domain
        )
        val stale = connectionPool.remove(key) ?: return
        Timber.d("SmbConnectionManager: Invalidating stale ExoPlayer connection for ${connectionInfo.server}")
        // Close all layers so SMBJ's internal connection cache is also purged.
        // SMBJ's SMBClient keeps a map of Connection objects; closing the Connection removes it.
        try { stale.share.close() } catch (_: Exception) {}
        try { stale.session.close() } catch (_: Exception) {}
        try { stale.connection.close() } catch (_: Exception) {}
    }

    /**
     * Clear connection pool (public API).
     * Used when refreshing resources or on connection issues.
     */
    fun clearConnectionPool() {
        closeAllConnections()
    }
    
    /**
     * Set callback for auto-reset notifications.
     * Should be called from Application or ViewModel to show user feedback.
     */
    fun setResetCallback(callback: SmbResetCallback?) {
        resetCallback = callback
    }
    
    /**
     * Automatically reset SMB connections when needed (auth/config errors).
     * Uses cooldown to prevent frequent resets.
     * @param reason Human-readable reason for logging
     */
    private fun autoResetIfNeeded(reason: String) {
        val currentTime = System.currentTimeMillis()
        val timeSinceLastReset = currentTime - lastAutoResetTime
        
        if (timeSinceLastReset < AUTO_RESET_COOLDOWN_MS) {
            Timber.d("SmbConnectionManager: Auto-reset skipped (cooldown: ${timeSinceLastReset}ms / ${AUTO_RESET_COOLDOWN_MS}ms)")
            return
        }
        
        Timber.d("SmbConnectionManager: Auto-reset triggered - $reason")
        lastAutoResetTime = currentTime
        
        closeAllConnections()
        resetClients()
        ConnectionThrottleManager.resetAllSmbStates()
        consecutiveTimeouts = 0
        lastSuccessfulOperation = currentTime
        
        // Notify callback (for UI toast)
        resetCallback?.onAutoReset(reason)
        
        Timber.d("SmbConnectionManager: Auto-reset complete")
    }
    
    /**
     * Reset all SMB connections and clear error state.
     * Public API for manual recovery from connection issues.
     * Closes all connections, resets clients, and clears timeout counters.
     */
    fun resetAllConnections() {
        Timber.d("SmbConnectionManager: Manual reset requested")
        closeAllConnections()
        resetClients()
        ConnectionThrottleManager.resetAllSmbStates()
        consecutiveTimeouts = 0
        lastSuccessfulOperation = System.currentTimeMillis()
        lastAutoResetTime = System.currentTimeMillis() // Update to prevent immediate auto-reset after manual
        Timber.d("SmbConnectionManager: Reset complete")
    }
    
    /**
     * Reset SMB clients (force recreation on next use).
     */
    private fun resetClients() {
        try {
            normalClient?.close()
            mediumClient?.close()
            degradedClient?.close()
        } catch (e: Exception) {
            Timber.w(e, "Error closing SMB clients during reset")
        } finally {
            normalClient = null
            mediumClient = null
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

    /**
     * Fast check if host:port is reachable.
     * Throws IOException if unreachable within timeout.
     */
    private fun checkConnectivity(host: String, port: Int, timeoutMs: Int) {
        try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), timeoutMs)
            }
        } catch (e: Exception) {
            val msg = "Fast connectivity check failed to $host:$port after ${timeoutMs}ms"
            Timber.w(msg)
            // Rethrow as IOException with clear message for handling
            throw IOException("Server unreachable ($host:$port)", e)
        }
    }
    
    /**     * Convert exception to user-friendly error message.
     */
    private fun getUserFriendlyMessage(e: Exception): String {
        val cause = e.cause
        return when {
            e.message?.contains("Server unreachable", ignoreCase = true) == true ||
            cause is SocketTimeoutException ||
            e is SocketTimeoutException ->
                "Server is not responding. Make sure the device is powered on and reachable."
            e.message?.contains("Unknown host", ignoreCase = true) == true ->
                "Cannot resolve server address. Check server name/IP."
            e.message?.contains("Connection refused", ignoreCase = true) == true ->
                "Connection refused. Check if the SMB/file sharing service is running."
            e.message?.contains("Connection timed out", ignoreCase = true) == true ->
                "Connection timed out. Check network and server availability."
            e.message?.contains("Authentication failed", ignoreCase = true) == true ->
                "Authentication failed. Check username and password."
            e.message?.contains("Access denied", ignoreCase = true) == true ->
                "Access denied. Check share permissions."
            e is kotlinx.coroutines.TimeoutCancellationException ->
                "Operation timed out. Server may be overloaded or network slow."
            // SMBJ-level transaction timeout: server did not reply to QUERY_DIRECTORY / READ_ANDX
            // within the configured per-request window (CONNECTION_TIMEOUT_DEGRADED_MS).
            e.toString().contains("TimeoutException", ignoreCase = true) ||
            e.cause?.toString()?.contains("TimeoutException", ignoreCase = true) == true ->
                "Server is responding slowly. Directory listing timed out — try again or check server load."
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
        
        // Try pooled connection first.
        // Only reuse if the pool entry was created on the PLAYER path — scanner-sourced
        // connections have been observed hanging on the first SMB2 CREATE for ExoPlayer
        // (TCP socket silently dropped while idle between scan end and playback start).
        val pooled = connectionPool[key]
        if (pooled != null && pooled.consumer == ConnectionConsumer.PLAYER && isConnectionValid(pooled)) {
            pooled.lastUsed = System.currentTimeMillis()
            Timber.d("SmbConnectionManager: Reusing pooled connection for ExoPlayer")
            return pooled
        }
        if (pooled != null && pooled.consumer != ConnectionConsumer.PLAYER) {
            Timber.d("SmbConnectionManager: Pool entry tagged ${pooled.consumer} — forcing fresh PLAYER connection")
        }

        // Stale or absent pool entry: close it before fresh connect.
        // SMBJ's SMBClient internally caches Connection objects by host:port.
        // Closing the stale Connection purges it from that cache so the next
        // client.connect() establishes a genuine new TCP socket.
        if (pooled != null) {
            connectionPool.remove(key)
            Timber.d("SmbConnectionManager: Evicting stale pooled connection before fresh ExoPlayer connect")
            try { pooled.share.close() } catch (_: Exception) {}
            try { pooled.session.close() } catch (_: Exception) {}
            try { pooled.connection.close() } catch (_: Exception) {}
        }

        // Create fresh connection (blocking), retrying once on broken-pipe / transport errors.
        // Even after evicting our pool entry, SMBJ's SMBClient may still hold its own internal
        // reference to the stale Connection. If authenticate() fails because of a dead TCP socket,
        // we close that Connection (purging the SMBJ-internal cache) and retry, which forces a
        // new TCP handshake on the second attempt.
        Timber.d("SmbConnectionManager: Creating fresh connection for ExoPlayer")
        var lastException: Exception? = null
        var candidateConnection: Connection? = null
        for (attempt in 1..2) {
            candidateConnection = null
            try {
                val client = getClient(connectionInfo.server, connectionInfo.port)
                val connection = client.connect(connectionInfo.server, connectionInfo.port)
                candidateConnection = connection

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

                // Store in pool tagged as PLAYER so future getConnectionForExoPlayer calls
                // can reuse it (and scanner code path still opens its own SCANNER entry).
                val newPooled = PooledConnection(
                    connection = connection,
                    session = session,
                    share = share,
                    consumer = ConnectionConsumer.PLAYER
                )
                connectionPool[key] = newPooled

                onSuccess()
                return newPooled
            } catch (e: Exception) {
                lastException = e
                // On a transport/socket error the SMBJ-cached Connection is stale.
                // Close it so SMBJ evicts it from its internal map; the second attempt
                // will then establish a real new TCP socket.
                if (isTransportOrBrokenPipe(e) && attempt == 1) {
                    Timber.w("SmbConnectionManager: Broken pipe on ExoPlayer connect attempt $attempt — closing stale SMBJ connection and retrying")
                    try { candidateConnection?.close() } catch (_: Exception) {}
                    continue
                }
                Timber.e(e, "SmbConnectionManager: Failed to create connection for ExoPlayer (attempt $attempt)")
                break
            }
        }
        throw IOException("Failed to connect to SMB: ${lastException?.message}", lastException)
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
    
    /**eanupScope.coroutineContext.cancel()  // Cancel any pending cleanup tasks (ML-009)
        cl
     * Close all resources and cleanup.
     */
    fun close() {
        closeAllConnections()
        resetClients()
    }
}
