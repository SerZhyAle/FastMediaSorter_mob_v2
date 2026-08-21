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
    BANDWIDTH_UNKNOWN
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
