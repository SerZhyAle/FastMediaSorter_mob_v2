package com.sza.fastmediasorter.wear.domain.model

/**
 * Why the channel affected a stream.
 *
 * A domain value rather than a message: the player owns the wording and the locale, and the policy
 * that produces this has no business knowing either.
 */
enum class StreamChannelReason {

    /** The link declares less bandwidth than this kind of stream needs. */
    NARROW_LINK,

    /** There is no default network at all. */
    NO_LINK,

    /** The link is up but the platform has not confirmed it reaches the internet. */
    UNVALIDATED_LINK,

    /** The link is up and the platform declared no usable bandwidth estimate for it. */
    BANDWIDTH_UNKNOWN,

    /**
     * S2550 pillar E: the watch has a link, but not the one it can serve audio over.
     *
     * Distinct from [NARROW_LINK] because it calls for a different sentence. A Bluetooth-carried link
     * would also fail the bandwidth floor, and reporting that as "the channel is narrow" tells the
     * owner about a measurement instead of about the one thing that fixes it - turning Wi-Fi on for
     * the watch.
     */
    NOT_ON_WIFI
}

/**
 * What the policy decided about starting a stream on the current link.
 *
 * [AllowDegraded] exists because "we are not sure" must not read as "no": an absent estimate is a gap
 * in what the platform told us, not a measurement of a bad link.
 */
sealed interface StreamChannelVerdict {

    /** Null on [Allow] only, so a caller can hand this straight to a screen that shows nothing for null. */
    val reason: StreamChannelReason?

    data object Allow : StreamChannelVerdict {
        override val reason: StreamChannelReason? = null
    }

    data class AllowDegraded(override val reason: StreamChannelReason) : StreamChannelVerdict

    data class Refuse(override val reason: StreamChannelReason) : StreamChannelVerdict
}
