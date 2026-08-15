package com.sza.fastmediasorter.domain.model.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S1205: the stored target of a pinned foreign shortcut is a persistence format, and its label is
 * data a third-party app wrote. These cases pin down that a label containing the field separator
 * cannot corrupt the record, and that every malformed payload decodes to null instead of throwing.
 */
class LauncherCellCommandTest {

    @Test
    fun `pinned shortcut survives a label carrying the separator and a newline`() {
        val original = LauncherCellCommand.PinnedShortcut(
            packageName = "com.google.android.apps.maps",
            shortcutId = "place:home",
            label = "Home: work\nroute",
        )

        val decoded = LauncherCellCommand.decode(original.encode())

        assertEquals(original, decoded)
    }

    @Test
    fun `pinned shortcut keeps an empty label`() {
        val original = LauncherCellCommand.PinnedShortcut("com.example", "id-1", "")

        assertEquals(original, LauncherCellCommand.decode(original.encode()))
    }

    @Test
    fun `empty pin payload decodes to null`() {
        assertNull(LauncherCellCommand.decode("pin:"))
    }

    @Test
    fun `pin payload with a missing field decodes to null`() {
        assertNull(LauncherCellCommand.decode("pin:com.example:id-1"))
    }

    @Test
    fun `pin payload without a package decodes to null`() {
        assertNull(LauncherCellCommand.decode("pin::id-1:Label"))
    }

    @Test
    fun `unknown prefix decodes to null`() {
        assertNull(LauncherCellCommand.decode("pinned:com.example:id-1:Label"))
    }

    @Test
    fun `section survives a round trip`() {
        val original = LauncherCellCommand.Section(LauncherCellCommand.SECTION_APP_FUNCTIONS)

        assertEquals(original, LauncherCellCommand.decode(original.encode()))
    }

    @Test
    fun `empty section payload decodes to null`() {
        assertNull(LauncherCellCommand.decode("sec:"))
    }

    @Test
    fun `geographic command survives separator query and label`() {
        val original = LauncherCellCommand.Geographic(
            action = LauncherGeographicAction.NAVIGATION,
            query = "47.4979,19.0402:destination",
            label = "Budapest: centre\nroute",
        )

        assertEquals(original, LauncherCellCommand.decode(original.encode()))
    }

    @Test
    fun `geographic command with an unknown action decodes to null`() {
        assertNull(LauncherCellCommand.decode("geo:UNKNOWN:47.4979%2C19.0402:Budapest"))
    }

    @Test
    fun `geographic command with a blank query decodes to null`() {
        assertNull(LauncherCellCommand.decode("geo:DIRECTIONS::Budapest"))
    }

    /**
     * S1616: the query a share keeps when its Maps link could not be resolved is a link, and opening
     * it as a search term is what showed a search for the URL text instead of the place.
     */
    @Test
    fun `geographic command recognises a link query`() {
        assertTrue(showPlace("https://maps.app.goo.gl/Ep9BAYWhvoDUL7BN6").isWebLinkQuery)
        assertTrue(showPlace("HTTP://goo.gl/maps/abc").isWebLinkQuery)
    }

    @Test
    fun `geographic command does not mistake a place for a link`() {
        assertFalse(showPlace("47.4979,19.0402").isWebLinkQuery)
        assertFalse(showPlace("Lviv Opera").isWebLinkQuery)
        assertFalse(showPlace("https-street 4").isWebLinkQuery)
    }

    private fun showPlace(query: String) = LauncherCellCommand.Geographic(
        action = LauncherGeographicAction.SHOW_PLACE,
        query = query,
        label = "",
    )
}
