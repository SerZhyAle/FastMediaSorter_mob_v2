package com.sza.fastmediasorter.domain.model

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/** S4158: the hand-over wire both modules mirror - what parses, what is refused by name, what never prints. */
class WearExchangeHandoverCodecTest {

    private val gson = Gson()
    private val payload = WearExchangeHandoverPayload(
        requestId = "r-1",
        host = "exchange.example.net",
        port = 44_022,
        login = "owner.home",
        pinnedCertificate = null,
        pairingCode = "7KQ2M9XA",
        expiresAt = "2026-10-11T12:10:00Z",
    )

    @Test
    fun `a payload survives the round trip`() {
        val parsed = WearExchangeHandoverCodec.parse(WearExchangeHandoverCodec.serialize(payload, gson), gson)

        assertEquals(WearExchangeHandoverParseResult.Parsed(payload), parsed)
    }

    @Test
    fun `a payload without a code is malformed`() {
        val bytes = """{"requestId":"r-1","formatVersion":1,"host":"h","port":1,"login":"abc"}""".toByteArray()

        assertEquals(WearExchangeHandoverParseResult.Malformed, WearExchangeHandoverCodec.parse(bytes, gson))
    }

    @Test
    fun `a port outside the range is malformed`() {
        val bytes = WearExchangeHandoverCodec.serialize(payload.copy(port = 0), gson)

        assertEquals(WearExchangeHandoverParseResult.Malformed, WearExchangeHandoverCodec.parse(bytes, gson))
    }

    @Test
    fun `an unknown version is refused by its number`() {
        val bytes = gson.toJson(payload.copy(formatVersion = 9)).toByteArray()

        assertEquals(
            WearExchangeHandoverParseResult.UnsupportedVersion(version = 9, requestId = "r-1"),
            WearExchangeHandoverCodec.parse(bytes, gson),
        )
    }

    @Test
    fun `a newer format that renamed the fields is still refused by its version`() {
        val bytes = """{"requestId":"r-7","formatVersion":3,"server":{"host":"h"},"code":"c"}""".toByteArray()

        assertEquals(
            WearExchangeHandoverParseResult.UnsupportedVersion(version = 3, requestId = "r-7"),
            WearExchangeHandoverCodec.parse(bytes, gson),
        )
    }

    @Test
    fun `an unknown version without an id is malformed`() {
        val bytes = gson.toJson(payload.copy(requestId = " ", formatVersion = 9)).toByteArray()

        assertEquals(WearExchangeHandoverParseResult.Malformed, WearExchangeHandoverCodec.parse(bytes, gson))
    }

    @Test
    fun `text that is not JSON is malformed`() {
        assertEquals(
            WearExchangeHandoverParseResult.Malformed,
            WearExchangeHandoverCodec.parse("not json".toByteArray(), gson),
        )
    }

    @Test
    fun `the payload text names neither the code nor the address nor the login`() {
        val text = payload.toString()

        assertFalse(text.contains(payload.pairingCode))
        assertFalse(text.contains(payload.host))
        assertFalse(text.contains(payload.login))
    }

    @Test
    fun `an answer survives the round trip and one without an id is dropped`() {
        val ack = WearExchangeHandoverAck("r-1", enrolled = false, reason = WearExchangeHandoverReasons.CODE_EXPIRED)

        assertEquals(ack, WearExchangeHandoverCodec.parseAck(WearExchangeHandoverCodec.serializeAck(ack, gson), gson))
        assertNull(WearExchangeHandoverCodec.parseAck("""{"enrolled":true}""".toByteArray(), gson))
    }
}
