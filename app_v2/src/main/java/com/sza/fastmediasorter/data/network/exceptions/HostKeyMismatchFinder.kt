package com.sza.fastmediasorter.data.network.exceptions

import com.sza.fastmediasorter.data.remote.sftp.HostKeyMismatchException

/**
 * S4037: the single recognition entry point for the runtime re-pin action. Walks the cause chain
 * of whatever an error surface received (Media3 wrappers, operation pipelines, the classifier's
 * cause threading) and returns the (expected, actual) canonical fingerprint pair from the first
 * typed verdict carrying one - a [NetworkHostKeyChangedException] with non-null fields or a
 * [HostKeyMismatchException] raised directly by the pool. Null means no typed pair exists: the
 * surface must keep the static safe message and never render a half-informed dialog (strategic
 * §7 risk row 3).
 */
object HostKeyMismatchFinder {

    fun find(throwable: Throwable?): Pair<String, String>? {
        var current = throwable
        var depth = 0
        while (current != null && depth < MAX_CHAIN_DEPTH) {
            typedPairIn(current)?.let { return it }
            current = current.cause
            depth++
        }
        return null
    }

    /** The typed pair one chain link carries, or null when the link is neither typed verdict form. */
    private fun typedPairIn(link: Throwable): Pair<String, String>? =
        (link as? NetworkHostKeyChangedException)
            ?.takeIf { it.expectedFingerprint != null && it.actualFingerprint != null }
            ?.let { verdict ->
                requireNotNull(verdict.expectedFingerprint) to requireNotNull(verdict.actualFingerprint)
            }
            ?: (link as? HostKeyMismatchException)?.let { it.expected to it.actual }

    private const val MAX_CHAIN_DEPTH = 12
}
