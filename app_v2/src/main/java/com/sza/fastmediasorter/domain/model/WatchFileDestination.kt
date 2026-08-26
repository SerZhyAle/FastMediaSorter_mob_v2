package com.sza.fastmediasorter.domain.model

/**
 * S2044: where one file received from the watch is headed.
 *
 * The two arms exist because the phone reaches them by different means. A local destination is
 * written straight from the Data Layer channel with a local sink, the way S1861 has always done it.
 * A remote one cannot be: the file-operation handlers take a [java.io.File] rather than a stream, and
 * holding the channel open for the length of an SMB write is refused by the strategic spec, so those
 * bytes are staged on the phone first and uploaded out of band.
 */
sealed interface WatchFileDestination {

    /** A directory on this device; [directoryPath] is a plain filesystem path. */
    data class Local(val directoryPath: String) : WatchFileDestination

    /**
     * A network or cloud resource. [parentPath] keeps its scheme prefix (`smb://`, `sftp://`,
     * `ftp://`, `cloud://`) because that prefix is what routes the later operation to a handler,
     * and [resourceId] identifies the resource whose credentials that handler needs.
     */
    data class Remote(val resourceId: Long, val parentPath: String) : WatchFileDestination
}
