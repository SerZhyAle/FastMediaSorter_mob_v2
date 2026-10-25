package com.sza.fastmediasorter.data.broadcast.exchange

import com.sza.fastmediasorter.data.broadcast.BroadcastDescriptorDto
import com.sza.fastmediasorter.data.broadcast.BroadcastEndpointDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExchangeBroadcastDescriptorsTest {

    private val audio = BroadcastDescriptorDto(
        url = "http://192.168.1.5:8090/live-audio.aac",
        title = "Pixel",
        mode = "AUDIO_ONLY",
        endpoints = listOf(
            BroadcastEndpointDto(url = "http://192.168.1.5:8090/live-audio.aac", transport = "HTTP", audioCodec = "AAC")
        ),
    )

    @Test
    fun `LAN first, then RELAY while uploading, then one TUNNEL per LAN service`() {
        val descriptor = ExchangeBroadcastDescriptors.withExchange(audio, ID, "exchange.example:7443", null, true)

        val endpoints = descriptor.endpoints.orEmpty()
        assertEquals(listOf("HTTP", "RELAY", "TUNNEL"), endpoints.map { it.transport })
        assertEquals("https://exchange.example:7443/v2/b/$ID/stream", endpoints[1].url)
        assertEquals("fmsx://exchange.example:7443/b/$ID/http", endpoints[2].url)
        assertEquals("http://192.168.1.5:8090/live-audio.aac", endpoints[2].inner)
        assertEquals(2_000L, endpoints[2].targetLatencyMs)
    }

    @Test
    fun `a sealed descriptor marks RELAY and TUNNEL with seal v1 and leaves LAN clear`() {
        val descriptor = ExchangeBroadcastDescriptors.withExchange(audio, ID, "exchange.example:7443", null, true)

        val seals = descriptor.endpoints.orEmpty().associate { it.transport to it.seal }
        assertNull(seals["HTTP"])
        assertEquals(1, seals["RELAY"]?.v)
        assertEquals(1, seals["TUNNEL"]?.v)
        assertNull("a record carries no key in clear", seals["RELAY"]?.key)
    }

    @Test
    fun `the outward descriptor carries the relay key and the tunnel pin in their seals`() {
        val sealed = ExchangeBroadcastDescriptors.withExchange(audio, ID, "exchange.example:7443", null, true)

        val outward = ExchangeBroadcastDescriptors.withOutwardKeys(sealed, "group-key", PIN)

        val seals = outward.endpoints.orEmpty().associate { it.transport to it.seal }
        assertNull(seals["HTTP"])
        assertEquals("group-key", seals["RELAY"]?.key)
        assertEquals(PIN, seals["TUNNEL"]?.pin)
        assertEquals(1, seals["TUNNEL"]?.v)
    }

    @Test
    fun `an outward sealed endpoint without its key is left out`() {
        val sealed = ExchangeBroadcastDescriptors.withExchange(audio, ID, "exchange.example:7443", null, true)

        val outward = ExchangeBroadcastDescriptors.withOutwardKeys(sealed, "group-key", tunnelPin = null)

        assertEquals(listOf("HTTP", "RELAY"), outward.endpoints.orEmpty().map { it.transport })
    }

    @Test
    fun `no RELAY without an accepted upload`() {
        val descriptor = ExchangeBroadcastDescriptors.withExchange(audio, ID, "exchange.example:7443", null, false)

        assertEquals(listOf("HTTP", "TUNNEL"), descriptor.endpoints.orEmpty().map { it.transport })
    }

    @Test
    fun `the pin is written on every exchange endpoint and only there`() {
        val descriptor = ExchangeBroadcastDescriptors.withExchange(audio, ID, "exchange.example:7443", PIN, true)

        val byTransport = descriptor.endpoints.orEmpty().associate { it.transport to it.certFingerprint }
        assertNull(byTransport["HTTP"])
        assertEquals(PIN, byTransport["RELAY"])
        assertEquals(PIN, byTransport["TUNNEL"])
    }

    @Test
    fun `a video broadcast tunnels its RTSP server and keeps the codecs`() {
        val video = BroadcastDescriptorDto(
            url = "rtsp://192.168.1.5:8554/live",
            mode = "VIDEO_AUDIO",
            endpoints = listOf(
                BroadcastEndpointDto(
                    url = "rtsp://192.168.1.5:8554/live",
                    transport = "RTSP",
                    mode = "VIDEO_AUDIO",
                    videoCodec = "H264",
                )
            ),
        )

        val descriptor = ExchangeBroadcastDescriptors.withExchange(video, ID, "exchange.example:7443", null, false)

        val tunnel = descriptor.endpoints.orEmpty().last()
        assertEquals("fmsx://exchange.example:7443/b/$ID/rtsp", tunnel.url)
        assertEquals("H264", tunnel.videoCodec)
        assertEquals(mapOf("rtsp" to 8554), ExchangeBroadcastDescriptors.localPorts(video))
    }

    @Test
    fun `a descriptor without an endpoint list is read as its single legacy endpoint`() {
        val legacy = audio.copy(endpoints = null)

        val endpoints = ExchangeBroadcastDescriptors.withExchange(legacy, ID, "x.example:443", null, false).endpoints

        assertEquals(listOf("HTTP", "TUNNEL"), endpoints.orEmpty().map { it.transport })
        assertEquals(mapOf("http" to 8090), ExchangeBroadcastDescriptors.localPorts(legacy))
    }

    @Test
    fun `a pending audio descriptor holds RELAY and TUNNEL only and keeps its expiry`() {
        val until = 1_800_000_000_000L

        val descriptor = ExchangeBroadcastDescriptors.pending(ID, SERVER, null, "AUDIO_ONLY", true, until)

        val endpoints = descriptor.endpoints.orEmpty()
        assertEquals(listOf("RELAY", "TUNNEL"), endpoints.map { it.transport })
        assertEquals(endpoints[0].url, descriptor.url)
        assertEquals("http://192.0.2.1/live-audio.aac", endpoints[1].inner)
        assertEquals(true, descriptor.isLive)
        assertEquals(true, descriptor.isPending)
        assertEquals(until, descriptor.pending?.untilMs())
    }

    @Test
    fun `outward keys are written into a pending descriptor`() {
        val pending = ExchangeBroadcastDescriptors.pending(ID, SERVER, null, "VIDEO_AUDIO", true, 0L)

        val outward = ExchangeBroadcastDescriptors.withOutwardKeys(pending, "relay-key", PIN)

        val seals = outward.endpoints.orEmpty().associate { it.transport to it.seal }
        assertEquals("relay-key", seals["RELAY"]?.key)
        assertEquals(PIN, seals["TUNNEL"]?.pin)
        assertEquals("rtsp://192.0.2.1/", outward.endpoints.orEmpty().last().inner)
    }

    private companion object {
        const val ID = "AAAAAAAAAAAAAAAAAAAAAA"
        const val SERVER = "exchange.example:7443"
        const val PIN = "SHA256:47DEQpj8HBSa+/TImW+5JCeuQeRkm5NMpJWZG3hSuFU"
    }
}
