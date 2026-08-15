package com.sza.fastmediasorter.data.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * S1585: covers coordinate extraction from a resolved Maps URL - the only part of resolution that
 * can be checked without a network.
 */
class HttpMapsShortLinkResolverTest {

    @Test
    fun `extracts the point from a resolved place URL`() {
        val resolved = "https://www.google.com/maps/place/48.444152,22.717282/data=!4m6!3m5!1s0" +
            "?utm_source=mstt_1&entry=gps"

        val point = HttpMapsShortLinkResolver.pointFrom(resolved)

        assertEquals(48.444152, point?.latitude!!, TOLERANCE)
        assertEquals(22.717282, point.longitude, TOLERANCE)
    }

    @Test
    fun `extracts negative coordinates`() {
        val resolved = "https://www.google.com/maps/place/-33.865143,-151.209900/data=x"

        val point = HttpMapsShortLinkResolver.pointFrom(resolved)

        assertEquals(-33.865143, point?.latitude!!, TOLERANCE)
        assertEquals(-151.209900, point.longitude, TOLERANCE)
    }

    @Test
    fun `returns null when the resolved URL carries no coordinates`() {
        val resolved = "https://www.google.com/maps/search/coffee+shop/data=!4m2"

        assertNull(HttpMapsShortLinkResolver.pointFrom(resolved))
    }

    @Test
    fun `returns null for coordinates outside the earth`() {
        val resolved = "https://www.google.com/maps/place/91.5,200.25/data=x"

        assertNull(HttpMapsShortLinkResolver.pointFrom(resolved))
    }

    @Test
    fun `returns null for an empty payload`() {
        assertNull(HttpMapsShortLinkResolver.pointFrom(""))
    }

    private companion object {
        const val TOLERANCE = 0.000001
    }
}
