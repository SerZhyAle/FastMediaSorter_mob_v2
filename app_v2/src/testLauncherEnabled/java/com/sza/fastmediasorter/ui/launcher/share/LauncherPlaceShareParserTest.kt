package com.sza.fastmediasorter.ui.launcher.share

import com.sza.fastmediasorter.domain.model.launcher.LauncherGeographicAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * S1175: the Share sheet is the only way a place enters the app, and Maps sends at least four payload
 * shapes through it. A misread payload does not fail - it creates a cell that opens the wrong thing,
 * which no build check and no glance at the desktop can distinguish from a correct one.
 *
 * Robolectric rather than plain JUnit: the URL split runs through `android.util.Patterns`, which
 * returns null under the module's `isReturnDefaultValues`, so every assertion would hold for the
 * wrong reason.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LauncherPlaceShareParserTest {

    @Test
    fun `coordinates in the payload become a route to those coordinates`() {
        val command = LauncherPlaceShareParser.parse("49.8397, 24.0297")

        assertEquals(LauncherGeographicAction.DIRECTIONS, command?.action)
        assertEquals("49.8397, 24.0297", command?.query)
    }

    @Test
    fun `a named place with coordinates keeps the name as the caption`() {
        val command = LauncherPlaceShareParser.parse(
            "Lviv Opera\n49.8397, 24.0297\nhttps://maps.app.goo.gl/opera",
        )

        assertEquals(LauncherGeographicAction.DIRECTIONS, command?.action)
        assertEquals("49.8397, 24.0297", command?.query)
        assertEquals("Lviv Opera", command?.label)
    }

    @Test
    fun `free text next to a link becomes a route to that text`() {
        val command = LauncherPlaceShareParser.parse(
            "Lviv Opera\nhttps://maps.app.goo.gl/opera",
        )

        assertEquals(LauncherGeographicAction.DIRECTIONS, command?.action)
        assertEquals("Lviv Opera", command?.query)
        assertEquals("Lviv Opera", command?.label)
    }

    @Test
    fun `a bare link degrades to showing the place instead of faking a route`() {
        val command = LauncherPlaceShareParser.parse("https://maps.app.goo.gl/opera")

        assertEquals(LauncherGeographicAction.SHOW_PLACE, command?.action)
        assertEquals("https://maps.app.goo.gl/opera", command?.query)
        assertEquals("https://maps.app.goo.gl/opera", command?.label)
    }

    @Test
    fun `a blank payload places nothing`() {
        assertNull(LauncherPlaceShareParser.parse("   \n  "))
    }

    @Test
    fun `an absent payload places nothing`() {
        assertNull(LauncherPlaceShareParser.parse(null))
    }
}
