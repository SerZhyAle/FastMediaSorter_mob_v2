package com.sza.fastmediasorter.wear.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The address travels as a navigation argument, so its token has to survive the trip.
 *
 * `WearRoutes.encodeArg` exists because an unencoded argument value silently truncated a route or
 * missed its pattern entirely - no error, no log, and the watch module has no instrumented test that
 * would notice. A folder name legally contains a space, an `&` and a `/`, so the round trip is pinned
 * here rather than discovered on a device.
 */
class WearFolderAddressTest {

    @Test
    fun `root round-trips through its token`() {
        assertEquals(WearFolderAddress.Root, WearFolderAddress.parse(WearFolderAddress.Root.asToken()))
    }

    @Test
    fun `an absent token is the root rather than a failure`() {
        assertEquals(WearFolderAddress.Root, WearFolderAddress.parse(null))
    }

    @Test
    fun `an app-owned path containing a space round-trips`() {
        val address = WearFolderAddress.AppOwned("/data/user/0/app/files/My Notes")
        assertEquals(address, WearFolderAddress.parse(address.asToken()))
    }

    @Test
    fun `a media-store path containing an ampersand round-trips`() {
        val address = WearFolderAddress.MediaStoreFolder("Music/Rock & Roll/")
        assertEquals(address, WearFolderAddress.parse(address.asToken()))
    }

    @Test
    fun `a media-store path keeps its trailing separator`() {
        val parsed = WearFolderAddress.parse(WearFolderAddress.MediaStoreFolder("DCIM/Camera/").asToken())
        assertEquals("DCIM/Camera/", (parsed as WearFolderAddress.MediaStoreFolder).relativePath)
    }

    @Test
    fun `an unknown scheme names no address`() {
        assertNull(WearFolderAddress.parse("z:/somewhere"))
    }

    @Test
    fun `a scheme with no value names no address`() {
        assertNull(WearFolderAddress.parse("f:"))
    }
}
