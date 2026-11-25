package com.sza.fastmediasorter.ui.cameracapture.helpers

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * S1261: the equivalent-multiplier rule against the Galaxy S25 FE numbers from research 01 - the
 * device whose raw focal ratios produced 0.32 instead of 0.57 (ultra-wide) and 1.30 instead of ~3
 * (tele). Reference lens: main back, 5.40 mm on a 5.76 mm-wide sensor.
 */
class LensEquivalentCalculatorTest {

    private val reference = LensEquivalentCalculator.Reference(
        focalMm = MAIN_FOCAL_MM,
        sensorWidthMm = MAIN_SENSOR_MM,
    )

    @Test
    fun `widest sub-lens of a sub-1x logical camera takes the parent floor exactly`() {
        val ultraWide = LensEquivalentCalculator.Lens(
            isBack = true,
            focalMm = ULTRA_WIDE_FOCAL_MM,
            sensorWidthMm = LensEquivalentCalculator.UNKNOWN_MM,
            parentLogicalMinZoom = PARENT_FLOOR,
            isWidestInParent = true,
        )
        // 0.57 exactly - the platform already solved the optics; 1.74/5.40 = 0.32 would be wrong.
        assertEquals(PARENT_FLOOR, LensEquivalentCalculator.multiplier(ultraWide, reference), 0f)
    }

    @Test
    fun `tele with known sensor widths goes through FOV normalization not the focal ratio`() {
        val tele = LensEquivalentCalculator.Lens(
            isBack = true,
            focalMm = TELE_FOCAL_MM,
            sensorWidthMm = TELE_SENSOR_MM,
            parentLogicalMinZoom = 1f,
            isWidestInParent = false,
        )
        // (7.00/2.489) / (5.40/5.76) = 3.0; the raw focal ratio would say 1.30.
        assertEquals(TELE_EXPECTED, LensEquivalentCalculator.multiplier(tele, reference), FOV_DELTA)
    }

    @Test
    fun `missing sensor data falls back to the raw focal ratio`() {
        val tele = LensEquivalentCalculator.Lens(isBack = true, focalMm = TELE_FOCAL_MM)
        val bareReference = LensEquivalentCalculator.Reference(focalMm = MAIN_FOCAL_MM)
        assertEquals(
            TELE_FOCAL_MM / MAIN_FOCAL_MM,
            LensEquivalentCalculator.multiplier(tele, bareReference),
            EXACT_DELTA,
        )
    }

    @Test
    fun `front lens stays at its native scale`() {
        val front = LensEquivalentCalculator.Lens(isBack = false, focalMm = FRONT_FOCAL_MM)
        assertEquals(1f, LensEquivalentCalculator.multiplier(front, reference), 0f)
    }

    @Test
    fun `unknown focal length stays neutral`() {
        val broken = LensEquivalentCalculator.Lens(isBack = true, focalMm = 0f)
        assertEquals(1f, LensEquivalentCalculator.multiplier(broken, reference), 0f)
    }

    @Test
    fun `non-widest lens ignores the parent floor and uses FOV`() {
        // The main lens inside the same sub-1x logical camera: floor 0.57 belongs to the ultra-wide
        // optics, not to it - it must read 1.0 (same focal, same sensor as the reference).
        val main = LensEquivalentCalculator.Lens(
            isBack = true,
            focalMm = MAIN_FOCAL_MM,
            sensorWidthMm = MAIN_SENSOR_MM,
            parentLogicalMinZoom = PARENT_FLOOR,
            isWidestInParent = false,
        )
        assertEquals(1f, LensEquivalentCalculator.multiplier(main, reference), EXACT_DELTA)
    }

    @Test
    fun `fused main with a sub-1x floor wins over a flash-bearing ultra-wide listed before it`() {
        // S4688, Galaxy S25+: standalone ultra-wide (floor 1.0, flash) ahead of the fused main (0.6).
        val backLogicals = listOf(
            Triple(1f, true, S25_PLUS_ULTRA_WIDE_EQ_MM),
            Triple(S25_PLUS_MAIN_FLOOR, true, S25_PLUS_MAIN_EQ_MM),
        )
        assertEquals(1, LensEquivalentCalculator.referenceIndex(backLogicals))
    }

    @Test
    fun `without a sub-1x floor the lens nearest the normal focal wins over flash and order`() {
        // An OEM that lists a flash-bearing ultra-wide first and exposes no zoom-out floor.
        val backLogicals = listOf(
            Triple(1f, true, S25_PLUS_ULTRA_WIDE_EQ_MM),
            Triple(1f, false, S25_PLUS_MAIN_EQ_MM),
            Triple(1f, true, TELE_EQ_MM),
        )
        assertEquals(1, LensEquivalentCalculator.referenceIndex(backLogicals))
    }

    @Test
    fun `a main whose sensor width is misreported still beats the ultra-wide`() {
        // S1261 S25 FE numbers: 5.40 mm on a reported 5.76 mm width reads as a 34 mm equivalent.
        val misreportedMain = LensEquivalentCalculator.equivalentFocalMm(MAIN_FOCAL_MM, MAIN_SENSOR_MM)
        val backLogicals = listOf(Triple(1f, true, S25_PLUS_ULTRA_WIDE_EQ_MM), Triple(1f, true, misreportedMain))
        assertEquals(1, LensEquivalentCalculator.referenceIndex(backLogicals))
    }

    @Test
    fun `without equivalents the first camera with a flash wins in platform order`() {
        val unknown = LensEquivalentCalculator.UNKNOWN_MM
        val backLogicals = listOf(Triple(1f, false, unknown), Triple(1f, true, unknown), Triple(1f, true, unknown))
        assertEquals(1, LensEquivalentCalculator.referenceIndex(backLogicals))
    }

    @Test
    fun `no floor, equivalent or flash falls back to the platform's first back camera`() {
        val unknown = LensEquivalentCalculator.UNKNOWN_MM
        val backLogicals = listOf(Triple(0f, false, unknown), Triple(1f, false, unknown))
        assertEquals(0, LensEquivalentCalculator.referenceIndex(backLogicals))
    }

    @Test
    fun `reporter's S25+ lens report elects camera 0 and labels ultra-wide 0_6x and tele 3x`() {
        // System info of the S4688 reporter: logical 0 = 5.40 mm on 8.2 mm, floor 0.60; standalone 2 =
        // 2.20 mm on 5.6 mm, floor 1.00; physical tele 6 = 7.00 mm on 3.6 mm. Before the fix: 2 started at 1.00.
        val main = Triple(S25_PLUS_FLOOR_REPORTED, true, LensEquivalentCalculator.equivalentFocalMm(5.40f, 8.2f))
        val ultraWide = Triple(1f, true, LensEquivalentCalculator.equivalentFocalMm(2.20f, 5.6f))
        assertEquals(0, LensEquivalentCalculator.referenceIndex(listOf(main, ultraWide)))
        val reference = LensEquivalentCalculator.Reference(focalMm = 5.40f, sensorWidthMm = 8.2f)
        val ultraWideLens = LensEquivalentCalculator.Lens(isBack = true, focalMm = 2.20f, sensorWidthMm = 5.6f)
        val teleLens = LensEquivalentCalculator.Lens(isBack = true, focalMm = 7.00f, sensorWidthMm = 3.6f)
        assertEquals(0.60f, LensEquivalentCalculator.multiplier(ultraWideLens, reference), FOV_DELTA)
        assertEquals(TELE_EXPECTED, LensEquivalentCalculator.multiplier(teleLens, reference), REPORT_DELTA)
    }

    @Test
    fun `no back camera elects nothing`() {
        assertEquals(-1, LensEquivalentCalculator.referenceIndex(emptyList()))
    }

    @Test
    fun `equivalent focal scales by the full-frame width and stays unknown without sensor data`() {
        assertEquals(FULL_FRAME_EQ_MM, LensEquivalentCalculator.equivalentFocalMm(MAIN_FOCAL_MM, MAIN_FOCAL_MM), 0f)
        assertEquals(0f, LensEquivalentCalculator.equivalentFocalMm(MAIN_FOCAL_MM, 0f), 0f)
    }

    private companion object {
        const val S25_PLUS_MAIN_FLOOR = 0.6f
        const val S25_PLUS_FLOOR_REPORTED = 0.60f

        /** The report's widths carry one decimal, so its tele lands at 2.95 rather than 3.0. */
        const val REPORT_DELTA = 0.06f

        /** EXIF of the S4688 report: main 23 mm, ultra-wide 13 mm; a 3x tele sits near 70 mm. */
        const val S25_PLUS_MAIN_EQ_MM = 23f
        const val S25_PLUS_ULTRA_WIDE_EQ_MM = 13f
        const val TELE_EQ_MM = 70f

        /** A lens as wide as its sensor gives exactly the full-frame width. */
        const val FULL_FRAME_EQ_MM = 36f
        const val MAIN_FOCAL_MM = 5.40f
        const val MAIN_SENSOR_MM = 5.76f
        const val ULTRA_WIDE_FOCAL_MM = 1.74f
        const val TELE_FOCAL_MM = 7.00f
        const val FRONT_FOCAL_MM = 3.22f

        /** Fabricated so the FOV ratio lands on the system camera's ~3x tele label. */
        const val TELE_SENSOR_MM = 2.489f
        const val PARENT_FLOOR = 0.57f
        const val TELE_EXPECTED = 3.0f
        const val FOV_DELTA = 0.01f
        const val EXACT_DELTA = 0.0001f
    }
}
