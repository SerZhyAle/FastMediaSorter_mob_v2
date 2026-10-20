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
    fun `typed accessors read numbers, booleans and objects`() {
        val read = WearExchangeEnvelope.decode(
            """{"schemaVersion":2,"type":"cast-offer","n":30,"ok":true,"broadcast":{"mode":"AUDIO_ONLY"}}"""
        )

        assertEquals(30, read.int("n"))
        assertEquals(true, read.boolean("ok"))
        assertEquals("AUDIO_ONLY", read.obj("broadcast")?.get("mode")?.asString)
        assertNull(read.int("ok"))
        assertNull(read.obj("n"))
    }

    @Test
    fun `an authenticated frame may exceed the enroll limit`() {
        val big = "x".repeat(WearExchangeFrameCodec.MAX_FRAME_BODY)
        val envelope = WearExchangeEnvelope.of(WearExchangeEnvelope.TYPE_CAST_OFFER) { addProperty("pad", big) }
        val out = ByteArrayOutputStream()

        assertThrows(WearExchangeWireException::class.java) { WearExchangeFrameCodec.write(out, envelope) }
        WearExchangeFrameCodec.write(out, envelope, WearExchangeFrameCodec.AUTHENTICATED_MAX_FRAME_BODY)
        val bytes = out.toByteArray()

        assertThrows(WearExchangeWireException::class.java) {
            WearExchangeFrameCodec.read(ByteArrayInputStream(bytes))
        }
        val read = WearExchangeFrameCodec.read(
            ByteArrayInputStream(bytes),
            WearExchangeFrameCodec.AUTHENTICATED_MAX_FRAME_BODY,
        )
        assertEquals(big, read?.string("pad"))
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
    fun `a renewed leaf passes only on the pinned key`() {
        assertEquals(
            WearExchangeTrustDecision.Verdict.RENEWED,
            WearExchangeTrustDecision.decide("SHA256:a", false, "SHA256:b", pinnedKey = "K", presentedKey = "K"),
        )
        assertEquals(
            WearExchangeTrustDecision.Verdict.CHANGED,
            WearExchangeTrustDecision.decide("SHA256:a", true, "SHA256:b", pinnedKey = "K", presentedKey = "L"),
        )
        assertEquals(
            WearExchangeTrustDecision.Verdict.CHANGED,
            WearExchangeTrustDecision.decide("SHA256:a", false, "SHA256:b", pinnedKey = null, presentedKey = "K"),
        )
        assertEquals(
            WearExchangeTrustDecision.Verdict.ACCEPT,
            WearExchangeTrustDecision.decide("SHA256:a", false, "SHA256:a", pinnedKey = "K", presentedKey = "K"),
        )
    }

    @Test
    fun `only TLS 1_3 and 1_2 are enabled, most preferred first`() {
        val supported = arrayOf("TLSv1", "TLSv1.1", "TLSv1.2", "TLSv1.3")

        assertEquals(listOf("TLSv1.3", "TLSv1.2"), TlsWearExchangeConnector.allowedProtocols(supported).toList())
        assertEquals(0, TlsWearExchangeConnector.allowedProtocols(arrayOf("TLSv1", "TLSv1.1")).size)
    }

    @Test
    fun `only a guest entry may not broadcast`() {
        val entry = WearExchangeEntry("h", 1, "login", "id", null)

        assertEquals(true, entry.mayProduce)
        assertEquals(true, entry.copy(accountKind = "member").mayProduce)
        assertEquals(false, entry.copy(accountKind = WearExchangeEntry.ACCOUNT_KIND_GUEST).mayProduce)
    }

    @Test
    fun `a fresh device id is sixteen bytes of base64url`() {
        val id = WearExchangeEntry.newDeviceId()

        assertEquals(22, id.length)
        assertEquals(true, id.all { it.isLetterOrDigit() || it == '-' || it == '_' })
    }
}
