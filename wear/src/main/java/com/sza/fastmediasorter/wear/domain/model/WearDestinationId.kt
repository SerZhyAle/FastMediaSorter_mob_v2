package com.sza.fastmediasorter.wear.domain.model

/**
 * S2511: the whole set of destinations an outside caller may name.
 *
 * A shortcut grid names a destination instead of pinning one unit of content, and the app's only exported
 * Activity is what receives that name. Naming a navigation route directly would hand every app installed on
 * the watch the right to open an arbitrary internal screen, so the wire carries a value of this perch and
 * the route is resolved on the inside (strategic ADR-3). A name absent from here reads as "no target was
 * named", which lands on the ordinary launch rather than somewhere the caller chose.
 *
 * The six home sections come first in the order the home screen draws them, then the mini-programs in the
 * order the Apps screen draws them. The order is not load-bearing - both grids read their own catalog -
 * but keeping it means a reader comparing this file against either catalog sees the same sequence.
 */
enum class WearDestinationId {
    RESOURCES,
    PHONE,
    LOCAL,
    STREAMS,
    APPS,
    FAVOURITES,
    CALCULATOR,
    NETWORK_MONITOR,
    GAME,
    VOICE_RECORDER,
    SYSTEM_INFO,
    WATER_FLASHLIGHT,
    MOTION_MONITOR,
    BODY_SENSOR,

    /** S2509: the watch's own audio broadcast, addressable as a shortcut like every program above. */
    BROADCAST
}

/**
 * The destination a mini-program is reached at.
 *
 * Exhaustive with no else branch on purpose: each new watch program must be given an address here rather
 * than silently dropping out of the shortcut grid.
 */
fun destinationFor(id: WearAppId): WearDestinationId = when (id) {
    WearAppId.CALCULATOR -> WearDestinationId.CALCULATOR
    WearAppId.NETWORK_MONITOR -> WearDestinationId.NETWORK_MONITOR
    WearAppId.GAME -> WearDestinationId.GAME
    WearAppId.VOICE_RECORDER -> WearDestinationId.VOICE_RECORDER
    WearAppId.SYSTEM_INFO -> WearDestinationId.SYSTEM_INFO
    WearAppId.WATER_FLASHLIGHT -> WearDestinationId.WATER_FLASHLIGHT
    WearAppId.MOTION_MONITOR -> WearDestinationId.MOTION_MONITOR
    WearAppId.BODY_SENSOR -> WearDestinationId.BODY_SENSOR
    WearAppId.BROADCAST -> WearDestinationId.BROADCAST
}

/**
 * The destination a home section is reached at, or null when the section has no fixed address.
 *
 * The two last-used rows answer null because they are not sections at all in this sense: each points at
 * whatever the owner opened most recently, and a stream among them resolves through playback preparation
 * that has not run on a cold start - which is exactly the state a tile is tapped in.
 */
fun destinationFor(id: HomeSectionId): WearDestinationId? = when (id) {
    HomeSectionId.RESOURCES -> WearDestinationId.RESOURCES
    HomeSectionId.PHONE -> WearDestinationId.PHONE
    HomeSectionId.LOCAL -> WearDestinationId.LOCAL
    HomeSectionId.STREAMS -> WearDestinationId.STREAMS
    HomeSectionId.APPS -> WearDestinationId.APPS
    HomeSectionId.FAVOURITES -> WearDestinationId.FAVOURITES
    // S2509: the same destination the Programs row resolves to - two entrances, one address, so a
    // tile pointed at either of them lands in the same place.
    HomeSectionId.BROADCAST -> WearDestinationId.BROADCAST
    HomeSectionId.LAST_USED_RESOURCE, HomeSectionId.LAST_USED_STREAM -> null
}
