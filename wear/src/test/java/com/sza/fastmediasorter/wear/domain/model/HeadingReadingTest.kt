package com.sza.fastmediasorter.wear.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S3370: the fallback boundary between the compass-colored spark pair and the white/random one.
 *
 * The overlay keys its fallback on [HeadingReading.isTrustworthy], so an unknown platform status
 * must land on [HeadingAccuracy.UNRELIABLE] - never on a confident value.
 */
class HeadingReadingTest {

    @Test
    fun `each known platform status maps to its named accuracy`() {
        assertEquals(HeadingAccuracy.LOW, HeadingAccuracy.fromPlatformStatus(PLATFORM_LOW))
        assertEquals(HeadingAccuracy.MEDIUM, HeadingAccuracy.fromPlatformStatus(PLATFORM_MEDIUM))
        assertEquals(HeadingAccuracy.HIGH, HeadingAccuracy.fromPlatformStatus(PLATFORM_HIGH))
    }

    @Test
    fun `unknown statuses land on UNRELIABLE`() {
        assertEquals(HeadingAccuracy.UNRELIABLE, HeadingAccuracy.fromPlatformStatus(PLATFORM_NO_CONTACT))
        assertEquals(HeadingAccuracy.UNRELIABLE, HeadingAccuracy.fromPlatformStatus(PLATFORM_UNRELIABLE))
        assertEquals(HeadingAccuracy.UNRELIABLE, HeadingAccuracy.fromPlatformStatus(UNKNOWN_STATUS))
    }

    @Test
    fun `only UNRELIABLE readings are untrustworthy`() {
        val unreliable = HeadingReading(AZIMUTH_NORTH, HeadingAccuracy.UNRELIABLE, TAKEN_AT)
        val low = HeadingReading(AZIMUTH_NORTH, HeadingAccuracy.LOW, TAKEN_AT)
        val high = HeadingReading(AZIMUTH_NEAR_FULL_CIRCLE, HeadingAccuracy.HIGH, TAKEN_AT)

        assertFalse(unreliable.isTrustworthy)
        assertTrue(low.isTrustworthy)
        assertTrue(high.isTrustworthy)
    }

    private companion object {
        const val PLATFORM_NO_CONTACT = -1
        const val PLATFORM_UNRELIABLE = 0
        const val PLATFORM_LOW = 1
        const val PLATFORM_MEDIUM = 2
        const val PLATFORM_HIGH = 3
        const val UNKNOWN_STATUS = 99
        const val AZIMUTH_NORTH = 0f
        const val AZIMUTH_NEAR_FULL_CIRCLE = 359f
        const val TAKEN_AT = 1000L
    }
}
