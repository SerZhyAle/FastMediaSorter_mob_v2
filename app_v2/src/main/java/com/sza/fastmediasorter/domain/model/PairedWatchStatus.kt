package com.sza.fastmediasorter.domain.model

/**
 * What the phone can say about the watch on the other end of the bridge (S1885).
 *
 * Three states rather than two, because "we have not asked yet" and "we asked and nothing answered"
 * look identical in a text row and mean opposite things to someone diagnosing a quiet watch.
 */
sealed interface PairedWatchStatus {

    /** Nothing has been asked yet - the row shows a neutral line rather than a wrong one. */
    data object Unknown : PairedWatchStatus

    /** A node answered and named itself. [name] is the bridge's display name for the watch. */
    data class Connected(val name: String) : PairedWatchStatus

    /**
     * No node answered.
     *
     * This deliberately covers both "no watch was ever paired" and "a paired watch is out of
     * range": the Data Layer reports only *connected* nodes, so both produce an empty list and the
     * phone cannot separate them without Bluetooth permissions it does not otherwise need. Merging
     * them here is a recorded decision (strategic §6.2), not an oversight.
     */
    data object NotConnected : PairedWatchStatus
}
