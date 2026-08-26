package com.sza.fastmediasorter.wear.domain.model

/**
 * What happened to one file of a batch.
 *
 * One record per file rather than one verdict per run: a batch is expected to succeed partly, and
 * the user has to be able to read which item failed without opening a system log.
 */
data class WearFileOperationResult(
    val fileName: String,
    val outcome: WearFileOperationOutcome,
    /** The name the file ended up with when a conflict forced a suffix; null when nothing renamed it. */
    val finalName: String? = null
)

/** Why one file of a batch ended the way it did. */
enum class WearFileOperationOutcome {
    SUCCEEDED,

    /** The file's storage class does not allow this operation - it was never touched. */
    REFUSED_UNSUPPORTED,

    /** Over the transfer channel's ceiling; refused before any byte was read. */
    REFUSED_TOO_LARGE,

    /** The paired phone did not answer, so a move left its source in place. */
    PHONE_UNREACHABLE,

    FAILED,

    CANCELLED
}
