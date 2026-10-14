package com.sza.fastmediasorter.wear.data.wear

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    }

    @Test
    fun `an answer the phone can read`() {
        val ack = WearExchangeHandoverAck("r-1", enrolled = false, reason = WearExchangeHandoverReasons.CODE_EXPIRED)

        val json = String(WearExchangeHandoverCodec.serializeAck(ack, gson))

        assertEquals("""{"requestId":"r-1","enrolled":false,"reason":"code_expired"}""", json)
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
