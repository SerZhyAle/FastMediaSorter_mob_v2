package com.sza.fastmediasorter.wear.data.exchange

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The RELAY endpoint a `cast-offer` hands the watch (contract 7.7, 7.8). */
class WearExchangeRelayEndpointTest {

    @Test
    fun `the first AUDIO_ONLY relay endpoint is taken with its pin`() {
        val endpoint = checkNotNull(WearExchangeRelayEndpoint.fromOffer(offer(RELAY_URL)))

        assertEquals("b1", endpoint.broadcastId)
        assertEquals("ex.example", endpoint.host)
        assertEquals(7443, endpoint.port)
        assertEquals("/v2/b/b1/stream", endpoint.path)
        assertEquals("SHA256:pin", endpoint.certFingerprint)
        assertEquals("Phone", endpoint.title)
        assertEquals("http://127.0.0.1:5000/v2/b/b1/stream", endpoint.loopbackUrl(5000))
    }

    @Test
    fun `a relay url without a port is on 443`() {
        assertEquals(443, WearExchangeRelayEndpoint.fromOffer(offer("https://ex.example/v2/b/b1/stream"))?.port)
    }

    @Test
    fun `a plain http relay is not played`() {
        assertNull(WearExchangeRelayEndpoint.fromOffer(offer("http://ex.example:7443/v2/b/b1/stream")))
    }

    @Test
    fun `a relay for another broadcast id is not played`() {
        assertNull(WearExchangeRelayEndpoint.fromOffer(offer("https://ex.example:7443/v2/b/other/stream")))
    }

    @Test
    fun `an offer with LAN endpoints only is not played`() {
        val lanOnly = WearExchangeEnvelope.decode(
            """{"schemaVersion":2,"type":"cast-offer","broadcastId":"b1","broadcast":{"broadcastId":"b1",""" +
                """"descriptor":{"endpoints":[{"url":"http://10.0.0.2:8080/","transport":"HTTP"}]}}}"""
        )

        assertNull(WearExchangeRelayEndpoint.fromOffer(lanOnly))
    }

    @Test
    fun `a video relay endpoint is not played`() {
        val video = WearExchangeEnvelope.decode(
            """{"schemaVersion":2,"type":"cast-offer","broadcast":{"broadcastId":"b1","descriptor":{"endpoints":""" +
                """[{"url":"$RELAY_URL","transport":"RELAY","mode":"VIDEO_AUDIO"}]}}}"""
        )

        assertNull(WearExchangeRelayEndpoint.fromOffer(video))
    }

    private fun offer(url: String): WearExchangeEnvelope = WearExchangeEnvelope.decode(
        """{"schemaVersion":2,"type":"cast-offer","fromDeviceId":"p","broadcastId":"b1",""" +
            """"broadcast":{"broadcastId":"b1","title":"Phone","mode":"AUDIO_ONLY","descriptor":{"endpoints":[""" +
            """{"url":"http://10.0.0.2:8080/","transport":"HTTP","mode":"AUDIO_ONLY"},""" +
            """{"url":"$url","transport":"RELAY","mode":"AUDIO_ONLY","certFingerprint":"SHA256:pin"}]}}}"""
    )

    private companion object {
        const val RELAY_URL = "https://ex.example:7443/v2/b/b1/stream"
    }
}
