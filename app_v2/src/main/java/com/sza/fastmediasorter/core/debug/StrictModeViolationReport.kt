package com.sza.fastmediasorter.core.debug

/**
 * Turns a StrictMode violation into a log line that survives the session file.
 *
 * The file logging tree writes a WARN as a single line carrying only the throwable's class and
 * message, and a StrictMode violation has no message - so the file kept `[DiskReadViolation: null]`
 * and no call site (S3129). The site therefore has to be in the message text itself. The same site
 * also fires on every touch or screen open, so repeats are counted and only sparse milestones logged.
 */
object StrictModeViolationReport {

    private const val APP_PACKAGE = "com.sza.fastmediasorter."
    private const val DECIMAL_BASE = 10

    /** Wrappers that relay a caller's disk access; naming them would point at the relay, not the caller. */
    private val appRelayPrefixes = listOf(
        "com.sza.fastmediasorter.core.debug.",
        "com.sza.fastmediasorter.core.logging.",
    )

    /** The BlockGuard and I/O machinery sitting above the real caller in every disk violation stack. */
    private val mechanismPrefixes = listOf(
        "android.os.StrictMode",
        "dalvik.system.BlockGuard",
        "libcore.io.",
        "java.io.",
        "java.lang.Thread",
    )

    private val counts = HashMap<String, Int>()

    /**
     * `at <frame>` for the first frame of this app's own code; when the stack holds none, the first
     * frame past the disk-access machinery, marked so a platform-only violation is not mistaken for ours.
     */
    fun describeSite(violation: Throwable): String {
        val frames = violation.stackTrace
        val appFrame = frames.firstOrNull { frame -> frame.isAppCode() }
        if (appFrame != null) {
            return "at $appFrame"
        }
        val callerFrame = frames.firstOrNull { frame -> !frame.isMechanism() }
        return "no app frame, at ${callerFrame ?: "unknown"}"
    }

    /** The running count for [key] when it is worth a log line (1, 10, 100, ..), otherwise null. */
    @Synchronized
    fun occurrence(key: String): Int? {
        val count = (counts[key] ?: 0) + 1
        counts[key] = count
        return count.takeIf { isMilestone(it) }
    }

    private fun isMilestone(count: Int): Boolean {
        var remainder = count
        while (remainder >= DECIMAL_BASE && remainder % DECIMAL_BASE == 0) {
            remainder /= DECIMAL_BASE
        }
        return remainder == 1
    }

    private fun StackTraceElement.isAppCode(): Boolean =
        className.startsWith(APP_PACKAGE) && appRelayPrefixes.none { prefix -> className.startsWith(prefix) }

    private fun StackTraceElement.isMechanism(): Boolean =
        mechanismPrefixes.any { prefix -> className.startsWith(prefix) }
}
