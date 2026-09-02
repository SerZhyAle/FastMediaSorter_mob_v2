package com.sza.fastmediasorter.core.util

import kotlinx.coroutines.CancellationException
import timber.log.Timber

/**
 * Re-throws this throwable when it represents coroutine cancellation, otherwise returns normally.
 *
 * Call as the FIRST statement of a broad `catch (e: Exception)` / `catch (e: Throwable)` block that
 * runs inside a coroutine. Structured-concurrency cancellation surfaces as a [CancellationException]
 * (e.g. kotlinx JobCancellationException), which is a plain [Exception] - so a generic catch would
 * otherwise treat normal teardown as a real failure: logging it at ERROR and/or showing a false
 * error toast when the screen is recreated (notably on foldables, where every fold/unfold cancels
 * in-flight work). Re-throwing keeps cancellation cooperative and out of the error path.
 */
fun Throwable.rethrowIfCancellation() {
    if (this is CancellationException) throw this
}

/**
 * Log [message] at warning level - unless this throwable is coroutine cancellation, which is rethrown
 * instead (see [rethrowIfCancellation]).
 *
 * Call as the FIRST statement of the catch block: anything above it has already run error-path work
 * on what was only a cancellation, and the gate counts that as uncured.
 *
 * The one-line form matters. Writing the guard and the log as two statements grows the enclosing
 * function by a line, which is enough to push an already-long scanner method over detekt's threshold
 * (S1890), and a guard on its own line is the one the next editor deletes as noise.
 *
 * [args] mirrors Timber's own format-argument tail, so a call site that formats its message does not
 * have to fall back to the two-statement form.
 */
fun Throwable.warnUnlessCancellation(message: String, vararg args: Any?) {
    rethrowIfCancellation()
    Timber.w(this, message, *args)
}

/**
 * Log [message] at error level - unless this throwable is coroutine cancellation, which is rethrown
 * instead (see [rethrowIfCancellation]).
 *
 * Call as the FIRST statement of the catch block, same as [warnUnlessCancellation].
 *
 * Exists so that curing a swallowed cancellation never changes the level of the line it replaces
 * (S2104): most of the debt logs at error, and curing those sites with the warn-level member alone
 * would silently downgrade real failures, which the project's log-level rule forbids.
 */
fun Throwable.errorUnlessCancellation(message: String, vararg args: Any?) {
    rethrowIfCancellation()
    Timber.e(this, message, *args)
}
