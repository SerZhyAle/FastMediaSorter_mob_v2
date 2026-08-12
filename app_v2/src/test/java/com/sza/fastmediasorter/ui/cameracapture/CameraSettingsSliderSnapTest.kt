package com.sza.fastmediasorter.ui.cameracapture

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * S1583: the camera settings dialog crashed from a layout pass because the shutter slider was handed
 * a log-mapped fraction (91.168594) on a 1-unit track. These tests pin the properties Material's
 * BaseSlider validates: the value sits on the step grid and inside the track bounds.
 */
class CameraSettingsSliderSnapTest {

    @Test
    fun `a log-mapped shutter fraction lands on the step grid`() {
        val snapped = CameraSettingsDialogFragment.snapToSliderStep(
            rawValue = CRASHING_SHUTTER_VALUE,
            from = 0f,
            to = 100f,
            step = 1f,
        )

        assertEquals(91f, snapped, 0f)
    }

    @Test
    fun `a value above the track is clamped to the last step`() {
        val snapped = CameraSettingsDialogFragment.snapToSliderStep(
            rawValue = 4800f,
            from = 50f,
            to = 3200f,
            step = 1f,
        )

        assertEquals(3200f, snapped, 0f)
    }

    @Test
    fun `a value below the track is clamped to the first step`() {
        val snapped = CameraSettingsDialogFragment.snapToSliderStep(
            rawValue = -8f,
            from = -2f,
            to = 2f,
            step = 1f,
        )

        assertEquals(-2f, snapped, 0f)
    }

    @Test
    fun `a negative valueFrom keeps the grid anchored to it`() {
        val snapped = CameraSettingsDialogFragment.snapToSliderStep(
            rawValue = 0.5f,
            from = -1.5f,
            to = 2.5f,
            step = 1f,
        )

        assertEquals(0.5f, snapped, 0f)
    }

    @Test
    fun `a value already on a node is left alone`() {
        val snapped = CameraSettingsDialogFragment.snapToSliderStep(
            rawValue = 42f,
            from = 0f,
            to = 100f,
            step = 1f,
        )

        assertEquals(42f, snapped, 0f)
    }

    @Test
    fun `a continuous track passes the value through unchanged`() {
        val snapped = CameraSettingsDialogFragment.snapToSliderStep(
            rawValue = CRASHING_SHUTTER_VALUE,
            from = 0f,
            to = 100f,
            step = 0f,
        )

        assertEquals(CRASHING_SHUTTER_VALUE, snapped, 0f)
    }

    private companion object {
        /** The exact value the reporting device (S21+, SDK 35) crashed on. */
        const val CRASHING_SHUTTER_VALUE = 91.168594f
    }
}
