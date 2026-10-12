package com.sza.fastmediasorter.wear.data.exchange

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/** S4158: the 7.1 framing and the amendment D trust decision as the watch mirrors them. */
class WearExchangeWireTest {

    @Test
    fun `an envelope survives a frame round trip`() {
        val out = ByteArrayOutputStream()
        WearExchangeFrameCodec.write(out, WearExchangeEnvelope.of("enroll") { addProperty("login", "abc") })

        val bytes = out.toByteArray()
        val read = WearExchangeFrameCodec.read(ByteArrayInputStream(bytes))

        assertEquals(0, bytes[0].toInt())
        assertEquals("enroll", read?.type)
        assertEquals("abc", read?.string("login"))
    }

    @Test
    fun `a clean end of stream is null and a truncated frame is a violation`() {
        assertNull(WearExchangeFrameCodec.read(ByteArrayInputStream(ByteArray(0))))
        assertThrows(WearExchangeWireException::class.java) {
            WearExchangeFrameCodec.read(ByteArrayInputStream(byteArrayOf(0, 0, 0, 9, '{'.code.toByte())))
        }
    }

    @Test
    fun `another schemaVersion ends the stream`() {
        assertThrows(WearExchangeWireException::class.java) {
            WearExchangeEnvelope.decode("""{"schemaVersion":1,"type":"welcome"}""")
        }
    }

    @Test
    fun `the trust decision follows amendment D`() {
        assertEquals(
            WearExchangeTrustDecision.Verdict.ACCEPT,
            WearExchangeTrustDecision.decide(pinned = null, platformTrusted = true, presented = "SHA256:a"),
        )
        assertEquals(
            WearExchangeTrustDecision.Verdict.UNTRUSTED,
            WearExchangeTrustDecision.decide(pinned = null, platformTrusted = false, presented = "SHA256:a"),
        )
        assertEquals(
            WearExchangeTrustDecision.Verdict.ACCEPT,
            WearExchangeTrustDecision.decide(pinned = "SHA256:a", platformTrusted = false, presented = "SHA256:a"),
        )
        assertEquals(
            WearExchangeTrustDecision.Verdict.CHANGED,
            WearExchangeTrustDecision.decide(pinned = "SHA256:a", platformTrusted = true, presented = "SHA256:b"),
        )
    }

    @Test
    fun `a fresh device id is sixteen bytes of base64url`() {
        val id = WearExchangeEntry.newDeviceId()

        assertEquals(22, id.length)
        assertEquals(true, id.all { it.isLetterOrDigit() || it == '-' || it == '_' })
    }
}
