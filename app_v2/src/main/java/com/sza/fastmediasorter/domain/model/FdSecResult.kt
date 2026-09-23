package com.sza.fastmediasorter.domain.model

import java.io.File

/**
 * S3382: the domain-facing result of a FileDO container operation.
 *
 * The crypto core has its own outcome type, bound to the container format; this is what the UI is
 * allowed to see. The three refusal classes stay distinct across the boundary, because the contract
 * forbids reporting one as another and a surface that collapsed them here could not obey it.
 */
sealed interface FdSecResult {

    /** A container was written and proven by read-back. The original was not touched. */
    data class Packed(val container: File) : FdSecResult

    /**
     * S3408: a container or a restored original was written beside a file that is not local, and what
     * the location stored was read back identical to the proven local result. [name] is the name it got.
     */
    data class Placed(val name: String) : FdSecResult

    /** The original was recovered, with the media type resolved from its sealed true name. */
    data class Restored(val file: File, val mediaType: MediaType?, val realSize: Long) : FdSecResult

    /** A wrong credential, a file that never was a container, or tampering - never told apart. */
    object WrongCredentialOrTamper : FdSecResult

    /** Truncated, structurally impossible, or the recovered bytes did not match the sealed digest. */
    object Damaged : FdSecResult

    /** A format version, suite, flag bit or key-slot type this app does not read. */
    object Unsupported : FdSecResult

    /** A refused input, a missing file, no space, a permission denial. */
    data class Failed(val detail: String) : FdSecResult
}
