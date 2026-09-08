package com.sza.fastmediasorter.wear.tile

import com.sza.fastmediasorter.wear.domain.model.NetworkSourceType
import com.sza.fastmediasorter.wear.domain.model.WearDestinationId
import com.sza.fastmediasorter.wear.domain.model.WearLaunchTarget
import com.sza.fastmediasorter.wear.domain.model.WearTileKind
import com.sza.fastmediasorter.wear.domain.model.WearTileTargetRef
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * S2589: the two string ids the tile layer addresses things by.
 *
 * Both fail silently when they drift. An image id is written into the layout by one call and published in
 * the resources response by another, in a different file, and a mismatch draws nothing and reports no error.
 * A click id is how the renderer says which element was tapped, so two cells sharing one make a mis-routed
 * tap indistinguishable from a correct one in any later diagnosis.
 */
class WearTileIdentifiersTest {

    @Test
    fun `an image id is derived from the drawable it stands for`() {
        assertEquals("drawable_17", tileImageResourceId(17))
    }

    @Test
    fun `two drawables never share an image id`() {
        assertEquals(2, setOf(tileImageResourceId(17), tileImageResourceId(18)).size)
    }

    @Test
    fun `every destination gets a click id of its own`() {
        val ids = WearDestinationId.entries.map { WearLaunchTarget.Destination(it).clickId() }

        assertEquals(WearDestinationId.entries.size, ids.toSet().size)
    }

    @Test
    fun `every pick target gets a click id of its own`() {
        val ids = WearTileKind.entries.map { WearLaunchTarget.Pick(it).clickId() }

        assertEquals(WearTileKind.entries.size, ids.toSet().size)
    }

    /**
     * An assigned tile draws one tappable area, so its id is a constant rather than an address - pinned here
     * because the builder writes the same literal separately and the two must keep answering the same thing.
     */
    @Test
    fun `an assigned target and a delivered file carry their own click ids`() {
        val open = WearLaunchTarget.Open(WearTileTargetRef.Favourites).clickId()
        val file = WearLaunchTarget.File(path = "/sdcard/a.jpg", mimeType = "image/jpeg").clickId()

        assertEquals("open_target", open)
        assertEquals("open_file", file)
    }

    @Test
    fun `two resources of one kind share the assigned click id because one tile draws one area`() {
        val first = WearLaunchTarget.Open(resourceRef("a")).clickId()
        val second = WearLaunchTarget.Open(resourceRef("b")).clickId()

        assertEquals(first, second)
    }

    private fun resourceRef(id: String) = WearTileTargetRef.Resource(
        id = id,
        type = NetworkSourceType.SMB,
        server = "192.168.0.2",
        port = 445,
        shareName = "media",
        basePath = "/"
    )
}
