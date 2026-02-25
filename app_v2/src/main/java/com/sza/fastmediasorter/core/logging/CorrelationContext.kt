package com.sza.fastmediasorter.core.logging

import kotlinx.coroutines.asContextElement
import java.util.UUID
import kotlin.coroutines.CoroutineContext

/**
 * Thread-local correlation context for tracing a logical operation
 * across multiple layers (UI → UseCase → Repository → Network).
 *
 * Usage:
 * ```
 * // For synchronous code:
 * CorrelationContext.start("copy-file") {
 *     fileOperationUseCase.copy(…)
 * }
 *
 * // For coroutines:
 * withContext(CorrelationContext.asContextElement("smb-scan")) {
 *     repository.scan()
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
     * Start a new correlation scope for synchronous code.
     */
    inline fun <T> start(
        operation: String,
        extras: Map<String, String> = emptyMap(),
        block: () -> T
    ): T {
        val parent = threadLocal.get()
        val ctx = createChildContext(parent, operation, extras)
        threadLocal.set(ctx)
        try {
            return block()
        } finally {
            if (parent != null) threadLocal.set(parent) else threadLocal.remove()
        }
    }

    /**
     * Propagate the current (or new) correlation context into a coroutine's context.
     * Use with `withContext()`.
     */
    fun asContextElement(
        operation: String,
        extras: Map<String, String> = emptyMap()
    ): CoroutineContext {
        val parent = threadLocal.get()
        val ctx = createChildContext(parent, operation, extras)
        return threadLocal.asContextElement(ctx)
    }

    @PublishedApi
    internal fun createChildContext(
        parent: ContextData?,
        operation: String,
        extras: Map<String, String>
    ): ContextData {
        return ContextData(
            correlationId = parent?.correlationId ?: UUID.randomUUID().toString().take(8),
            operation = operation,
            extras = parent?.extras.orEmpty() + extras,
            depth = (parent?.depth ?: 0) + 1
        )
    }

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
