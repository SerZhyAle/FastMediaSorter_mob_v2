package com.sza.fastmediasorter.data.broadcast.exchange

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExchangeRelayKeysTest {

    private val keys = ExchangeRelayKeys().apply { begin(BROADCAST) }

    @Test
    fun `a key made before the first epoch is in its ring and keeps it current`() {
        val outward = keys.linkKey(BROADCAST, "outward")!!
        val ring = keys.newEpoch(BROADCAST)!!
        assertTrue(ring.groupKeys.any { it.contentEquals(outward) })
        assertTrue(keys.isCurrent(BROADCAST, ring))
    }

    @Test
    fun `a grant key made during an epoch ends it and joins the next ring`() {
        val ring = keys.newEpoch(BROADCAST)!!
        val guest = keys.linkKey(BROADCAST, "g1")!!
        assertFalse(keys.isCurrent(BROADCAST, ring))
        val next = keys.newEpoch(BROADCAST)!!
        assertTrue(next.groupKeys.any { it.contentEquals(guest) })
        assertTrue(keys.isCurrent(BROADCAST, next))
    }

    @Test
    fun `a dropped grant key ends the epoch and leaves the next ring`() {
        val guest = keys.linkKey(BROADCAST, "g1")!!
        val ring = keys.newEpoch(BROADCAST)!!
        keys.dropLink(BROADCAST, "g1")
        assertFalse(keys.isCurrent(BROADCAST, ring))
        val next = keys.newEpoch(BROADCAST)!!
        assertFalse(next.groupKeys.any { it.contentEquals(guest) })
    }

    @Test
    fun `asking for the same key again changes nothing`() {
        val first = keys.linkKey(BROADCAST, "g1")!!
        val ring = keys.newEpoch(BROADCAST)!!
        assertTrue(first.contentEquals(keys.linkKey(BROADCAST, "g1")))
        assertTrue(keys.isCurrent(BROADCAST, ring))
    }

    @Test
    fun `no key is made for another broadcast or past the ring limit`() {
        assertNull(keys.linkKey("other", "g1"))
        val made = (1..RING_LIMIT).mapNotNull { keys.linkKey(BROADCAST, "g$it") }
        assertEquals(RING_LIMIT - 1, made.size)
        assertNotNull(keys.newEpoch(BROADCAST))
    }

    private companion object {
        const val BROADCAST = "b1"
        const val RING_LIMIT = 64
    }
}
