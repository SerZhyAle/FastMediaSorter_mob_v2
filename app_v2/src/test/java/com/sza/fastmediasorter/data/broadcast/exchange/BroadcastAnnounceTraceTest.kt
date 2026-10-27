package com.sza.fastmediasorter.data.broadcast.exchange

import com.sza.fastmediasorter.data.broadcast.BroadcastAuthDto
import com.sza.fastmediasorter.data.broadcast.BroadcastDescriptorDto
import com.sza.fastmediasorter.data.broadcast.BroadcastDescriptorSerializer
import com.sza.fastmediasorter.data.broadcast.BroadcastEndpointDto
import com.sza.fastmediasorter.data.broadcast.BroadcastSealDto
import com.sza.fastmediasorter.data.broadcast.auth.BroadcastLinkChoice
import com.sza.fastmediasorter.data.broadcast.auth.BroadcastVerifier
import com.sza.fastmediasorter.data.remote.exchange.BroadcastLinkTrace
import com.sza.fastmediasorter.data.remote.exchange.ExchangeBroadcastPublication
import com.sza.fastmediasorter.domain.model.BroadcastLinkPasswordMode
import com.sza.fastmediasorter.domain.model.SftpRendezvousBroadcast
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BroadcastAnnounceTraceTest {

    private val serializer = BroadcastDescriptorSerializer()

    @Test
    fun `a protected relay broadcast names its auth scheme, seals, verifiers and box`() {
        val line = BroadcastAnnounceTrace.describe(
            "broadcast-update",
            publication(
                descriptor(
                    auth = BroadcastAuthDto(user = "listener"),
                    endpoints = listOf(rtsp(), sealed("RELAY"), sealed("TUNNEL")),
                ),
                link = BroadcastLinkTrace(BroadcastLinkPasswordMode.AUTO, BroadcastLinkChoice.Generated),
                verifiers = 3,
                box = true,
            ),
        )

        assertEquals(
            "type=broadcast-update id=1a2b3c4d mode=VIDEO_ONLY remembered=AUTO link=Generated auth=password-v1 " +
                "endpoints=RTSP:plain,RELAY:seal,TUNNEL:seal verifiers=3 box=yes",
            line,
        )
    }

    @Test
    fun `an open link shows no auth and the remembered mode beside the live choice`() {
        val line = BroadcastAnnounceTrace.describe(
            "broadcast-start",
            publication(
                descriptor(auth = null, endpoints = listOf(rtsp(), sealed("TUNNEL"))),
                link = BroadcastLinkTrace(BroadcastLinkPasswordMode.AUTO, BroadcastLinkChoice.Open),
                verifiers = 0,
                box = false,
            ),
        )

        assertEquals(
            "type=broadcast-start id=1a2b3c4d mode=VIDEO_ONLY remembered=AUTO link=Open auth=absent " +
                "endpoints=RTSP:plain,TUNNEL:seal verifiers=0 box=no",
            line,
        )
    }

    @Test
    fun `no key, pin, password or url reaches the line`() {
        val line = BroadcastAnnounceTrace.describe(
            "broadcast-update",
            publication(
                descriptor(auth = BroadcastAuthDto(user = "listener"), endpoints = listOf(sealed("RELAY"))),
                link = BroadcastLinkTrace(BroadcastLinkPasswordMode.CUSTOM, BroadcastLinkChoice.Custom("hunter2")),
                verifiers = 1,
                box = true,
            ),
        )

        listOf("hunter2", SECRET_KEY, SECRET_PIN, "192.168.1.195", "rtsp://", "listener", FULL_ID).forEach {
            assertFalse("the line must not carry $it: $line", line.contains(it))
        }
        assertTrue(line.contains("link=Custom"))
    }

    @Test
    fun `an unreadable descriptor and a missing link context still yield a line`() {
        val readable = publication(descriptor(null, emptyList()))
        val line = BroadcastAnnounceTrace.describe(
            "broadcast-update",
            readable.copy(record = readable.record.copy(descriptorJson = "not json")),
        )

        assertEquals(
            "type=broadcast-update id=1a2b3c4d mode=VIDEO_ONLY remembered=- link=- auth=unreadable " +
                "endpoints=- verifiers=0 box=no",
            line,
        )
    }

    private fun publication(
        descriptor: BroadcastDescriptorDto,
        link: BroadcastLinkTrace? = null,
        verifiers: Int = 0,
        box: Boolean = false,
    ) = ExchangeBroadcastPublication(
        record = SftpRendezvousBroadcast(
            broadcastId = FULL_ID,
            deviceId = "AAAAAAAAAAAAAAAAAAAAAA",
            title = "Pixel",
            mode = "VIDEO_ONLY",
            startedAtMs = 0L,
            updatedAtMs = 0L,
            descriptorJson = serializer.serialize(descriptor),
            writtenAtMs = 0L,
            ttlSeconds = 0L,
            sealed = BOX.takeIf { box },
        ),
        maxListeners = ExchangeBroadcastPublication.NO_LISTENER_CAP,
        localPorts = emptyMap(),
        verifiers = List(verifiers) { BroadcastVerifier(user = "u$it", salt = "c2FsdA", hash = "aGFzaA") },
        link = link,
    )

    private fun descriptor(auth: BroadcastAuthDto?, endpoints: List<BroadcastEndpointDto>) = BroadcastDescriptorDto(
        url = "rtsp://192.168.1.195:8554/",
        mode = "VIDEO_ONLY",
        endpoints = endpoints,
        auth = auth,
    )

    private fun rtsp() = BroadcastEndpointDto(url = "rtsp://192.168.1.195:8554/", transport = "RTSP")

    private fun sealed(transport: String) = BroadcastEndpointDto(
        url = "exchange.example:22022",
        transport = transport,
        seal = BroadcastSealDto(key = SECRET_KEY, pin = SECRET_PIN),
    )

    private companion object {
        const val FULL_ID = "1a2b3c4d5e6f7a8b9c0d1e"
        const val SECRET_KEY = "KEYKEYKEYKEYKEY"
        const val SECRET_PIN = "PINPINPINPIN"
        const val BOX = "{\"box\":1}"
    }
}
