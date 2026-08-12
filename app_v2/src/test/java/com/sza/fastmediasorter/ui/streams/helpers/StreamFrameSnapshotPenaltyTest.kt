package com.sza.fastmediasorter.ui.streams.helpers

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S1469: on a device that boots before its network is up, every visible tile failed at once and each
 * failure was charged to its own url, so live channels came out of the outage serving a dead channel's
 * cooldown. This pins the rule that separates "this channel is dead" from "there is no network".
 */
class StreamFrameSnapshotPenaltyTest {

    @Test
    fun `a failure with a live network counts against the url`() {
        assertTrue(shouldPenaliseCaptureFailure(ok = false, hasNetwork = true))
    }

    @Test
    fun `a failure during an outage counts against nothing`() {
        assertFalse(shouldPenaliseCaptureFailure(ok = false, hasNetwork = false))
    }

    @Test
    fun `a success is never a penalty, network up`() {
        assertFalse(shouldPenaliseCaptureFailure(ok = true, hasNetwork = true))
    }

    @Test
    fun `a success is never a penalty, network down`() {
        // Reachable in practice: the capture finished just before connectivity dropped.
        assertFalse(shouldPenaliseCaptureFailure(ok = true, hasNetwork = false))
    }
}
