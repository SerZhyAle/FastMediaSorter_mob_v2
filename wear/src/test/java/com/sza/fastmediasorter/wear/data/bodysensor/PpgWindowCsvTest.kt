package com.sza.fastmediasorter.wear.data.bodysensor

import com.sza.fastmediasorter.wear.domain.bodysensor.MotionSample
import com.sza.fastmediasorter.wear.domain.bodysensor.PpgSample
import com.sza.fastmediasorter.wear.domain.bodysensor.PpgWindow
import com.sza.fastmediasorter.wear.domain.bodysensor.PulseWaveLayout
import org.junit.Assert.assertEquals
import org.junit.Test

class PpgWindowCsvTest {

    @Test
    fun `a formatted window parses back to the same window`() {
        val window = PpgWindow(
            startedAtMillis = STARTED_AT,
            ppg = listOf(
                PpgSample(timestampNanos = FIRST_NANOS, channels = listOf(FIRST_VALUE, SECOND_VALUE, 0f)),
                PpgSample(timestampNanos = SECOND_NANOS, channels = listOf(-SECOND_VALUE))
            ),
            motion = listOf(MotionSample(timestampNanos = FIRST_NANOS, magnitude = GRAVITY)),
            layout = LAYOUT
        )

        assertEquals(window, PpgWindowCsv.parse(PpgWindowCsv.format(window)))
    }

    @Test
    fun `a row of an unknown kind is skipped`() {
        val empty = PpgWindow(STARTED_AT, emptyList(), emptyList(), LAYOUT)
        val text = PpgWindowCsv.format(empty) + "future,1,2\n"

        assertEquals(empty, PpgWindowCsv.parse(text))
    }

    @Test
    fun `a version 1 file is read with its counts decoded from float bits`() {
        val text = "# fms-ppg-window v1 startedAtMillis=$STARTED_AT\nppg,$FIRST_NANOS,1.4E-45,2.938736E-39\n"

        val window = PpgWindowCsv.parse(text)

        assertEquals(listOf(1f, BIT_ENCODED_COUNT), window.ppg.single().channels)
        assertEquals(PulseWaveLayout(channel = V1_CHANNEL, inverted = true), window.layout)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `text without the header is refused`() {
        PpgWindowCsv.parse("ppg,1,2\n")
    }

    private companion object {
        const val STARTED_AT = 1_789_000_000_000L
        const val FIRST_NANOS = 123_456_789_000L
        const val SECOND_NANOS = 123_496_789_000L
        const val FIRST_VALUE = 1_985_769f
        const val SECOND_VALUE = 0.1f
        const val GRAVITY = 9.80665f
        const val BIT_ENCODED_COUNT = 2_097_152f
        const val V1_CHANNEL = 5
        val LAYOUT = PulseWaveLayout(channel = 2, inverted = false)
    }
}
