package com.sza.fastmediasorter.data.network

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Manages dynamic parallelism limits for network protocols.
 * Limits adjust based on protocol type and connection health.
 * 
 * Usage:
 * ```
 * ConnectionThrottleManager.withThrottle(ProtocolLimits.SMB, "192.168.1.10") {
 *     smbClient.readFileBytes(...)
 * }
 * ```
 */
object ConnectionThrottleManager {
    
    /**
     * Base parallelism limits per protocol type.
     * Each protocol has max (initial) and min (degraded) concurrent request limits.
     */
    enum class ProtocolLimits(val maxConcurrent: Int, val minConcurrent: Int) {
        LOCAL(24, 24),        // No throttling for local files
        SMB(2, 1),            // Reduced from 3→2: further limit to prevent ArrayIndexOutOfBoundsException
        SFTP(3, 1),           // Increased from 2→3: channel pooling prevents race conditions
        FTP(2, 1),            // Reduced from 3→2: align with SMB for consistency
        CLOUD(8, 3)           // Cloud APIs usually handle batching well
    }
    
    /**
     * Connection state tracking per resource
     */
    private data class ProtocolState(
        var currentLimit: Int,
        val consecutiveTimeouts: AtomicInteger = AtomicInteger(0),
        val consecutiveSuccesses: AtomicInteger = AtomicInteger(0),
        var isDegraded: Boolean = false,  // Tracks if protocol is in degraded state with extended timeouts
        val activeTasks: AtomicInteger = AtomicInteger(0)  // Track active operations
    )
    
    private val protocolStates = ConcurrentHashMap<String, ProtocolState>()
    private val semaphores = ConcurrentHashMap<String, Semaphore>()
    
    // Lock for semaphore recreation
    private val semaphoreLocks = ConcurrentHashMap<String, Any>()
    
    // User defined limit for network protocols (SMB, FTP, SFTP)
    // Reduced default from 2 to match new base limits
    private var userDefinedNetworkLimit: Int = 2
    
    // Cache for speed test recommended threads per resource
    private val recommendedThreadsCache = ConcurrentHashMap<String, Int>()
    
    // Cache for speed test recommended buffer size per resource
    private val recommendedBufferSizeCache = ConcurrentHashMap<String, Int>()
    
    // Video player priority mode: suspends thumbnail loading to prioritize video streaming
    @Volatile
    private var videoPlayerActive = false
    private val videoPlayerResources = mutableSetOf<String>()
    private var videoPlayerResumeJob: Job? = null
    private val managerScope = CoroutineScope(Dispatchers.Default + Job())

    /**
     * Update the user-defined network parallelism limit.
     * Clears current states to apply new limit immediately.
     */
    fun setUserNetworkLimit(limit: Int) {
        if (userDefinedNetworkLimit != limit) {
            userDefinedNetworkLimit = limit
            // Clear states to force refresh on next usage
            protocolStates.clear()
            semaphores.clear()
            semaphoreLocks.clear()
            Timber.i("ConnectionThrottle: User network limit set to $limit. States cleared.")
        }
    }
    
    /**
     * Get the current user-defined network parallelism limit.
     * Used by LocalMediaScanner for SAF parallel scanning.
     */
    fun getUserNetworkLimit(): Int = userDefinedNetworkLimit
    
    /**
     * Set recommended threads for a resource from speed test results.
     * @param resourceKey Resource identifier (e.g., "ftp://193.178.50.43:21")
     * @param threads Recommended number of concurrent threads
     */
    fun setRecommendedThreads(resourceKey: String, threads: Int) {
        recommendedThreadsCache[resourceKey] = threads
        Timber.d("ConnectionThrottle: Set recommended threads for $resourceKey = $threads")
        // Clear state to apply new limit on next usage
        protocolStates.remove(resourceKey)
        semaphores.remove(resourceKey)
    }
    
    /**
     * Get recommended threads for a resource.
     * @return Recommended threads or null if not set
     */
    fun getRecommendedThreads(resourceKey: String): Int? {
        return recommendedThreadsCache[resourceKey]
    }

    /**
     * Set recommended buffer size for a resource.
     */
    fun setRecommendedBufferSize(resourceKey: String, bufferSize: Int) {
        recommendedBufferSizeCache[resourceKey] = bufferSize
        Timber.d("ConnectionThrottle: Set recommended buffer size for $resourceKey = ${bufferSize / 1024} KB")
    }

    /**
     * Get recommended buffer size for a resource.
     * Returns default 64KB if not set.
     */
    fun getRecommendedBufferSize(resourceKey: String, defaultSize: Int = 64 * 1024): Int {
        return recommendedBufferSizeCache[resourceKey] ?: defaultSize
    }
    
    /**
     * Activate video player priority mode for a resource.
     * Suspends all low-priority operations (thumbnails) for this resource.
     * @param resourceKey Resource identifier (e.g., "smb://192.168.1.10:445")
     */
    fun activateVideoPlayerMode(resourceKey: String) {
        synchronized(videoPlayerResources) {
            videoPlayerResources.add(resourceKey)
            videoPlayerActive = true
            Timber.i("ConnectionThrottle: *** VIDEO PLAYER ACTIVATED for $resourceKey - SUSPENDING THUMBNAILS ***")
        }
    }
    
    /**
     * Deactivate video player priority mode for a resource.
     * Resumes normal operation for thumbnails AFTER 300ms delay to prevent race conditions.
     * @param resourceKey Resource identifier
     */
    fun deactivateVideoPlayerMode(resourceKey: String) {
        synchronized(videoPlayerResources) {
            videoPlayerResources.remove(resourceKey)
            if (videoPlayerResources.isEmpty()) {
                // Cancel any pending resume
                videoPlayerResumeJob?.cancel()
                
                Timber.i("ConnectionThrottle: *** VIDEO PLAYER DEACTIVATED for $resourceKey - RESUMING THUMBNAILS IN 300ms ***")
                
                // Add 300ms delay before resuming thumbnails to allow FTP connections to fully close
                // This prevents race condition where Glide tries to load thumbnails while
                // FTP connections are still in process of being released (prevents SIGSEGV)
                videoPlayerResumeJob = managerScope.launch {
                    delay(300)
                    videoPlayerActive = false
                    Timber.i("ConnectionThrottle: *** THUMBNAILS RESUMED after delay ***")
                }
            }
        }
    }
    
    /**
     * Check if video player is active for any resource.
     */
    fun isVideoPlayerActive(): Boolean = videoPlayerActive

    /**
     * Check if the connection is congested (degraded state or high active task count).
     * Used by prefetch systems to reduce lookahead depth under pressure.
     *
     * @param resourceKey Resource identifier (e.g., "smb://192.168.1.10:445")
     * @return true if network pressure is high and prefetch should be conservative
     */
    fun isCongested(resourceKey: String): Boolean {
        val state = protocolStates[resourceKey] ?: return false
        // Congested if: degraded OR active tasks >= 80% of limit
        val threshold = (state.currentLimit * 0.8).toInt().coerceAtLeast(1)
        return state.isDegraded || state.activeTasks.get() >= threshold
    }

    /**
     * Check if any network resource is congested.
     * Convenience method for global congestion check.
     */
    fun isAnyCongested(): Boolean {
        return protocolStates.values.any { state ->
            val threshold = (state.currentLimit * 0.8).toInt().coerceAtLeast(1)
            state.isDegraded || state.activeTasks.get() >= threshold
        }
    }

    private fun getMaxLimit(protocol: ProtocolLimits, resourceKey: String): Int {
        return when (protocol) {
            ProtocolLimits.SMB, ProtocolLimits.SFTP, ProtocolLimits.FTP, ProtocolLimits.CLOUD -> {
                // Use speed test recommended threads if available
                recommendedThreadsCache[resourceKey] ?: userDefinedNetworkLimit
            }
            else -> protocol.maxConcurrent
        }
    }

    private fun getMinLimit(protocol: ProtocolLimits, resourceKey: String): Int {
        return when (protocol) {
            ProtocolLimits.SMB, ProtocolLimits.SFTP, ProtocolLimits.FTP, ProtocolLimits.CLOUD -> {
                val maxLimit = getMaxLimit(protocol, resourceKey)
                (maxLimit / 2).coerceAtLeast(1)
            }
            else -> protocol.minConcurrent
        }
    }
    
    // Degradation/restoration thresholds
    private const val DEGRADE_AFTER_TIMEOUTS = 3       // Reduce limit after 3 consecutive timeouts
    private const val RESTORE_AFTER_SUCCESSES = 10     // Increase limit after 10 consecutive successes
    
    /**
     * Get or create protocol state for resource
     */
    private fun getState(protocol: ProtocolLimits, resourceKey: String): ProtocolState {
        return protocolStates.getOrPut(resourceKey) {
            ProtocolState(currentLimit = getMaxLimit(protocol, resourceKey))
        }
    }
    
    /**
     * Check if protocol is in degraded state (for timeout adjustment).
     * @param protocol Protocol type
     * @param resourceKey Unique resource identifier
     * @return true if protocol is degraded and should use extended timeouts
     */
    @Suppress("UNUSED_PARAMETER")
    fun isDegraded(protocol: ProtocolLimits, resourceKey: String): Boolean {
        return protocolStates[resourceKey]?.isDegraded ?: false
    }
    
    /**
     * Get or create semaphore for resource with current limit.
     * Thread-safe: uses lock per resource to prevent concurrent recreation.
     */
    private fun getSemaphoreAndLock(resourceKey: String, state: ProtocolState): Pair<Semaphore, Any> {
        val lock = semaphoreLocks.getOrPut(resourceKey) { Any() }
        
        synchronized(lock) {
            val semaphore = semaphores[resourceKey]
            
            // Recreate semaphore if limit changed and no active tasks
            if (semaphore == null) {
                val newSemaphore = Semaphore(state.currentLimit)
                semaphores[resourceKey] = newSemaphore
                Timber.d("ConnectionThrottle: Created semaphore for $resourceKey with limit ${state.currentLimit}")
                return newSemaphore to lock
            }
            
            // Check if limit changed
            val currentPermits = semaphore.availablePermits + state.activeTasks.get()
            if (currentPermits != state.currentLimit) {
                // Limit changed - recreate semaphore when safe
                if (state.activeTasks.get() == 0) {
                    val newSemaphore = Semaphore(state.currentLimit)
                    semaphores[resourceKey] = newSemaphore
                    Timber.d("ConnectionThrottle: Recreated semaphore for $resourceKey with new limit ${state.currentLimit} (was $currentPermits)")
                    return newSemaphore to lock
                } else {
                    Timber.w("ConnectionThrottle: Cannot change $resourceKey limit to ${state.currentLimit} (${state.activeTasks.get()} active tasks, current=$currentPermits)")
                }
            }
            
            return semaphore to lock
        }
    }
    
    /**
     * Execute operation with dynamic throttling based on protocol and connection health.
     * 
     * @param protocol Protocol type (LOCAL/SMB/SFTP/FTP/CLOUD)
     * @param resourceKey Unique key for resource (e.g., "smb://192.168.1.10:445/share")
     * @param highPriority If true, bypasses low-priority operations (e.g., PlayerActivity vs thumbnails)
     * @param operation Suspend function to execute with throttling
     * @return Result of operation
     * @throws Exception propagates exceptions from operation
     */
    suspend fun <T> withThrottle(
        protocol: ProtocolLimits,
        resourceKey: String,
        highPriority: Boolean = false,
        operation: suspend () -> T
    ): T {
        // LOCAL protocol: no throttling
        if (protocol == ProtocolLimits.LOCAL) {
            return operation()
        }
        
        // Block low-priority operations when video player is active for this resource
        if (!highPriority && videoPlayerActive) {
            synchronized(videoPlayerResources) {
                if (videoPlayerResources.contains(resourceKey)) {
                    Timber.d("ConnectionThrottle: BLOCKED low-priority request for $resourceKey (video player active)")
                    throw kotlinx.coroutines.CancellationException("Video player priority - thumbnail loading suspended")
                }
            }
        }
        
        val state = getState(protocol, resourceKey)
        val (semaphore, _) = getSemaphoreAndLock(resourceKey, state)
        
        // Log wait status for debugging
        val availablePermits = semaphore.availablePermits
        val activeTasks = state.activeTasks.get()
        if (availablePermits == 0) {
            Timber.d("ConnectionThrottle: WAITING for $resourceKey (active=$activeTasks, limit=${state.currentLimit})")
        }
        
        // Track whether we actually acquired a permit (to avoid double-release)
        val permitAcquired: Boolean
        
        // High-priority requests (UI) should never block behind low-priority ones (background)
        if (highPriority) {
            // Try to acquire a permit if available to respect limits when possible
            permitAcquired = semaphore.tryAcquire()
            if (!permitAcquired) {
                // If no permits, we BYPASS the limit to ensure UI responsiveness.
                // We do NOT acquire a permit and do NOT release later.
                Timber.d("ConnectionThrottle: High-priority request BYPASSING LIMIT for $resourceKey (limit=${state.currentLimit})")
            }
        } else {
            // Low-priority requests must wait for a permit
            semaphore.acquire()
            permitAcquired = true
        }
        
        state.activeTasks.incrementAndGet()
        
        try {
            val result = operation()
            
            // Success: increment success counter, reset timeout counter
            state.consecutiveTimeouts.set(0)
            val successes = state.consecutiveSuccesses.incrementAndGet()
            
            // Restore limit if enough consecutive successes
            val maxLimit = getMaxLimit(protocol, resourceKey)
            if (successes >= RESTORE_AFTER_SUCCESSES && state.currentLimit < maxLimit) {
                synchronized(state) {
                    if (state.currentLimit < maxLimit) {
                        state.currentLimit++
                        state.consecutiveSuccesses.set(0)
                        state.isDegraded = false  // Restore normal timeouts
                        Timber.i("ConnectionThrottle: Restored $resourceKey limit to ${state.currentLimit} (${successes} successes) - NORMAL TIMEOUTS RESTORED")
                    }
                }
            }
            
            return result
        } catch (e: Exception) {
            // Check if timeout/network error
            val isTimeout = e is kotlinx.coroutines.TimeoutCancellationException ||
                            e.cause is java.net.SocketTimeoutException ||
                            e.message?.contains("timeout", ignoreCase = true) == true
            
            if (isTimeout) {
                // Timeout: increment timeout counter, reset success counter
                state.consecutiveSuccesses.set(0)
                val timeouts = state.consecutiveTimeouts.incrementAndGet()
                
                // Degrade limit if enough consecutive timeouts
                val minLimit = getMinLimit(protocol, resourceKey)
                if (timeouts >= DEGRADE_AFTER_TIMEOUTS && state.currentLimit > minLimit) {
                    synchronized(state) {
                        if (state.currentLimit > minLimit) {
                            state.currentLimit--
                            state.consecutiveTimeouts.set(0)
                            state.isDegraded = true  // Mark as degraded for extended timeouts
                            Timber.w("ConnectionThrottle: Degraded $resourceKey limit to ${state.currentLimit} (${timeouts} timeouts) - EXTENDED TIMEOUTS ENABLED")
                        }
                    }
                }
            }
            
            throw e
        } finally {
            state.activeTasks.decrementAndGet()
            // Only release if we actually acquired a permit
            if (permitAcquired) {
                semaphore.release()
            }
        }
    }
    
    /**
     * Get current limit for resource (for debugging/monitoring)
     */
    fun getCurrentLimit(resourceKey: String): Int? {
        return protocolStates[resourceKey]?.currentLimit
    }
    
    /**
     * Get count of currently active operations for a resource.
     */
    fun getActiveTaskCount(resourceKey: String): Int {
        return protocolStates[resourceKey]?.activeTasks?.get() ?: 0
    }
    
    /**
     * Force reset all connections for a resource.
     * Use when network appears stalled (e.g., image taking too long to load).
     * This will:
     * 1. Cancel all active operations
     * 2. Reset semaphore to initial state
     * 3. Clear degraded state
     * 
     * @param resourceKey Resource identifier (e.g., "smb://192.168.1.10:445")
     * @return Number of operations that were cancelled
     */
    fun forceResetConnections(resourceKey: String): Int {
        val state = protocolStates[resourceKey]
        val cancelledCount = state?.activeTasks?.getAndSet(0) ?: 0
        
        if (cancelledCount > 0) {
            Timber.w("ConnectionThrottle: FORCE RESET for $resourceKey - cancelled $cancelledCount operations")
        }
        
        // Remove state and semaphore to force fresh start
        protocolStates.remove(resourceKey)
        semaphores.remove(resourceKey)
        semaphoreLocks.remove(resourceKey)
        
        Timber.i("ConnectionThrottle: Reset complete for $resourceKey")
        return cancelledCount
    }

    fun resetAllSmbStates() {
        val smbKeys = protocolStates.keys.filter { it.startsWith("smb://", ignoreCase = true) }
        smbKeys.forEach { key ->
            protocolStates.remove(key)
            semaphores.remove(key)
            semaphoreLocks.remove(key)
            recommendedThreadsCache.remove(key)
            recommendedBufferSizeCache.remove(key)
            synchronized(videoPlayerResources) {
                videoPlayerResources.remove(key)
                if (videoPlayerResources.isEmpty()) {
                    videoPlayerActive = false
                }
            }
        }
        if (smbKeys.isNotEmpty()) {
            Timber.i("ConnectionThrottle: Reset SMB states for ${smbKeys.size} resources")
        }
    }
    
    /**
     * Cancel all active operations for a specific resource.
     * Used when closing Browse or switching resources to abort pending thumbnail loads.
     * 
     * @param resourceKey Resource identifier (e.g., "smb://192.168.1.10:445")
     */
    fun cancelAllForResource(resourceKey: String) {
        protocolStates[resourceKey]?.let { state ->
            val activeCount = state.activeTasks.get()
            if (activeCount > 0) {
                Timber.d("ConnectionThrottle: Cancelling $activeCount active operations for $resourceKey")
                // Active tasks will be cancelled by their coroutine scopes
                // We just reset the counter and state
                state.activeTasks.set(0)
            }
        }
        // Also reset protocol state to clean start
        resetState(resourceKey, ProtocolLimits.SMB) // Protocol will be re-determined on next use
    }
    
    /**
     * Reset state for resource (e.g., when resource reconnects)
     */
    @Suppress("UNUSED_PARAMETER")
    fun resetState(resourceKey: String, protocol: ProtocolLimits) {
        protocolStates.remove(resourceKey)
        semaphores.remove(resourceKey)
        Timber.d("ConnectionThrottle: Reset state for $resourceKey")
    }
}
