package com.sza.fastmediasorter.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the custom [WearEventEnvelope.equals]/[WearEventEnvelope.hashCode] -
 * the only hand-written logic on the model (content-based ByteArray comparison).
 */
class WearEventEnvelopeTest {

    private fun envelope(
        eventType: String = "playback",
        schemaVersion: Int = 1,
        sentAt: Long = 100L,
        data: ByteArray = byteArrayOf(1, 2, 3),
    ) = WearEventEnvelope(eventType, schemaVersion, sentAt, data)

    @Test
    fun `equal when all fields match including byte content`() {
        val a = envelope(data = byteArrayOf(1, 2, 3))
        val b = envelope(data = byteArrayOf(1, 2, 3))
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `not equal when byte content differs`() {
        assertNotEquals(envelope(data = byteArrayOf(1, 2, 3)), envelope(data = byteArrayOf(1, 2, 4)))
    }

    @Test
    fun `not equal when a scalar field differs`() {
        assertNotEquals(envelope(eventType = "a"), envelope(eventType = "b"))
        assertNotEquals(envelope(schemaVersion = 1), envelope(schemaVersion = 2))
        assertNotEquals(envelope(sentAt = 1L), envelope(sentAt = 2L))
    }

    @Test
    fun `reflexive and type-guarded`() {
        val a = envelope()
        assertEquals(a, a)
        assertNotEquals(a, "not an envelope")
    }

    @Test
    fun `equal instances with distinct byte arrays share a hash code`() {
        val a = envelope(data = byteArrayOf(9, 8, 7))
        val b = envelope(data = byteArrayOf(9, 8, 7))
        // Distinct array references but content-equal: contract requires equal hash codes.
        assertTrue(a.data !== b.data)
        assertEquals(a.hashCode(), b.hashCode())
    }
}
