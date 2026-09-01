package com.sza.fastmediasorter.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * S2292: this object decides how much of the user's position leaves the app - the live map frame
 * publishes its output to Google. A regression here is invisible on a device, because a frame
 * centred on the exact point and one centred on the tile look identical, so the precision has to be
 * asserted rather than looked at.
 */
class WebMercatorTileTest {

    @Test
    fun `a coarsened position addresses the same tile as the precise one`() {
        for ((latitude, longitude) in SAMPLES) {
            assertEquals(
                "latitude $latitude changed tile",
                WebMercatorTile.tileY(latitude, ZOOM),
                WebMercatorTile.tileY(WebMercatorTile.coarseLatitude(latitude, ZOOM), ZOOM),
            )
            assertEquals(
                "longitude $longitude changed tile",
                WebMercatorTile.tileX(longitude, ZOOM),
                WebMercatorTile.tileX(WebMercatorTile.coarseLongitude(longitude, ZOOM), ZOOM),
            )
        }
    }

    @Test
    fun `positions sharing a tile coarsen to one indistinguishable value`() {
        val centre = WebMercatorTile.centreLongitude(WebMercatorTile.tileX(SAMPLE_LONGITUDE, ZOOM), ZOOM)
        val offset = TILE_SPAN_DEGREES / 10
        assertEquals(centre, WebMercatorTile.coarseLongitude(centre - offset, ZOOM), 0.0)
        assertEquals(centre, WebMercatorTile.coarseLongitude(centre + offset, ZOOM), 0.0)
    }

    @Test
    fun `coarsening removes precision rather than passing the value through`() {
        assertNotEquals(SAMPLE_LONGITUDE, WebMercatorTile.coarseLongitude(SAMPLE_LONGITUDE, ZOOM), 0.0)
    }

    @Test
    fun `coarsening never moves a position further than one tile`() {
        for ((latitude, longitude) in SAMPLES) {
            val movedLongitude = abs(WebMercatorTile.coarseLongitude(longitude, ZOOM) - longitude)
            val movedLatitude = abs(WebMercatorTile.coarseLatitude(latitude, ZOOM) - latitude)
            assertTrue("longitude $longitude moved $movedLongitude", movedLongitude <= TILE_SPAN_DEGREES)
            assertTrue("latitude $latitude moved $movedLatitude", movedLatitude <= TILE_SPAN_DEGREES)
        }
    }

    @Test
    fun `a tile centre sits half a tile east of the tile's western edge`() {
        assertEquals(TILE_SPAN_DEGREES / 2, WebMercatorTile.centreLongitude(TILE_COUNT / 2, ZOOM), TOLERANCE)
    }

    @Test
    fun `the poles and the antimeridian stay inside the tile grid`() {
        assertEquals(TILE_COUNT - 1, WebMercatorTile.tileX(180.0, ZOOM))
        assertEquals(0, WebMercatorTile.tileX(-180.0, ZOOM))
        assertEquals(TILE_COUNT - 1, WebMercatorTile.tileY(-90.0, ZOOM))
        assertEquals(0, WebMercatorTile.tileY(90.0, ZOOM))
    }

    private companion object {
        /** The zoom both map gadgets frame at, and therefore the precision class they must share. */
        const val ZOOM = 15
        const val TILE_COUNT = 32768
        const val TILE_SPAN_DEGREES = 360.0 / TILE_COUNT
        const val SAMPLE_LONGITUDE = 30.523456789
        const val TOLERANCE = 1e-12

        /** Equator, both hemispheres, both sides of the prime meridian, and a high northern latitude. */
        val SAMPLES = listOf(
            0.0 to 0.0,
            50.450100 to 30.523400,
            -33.868800 to 151.209300,
            71.170800 to -156.766600,
            -54.801900 to -68.302900,
        )
    }
}
