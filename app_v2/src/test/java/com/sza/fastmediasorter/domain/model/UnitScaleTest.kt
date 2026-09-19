package com.sza.fastmediasorter.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * S3101: the conversions are the only arithmetic in the format seam, and a wrong coefficient fails
 * silently - a plausible number is still a wrong one, so each one is pinned rather than reviewed.
 */
class UnitScaleTest {

    @Test
    fun `kmh converts to mph`() {
        assertEquals(62.1371, UnitScale.kmhToMph(100.0), DELTA)
    }

    @Test
    fun `zero kmh stays zero`() {
        assertEquals(0.0, UnitScale.kmhToMph(0.0), DELTA)
    }

    @Test
    fun `freezing point converts to 32 fahrenheit`() {
        assertEquals(32.0, UnitScale.celsiusToFahrenheit(0.0), DELTA)
    }

    @Test
    fun `boiling point converts to 212 fahrenheit`() {
        assertEquals(212.0, UnitScale.celsiusToFahrenheit(100.0), DELTA)
    }

    @Test
    fun `minus forty is the same on both scales`() {
        assertEquals(-40.0, UnitScale.celsiusToFahrenheit(-40.0), DELTA)
    }

    private companion object {
        const val DELTA = 0.001
    }
}
