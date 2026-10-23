package com.sza.fastmediasorter.data.broadcast.auth

/**
 * S4405: what protects the outward link of an announced broadcast. [Open] has no link pair at all, which the
 * phone's servers and the relay already read as "demand nothing" (LIVE-BROADCAST item R).
 */
sealed interface BroadcastLinkChoice {
    data object Generated : BroadcastLinkChoice

    data class Custom(val password: String) : BroadcastLinkChoice {
        // Never printed: the data-class default would put the password into any log line that formats it.
        override fun toString(): String = "Custom"
    }

    data object Open : BroadcastLinkChoice
}
