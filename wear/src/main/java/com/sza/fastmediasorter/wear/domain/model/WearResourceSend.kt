package com.sza.fastmediasorter.wear.domain.model

/**
 * S4230: why one file did not reach a resource the watch sends to on its own (contract DEVICE-EXCHANGE 23 item CH).
 *
 * Each member names what the wearer can act on, not which library threw: the screens map one text per member, so
 * a refusal of SFTP and a refusal of SMB read the same.
 */
enum class WearResourceSendFailure {
    /** No Wi-Fi or mobile network came up for the watch; nothing was started. */
    NO_NETWORK,

    /** The resource did not answer: host unknown, connection refused, timed out, or the source is gone. */
    UNREACHABLE,

    /** The resource answered and said no: credentials, permissions or a read-only share. */
    REFUSED,

    /** The resource has no room for the file. */
    NO_SPACE,

    /** The file on the watch could not be opened for reading. */
    SOURCE_UNREADABLE,

    /** A protected folder whose PIN the phone has not handed over; the PIN is never typed on the watch. */
    PIN_NEEDED,

    /** An exchange-server folder of an account this watch holds no entry of. */
    NOT_CONNECTED,

    /** The transfer started and broke off; nothing was left under the real name. */
    BROKEN,
}

/**
 * What one send of one file to one resource came to.
 *
 * [resourceName] is null only when the resource itself could not be found; [finalName] is set exactly when the
 * file was stored, and [failure] exactly when it was not.
 */
data class WearResourceSendReport(
    val resourceName: String?,
    val finalName: String?,
    val failure: WearResourceSendFailure?
) {
    val sent: Boolean get() = failure == null
}

/** The outcome of storing one stream on one resource. */
sealed interface WearResourceStoreResult {

    /** [finalName] is the name the file ended up with, which differs from the asked one after a name clash. */
    data class Stored(val finalName: String) : WearResourceStoreResult

    data class Failed(val failure: WearResourceSendFailure) : WearResourceStoreResult
}
