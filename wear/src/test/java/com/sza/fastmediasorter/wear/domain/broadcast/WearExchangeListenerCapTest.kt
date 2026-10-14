package com.sza.fastmediasorter.wear.domain.broadcast

import org.junit.Assert.assertEquals
import org.junit.Test

/** Amendment M: the relay share of the watch's cap of four, never the "no cap" zero. */
class WearExchangeListenerCapTest {

    @Test
    fun `no LAN listener leaves the whole cap to the relay`() {
        assertEquals(4, WearExchangeListenerCap.maxListeners(0))
    }

    @Test
    fun `every LAN listener lowers the relay share by one`() {
        assertEquals(3, WearExchangeListenerCap.maxListeners(1))
        assertEquals(1, WearExchangeListenerCap.maxListeners(3))
    }

    @Test
    fun `a full LAN never sends zero, which would lift the cap`() {
        assertEquals(1, WearExchangeListenerCap.maxListeners(4))
        assertEquals(1, WearExchangeListenerCap.maxListeners(5))
    }

    @Test
    fun `a negative count is read as none`() {
        assertEquals(4, WearExchangeListenerCap.maxListeners(-1))
    }
}
