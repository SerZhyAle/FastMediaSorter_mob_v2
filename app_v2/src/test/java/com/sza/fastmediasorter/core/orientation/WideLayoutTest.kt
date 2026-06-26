package com.sza.fastmediasorter.core.orientation

import android.content.res.Configuration
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WideLayoutTest {

    @Test
    fun landscapeBelowThresholdIsWide() {
        assertTrue(isWideLayout(Configuration.ORIENTATION_LANDSCAPE, 400))
    }

    @Test
    fun portraitAtThresholdIsWide() {
        assertTrue(isWideLayout(Configuration.ORIENTATION_PORTRAIT, WIDE_LAYOUT_MIN_WIDTH_DP))
    }

    @Test
    fun portraitAboveThresholdIsWide() {
        assertTrue(isWideLayout(Configuration.ORIENTATION_PORTRAIT, 800))
    }

    @Test
    fun portraitBelowThresholdIsNotWide() {
        assertFalse(isWideLayout(Configuration.ORIENTATION_PORTRAIT, WIDE_LAYOUT_MIN_WIDTH_DP - 1))
    }
}
