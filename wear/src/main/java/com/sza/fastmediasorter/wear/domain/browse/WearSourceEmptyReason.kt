package com.sza.fastmediasorter.wear.domain.browse

/**
 * S2640: why a network source offers no browse category, when it offers none.
 *
 * The distinction exists because the two causes send the wearer to different places. One is a
 * setting on this watch and is fixed two taps away; the other is a property of how the resource was
 * configured on the phone, and no amount of switching things on here will change it. The screen used
 * to name the first cause for both, which is worse than saying nothing: it sent the wearer into a
 * settings screen where everything was already on.
 */
enum class WearSourceEmptyReason {
    /** The source offers at least one category - there is no emptiness to explain. */
    NONE,

    /** Every type the source offers is one the wearer switched off in this watch's settings. */
    TYPES_DISABLED_IN_SETTINGS,

    /** The source offers only file kinds this module has no way to list from a network share. */
    SOURCE_TYPES_UNSUPPORTED
}
