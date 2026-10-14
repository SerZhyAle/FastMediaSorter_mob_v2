package com.sza.fastmediasorter.wear.data.exchange

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The endpoints of a directory record the watch plays, in the producer's order (contract 7.7 and item CH). */
class WearBroadcastEndpointsTest {

    @Test
    fun `the producer's order is kept and a tunnel is dropped`() {
        val endpoints = WearBroadcastEndpoints.parse(
            "b1",
            descriptor(
                """{"endpoints":[""" +
                    """{"url":"http://10.0.0.2:8080/","transport":"HTTP","mode":"VIDEO_AUDIO"},""" +
                    """{"url":"https://ex.example:7443/v2/b/b1/stream","transport":"RELAY","mode":"VIDEO_AUDIO",""" +
                    """"certFingerprint":"SHA256:pin"},""" +
                    """{"url":"fmsx://ex.example:7443/b/b1/rtsp","transport":"TUNNEL","inner":"rtsp://192.0.2.1/"}]}""",
            ),
            null,
        )

        assertEquals(2, endpoints.size)
        val lan = endpoints[0] as WearBroadcastEndpoint.Lan
        assertEquals("10.0.0.2", lan.host)
        assertEquals(8080, lan.port)
        val relay = endpoints[1] as WearBroadcastEndpoint.Relay
        assertEquals("ex.example", relay.host)
        assertEquals(7443, relay.port)
        assertEquals("/v2/b/b1/stream", relay.path)
        assertEquals("SHA256:pin", relay.certFingerprint)
    }

    @Test
    fun `a relay for another broadcast id or on plain http is not played`() {
        val endpoints = WearBroadcastEndpoints.parse(
            "b1",
            descriptor(
                """{"endpoints":[""" +
                    """{"url":"https://ex.example/v2/b/other/stream","transport":"RELAY"},""" +
                    """{"url":"http://ex.example/v2/b/b1/stream","transport":"RELAY"}]}""",
            ),
            "AUDIO_ONLY",
        )

        assertTrue(endpoints.isEmpty())
    }

    @Test
    fun `a relay url without a port is on 443`() {
        val relay = WearBroadcastEndpoints.parse(
            "b1",
            descriptor("""{"endpoints":[{"url":"https://ex.example/v2/b/b1/stream","transport":"RELAY"}]}"""),
            "AUDIO_ONLY",
        ).single() as WearBroadcastEndpoint.Relay

        assertEquals(443, relay.port)
    }

    @Test
    fun `a descriptor without a list is read as one endpoint from its url`() {
        val endpoints = WearBroadcastEndpoints.parse(
            "b1",
            descriptor("""{"url":"rtsp://10.0.0.2:8554/","mode":"VIDEO_AUDIO"}"""),
            null,
        )

        val lan = endpoints.single() as WearBroadcastEndpoint.Lan
        assertEquals(8554, lan.port)
        assertEquals("VIDEO_AUDIO", lan.mode)
    }

    @Test
    fun `the mode of an endpoint falls back to the descriptor then the record`() {
        val fromDescriptor = WearBroadcastEndpoints.parse(
            "b1",
            descriptor("""{"mode":"VIDEO_ONLY","endpoints":[{"url":"http://10.0.0.2:1/","transport":"HTTP"}]}"""),
            "AUDIO_ONLY",
        ).single()
        val fromRecord = WearBroadcastEndpoints.parse(
            "b1",
            descriptor("""{"endpoints":[{"url":"http://10.0.0.2:1/","transport":"HTTP"}]}"""),
            "AUDIO_ONLY",
        ).single()

        assertEquals("VIDEO_ONLY", fromDescriptor.mode)
        assertEquals("AUDIO_ONLY", fromRecord.mode)
    }

    @Test
    fun `a record with no descriptor has no endpoints`() {
        assertTrue(WearBroadcastEndpoints.parse("b1", null, "AUDIO_ONLY").isEmpty())
    }

    @Test
    fun `only the three known modes are played`() {
        assertTrue(WearBroadcastEndpoints.isPlayableMode("AUDIO_ONLY"))
        assertTrue(WearBroadcastEndpoints.isPlayableMode("video_audio"))
        assertTrue(WearBroadcastEndpoints.isPlayableMode("VIDEO_ONLY"))
        assertFalse(WearBroadcastEndpoints.isPlayableMode("HOLOGRAM"))
        assertFalse(WearBroadcastEndpoints.isPlayableMode(null))
        assertTrue(WearBroadcastEndpoints.isVideo("VIDEO_AUDIO"))
        assertFalse(WearBroadcastEndpoints.isVideo("AUDIO_ONLY"))
    }

    @Test
    fun `an auth object marks a protected descriptor`() {
        assertTrue(WearBroadcastEndpoints.isProtected(descriptor("""{"auth":{"scheme":"password-v1","user":"u"}}""")))
        assertFalse(WearBroadcastEndpoints.isProtected(descriptor("""{"endpoints":[]}""")))
        assertFalse(WearBroadcastEndpoints.isProtected(null))
        assertNull(WearBroadcastEndpoints.parse("b1", null, null).firstOrNull())
    }

    private fun descriptor(json: String) = JsonParser.parseString(json).asJsonObject
}
