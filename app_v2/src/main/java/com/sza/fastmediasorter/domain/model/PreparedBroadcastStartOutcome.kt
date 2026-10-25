package com.sza.fastmediasorter.domain.model

/** S4247: what the live broadcast did with a link prepared before its start (DEVICE-EXCHANGE item DW). */
enum class PreparedBroadcastStartOutcome {
    /** No prepared link was used, or nothing is live. */
    None,

    /** The broadcast went on air under the prepared id, pair and keys. */
    Consumed,

    /** Another device of the deployment held the prepared id; the broadcast went on air under a new one. */
    Collided,
}
