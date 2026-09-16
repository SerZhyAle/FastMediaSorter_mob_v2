package com.sza.fastmediasorter.wear.ui.navigation

import com.sza.fastmediasorter.wear.domain.model.WearAppId
import com.sza.fastmediasorter.wear.domain.model.WearDestinationId
import com.sza.fastmediasorter.wear.domain.model.WearLaunchAddress
import com.sza.fastmediasorter.wear.domain.model.appIdFor
import com.sza.fastmediasorter.wear.ui.common.playerRouteFor

/**
 * S2751: the one table turning a domain launch address into a navigation route.
 *
 * Lifted out of the launch resolver, which lives in the domain branch and had to import this module's
 * routes and player picker to answer at all. A route is an address inside the navigation graph, so it
 * belongs beside the graph rather than under it (strategic ADR-1).
 */
internal object WearLaunchRoutes {

    /**
     * A program is answered through [appIdFor] and a home section by the table below.
     *
     * The split is what keeps this function under detekt's complexity ceiling as the program list
     * grows, and it costs no safety: adding a program already forces an entry in `destinationFor`,
     * which [programRouteFor] is exhaustive over, so a new one still fails compilation - one file
     * further along. The `else` covers only a home section added without a route, and it lands on the
     * ordinary launch, which is what `WearDestinationId` already says an unnamed target does.
     */
    fun routeFor(destination: WearDestinationId): String {
        val program = appIdFor(destination)
        if (program != null) {
            return programRouteFor(program)
        }
        return when (destination) {
            WearDestinationId.RESOURCES -> WearRoutes.NETWORK_SOURCES
            WearDestinationId.PHONE -> WearRoutes.PHONE_HOME
            WearDestinationId.LOCAL -> WearRoutes.LOCAL_HOME
            WearDestinationId.STREAMS -> WearRoutes.STREAMS
            WearDestinationId.APPS -> WearRoutes.APPS
            WearDestinationId.FAVOURITES -> WearRoutes.FAVOURITES
            // S2551: the mirror entrance of the watch's own broadcast, and a separate address for the
            // same reason - one of them opens this watch's microphone, the other the phone's camera.
            WearDestinationId.PHONE_CAMERA -> WearRoutes.PHONE_CAMERA
            // S2511: the overflow cell of a shortcut grid, which offers what the grid could not hold.
            else -> WearRoutes.HOME
        }
    }

    /**
     * Exhaustive with no else branch on purpose: a new program fails compilation here rather than
     * silently resolving to nothing.
     */
    private fun programRouteFor(program: WearAppId): String = when (program) {
        WearAppId.CALCULATOR -> WearRoutes.CALCULATOR
        WearAppId.NETWORK_MONITOR -> WearRoutes.NETWORK_MONITOR
        WearAppId.GAME -> WearRoutes.GAME
        WearAppId.VOICE_RECORDER -> WearRoutes.VOICE_RECORDER
        WearAppId.SYSTEM_INFO -> WearRoutes.SYSTEM_INFO
        WearAppId.WATER_FLASHLIGHT -> WearRoutes.WATER_FLASHLIGHT
        WearAppId.MOTION_MONITOR -> WearRoutes.MOTION_MONITOR
        // S2457: resolves in both flavors on purpose. A shortcut naming this destination on a
        // `standard` watch lands on the screen, which states that the build withholds the capability -
        // a better answer than a dead tap, and the only one available here, since a route is a static
        // address and this table has no way to ask whether the Apps catalog offered the row.
        WearAppId.BODY_SENSOR -> WearRoutes.BODY_SENSOR
        WearAppId.BLOOD_PRESSURE -> WearRoutes.BLOOD_PRESSURE
        // S2509: one route for both entrances, so a shortcut pinned from the Home section and one
        // pinned from the Programs grid land on the same control screen.
        WearAppId.BROADCAST -> WearRoutes.BROADCAST
        WearAppId.STOPWATCH -> WearRoutes.STOPWATCH
        WearAppId.TOURIST -> WearRoutes.TOURIST
        WearAppId.CLIPBOARD -> WearRoutes.CLIPBOARD
    }

    /**
     * S3116: the program a navigation route belongs to, or null when the route is not a program's.
     *
     * Derived from [programRouteFor] rather than written out as a second table, for the reason
     * `appIdFor` states about its own inverse: the two would be one edit apart from disagreeing, and
     * here the disagreement would be silent - a program whose route fell out of the table would stop
     * being recorded rather than fail to compile. Every program route is a constant with no argument,
     * so an exact comparison is the whole match (strategic ADR-2).
     */
    fun appIdForRoute(route: String): WearAppId? =
        WearAppId.entries.firstOrNull { program -> programRouteFor(program) == route }

    fun routeFor(address: WearLaunchAddress): String = when (address) {
        is WearLaunchAddress.Screen -> routeFor(address.destination)
        is WearLaunchAddress.TileTargetPicker -> WearRoutes.tileTargetPicker(address.kind.name)
        is WearLaunchAddress.SourceOverview ->
            WearRoutes.sourceMediaType(address.sourceId, address.sourceName)

        is WearLaunchAddress.StreamPlayback -> if (address.isVideo) {
            WearRoutes.videoPlayer(address.fileId)
        } else {
            WearRoutes.audioPlayer(address.fileId)
        }

        is WearLaunchAddress.MediaFile ->
            playerRouteFor(address.fileId, address.mimeType, fileName = address.fileName)
    }
}
