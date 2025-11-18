package com.sza.fastmediasorter.data.network

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
        SMB(6, 2),            // Start at 6, degrade to 2 on repeated failures
        SFTP(3, 1),           // Conservative: SSH overhead + single TCP connection
        FTP(3, 1),            // Conservative: FTP control/data channel multiplexing
        CLOUD(8, 3)           // Cloud APIs usually handle batching well
    }
    
    /**
     * Connection state tracking per resource
     */
    private data class ProtocolState(
        var currentLimit: Int,
        val consecutiveTimeouts: AtomicInteger = AtomicInteger(0),
        val consecutiveSuccesses: AtomicInteger = AtomicInteger(0),
        var isDegraded: Boolean = false  // Tracks if protocol is in degraded state with extended timeouts
    )
    
    private val protocolStates = ConcurrentHashMap<String, ProtocolState>()
    private val semaphores = ConcurrentHashMap<String, Semaphore>()
    
    // Degradation/restoration thresholds
    private const val DEGRADE_AFTER_TIMEOUTS = 3       // Reduce limit after 3 consecutive timeouts
    private const val RESTORE_AFTER_SUCCESSES = 10     // Increase limit after 10 consecutive successes
    
    /**
     * Get or create protocol state for resource
     */
    private fun getState(protocol: ProtocolLimits, resourceKey: String): ProtocolState {
        return protocolStates.getOrPut(resourceKey) {
            ProtocolState(currentLimit = protocol.maxConcurrent)
        }
    }
    
    /**
     * Check if protocol is in degraded state (for timeout adjustment).
     * @param protocol Protocol type
     * @param resourceKey Unique resource identifier
     * @return true if protocol is degraded and should use extended timeouts
     */
    fun isDegraded(protocol: ProtocolLimits, resourceKey: String): Boolean {
        return protocolStates[resourceKey]?.isDegraded ?: false
    }
    
    /**
     * Get or create semaphore for resource with current limit
     */
    private fun getSemaphore(resourceKey: String, permits: Int): Semaphore {
        return semaphores.getOrPut(resourceKey) {
            Semaphore(permits)
        }
    }
    
    /**
     * Execute operation with dynamic throttling based on protocol and connection health.
     * 
     * @param protocol Protocol type (LOCAL/SMB/SFTP/FTP/CLOUD)
     * @param resourceKey Unique key for resource (e.g., "smb://192.168.1.10:445/share")
     * @param operation Suspend function to execute with throttling
     * @return Result of operation
     * @throws Exception propagates exceptions from operation
     */
    suspend fun <T> withThrottle(
        protocol: ProtocolLimits,
        resourceKey: String,
        operation: suspend () -> T
    ): T {
        // LOCAL protocol: no throttling
        if (protocol == ProtocolLimits.LOCAL) {
            return operation()
        }
        
        val state = getState(protocol, resourceKey)
        val semaphore = getSemaphore(resourceKey, state.currentLimit)
        
        // Adjust semaphore if limit changed
        synchronized(state) {
            val currentPermits = semaphore.availablePermits
            val targetPermits = state.currentLimit
            
            if (currentPermits < targetPermits) {
                // Increase permits (restoration)
                val toAdd = targetPermits - currentPermits
                repeat(toAdd) { semaphore.release() }
                Timber.d("ConnectionThrottle: Increased $resourceKey limit to ${state.currentLimit}")
            } else if (currentPermits > targetPermits) {
                // Decrease permits (degradation) - drain excess
                val toDrain = currentPermits - targetPermits
                repeat(toDrain) { 
                    if (semaphore.tryAcquire()) {
                        // Successfully acquired, don't release
                    }
                }
                Timber.d("ConnectionThrottle: Decreased $resourceKey limit to ${state.currentLimit}")
            }
        }
        
        semaphore.acquire()
        var permitReleased = false
        try {
            val result = operation()
            
            // Success: increment success counter, reset timeout counter
            state.consecutiveTimeouts.set(0)
            val successes = state.consecutiveSuccesses.incrementAndGet()
            
            // Restore limit if enough consecutive successes
            if (successes >= RESTORE_AFTER_SUCCESSES && state.currentLimit < protocol.maxConcurrent) {
                synchronized(state) {
                    if (state.currentLimit < protocol.maxConcurrent) {
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
                if (timeouts >= DEGRADE_AFTER_TIMEOUTS && state.currentLimit > protocol.minConcurrent) {
                    synchronized(state) {
                        if (state.currentLimit > protocol.minConcurrent) {
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
            if (!permitReleased) {
                permitReleased = true
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
     * Reset state for resource (e.g., when resource reconnects)
     */
    fun resetState(resourceKey: String, protocol: ProtocolLimits) {
        protocolStates.remove(resourceKey)
        semaphores.remove(resourceKey)
        Timber.d("ConnectionThrottle: Reset state for $resourceKey")
    }
}
