package com.sza.fastmediasorter.wear.domain.model

import com.google.gson.annotations.SerializedName

/**
 * S2531: what this watch sends when it asks the phone to cast something, and what comes back.
 *
 * Hand-kept in step with the phone module's copy at
 * `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/WearCastPayload.kt`; the two modules share
 * no artifact, so the matching characters are the whole contract and the wire-vocabulary parity gate is
 * what keeps them matching.
 */
data class WearCastRequest(
    /** Correlates the answer with this request; this watch has at most one outstanding. */
    @SerializedName("requestId") val requestId: String,

    /** Which of the two addressing schemes [address] is written in. */
    @SerializedName("origin") val origin: WearCastOrigin,

    /**
     * The address of the content, read according to [origin].
     *
     * A full URL for a stream; a share-relative path inside [sourceId] for a network source. Never this
     * watch's own storage address - the phone cannot resolve one (strategic 6.2).
     */
    @SerializedName("address") val address: String,

    /** The network source [address] is relative to, or zero when [origin] needs none. */
    @SerializedName("sourceId") val sourceId: Long,

    /** What the receiver is being asked to show, so the phone need not guess it from the address. */
    @SerializedName("mediaType") val mediaType: WearCastMediaType,

    /** The name the phone titles the session by, so the address need not be parsed just to show it. */
    @SerializedName("displayName") val displayName: String
)

/** S2531: how [WearCastRequest.address] is to be read. */
enum class WearCastOrigin {

    /** [WearCastRequest.address] is a full stream URL, reachable from any device with a network. */
    STREAM,

    /**
     * [WearCastRequest.address] is a path inside the network source named by the request.
     *
     * The source record originated on the phone and reached this watch by sync, so the phone can
     * rebuild the same address without this watch sending any credentials.
     */
    NETWORK_SOURCE
}

/** S2531: what the receiver is being asked to show. */
enum class WearCastMediaType {
    IMAGE,
    VIDEO,
    AUDIO
}

/** S2531: what the phone did with the request. */
enum class WearCastOutcome {

    /** The content was handed to the active session; the receiver is showing it. */
    CASTING,

    /**
     * Cast works on the phone, but no receiver has been chosen yet.
     *
     * Distinct from unavailable on purpose: the user's next move is on the phone, and that is a
     * different sentence from "the phone cannot cast at all" (strategic 11 criterion 3).
     */
    PICKER_NEEDED,

    /** The phone cannot cast - no Play Services, or a build with the Cast seam disabled. */
    CAST_UNAVAILABLE,

    /** The request arrived but could not be read, or names content the phone cannot cast. */
    UNSUPPORTED_CONTENT,

    /** The request names a source the phone no longer holds. */
    NOT_FOUND,

    /** Nothing answered in time; say so rather than waiting on. */
    PHONE_BUSY,

    /**
     * The session was ended at this watch's request.
     *
     * Distinct from [CASTING] because it answers the opposite ask: the watch that sent a stop needs to
     * know it took effect, and reusing the word for a running broadcast would say the reverse.
     */
    STOPPED
}

/**
 * S2531: the watch asking the phone to end the session it is running.
 *
 * A type of its own rather than a flag on [WearCastRequest]: a stop names no content, and a request
 * whose content fields all had to be ignored would be one the phone could not tell from a malformed
 * cast (strategic 11 criterion 4).
 */
data class WearCastStopRequest(
    @SerializedName("requestId") val requestId: String
)

/** S2531: the phone's answer to one [WearCastRequest]. */
data class WearCastAck(
    @SerializedName("requestId") val requestId: String,
    @SerializedName("outcome") val outcome: WearCastOutcome
)

/**
 * S2531: the phone's current cast session, as this watch displays it.
 *
 * The phone is the only owner of the session, so this travels one way and the watch never computes it
 * (strategic ADR-1).
 */
data class WearCastState(
    @SerializedName("isCasting") val isCasting: Boolean,
    @SerializedName("deviceName") val deviceName: String?,
    @SerializedName("displayName") val displayName: String?
)
