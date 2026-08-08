package com.sza.fastmediasorter.domain.radio

/**
 * S1441: the radios this app may switch on and off by itself.
 *
 * Closed on purpose - mobile data and SIM are refused to a normal app on every platform version, and
 * auto-rotate goes through `WRITE_SETTINGS`, a different class of consent.
 */
enum class RadioKind {
    WIFI,
    BLUETOOTH,
}
