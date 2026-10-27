package com.sza.fastmediasorter.data.broadcast.auth

import com.sza.fastmediasorter.domain.model.BroadcastLinkPasswordMode

/**
 * S4405: what protects the outward link of an announced broadcast. [Open] has no link pair at all, which the
 * phone's servers and the relay already read as "demand nothing" (LIVE-BROADCAST item R).
 */
sealed interface BroadcastLinkChoice {

    /** S4440: the same choice in the vocabulary of the remembered setting, so a dialog can name what is live. */
    val mode: BroadcastLinkPasswordMode

    data object Generated : BroadcastLinkChoice {
        override val mode: BroadcastLinkPasswordMode = BroadcastLinkPasswordMode.AUTO
    }

    data class Custom(val password: String) : BroadcastLinkChoice {
        override val mode: BroadcastLinkPasswordMode get() = BroadcastLinkPasswordMode.CUSTOM

        // Never printed: the data-class default would put the password into any log line that formats it.
        override fun toString(): String = "Custom"
    }

    data object Open : BroadcastLinkChoice {
        override val mode: BroadcastLinkPasswordMode = BroadcastLinkPasswordMode.NONE
    }
}
