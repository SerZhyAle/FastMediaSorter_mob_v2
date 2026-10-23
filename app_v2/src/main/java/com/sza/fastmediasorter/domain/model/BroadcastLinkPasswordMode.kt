package com.sza.fastmediasorter.domain.model

/**
 * S4405: how the external link of a relayed broadcast is protected. [AUTO] is the generated password every
 * broadcast had before the ticket, so it stays the default an upgraded install reads.
 */
enum class BroadcastLinkPasswordMode {
    AUTO,
    NONE,
    CUSTOM,
    ;

    companion object {
        // A stored name this build does not know - a newer build's mode, a hand-edited preference - reads as the
        // protected default rather than silently dropping the password.
        fun fromStored(name: String?): BroadcastLinkPasswordMode =
            entries.firstOrNull { it.name == name } ?: AUTO
    }
}
