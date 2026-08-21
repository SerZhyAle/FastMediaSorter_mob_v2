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
    @SerializedName("media_kind") val mediaKind: String
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
    }
}
