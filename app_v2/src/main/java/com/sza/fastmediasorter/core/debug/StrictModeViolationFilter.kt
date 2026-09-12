package com.sza.fastmediasorter.core.debug

/**
 * Classifies a StrictMode violation as platform-owned noise or as this app's own defect.
 *
 * Samsung/Knox builds do several small synchronous disk reads inside `Toast.show()` itself: a
 * package-flag check backed by `File.exists()`, then a Binder call into the Knox EDM policy service
 * asking whether toasts are administratively allowed, which reads a SQLite policy database. Binder
 * propagates the callee's violations back to the calling thread, so all of them are reported against
 * this app although no app code issues a single one of those reads. On a device with that policy
 * service every toast produces five violations, which buries the ones worth reading.
 *
 * Filtering here rather than at each of the 173 toast call sites also covers call sites added later,
 * and keeps release code untouched - StrictMode runs in debug builds only.
 */
object StrictModeViolationFilter {

    /**
     * A cause chain longer than this is not a violation shape this filter knows; walking it further
     * costs more than the noise it could still find.
     */
    private const val MAX_CAUSE_DEPTH = 8

    private val platformNoiseFrames = listOf(
        "android.widget.Toast",
        "android.app.IdsController",
        "com.samsung.android.knox.custom",
        "com.android.server.enterprise",
    )

    /**
     * True when any frame, in the violation's own stack or in any cause below it, belongs to a
     * platform-owned pipeline. The cause chain matters: a violation that travelled back over a
     * Binder call carries the caller's frames one level down rather than in the top-level stack.
     */
    fun isPlatformNoise(violation: Throwable): Boolean {
        var current: Throwable? = violation
        var depth = 0
        while (current != null && depth < MAX_CAUSE_DEPTH) {
            if (current.stackTrace.any { frame -> frame.isPlatformOwned() }) {
                return true
            }
            current = current.cause
            depth++
        }
        return false
    }

    private fun StackTraceElement.isPlatformOwned(): Boolean =
        platformNoiseFrames.any { prefix -> className.startsWith(prefix) }
}
