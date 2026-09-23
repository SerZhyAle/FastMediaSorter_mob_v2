package com.sza.fastmediasorter.wear.domain.model

import java.io.File

/**
 * S3383: the watch-facing result of a FileDO container operation.
 *
 * The crypto core has its own outcome type, bound to the container format; this is what a watch
 * screen is allowed to see. The refusal classes stay distinct across the boundary because the
 * contract forbids reporting one as another, and a type that collapsed them here would make obeying
 * that impossible further up.
 */
sealed interface WearFdSecResult {

    /** A container was written and proven by read-back. The original was not touched. */
    data class Packed(val container: File) : WearFdSecResult

    /** The original was recovered, under the true name sealed inside the container. */
    data class Restored(val file: File, val realName: String, val realSize: Long) : WearFdSecResult

    /** A wrong credential, a file that never was a container, or tampering - never told apart. */
    object WrongCredentialOrTamper : WearFdSecResult

    /** Truncated, structurally impossible, or the recovered bytes did not match the sealed digest. */
    object Damaged : WearFdSecResult

    /** A format version, suite, flag bit or key-slot type this build does not read. */
    object Unsupported : WearFdSecResult

    /**
     * The key derivation could not get its memory on this watch.
     *
     * Its own member rather than a [Failed] detail: the format fixes a 64 MiB Argon2id profile that
     * no platform may lower, so on a small watch this is an expected outcome with its own remedy -
     * and a wearer told "wrong password" for it would keep retyping a correct one.
     */
    object OutOfMemory : WearFdSecResult

    /** A refused input, a missing file, no space, a permission denial. */
    data class Failed(val detail: String) : WearFdSecResult
}
