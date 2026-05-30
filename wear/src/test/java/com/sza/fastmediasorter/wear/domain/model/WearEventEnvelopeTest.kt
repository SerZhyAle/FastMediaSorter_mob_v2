package com.sza.fastmediasorter.wear.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class WearEventEnvelopeTest {

    @Test
    fun `equals uses byte array content`() {
        val first = WearEventEnvelope(
            eventType = "playback",
            sentAt = 123L,
            data = byteArrayOf(1, 2, 3)
        )
        val second = WearEventEnvelope(
            eventType = "playback",
            sentAt = 123L,
            data = byteArrayOf(1, 2, 3)
        )

        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())
    }

    @Test
    fun `equals rejects different byte array content`() {
        val first = WearEventEnvelope(
            eventType = "playback",
            sentAt = 123L,
            data = byteArrayOf(1, 2, 3)
        )
        val second = first.copy(data = byteArrayOf(1, 2, 4))

        assertNotEquals(first, second)
    }
}