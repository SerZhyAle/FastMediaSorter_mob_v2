package com.sza.fastmediasorter.domain.model

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WearResourcePassPayloadTest {

    private val gson = Gson()

    private val payload = WearResourcePassPayload(
        requestId = "r-1",
        fingerprint = "SHA256:abc",
        root = "Music",
        exchangeAccount = "fms.example:7443/anna",
        resourceId = "res-1",
        pin = "4321",
    )

    @Test
    fun `a pass survives the wire`() {
        val parsed = WearResourcePassCodec.parse(WearResourcePassCodec.serialize(payload, gson), gson)

        assertEquals(WearResourcePassParseResult.Parsed(payload), parsed)
    }

    @Test
    fun `the text form names no secret`() {
        val text = payload.toString()

        assertFalse(text.contains("4321"))
        assertFalse(text.contains("Music"))
        assertFalse(text.contains("anna"))
    }

    @Test
    fun `an unknown version is refused by name`() {
        val bytes = """{"requestId":"r-1","formatVersion":2,"fingerprint":"f","root":"r","exchangeAccount":"a",""" +
            """"resourceId":"i","pin":"1"}"""

        assertEquals(
            WearResourcePassParseResult.UnsupportedVersion(2),
            WearResourcePassCodec.parse(bytes.toByteArray(), gson),
        )
    }

    @Test
    fun `a pass without its PIN is malformed`() {
        val bytes = """{"requestId":"r-1","formatVersion":1,"fingerprint":"f","root":"r","exchangeAccount":"a",""" +
            """"resourceId":"i"}"""

        assertEquals(WearResourcePassParseResult.Malformed, WearResourcePassCodec.parse(bytes.toByteArray(), gson))
    }

    @Test
    fun `an answer survives the wire and an empty one is not an answer`() {
        val ack = WearResourcePassAck(requestId = "r-1", stored = false, reason = WearResourcePassReasons.NOT_STORED)

        assertEquals(ack, WearResourcePassCodec.parseAck(WearResourcePassCodec.serializeAck(ack, gson), gson))
        assertNull(WearResourcePassCodec.parseAck("{}".toByteArray(), gson))
        assertTrue(WearResourcePassCodec.parseAck("""{"requestId":"r-2","stored":true}""".toByteArray(), gson)!!.stored)
    }
}
