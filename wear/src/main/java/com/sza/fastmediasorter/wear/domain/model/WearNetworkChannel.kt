package com.sza.fastmediasorter.wear.domain.model

/**
 * Which transport is carrying the watch's network right now.
 *
 * S1728 ADR-2: the transport name predicts capacity badly, so no CAPACITY decision is taken from this
 * value - the bandwidth figures on [WearNetworkChannel] are what the stream policy reads.
 *
 * S2550 added the one reading that is not about capacity: serving audio to a phone needs a socket on
 * the watch's own LAN, which exists on [WIFI] and on nothing else, so there the kind is the fact
 * itself rather than a proxy for one.
 */
enum class WearNetworkChannelKind {
    WIFI,
    CELLULAR,
    BLUETOOTH,
    OTHER,

    /** No default network at all - not a slow one. */
    NONE
}

/**
 * What the watch's current network link declares about itself.
 *
 * The bandwidth figures are the platform's own estimate and may be absent even on a working link,
 * which is why [hasBandwidthEstimate] exists as a separate question from "is there a link".
 */
data class WearNetworkChannel(
    val kind: WearNetworkChannelKind,
    val downstreamKbps: Int?,
    val upstreamKbps: Int?,
    val isMetered: Boolean,
    val isValidated: Boolean
) {
    /** True only when the platform gave a usable downstream figure; an absent estimate is not zero. */
    val hasBandwidthEstimate: Boolean
        get() = downstreamKbps != null && downstreamKbps > 0

    companion object {
        /** The state before the first callback arrives, and the state after the link is lost. */
        val NONE = WearNetworkChannel(
            kind = WearNetworkChannelKind.NONE,
            downstreamKbps = null,
            upstreamKbps = null,
            isMetered = false,
            isValidated = false
        )
    }
}
