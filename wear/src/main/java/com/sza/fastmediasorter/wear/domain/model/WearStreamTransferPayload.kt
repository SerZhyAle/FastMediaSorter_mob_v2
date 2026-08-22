package com.sza.fastmediasorter.wear.domain.model

import com.google.gson.annotations.SerializedName

/**
 * S1799: description of one stream channel sent from the phone to the watch.
 * Field names are the wire contract - the phone module carries a mirrored copy of this class,
 * and both sides must keep the snake_case JSON names identical.
 */
data class WearStreamTransferPayload(
    @SerializedName("request_id") val requestId: String,
    @SerializedName("name") val name: String,
    @SerializedName("url") val url: String,
    @SerializedName("media_kind") val mediaKind: String,
    // S1944: "store it and open it" rather than "store it". The default is the compatibility
    // contract, not a convenience: a payload written before this field deserialises to false and
    // behaves exactly as S1799 did, so the two sides never need to agree on a version.
    @SerializedName("open_now") val openNow: Boolean = false
)

/**
 * S1799: watch → phone answer for one stream transfer, correlated by [requestId].
 */
data class WearStreamTransferAck(
    @SerializedName("request_id") val requestId: String,
    @SerializedName("outcome") val outcome: String,
    @SerializedName("message") val message: String? = null
) {
    companion object {
        const val OUTCOME_STORED = "STORED"
        const val OUTCOME_UPDATED = "UPDATED"
        const val OUTCOME_ERROR = "ERROR"

        /** S1944: the channel was stored and the watch's open app navigated to its player. */
        const val OUTCOME_OPENED = "OPENED"

        /**
         * S1944: the channel was stored, but the watch app was not on screen and the platform does
         * not let it be woken from a Data Layer message (ADR-1). An expected ending, not an error -
         * the phone renders it as its own sentence rather than as a failure.
         */
        const val OUTCOME_NOT_FOREGROUND = "NOT_FOREGROUND"
    }
}
