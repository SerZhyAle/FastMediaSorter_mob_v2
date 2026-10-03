package com.sza.fastmediasorter.wear.complication

import com.sza.fastmediasorter.wear.domain.model.PhoneBatteryReport
import com.sza.fastmediasorter.wear.domain.model.WearAnimationPalette
import com.sza.fastmediasorter.wear.domain.model.WearBackground
import com.sza.fastmediasorter.wear.domain.model.WearClockStyle
import com.sza.fastmediasorter.wear.domain.model.WearClockTypeface
import com.sza.fastmediasorter.wear.domain.model.WearFaceBackdrop
import com.sza.fastmediasorter.wear.domain.repository.WearPhoneBatteryRepository
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The watch face decodes the same layout declaratively (`code % 2`, `(code % 10) / 2`, `code / 10`),
 * so these tests pin the layout itself, not only this encoder's round trip.
 */
class WearClockStyleFaceEncoderTest {

    private val base = WearClockStyle.DEFAULT.copy(sentAt = 1_700_000_000_000L)

    @Test
    fun `phone percent and charging decode for every style digit and percentage`() {
        for (percent in 0..100) {
            for (charging in listOf(false, true)) {
                val report = PhoneBatteryReport(percent, charging, base.sentAt)
                val band = WearClockStyleFaceEncoder.phoneBatteryBand(report, base.sentAt)
                assertEquals(percent + if (charging) 101 else 0, band)
                for (styleDigits in 0..159) {
                    val code = styleDigits + WearClockStyleFaceEncoder.PHONE_BAND_WEIGHT * band
                    val decodedBand = code.coerceIn(0, 102159) / 400
                    assertTrue(code in WearClockStyleFaceEncoder.CODE_MIN..WearClockStyleFaceEncoder.CODE_MAX)
                    assertEquals(percent, decodedBand % 101)
                    assertEquals(charging, decodedBand in 101..201)
                    assertEquals(styleDigits, code - decodedBand * 400)
                }
            }
        }
    }

    @Test
    fun `absent and stale phone reports retain the empty track sentinel`() {
        assertEquals(255, WearClockStyleFaceEncoder.phoneBatteryBand(null, base.sentAt))
        for (charging in listOf(false, true)) {
            val report = PhoneBatteryReport(100, charging, base.sentAt)
            val boundary = base.sentAt + WearPhoneBatteryRepository.STALE_AFTER_MS
            assertEquals(if (charging) 201 else 100, WearClockStyleFaceEncoder.phoneBatteryBand(report, boundary))
            assertEquals(255, WearClockStyleFaceEncoder.phoneBatteryBand(report, boundary + 1))
        }
    }

    @Test
    fun `malformed percent is clamped without colliding with the stale band`() {
        for (charging in listOf(false, true)) {
            val offset = if (charging) 101 else 0
            assertEquals(
                offset,
                WearClockStyleFaceEncoder.phoneBatteryBand(PhoneBatteryReport(-1, charging, base.sentAt), base.sentAt)
            )
            assertEquals(
                100 + offset,
                WearClockStyleFaceEncoder.phoneBatteryBand(PhoneBatteryReport(101, charging, base.sentAt), base.sentAt)
            )
        }
    }

    @Test
    fun `every seconds, typeface and palette combination decodes back as the face reads it`() {
        for (palette in WearAnimationPalette.entries) {
            for (typeface in WearClockTypeface.entries) {
                for (seconds in listOf(false, true)) {
                    val code = WearClockStyleFaceEncoder.code(
                        base.copy(secondsVisible = seconds, typeface = typeface, palette = palette)
                    )

                    assertTrue(code in WearClockStyleFaceEncoder.CODE_MIN..WearClockStyleFaceEncoder.CODE_MAX)
                    assertEquals(if (seconds) 1 else 0, code % 2)
                    assertEquals(typeface.ordinal, (code % 10) / 2)
                    assertEquals(palette.ordinal, code / 10)
                }
            }
        }
    }

    @Test
    fun `the frozen indices match the documented layout`() {
        assertEquals(0, WearClockStyleFaceEncoder.code(base))
        assertEquals(1, WearClockStyleFaceEncoder.code(base.copy(secondsVisible = true)))
        assertEquals(2, WearClockStyleFaceEncoder.code(base.copy(typeface = WearClockTypeface.CONDENSED)))
        assertEquals(4, WearClockStyleFaceEncoder.code(base.copy(typeface = WearClockTypeface.SERIF)))
        assertEquals(6, WearClockStyleFaceEncoder.code(base.copy(typeface = WearClockTypeface.MONOSPACE)))
        assertEquals(8, WearClockStyleFaceEncoder.code(base.copy(typeface = WearClockTypeface.CASUAL)))
        assertEquals(10, WearClockStyleFaceEncoder.code(base.copy(palette = WearAnimationPalette.GREEN)))
        assertEquals(20, WearClockStyleFaceEncoder.code(base.copy(palette = WearAnimationPalette.PINK)))
        assertEquals(30, WearClockStyleFaceEncoder.code(base.copy(palette = WearAnimationPalette.BLUE)))
    }

    @Test
    fun `the backdrop digit sits above the style digits and leaves them readable`() {
        val style = base.copy(
            secondsVisible = true,
            typeface = WearClockTypeface.CASUAL,
            palette = WearAnimationPalette.BLUE
        )
        for (backdrop in WearFaceBackdrop.entries) {
            val code = WearClockStyleFaceEncoder.code(style, backdrop)

            assertTrue(code in WearClockStyleFaceEncoder.CODE_MIN..WearClockStyleFaceEncoder.CODE_MAX)
            assertEquals(backdrop.ordinal, code / 40)
            assertEquals(WearClockStyleFaceEncoder.code(style), code % 40)
        }
        assertEquals(80, WearClockStyleFaceEncoder.code(base, WearFaceBackdrop.NONE))
        assertEquals(120, WearClockStyleFaceEncoder.code(base, WearFaceBackdrop.PHOTO))
    }

    @Test
    fun `the face backdrop follows the app backdrop and the animations switch`() {
        val frame = WearBackground.Image(java.io.File("frame.jpg"), lastModified = 1L)
        assertEquals(WearFaceBackdrop.ANIMATION, WearFaceBackdrop.of(WearBackground.BrandedAnimation, false))
        assertEquals(WearFaceBackdrop.STILL, WearFaceBackdrop.of(WearBackground.BrandedAnimation, true))
        assertEquals(WearFaceBackdrop.STILL, WearFaceBackdrop.of(WearBackground.BrandedStill, false))
        assertEquals(WearFaceBackdrop.NONE, WearFaceBackdrop.of(WearBackground.None, false))
        assertEquals(WearFaceBackdrop.PHOTO, WearFaceBackdrop.of(frame, false))
        assertEquals(WearFaceBackdrop.PHOTO, WearFaceBackdrop.of(frame, true))
    }

    @Test
    fun `a null dial colour is white and a set one passes through`() {
        assertEquals(0xFFFFFFFF.toInt(), WearClockStyleFaceEncoder.colors(base)[0])
        val red = 0xFFFF0000.toInt()
        assertEquals(red, WearClockStyleFaceEncoder.colors(base.copy(dialColor = red))[0])
    }

    @Test
    fun `the ramp holds the dial, four lanes and one particle colour, all opaque`() {
        val colors = WearClockStyleFaceEncoder.colors(base.copy(palette = WearAnimationPalette.GREEN))

        assertEquals(WearClockStyleFaceEncoder.COLOR_COUNT, colors.size)
        colors.forEach { assertEquals(0xFF, it ushr 24) }
    }

    @Test
    fun `lane hues stay inside each banded palette`() {
        // WAVE-PARTICLES section 3.3: base in the band, step at most 6 degrees, so lane 3 ends at most
        // 18 degrees past the band's top.
        val bands = mapOf(
            WearAnimationPalette.GREEN to 95f..(130f + 18f),
            WearAnimationPalette.PINK to 305f..(335f + 18f),
            WearAnimationPalette.BLUE to 200f..(230f + 18f)
        )
        for ((palette, band) in bands) {
            for (seed in 0L until 50L) {
                val hues = WearClockStyleFaceEncoder.laneHues(base.copy(palette = palette, sentAt = seed))
                hues.forEach { hue ->
                    // PINK wraps past 360; unwrap before comparing with the band.
                    val unwrapped = if (hue < band.start - 180f) hue + 360f else hue
                    assertTrue("$palette seed $seed hue $hue outside $band", unwrapped in band)
                }
            }
        }
    }

    @Test
    fun `identical sentAt gives identical colours`() {
        val style = base.copy(palette = WearAnimationPalette.DYNAMIC, sentAt = 123_456L)

        assertArrayEquals(WearClockStyleFaceEncoder.colors(style), WearClockStyleFaceEncoder.colors(style.copy()))
    }
}
