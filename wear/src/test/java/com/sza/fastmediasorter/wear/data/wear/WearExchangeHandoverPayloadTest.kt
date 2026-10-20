package com.sza.fastmediasorter.wear.data.wear

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** S4158: the watch's copy of the hand-over wire, and the expiry check that refuses a stale code by name. */
class WearExchangeHandoverPayloadTest {

    private val gson = Gson()

    @Test
    fun `the phone's bytes parse on the watch`() {
        val phoneBytes = (
            """{"requestId":"r-1","formatVersion":1,"host":"exchange.example.net","port":44022,""" +
                """"login":"owner","pinnedCertificate":null,"pairingCode":"7KQ2M9XA","expiresAt":null}"""
            ).toByteArray()

        val parsed = WearExchangeHandoverCodec.parse(phoneBytes, gson) as WearExchangeHandoverParseResult.Parsed

        assertEquals("7KQ2M9XA", parsed.payload.pairingCode)
        assertEquals(44_022, parsed.payload.port)
        assertFalse(parsed.payload.toString().contains("7KQ2M9XA"))
        assertNull(parsed.payload.pinnedKey)
    }

    @Test
    fun `a phone that pins a key hands it over beside the leaf`() {
        val phoneBytes = (
            """{"requestId":"r-1","formatVersion":1,"host":"exchange.example.net","port":44022,""" +
                """"login":"owner","pinnedCertificate":"SHA256:leaf","pairingCode":"7KQ2M9XA",""" +
                """"expiresAt":null,"pinnedKey":"SHA256:key"}"""
            ).toByteArray()

        val parsed = WearExchangeHandoverCodec.parse(phoneBytes, gson) as WearExchangeHandoverParseResult.Parsed

        assertEquals("SHA256:leaf", parsed.payload.pinnedCertificate)
        assertEquals("SHA256:key", parsed.payload.pinnedKey)
    }

    @Test
    fun `an answer the phone can read`() {
        val ack = WearExchangeHandoverAck("r-1", enrolled = false, reason = WearExchangeHandoverReasons.CODE_EXPIRED)

        val json = String(WearExchangeHandoverCodec.serializeAck(ack, gson))

        assertEquals("""{"requestId":"r-1","enrolled":false,"reason":"code_expired"}""", json)
    }

    @Test
    fun `a successful answer carries the watch's deviceId and key`() {
        val ack = WearExchangeHandoverAck("r-2", enrolled = true, deviceId = "w-1", e2eKem = "kem-value")

        val json = String(WearExchangeHandoverCodec.serializeAck(ack, gson))

        assertEquals("""{"requestId":"r-2","enrolled":true,"deviceId":"w-1","e2eKem":"kem-value"}""", json)
    }

    @Test
    fun `an unknown version keeps the request id even when the fields were renamed`() {
        val bytes = """{"requestId":"r-7","formatVersion":3,"server":{"host":"h"},"code":"c"}""".toByteArray()

        assertEquals(
            WearExchangeHandoverParseResult.UnsupportedVersion(version = 3, requestId = "r-7"),
            WearExchangeHandoverCodec.parse(bytes, gson),
        )
    }

    @Test
    fun `an unknown version without an id is malformed`() {
        val bytes = (
            """{"requestId":"","formatVersion":2,"host":"h","port":44022,"login":"owner",""" +
                """"pinnedCertificate":null,"pairingCode":"7KQ2M9XA","expiresAt":null}"""
            ).toByteArray()

        assertEquals(WearExchangeHandoverParseResult.Malformed, WearExchangeHandoverCodec.parse(bytes, gson))
    }

    @Test
    fun `the receiver answers a version refusal with the sender's request id`() {
        val bytes = (
            """{"requestId":"r-9","formatVersion":2,"host":"h","port":44022,"login":"owner",""" +
                """"pinnedCertificate":null,"pairingCode":"7KQ2M9XA","expiresAt":null}"""
            ).toByteArray()
        val parsed = WearExchangeHandoverCodec.parse(bytes, gson)
            as WearExchangeHandoverParseResult.UnsupportedVersion

        val ack = WearExchangeHandoverReceiver.unsupportedVersionRefusal(parsed)

        assertEquals(
            WearExchangeHandoverAck("r-9", enrolled = false, reason = WearExchangeHandoverReasons.UNSUPPORTED_VERSION),
            ack,
        )
    }

    @Test
    fun `only a readable past expiry refuses`() {
        val now = Instant.parse("2026-10-11T12:00:00Z")

        assertTrue(WearExchangeHandoverReceiver.isExpired("2026-10-11T11:59:59Z", now))
        assertFalse(WearExchangeHandoverReceiver.isExpired("2026-10-11T12:10:00Z", now))
        assertFalse(WearExchangeHandoverReceiver.isExpired(null, now))
        assertFalse(WearExchangeHandoverReceiver.isExpired("in ten minutes", now))
    }
}
