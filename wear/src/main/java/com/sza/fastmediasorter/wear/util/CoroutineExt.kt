package com.sza.fastmediasorter.wear.util

import kotlinx.coroutines.CancellationException
import timber.log.Timber

/*
 * Deliberate copy of app_v2's core/util/CoroutineExt.kt rather than a shared module (S2104 ADR-4).
 * No Gradle module is shared between app_v2 and wear, and introducing one for three extension
 * functions is a build restructure out of proportion to the problem. S1910 already keeps the two
 * modules apart down to separate swallowed-cancellation ratchet baselines, so the copy follows a
 * boundary that is already decided rather than drawing a new one.
 *
 * Keep the two files in step: the gate recognises the cure by name shape, so a member that exists
 * on one side only silently leaves that module's debt uncurable in the one-line form.
 */

/**
 * Re-throws this throwable when it represents coroutine cancellation, otherwise returns normally.
 *
 * Call as the FIRST statement of a broad `catch (e: Exception)` / `catch (e: Throwable)` block that
 * runs inside a coroutine. Structured-concurrency cancellation surfaces as a [CancellationException],
 * which is a plain [Exception] - so a generic catch would otherwise treat normal teardown as a real
 * failure, logging it as an error and keeping cancelled work running after the screen is gone.
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
