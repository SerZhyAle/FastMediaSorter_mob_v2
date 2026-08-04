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

    private companion object {
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
