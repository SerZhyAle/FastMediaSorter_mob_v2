package com.sza.fastmediasorter.wear.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WearEventEnvelopeCodecTest {

    private val codec = WearEventEnvelopeCodec()

    @Test
    fun `round trips compact binary payload`() {
        val envelope = fixtureEnvelope()

        assertEquals(envelope, codec.decode(codec.encode(envelope)))
    }

    @Test
    fun `uses the shared wire fixture`() {
        val encoded = codec.encode(fixtureEnvelope()).toString(Charsets.UTF_8)

        assertEquals(FIXTURE, encoded)
        assertEquals(fixtureEnvelope(), codec.decode(FIXTURE.toByteArray(Charsets.UTF_8)))
    }

    @Test
    fun `does not serialize payload as a decimal array`() {
        val encoded = codec.encode(fixtureEnvelope()).toString(Charsets.UTF_8)

        assertTrue(encoded.contains("\"data\":\"AAECA/8=\""))
        assertFalse(encoded.contains("\"data\":["))
    }

    private fun fixtureEnvelope() = WearEventEnvelope(
        eventType = "resource_page",
        sentAt = 1_700_000_000_000L,
        data = byteArrayOf(0, 1, 2, 3, -1)
    )

    private companion object {
        const val FIXTURE =
            "{\"eventType\":\"resource_page\",\"schemaVersion\":1,\"sentAt\":1700000000000,\"data\":\"AAECA/8=\"}"
    }
}
