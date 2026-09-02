package com.sza.fastmediasorter.data.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * S2297: the caption composed from a tile-centre geocoder answer.
 *
 * The provider itself needs a device - `Geocoder` is a system service - so this pure function is the
 * only part of the coarsening whose correctness can be proven before the device test.
 */
class MapPlaceLabelCompositionTest {

    @Test
    fun `town wins over the coarser fields`() {
        assertEquals(
            "Odesa, Ukraine",
            composeCoarseLabel(
                locality = "Odesa",
                subAdminArea = "Odesa Raion",
                adminArea = "Odesa Oblast",
                countryName = "Ukraine",
            ),
        )
    }

    @Test
    fun `district carries the label when the town field is empty`() {
        assertEquals(
            "Odesa Raion, Ukraine",
            composeCoarseLabel(
                locality = null,
                subAdminArea = "Odesa Raion",
                adminArea = "Odesa Oblast",
                countryName = "Ukraine",
            ),
        )
    }

    @Test
    fun `region carries the label when town and district are empty`() {
        assertEquals(
            "Odesa Oblast, Ukraine",
            composeCoarseLabel(
                locality = null,
                subAdminArea = null,
                adminArea = "Odesa Oblast",
                countryName = "Ukraine",
            ),
        )
    }

    @Test
    fun `blank strings count as absent, not as an empty part`() {
        assertEquals(
            "Odesa Oblast, Ukraine",
            composeCoarseLabel(
                locality = "",
                subAdminArea = "   ",
                adminArea = "Odesa Oblast",
                countryName = "Ukraine",
            ),
        )
    }

    @Test
    fun `country alone is still a usable caption`() {
        assertEquals(
            "Ukraine",
            composeCoarseLabel(
                locality = null,
                subAdminArea = null,
                adminArea = null,
                countryName = "Ukraine",
            ),
        )
    }

    @Test
    fun `place alone is still a usable caption`() {
        assertEquals(
            "Odesa",
            composeCoarseLabel(
                locality = "Odesa",
                subAdminArea = null,
                adminArea = null,
                countryName = "  ",
            ),
        )
    }

    @Test
    fun `nothing usable yields null so the caller falls back to coordinates`() {
        assertNull(
            composeCoarseLabel(
                locality = null,
                subAdminArea = "",
                adminArea = "   ",
                countryName = null,
            ),
        )
    }
}
