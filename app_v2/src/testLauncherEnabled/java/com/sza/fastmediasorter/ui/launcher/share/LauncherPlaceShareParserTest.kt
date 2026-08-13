package com.sza.fastmediasorter.ui.launcher.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * S1175: the Share sheet is the only way a place enters the app, and Maps sends at least four payload
 * shapes through it. A misread payload does not fail - it creates a cell that opens the wrong thing,
 * which no build check and no glance at the desktop can distinguish from a correct one.
 *
 * S1585: the priority between a link and the caption beside it is asserted here, because getting it
 * backwards is exactly what shipped - the caption was handed to Maps as a destination and opened a
 * search.
 *
 * Robolectric rather than plain JUnit: the URL split runs through `android.util.Patterns`, which
 * returns null under the module's `isReturnDefaultValues`, so every assertion would hold for the
 * wrong reason.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LauncherPlaceShareParserTest {

    @Test
    fun `coordinates in the payload name the point outright`() {
        val shape = LauncherPlaceShareParser.parse("49.8397, 24.0297")

        assertTrue(shape is LauncherPlaceShareParser.Shape.Coordinates)
        assertEquals("49.8397, 24.0297", (shape as LauncherPlaceShareParser.Shape.Coordinates).query)
    }

    @Test
    fun `a named place with coordinates keeps the name as the caption`() {
        val shape = LauncherPlaceShareParser.parse(
            "Lviv Opera\n49.8397, 24.0297\nhttps://maps.app.goo.gl/opera",
        )

        assertTrue(shape is LauncherPlaceShareParser.Shape.Coordinates)
        val coordinates = shape as LauncherPlaceShareParser.Shape.Coordinates
        assertEquals("49.8397, 24.0297", coordinates.query)
        assertEquals("Lviv Opera", coordinates.label)
    }

    @Test
    fun `a maps link outranks the caption travelling with it`() {
        val shape = LauncherPlaceShareParser.parse(
            "Lviv Opera\nhttps://maps.app.goo.gl/opera",
        )

        assertTrue(shape is LauncherPlaceShareParser.Shape.MapsLink)
        val link = shape as LauncherPlaceShareParser.Shape.MapsLink
        assertEquals("https://maps.app.goo.gl/opera", link.link)
        assertEquals("Lviv Opera", link.label)
    }

    @Test
    fun `a bare maps link is reported as resolvable`() {
        val shape = LauncherPlaceShareParser.parse("https://maps.app.goo.gl/Ep9BAYWhvoDUL7BN6")

        assertTrue(shape is LauncherPlaceShareParser.Shape.MapsLink)
        assertEquals(
            "https://maps.app.goo.gl/Ep9BAYWhvoDUL7BN6",
            (shape as LauncherPlaceShareParser.Shape.MapsLink).link,
        )
    }

    @Test
    fun `a maps link keeps its tracking query string`() {
        val shared = "https://maps.app.goo.gl/Ep9BAYWhvoDUL7BN6?g_st=atm"

        val shape = LauncherPlaceShareParser.parse(shared)

        assertTrue(shape is LauncherPlaceShareParser.Shape.MapsLink)
        assertEquals(shared, (shape as LauncherPlaceShareParser.Shape.MapsLink).link)
    }

    @Test
    fun `free text with no link stays the destination`() {
        val shape = LauncherPlaceShareParser.parse("Lviv Opera")

        assertTrue(shape is LauncherPlaceShareParser.Shape.PlaceText)
        assertEquals("Lviv Opera", (shape as LauncherPlaceShareParser.Shape.PlaceText).query)
    }

    @Test
    fun `a non-maps link is not treated as resolvable`() {
        val shape = LauncherPlaceShareParser.parse("https://example.com/place")

        assertTrue(shape is LauncherPlaceShareParser.Shape.PlaceText)
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
