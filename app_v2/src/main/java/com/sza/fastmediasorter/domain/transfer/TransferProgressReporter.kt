package com.sza.fastmediasorter.domain.transfer

import android.os.SystemClock
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Percent is deliberately absent: the rendered figure needs a running ceiling and a file-count
 * fallback, so it stays in the single UI-side helper every consumer already calls rather than
 * existing here as a second formula.
 */
data class TransferProgressReport(
    val speedBytesPerSecond: Long,
    val shouldPublish: Boolean,
)

@Singleton
class TransferProgressReporter(
    private val elapsedRealtimeMs: () -> Long = SystemClock::elapsedRealtime,
) {

    @Inject
    constructor() : this(SystemClock::elapsedRealtime)

    private companion object {
        const val RATE_WINDOW_MS = 3_000L
        const val MILLIS_PER_SECOND = 1_000L
    }

    private val operationStates = ConcurrentHashMap<String, OperationState>()

    fun report(
        operationId: String,
        bytesTransferred: Long,
        totalBytes: Long,
        consumerKey: String,
        minimumPublishIntervalMs: Long,
        forcePublish: Boolean = false,
    ): TransferProgressReport {
        val nowMs = elapsedRealtimeMs()
        val state = operationStates.computeIfAbsent(operationId) { OperationState() }
        return synchronized(state) {
            state.record(bytesTransferred, nowMs)
            val isTerminal = totalBytes > 0L && bytesTransferred >= totalBytes
            val shouldPublish = forcePublish || isTerminal || state.publishElapsed(
                consumerKey,
                nowMs,
                minimumPublishIntervalMs,
            )
            if (shouldPublish) {
                state.recordPublication(consumerKey, nowMs)
            }
            val report = TransferProgressReport(
                speedBytesPerSecond = state.speedBytesPerSecond(),
                shouldPublish = shouldPublish,
            )
            if (isTerminal) {
                operationStates.remove(operationId, state)
            }
            report
        }
    }

    fun clear(operationId: String) {
        operationStates.remove(operationId)
    }

    private class OperationState {
        private val samples = ArrayDeque<Sample>()
        private val lastPublications = mutableMapOf<String, Long>()

        fun record(bytesTransferred: Long, nowMs: Long) {
            if (samples.lastOrNull()?.bytesTransferred != bytesTransferred) {
                samples.addLast(Sample(bytesTransferred, nowMs))
            }
            val cutoffMs = nowMs - RATE_WINDOW_MS
            while (samples.size > 1 && samples.first().timeMs < cutoffMs) {
                samples.removeFirst()
            }
        }

        fun publishElapsed(consumerKey: String, nowMs: Long, minimumPublishIntervalMs: Long): Boolean {
            val lastPublishedMs = lastPublications[consumerKey] ?: return true
            return nowMs - lastPublishedMs >= minimumPublishIntervalMs
        }

        fun recordPublication(consumerKey: String, nowMs: Long) {
            lastPublications[consumerKey] = nowMs
        }

        fun speedBytesPerSecond(): Long {
            val first = samples.firstOrNull() ?: return 0L
            val last = samples.last()
            val elapsedMs = last.timeMs - first.timeMs
            // A restarted file, or a path that reports a fresh byte counter under the same operation
            // id, makes the window run backwards; a negative rate must never reach a consumer as one.
            return if (elapsedMs > 0L) {
                ((last.bytesTransferred - first.bytesTransferred) * MILLIS_PER_SECOND / elapsedMs)
                    .coerceAtLeast(0L)
            } else {
                0L
            }
        }
    }

    private data class Sample(val bytesTransferred: Long, val timeMs: Long)
}
