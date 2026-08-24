package com.sza.fastmediasorter.wear.domain.model

import com.google.gson.annotations.SerializedName

/**
 * S1861: the ceiling both directions of the bridge refuse above, in bytes.
 *
 * Mirrored by the phone half rather than shared: the two modules compile separately and there is no
 * common artifact between them, so the number is pinned once on each side and must move on both.
 */
const val WEAR_FILE_TRANSFER_MAX_BYTES = 32L * 1024L * 1024L

/**
 * S1861: what one side announces about a file before it opens the channel that carries it.
 *
 * Every field is defaulted because this arrives as JSON written by the other side of the bridge:
 * Gson leaves a missing field untouched, and a null in a non-null Kotlin field would blow up at the
 * first read rather than at parse time.
 */
data class WearFileTransferMetadata(
    @SerializedName("name")
    val name: String = "",
    @SerializedName("size")
    val size: Long = 0L,
    @SerializedName("mimeType")
    val mimeType: String? = null
)

/** S1861: how one incoming file ended on this side of the bridge. */
enum class WearFileReceiveOutcome {
    SAVED,

    /**
     * Refused for size - either the declared size was already over the ceiling, or the bytes that
     * actually arrived outran what was declared. The two are one outcome because the user is told
     * the same thing: this file is too big for the bridge.
     */
    REFUSED_TOO_LARGE,

    FAILED
}

/** S1861: how one outgoing file ended when this side started the transfer. */
enum class WearFileSendOutcome {
    SENT,
    TOO_LARGE,
    PHONE_UNREACHABLE,
    FAILED
}
