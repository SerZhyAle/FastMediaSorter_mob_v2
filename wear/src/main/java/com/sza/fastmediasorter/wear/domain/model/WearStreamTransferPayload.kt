package com.sza.fastmediasorter.wear.domain.model

import com.google.gson.JsonObject
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
 * S4261: a broadcast with a server endpoint or `auth` handed from the phone (LIVE-BROADCAST item P). Its own
 * message path, so a watch build that does not know it stays silent instead of keeping a bare URL.
 * Mirrored in the phone module; the snake_case names are the wire contract.
 */
data class WearBroadcastHandoffPayload(
    @SerializedName("request_id") val requestId: String,
    @SerializedName("name") val name: String,
    @SerializedName("url") val url: String,
    @SerializedName("media_kind") val mediaKind: String,
    @SerializedName("open_now") val openNow: Boolean = false,
    // The descriptor names no id, and a RELAY endpoint is valid only at `/v2/b/<id>/stream`. Both nullable
    // on this side: Gson leaves an absent member null whatever the Kotlin type says.
    @SerializedName("broadcast_id") val broadcastId: String? = null,
    @SerializedName("descriptor") val descriptor: JsonObject? = null,
    @SerializedName("pass") val pass: WearBroadcastPass? = null
) {
    // DEVICE-EXCHANGE item DK: a sealed endpoint's key opens the broadcast, so neither member reaches a log.
    override fun toString(): String =
        "WearBroadcastHandoffPayload(requestId=$requestId, mediaKind=$mediaKind, openNow=$openNow, " +
            "protected=${pass != null})"
}

/** S4261: the pair the phone holds for a protected broadcast (DEVICE-EXCHANGE item CG); never printed. */
data class WearBroadcastPass(
    @SerializedName("user") val user: String,
    @SerializedName("password") val password: String
) {
    override fun toString(): String = "WearBroadcastPass(user=***, password=***)"
}

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

        /** S4261: a handed-off broadcast with no endpoint this watch plays, or protected without its pass. */
        const val OUTCOME_UNPLAYABLE = "UNPLAYABLE"
    }
}
