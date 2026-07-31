package com.sza.fastmediasorter.domain.transfer

import android.os.SystemClock
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

data class TransferProgressReport(
    val percent: Int?,
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
        const val PERCENT_SCALE = 100L
        const val PERCENT_MAX = 100
        const val RATE_WINDOW_MS = 3_000L
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
                percent = totalBytes.takeIf { it > 0L }?.let {
                    (bytesTransferred * PERCENT_SCALE / it).toInt().coerceIn(0, PERCENT_MAX)
                },
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
            val last = samples.lastOrNull() ?: return 0L
            val elapsedMs = last.timeMs - first.timeMs
            if (elapsedMs <= 0L) return 0L
            return (last.bytesTransferred - first.bytesTransferred) * 1_000L / elapsedMs
        }
    }

    private data class Sample(val bytesTransferred: Long, val timeMs: Long)
}
