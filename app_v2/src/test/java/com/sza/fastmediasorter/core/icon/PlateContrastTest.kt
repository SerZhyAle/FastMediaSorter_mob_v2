package com.sza.fastmediasorter.core.icon

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The same cases the contract's palette.json records for the product's plates, so the Kotlin rule and
 * the exporter's copy of it cannot drift apart unnoticed.
 */
class PlateContrastTest {

    @Test
    fun `black on white measures the WCAG maximum`() {
        assertEquals(21.0, PlateContrast.contrastRatio(0xFF000000.toInt(), 0xFFFFFFFF.toInt()), 0.01)
    }

    @Test
    fun `amber is too light for a white glyph`() {
        assertEquals(PlateContrast.ON_PLATE_DARK, PlateContrast.onPlateColour(AMBER_700))
        assertEquals(PlateContrast.ON_PLATE_DARK, PlateContrast.onPlateColour(ORANGE_700))
    }

    @Test
    fun `the product plates keep one white glyph`() {
        listOf(BLUE_700, CYAN_700, ORANGE_900, GREEN_700, PURPLE_600).forEach {
            assertEquals(Integer.toHexString(it), PlateContrast.ON_PLATE_LIGHT, PlateContrast.onPlateColour(it))
        }
    }

    @Test
    fun `the chosen glyph always reaches the floor`() {
        val sweep = (0..255 step 15).flatMap { r ->
            (0..255 step 51).map { g -> 0xFF000000.toInt() or (r shl 16) or (g shl 8) or 0x80 }
        }
        sweep.forEach {
            val ratio = PlateContrast.contrastRatio(it, PlateContrast.onPlateColour(it))
            assertTrue("${Integer.toHexString(it)} measured $ratio", ratio >= PlateContrast.MIN_CONTRAST)
        }
    }

    private companion object {
        const val AMBER_700 = 0xFFFFA000.toInt()
        const val ORANGE_700 = 0xFFF57C00.toInt()
        const val BLUE_700 = 0xFF1976D2.toInt()
        const val CYAN_700 = 0xFF0097A7.toInt()
        const val ORANGE_900 = 0xFFE65100.toInt()
        const val GREEN_700 = 0xFF388E3C.toInt()
        const val PURPLE_600 = 0xFF8E24AA.toInt()
    }
}
