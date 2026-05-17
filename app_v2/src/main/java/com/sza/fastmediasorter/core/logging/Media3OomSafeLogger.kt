package com.sza.fastmediasorter.core.logging

import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

/**
 * OOM-safe wrapper for media3 logging (S0213 §5.1 Pillar B).
 *
 * Replaces the default [androidx.media3.common.util.Log.Logger] installed by media3 so that a
 * near-OOM stacktrace stringification inside ExoPlayer cannot itself become the killing
 * OutOfMemoryError. Two layered defenses:
 *
 * 1. **Preventive truncation.** Before the underlying [android.util.Log] sees the throwable,
 *    if `throwable.stackTraceToString().length > STACKTRACE_LENGTH_CAP`, the throwable is
 *    replaced with a synthetic short [Throwable] carrying only the class name + length.
 * 2. **Reactive catch.** The whole emit path is wrapped in `try { … } catch (OutOfMemoryError) { … }`.
 *    The fallback writes a fixed ≤ [FALLBACK_MAX_LEN]-char Timber.w line and never re-throws.
 *
 * Installed once at process start by [com.sza.fastmediasorter.FastMediaSorterApp.attachBaseContext]
 * via `androidx.media3.common.util.Log.setLogger(media3Logger)`.
 */
@Singleton
class Media3OomSafeLogger @Inject constructor() : androidx.media3.common.util.Log.Logger {

    override fun d(tag: String, message: String, throwable: Throwable?) {
        try {
            android.util.Log.d(tag, message, truncate(throwable))
        } catch (oom: OutOfMemoryError) {
            emitOomFallback(tag, throwable)
        }
    }

    override fun i(tag: String, message: String, throwable: Throwable?) {
        try {
            android.util.Log.i(tag, message, truncate(throwable))
        } catch (oom: OutOfMemoryError) {
            emitOomFallback(tag, throwable)
        }
    }

    override fun w(tag: String, message: String, throwable: Throwable?) {
        try {
            android.util.Log.w(tag, message, truncate(throwable))
        } catch (oom: OutOfMemoryError) {
            emitOomFallback(tag, throwable)
        }
    }

    override fun e(tag: String, message: String, throwable: Throwable?) {
        try {
            android.util.Log.e(tag, message, truncate(throwable))
        } catch (oom: OutOfMemoryError) {
            emitOomFallback(tag, throwable)
        }
    }

    /**
     * Replace a pathological-size stacktrace with a short synthetic placeholder.
     * Keeps the original class name and the original length so post-mortem diagnostics retain
     * the most relevant signal without the multi-KB string allocation.
     */
    private fun truncate(throwable: Throwable?): Throwable? {
        if (throwable == null) return null
        return try {
            val rendered = throwable.stackTraceToString()
            // Pre-OOM safety net: replace any rendered stacktrace whose length > 4096 chars
            // (see [STACKTRACE_LENGTH_CAP]) with a synthetic short placeholder.
            if (rendered.length > STACKTRACE_LENGTH_CAP) {
                Throwable("<truncated: ${throwable.javaClass.name}, original ${rendered.length} chars>")
            } else {
                throwable
            }
        } catch (oom: OutOfMemoryError) {
            // Even stringifying for the length check ran out of memory — substitute immediately.
            Throwable("<truncated due to OOM: ${throwable.javaClass.simpleName}>")
        }
    }

    /**
     * Last-resort short Timber.w line emitted when the normal logging path threw an OutOfMemoryError.
     * Bounded allocations only — no collections, no large StringBuilder. Wrapped in another
     * try/catch so a secondary OOM inside the fallback itself is swallowed.
     */
    private fun emitOomFallback(tag: String?, throwable: Throwable?) {
        try {
            val safeTag = tag ?: "?"
            val safeCls = throwable?.javaClass?.simpleName ?: "?"
            val message = "media3 log dropped due to OOM: tag=$safeTag throwable=$safeCls"
            Timber.d("S0213: Pillar B OOM fallback — tag=$safeTag throwable=$safeCls")
            Timber.w(message.take(FALLBACK_MAX_LEN))
        } catch (oom: OutOfMemoryError) {
            // Genuinely terminal — swallow. The whole point of this class is "do not crash here".
        }
    }

    companion object {

        /** Maximum allowed stacktrace string before [truncate] swaps it for a placeholder. */
        const val STACKTRACE_LENGTH_CAP: Int = 4096

        /** Hard cap on the fallback Timber.w message length to prevent re-OOM in the fallback. */
        const val FALLBACK_MAX_LEN: Int = 256
    }
}
