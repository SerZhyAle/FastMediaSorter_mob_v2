package com.sza.fastmediasorter.core.logging

import java.util.UUID

/**
 * Thread-local correlation context for tracing a logical operation
 * across multiple layers (UI → UseCase → Repository → Network).
 *
 * Usage:
 * ```
 * CorrelationContext.start("copy-file") {
 *     // All Timber logs inside this block can access the correlation ID
 *     fileOperationUseCase.copy(…)
 * }
 * ```
 */
object CorrelationContext {

    @PublishedApi
    internal val threadLocal = ThreadLocal<ContextData>()

    /** Currently active context on this thread, or `null`. */
    val current: ContextData?
        get() = threadLocal.get()

    /** Convenience: current correlation ID or empty string. */
    val id: String
        get() = current?.correlationId ?: ""

    /**
     * Start a new correlation scope. Nesting is supported —
     * the outer scope is restored when the inner scope ends.
     *
     * @param operation Human-readable label (e.g. "copy-file", "smb-list")
     * @param extras Additional key-value pairs attached to every log in this scope.
     */
    inline fun <T> start(
        operation: String,
        extras: Map<String, String> = emptyMap(),
        block: () -> T
    ): T {
        val parent = threadLocal.get()
        val ctx = ContextData(
            correlationId = parent?.correlationId ?: UUID.randomUUID().toString().take(8),
            operation = operation,
            extras = parent?.extras.orEmpty() + extras,
            depth = (parent?.depth ?: 0) + 1
        )
        threadLocal.set(ctx)
        try {
            return block()
        } finally {
            if (parent != null) threadLocal.set(parent) else threadLocal.remove()
        }
    }

    /**
     * Suspend-compatible variant using coroutine context would go here;
     * for now the thread-local approach covers most use cases since
     * Timber calls happen on the calling thread.
     */

    data class ContextData(
        val correlationId: String,
        val operation: String,
        val extras: Map<String, String> = emptyMap(),
        val depth: Int = 1
    ) {
        /** Compact prefix for log lines: `[abc123|copy-file]` */
        val prefix: String
            get() = "[$correlationId|$operation]"
    }
}
