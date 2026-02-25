package com.sza.fastmediasorter.core.logging

import timber.log.Timber

/**
 * Structured logger that automatically prepends [CorrelationContext] info
 * to every log message. Use this instead of raw `Timber` calls in code
 * that participates in traced operations (file transfers, network I/O, etc.).
 *
 * ```
 * CorrelationContext.start("smb-list") {
 *     StructuredLogger.d("Listing files", "path" to "/share/music")
 *     //  D/StructuredLogger: [a1b2c3d4|smb-list] Listing files {path=/share/music}
 * }
 * ```
 */
object StructuredLogger {

    private const val TAG = "SLog"

    fun v(message: String, vararg attrs: Pair<String, Any?>) =
        log(Timber.Forest::v, message, attrs)

    fun d(message: String, vararg attrs: Pair<String, Any?>) =
        log(Timber.Forest::d, message, attrs)

    fun i(message: String, vararg attrs: Pair<String, Any?>) =
        log(Timber.Forest::i, message, attrs)

    fun w(message: String, vararg attrs: Pair<String, Any?>) =
        log(Timber.Forest::w, message, attrs)

    fun w(throwable: Throwable, message: String, vararg attrs: Pair<String, Any?>) =
        logWithThrowable(Timber.Forest::w, throwable, message, attrs)

    fun e(message: String, vararg attrs: Pair<String, Any?>) =
        log(Timber.Forest::e, message, attrs)

    fun e(throwable: Throwable, message: String, vararg attrs: Pair<String, Any?>) =
        logWithThrowable(Timber.Forest::e, throwable, message, attrs)

    // ── internals ───────────────────────────────────────────────────

    private inline fun log(
        logFn: (String, Array<out Any?>) -> Unit,
        message: String,
        attrs: Array<out Pair<String, Any?>>
    ) {
        Timber.tag(TAG)
        logFn(format(message, attrs), emptyArray())
    }

    private inline fun logWithThrowable(
        logFn: (Throwable, String, Array<out Any?>) -> Unit,
        throwable: Throwable,
        message: String,
        attrs: Array<out Pair<String, Any?>>
    ) {
        Timber.tag(TAG)
        logFn(throwable, format(message, attrs), emptyArray())
    }

    private fun format(message: String, attrs: Array<out Pair<String, Any?>>): String {
        val sb = StringBuilder()

        // Correlation prefix
        CorrelationContext.current?.let { ctx ->
            sb.append(ctx.prefix).append(' ')
        }

        sb.append(message)

        // Key=value attributes
        if (attrs.isNotEmpty()) {
            sb.append(" {")
            attrs.joinTo(sb, ", ") { (k, v) -> "$k=$v" }
            sb.append('}')
        }

        return sb.toString()
    }
}
