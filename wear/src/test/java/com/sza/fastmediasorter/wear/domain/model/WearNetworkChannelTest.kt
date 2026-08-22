package com.sza.fastmediasorter.wear.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WearNetworkChannelTest {

    @Test
    fun `a positive downstream figure counts as an estimate`() {
        val channel = channelWith(downstreamKbps = WIFI_KBPS)

        assertTrue(channel.hasBandwidthEstimate)
    }

    @Test
    fun `an absent downstream figure is not an estimate`() {
        val channel = channelWith(downstreamKbps = null)

        assertFalse(channel.hasBandwidthEstimate)
    }

    @Test
    fun `a zero downstream figure is not an estimate`() {
        val channel = channelWith(downstreamKbps = 0)

        assertFalse(channel.hasBandwidthEstimate)
    }

    @Test
    fun `the no-link constant reports no estimate`() {
        assertFalse(WearNetworkChannel.NONE.hasBandwidthEstimate)
        assertTrue(WearNetworkChannel.NONE.kind == WearNetworkChannelKind.NONE)
    }

    private fun channelWith(downstreamKbps: Int?): WearNetworkChannel = WearNetworkChannel(
        kind = WearNetworkChannelKind.WIFI,
        downstreamKbps = downstreamKbps,
        upstreamKbps = null,
        isMetered = false,
        isValidated = true
    )

    companion object {
        private const val WIFI_KBPS = 1500
    }
}
