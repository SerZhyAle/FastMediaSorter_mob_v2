package com.sza.fastmediasorter.wear.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * S1955: a tile's memory of what it points at, tested where it can be got wrong.
 *
 * Both halves are defects that only ever appear after something else happened - a phone sync that recreated
 * the row, or a catalog re-import - so neither is reachable by running the app once. Pinning them here is
 * what makes them provable without a watch.
 */
class WearTileTargetRefTest {

    private fun source(
        id: String,
        server: String = "192.168.1.100",
        shareName: String? = "Common",
        basePath: String = "/photos"
    ) = NetworkSource(
        id = id,
        type = NetworkSourceType.SMB,
        name = "n-$id",
        server = server,
        port = 445,
        username = "user",
        password = "pw",
        shareName = shareName,
        basePath = basePath
    )

    private fun refOf(
        id: String,
        server: String = "192.168.1.100",
        shareName: String? = "Common",
        basePath: String = "/photos"
    ) = WearTileTargetRef.Resource(
        id = id,
        type = NetworkSourceType.SMB,
        server = server,
        port = 445,
        shareName = shareName,
        basePath = basePath
    )

    @Test
    fun `a resource ref finds its source by id`() {
        val stored = listOf(source(id = "other", basePath = "/videos"), source(id = "wanted"))

        assertEquals(
            "an id hit is the exact answer and must win",
            "wanted",
            stored.findByTargetRef(refOf(id = "wanted"))?.id
        )
    }

    @Test
    fun `a resource ref whose id is gone still finds the source by its feature tuple`() {
        // The shape of a phone sync that recreated the row: same resource, new primary key.
        val stored = listOf(source(id = "regenerated-by-sync"))

        assertEquals(
            "the tuple is the durable identity when the id no longer exists",
            "regenerated-by-sync",
            stored.findByTargetRef(refOf(id = "id-the-phone-has-thrown-away"))?.id
        )
    }

    @Test
    fun `a resource ref matching neither id nor tuple resolves to nothing`() {
        val stored = listOf(source(id = "kept"))

        assertNull(
            "a deleted resource must read as gone rather than as some other resource",
            stored.findByTargetRef(refOf(id = "deleted", server = "10.0.0.9", basePath = "/gone"))
        )
    }

    @Test
    fun `two spellings of one stream address are one ref`() {
        val typed = streamTargetRef("HTTP://Host.Example:80/live/")
        val stored = streamTargetRef("http://host.example/live")

        assertEquals(
            "scheme case, the default port and a trailing slash must not split one channel in two",
            stored,
            typed
        )
    }
}
