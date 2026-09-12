package com.sza.fastmediasorter.wear.ui.navigation

import com.sza.fastmediasorter.wear.domain.model.WearDestinationId
import com.sza.fastmediasorter.wear.domain.model.WearLaunchAddress
import com.sza.fastmediasorter.wear.domain.model.WearTileKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S2751: the table that turns a domain launch address into a navigation route.
 *
 * The destination half carries over from `ResolveWearLaunchRouteUseCaseTest`, which owned it while the
 * table lived inside the resolver. The address half is new and exists because a substitution here - a
 * pinned channel opening the image viewer, say - is a silent behaviour change rather than a compile error.
 */
class WearLaunchRoutesTest {

    private val fileId = 42L

    @Test
    fun `every destination resolves to a route`() {
        WearDestinationId.entries.forEach { id ->
            assertTrue(
                "$id is offered on a tile, so it must have somewhere to go",
                WearLaunchRoutes.routeFor(id).isNotBlank()
            )
        }
    }

    @Test
    fun `no two destinations resolve to the same route`() {
        val routes = WearDestinationId.entries.map { WearLaunchRoutes.routeFor(it) }

        // Two buttons that land on one screen would be two ways to reach it and no way to reach the other,
        // which is a mapping typo rather than a design choice - and it is invisible on the watch.
        assertEquals("each destination owns its route", routes.size, routes.toSet().size)
    }

    @Test
    fun `a screen address resolves through the destination table`() {
        assertEquals(
            WearLaunchRoutes.routeFor(WearDestinationId.FAVOURITES),
            WearLaunchRoutes.routeFor(WearLaunchAddress.Screen(WearDestinationId.FAVOURITES))
        )
    }

    @Test
    fun `a picker address names the tile kind it picks for`() {
        assertEquals(
            WearRoutes.tileTargetPicker(WearTileKind.STREAM.name),
            WearLaunchRoutes.routeFor(WearLaunchAddress.TileTargetPicker(WearTileKind.STREAM))
        )
    }

    @Test
    fun `a source overview keeps its id and name`() {
        assertEquals(
            WearRoutes.sourceMediaType("source-1", "Home NAS"),
            WearLaunchRoutes.routeFor(WearLaunchAddress.SourceOverview("source-1", "Home NAS"))
        )
    }

    @Test
    fun `a stream picks its player from the prepared playback`() {
        assertEquals(
            WearRoutes.videoPlayer(fileId),
            WearLaunchRoutes.routeFor(WearLaunchAddress.StreamPlayback(fileId, isVideo = true))
        )
        assertEquals(
            WearRoutes.audioPlayer(fileId),
            WearLaunchRoutes.routeFor(WearLaunchAddress.StreamPlayback(fileId, isVideo = false))
        )
    }

    @Test
    fun `a media file picks its player from its own mime type`() {
        assertEquals(
            WearRoutes.imageViewer(fileId),
            WearLaunchRoutes.routeFor(WearLaunchAddress.MediaFile(fileId, "image/jpeg"))
        )
    }
}
