package com.sza.fastmediasorter.core.systeminfo

import java.io.IOException

/**
 * Classifies exceptions thrown while reading individual system-info / diagnostic fields.
 *
 * Some device fields are unreadable by design on a restricted device: reading the OS user name
 * needs a privilege the app does not hold (`SecurityException`), and reading a kernel/security
 * status file fails with `EACCES` on a locked device. Those are expected, not failures of the
 * diagnostic collector, so they should be logged quietly (no stack trace) while the field simply
 * degrades to "unknown". A genuine, unexpected collection failure must still log loudly.
 *
 * Shared by the base [GatherSystemInfoUseCase] collector and the flavor-specific extended
 * diagnostics collector so both classify access denials identically (single point, no duplication).
 */
object SystemInfoAccessClassifier {

    private const val MAX_CAUSE_DEPTH = 5

    /**
     * True when [error] (or any cause within [MAX_CAUSE_DEPTH] links) is an expected access denial:
     * a [SecurityException] (insufficient privilege) or an [IOException] whose message indicates a
     * permission/access failure (`EACCES`, "permission denied"). Narrowly scoped on purpose - any
     * other throwable is treated as an unexpected collection failure and must keep logging loudly.
     */
    fun isExpectedAccessDenial(error: Throwable?): Boolean =
        generateSequence(error) { it.cause }
            .take(MAX_CAUSE_DEPTH)
            .any { t ->
                t is SecurityException ||
                    (t is IOException && t.message?.let { msg ->
                        msg.contains("EACCES", ignoreCase = true) ||
                            msg.contains("permission denied", ignoreCase = true)
                    } == true)
            }
}
