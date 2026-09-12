package com.sza.fastmediasorter.wear.ui.navigation

import com.sza.fastmediasorter.wear.domain.model.WearDestinationId
import com.sza.fastmediasorter.wear.domain.model.WearLaunchAddress
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
     * Exhaustive with no else branch on purpose: a new destination fails compilation here rather than
     * silently resolving to nothing.
     */
    fun routeFor(destination: WearDestinationId): String = when (destination) {
        WearDestinationId.RESOURCES -> WearRoutes.NETWORK_SOURCES
        WearDestinationId.PHONE -> WearRoutes.PHONE_HOME
        WearDestinationId.LOCAL -> WearRoutes.LOCAL_HOME
        WearDestinationId.STREAMS -> WearRoutes.STREAMS
        WearDestinationId.APPS -> WearRoutes.APPS
        WearDestinationId.FAVOURITES -> WearRoutes.FAVOURITES
        WearDestinationId.CALCULATOR -> WearRoutes.CALCULATOR
        WearDestinationId.NETWORK_MONITOR -> WearRoutes.NETWORK_MONITOR
        WearDestinationId.GAME -> WearRoutes.GAME
        WearDestinationId.VOICE_RECORDER -> WearRoutes.VOICE_RECORDER
        WearDestinationId.SYSTEM_INFO -> WearRoutes.SYSTEM_INFO
        WearDestinationId.WATER_FLASHLIGHT -> WearRoutes.WATER_FLASHLIGHT
        WearDestinationId.MOTION_MONITOR -> WearRoutes.MOTION_MONITOR
        // S2457: resolves in both flavors on purpose. A shortcut naming this destination on a `standard`
        // watch lands on the screen, which states that the build withholds the capability - a better
        // answer than a dead tap, and the only one available here, since a route is a static address and
        // this table has no way to ask whether the Apps catalog offered the row.
        WearDestinationId.BODY_SENSOR -> WearRoutes.BODY_SENSOR
        WearDestinationId.BLOOD_PRESSURE -> WearRoutes.BLOOD_PRESSURE
        // S2509: one route for both entrances, so a shortcut pinned from the Home section and one
        // pinned from the Programs grid land on the same control screen.
        WearDestinationId.BROADCAST -> WearRoutes.BROADCAST
        WearDestinationId.STOPWATCH -> WearRoutes.STOPWATCH
        WearDestinationId.TOURIST -> WearRoutes.TOURIST
        // S2551: the mirror entrance of the row above, and a separate address for the same reason -
        // one of them opens this watch's microphone, the other the phone's camera.
        WearDestinationId.PHONE_CAMERA -> WearRoutes.PHONE_CAMERA
        // S2511: the overflow cell of a shortcut grid, which offers what the grid could not hold.
        WearDestinationId.HOME -> WearRoutes.HOME
    }

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
