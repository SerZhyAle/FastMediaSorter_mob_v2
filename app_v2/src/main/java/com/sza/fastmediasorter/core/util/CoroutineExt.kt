package com.sza.fastmediasorter.core.util

import kotlinx.coroutines.CancellationException

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
