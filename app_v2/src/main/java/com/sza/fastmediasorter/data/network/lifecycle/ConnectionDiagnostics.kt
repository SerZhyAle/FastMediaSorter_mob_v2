package com.sza.fastmediasorter.data.network.lifecycle

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Aggregates per-resource connection events (recreate / success / failure) and emits
 * [InstabilityWarning] when a single resource crosses [INSTABILITY_THRESHOLD] recreates
 * inside a sliding [INSTABILITY_WINDOW_MS] window. S0067.
 */
@Singleton
class ConnectionDiagnostics @Inject constructor() {

    /**
     * Event surfaced once per `(protocol, resourceKey)` per [INSTABILITY_WINDOW_MS] when the
     * recreate count crosses [INSTABILITY_THRESHOLD]. UI surface throttles toasts above this
     * via its own per-key cooldown (S0067 phase 06).
     */
    data class InstabilityWarning(
        val protocol: NetworkProtocol,
        val resourceKey: String,
        val recreateCount: Int
    )

    private val recreateLog = ConcurrentHashMap<String, ArrayDeque<Long>>()

    private val _events = MutableSharedFlow<InstabilityWarning>(
        replay = 0,
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val events: SharedFlow<InstabilityWarning> = _events.asSharedFlow()

    fun recordRecreate(
        protocol: NetworkProtocol,
        resourceKey: String,
        reason: TransientFailure.Reason
    ) {
        Timber.i("[scope=connection protocol=$protocol resource=$resourceKey reason=$reason action=recreate]")
        val now = System.currentTimeMillis()
        val deque = recreateLog.getOrPut(keyFor(protocol, resourceKey)) { ArrayDeque() }
        synchronized(deque) {
            deque.addLast(now)
            // Drop entries outside the sliding window.
            while (deque.isNotEmpty() && now - deque.first() > INSTABILITY_WINDOW_MS) {
                deque.removeFirst()
            }
            if (deque.size >= INSTABILITY_THRESHOLD) {
                _events.tryEmit(InstabilityWarning(protocol, resourceKey, deque.size))
            }
        }
    }

    fun recordSuccess(protocol: NetworkProtocol, resourceKey: String) {
        Timber.v("[scope=connection protocol=$protocol resource=$resourceKey action=success]")
    }

    fun recordFailure(
        protocol: NetworkProtocol,
        resourceKey: String,
        reason: TransientFailure.Reason?
    ) {
        Timber.w("[scope=connection protocol=$protocol resource=$resourceKey reason=${reason ?: "non-transient"} action=fail]")
    }

    private fun keyFor(protocol: NetworkProtocol, resourceKey: String): String =
        "$protocol|$resourceKey"

    companion object {
        private const val INSTABILITY_WINDOW_MS = 5L * 60 * 1000  // 5 minutes
        private const val INSTABILITY_THRESHOLD = 3
    }
}
