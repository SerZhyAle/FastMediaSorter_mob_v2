package com.sza.fastmediasorter.wear.domain.tourist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WearTripSnapshotCodecTest {

    @Test
    fun `a full trip survives a round trip`() {
        val trip = WearTripSnapshot(
            startedAtMillis = 1_000L,
            lastSampleAtMillis = 2_000L,
            movingMillis = 900L,
            distanceMeters = 1234.5,
            gainMeters = 40.0,
            lossMeters = 12.5,
            maxSpeedKmh = 7.5f,
            elevationSource = WearElevationSource.BAROMETER,
            lastElevationMeters = 310.25,
            pressureSamples = listOf(WearPressureSample(1_000L, 1012.5f), WearPressureSample(61_000L, 1012.0f)),
        )

        assertEquals(trip, WearTripSnapshotCodec.decode(WearTripSnapshotCodec.encode(trip)))
    }

    @Test
    fun `an empty trip survives a round trip`() {
        assertEquals(
            WearTripSnapshot.EMPTY,
            WearTripSnapshotCodec.decode(WearTripSnapshotCodec.encode(WearTripSnapshot.EMPTY)),
        )
    }

    @Test
    fun `nothing stored decodes to null`() {
        assertNull(WearTripSnapshotCodec.decode(null))
    }

    @Test
    fun `a corrupt string decodes to null`() {
        assertNull(WearTripSnapshotCodec.decode("garbage"))
        val broken = WearTripSnapshotCodec.encode(WearTripSnapshot.EMPTY).replace("0.0", "x")
        assertNull(WearTripSnapshotCodec.decode(broken))
    }

    @Test
    fun `another version decodes to null`() {
        val encoded = WearTripSnapshotCodec.encode(WearTripSnapshot.EMPTY)
        val future = "99" + encoded.substring(encoded.indexOf('\u001F'))

        assertNull(WearTripSnapshotCodec.decode(future))
    }
}
