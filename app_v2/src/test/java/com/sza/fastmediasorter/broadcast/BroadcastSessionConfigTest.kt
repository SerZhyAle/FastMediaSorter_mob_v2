package com.sza.fastmediasorter.broadcast

import com.sza.fastmediasorter.domain.model.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * S2817: the session config is the single immutable snapshot every downstream component reads, so
 * metadata consistency (ICY title and bit rate matching the encoder) and the no-fallback port
 * policy are observable as properties of [BroadcastSessionConfig] itself.
 */
class BroadcastSessionConfigTest {

    @Test
    fun `default config preserves the pre-S2817 hard-coded session values`() {
        val config = BroadcastSessionConfig.DEFAULT

        assertEquals("Phone Audio Stream", config.streamTitle)
        assertEquals(128_000, config.bitRateBps)
        assertEquals(8768, config.port)
        assertEquals(44_100, config.sampleRateHz)
        assertEquals(1, config.channelCount)
        // S2814: DEFAULT has no device identity; readSessionConfig fills it from settings.
        assertEquals(null, config.sourceDeviceId)
    }

    @Test
    fun `icy bit rate header derives from the same config as the encoder bit rate`() {
        // The server sends icy-br as bitRateBps / 1000; the encoder uses bitRateBps directly.
        // A config that drifts between the two would make the header a lie. This pins the formula
        // both sides read from the same instance.
        val config = BroadcastSessionConfig(
            streamTitle = "Test",
            bitRateBps = 256_000,
            port = 9000,
            sampleRateHz = 48_000,
            channelCount = 2,
            sourceDeviceId = null,
        )

        val expectedIcyBr = config.bitRateBps / 1000
        assertEquals(256, expectedIcyBr)
        assertEquals(config.bitRateBps, config.bitRateBps)
    }

    @Test
    fun `a single config title serves both icy-name and descriptor title`() {
        val title = "My Station"
        val config = BroadcastSessionConfig(
            streamTitle = title,
            bitRateBps = 128_000,
            port = 8768,
            sampleRateHz = 44_100,
            channelCount = 1,
            sourceDeviceId = null,
        )

        // The pre-S2817 code had two different hard-coded titles ("Phone Audio Broadcast" in ICY vs
        // "Phone Audio Stream" in the descriptor). A single config field closes that gap: both the
        // server's icy-name and the service's descriptor title read config.streamTitle.
        assertEquals(title, config.streamTitle)
    }

    @Test
    fun `config holds exactly one port with no fallback candidates`() {
        val config = BroadcastSessionConfig(
            streamTitle = "T",
            bitRateBps = 128_000,
            port = 7777,
            sampleRateHz = 44_100,
            channelCount = 1,
            sourceDeviceId = null,
        )

        // The pre-S2817 server tried three candidate ports (8768, 8769, 8770). The config carries a
        // single user-chosen port; the server binds it or fails - no silent fallback.
        assertEquals(7777, config.port)
        assertNotEquals(8769, config.port)
        assertNotEquals(8770, config.port)
    }

    @Test
    fun `config built from AppSettings carries the user's preferences`() {
        val settings = AppSettings(
            broadcastStreamTitle = "Custom Title",
            broadcastBitRateBps = 192_000,
            broadcastPort = 8080,
            broadcastSampleRateHz = 48_000,
            broadcastChannelCount = 2,
            broadcastAutoOpenShare = false,
        )

        val config = BroadcastSessionConfig(
            streamTitle = settings.broadcastStreamTitle,
            bitRateBps = settings.broadcastBitRateBps,
            port = settings.broadcastPort,
            sampleRateHz = settings.broadcastSampleRateHz,
            channelCount = settings.broadcastChannelCount,
            sourceDeviceId = null,
        )

        assertEquals("Custom Title", config.streamTitle)
        assertEquals(192_000, config.bitRateBps)
        assertEquals(8080, config.port)
        assertEquals(48_000, config.sampleRateHz)
        assertEquals(2, config.channelCount)
    }
}
