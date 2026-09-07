package com.sza.fastmediasorter.wear.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * S2694: the empty path is the trap this guards.
 *
 * An SMB share root is legitimately the empty string, while the parser refuses an empty value for
 * the two local schemes. Refusing it here too would not surface as a parse failure - the walk falls
 * back to the local root, and the wearer gets the watch's own storage where the share was asked for.
 */
class WearFolderAddressNetworkTest {

    private val sourceId = "3f2a51e0-0d1c-4a6b-9f77-2b8c1d0e5a44"

    @Test
    fun `a network level round-trips`() {
        val address = WearFolderAddress.NetworkLevel(sourceId, "photos/2024")

        assertEquals(address, WearFolderAddress.parse(address.asToken()))
    }

    @Test
    fun `an empty path round-trips rather than parsing to null`() {
        val address = WearFolderAddress.NetworkLevel(sourceId, "")

        val parsed = WearFolderAddress.parse(address.asToken())

        assertEquals(address, parsed)
    }

    @Test
    fun `an absolute path with separators survives`() {
        val address = WearFolderAddress.NetworkLevel(sourceId, "/srv/media/2024 trip")

        assertEquals(address, WearFolderAddress.parse(address.asToken()))
    }

    @Test
    fun `a token carrying no source identifier is refused`() {
        assertNull(WearFolderAddress.parse("n::photos"))
        assertNull(WearFolderAddress.parse("n:"))
    }

    @Test
    fun `the local schemes are untouched`() {
        assertEquals(WearFolderAddress.Root, WearFolderAddress.parse(""))
        assertEquals(
            WearFolderAddress.AppOwned("/data/media"),
            WearFolderAddress.parse(WearFolderAddress.AppOwned("/data/media").asToken())
        )
        assertEquals(
            WearFolderAddress.MediaStoreFolder("DCIM/Camera/"),
            WearFolderAddress.parse(WearFolderAddress.MediaStoreFolder("DCIM/Camera/").asToken())
        )
    }
}
