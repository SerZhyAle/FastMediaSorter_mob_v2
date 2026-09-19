package com.sza.fastmediasorter.wear.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * S3223: the wire refusal read against whether this watch asked for it.
 *
 * The listener that makes this call is a `WearableListenerService` and cannot be instantiated in a
 * unit test, so the decision it makes lives here instead of inside it.
 */
class PhoneCameraRefusalMappingTest {

    @Test
    fun `an unrequested stop is the phone ending the broadcast`() {
        assertEquals(
            PhoneCameraFailure.ENDED,
            CameraRefusal.STOPPED.asSessionFailure(unrequested = true)
        )
    }

    @Test
    fun `a stop this watch asked for stays a stop`() {
        assertEquals(
            PhoneCameraFailure.STOPPED,
            CameraRefusal.STOPPED.asSessionFailure(unrequested = false)
        )
    }

    @Test
    fun `every other refusal reads the same either way`() {
        CameraRefusal.entries.filter { it != CameraRefusal.STOPPED }.forEach { refusal ->
            assertEquals(
                refusal.asSessionFailure(),
                refusal.asSessionFailure(unrequested = true)
            )
            assertEquals(
                refusal.asSessionFailure(),
                refusal.asSessionFailure(unrequested = false)
            )
        }
    }
}
