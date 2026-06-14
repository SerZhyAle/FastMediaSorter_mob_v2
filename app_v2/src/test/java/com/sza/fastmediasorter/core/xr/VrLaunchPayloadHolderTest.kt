package com.sza.fastmediasorter.core.xr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VrLaunchPayloadHolderTest {

    @Test
    fun `put returns a unique token each call`() {
        val holder = VrLaunchPayloadHolder()
        val first = holder.put("a")
        val second = holder.put("b")
        assertNotEquals(first, second)
    }

    @Test
    fun `peek returns the payload without removing it`() {
        val holder = VrLaunchPayloadHolder()
        val token = holder.put("payload")
        assertEquals("payload", holder.peek<String>(token))
        assertEquals("payload", holder.peek<String>(token))
    }

    @Test
    fun `consume returns the payload once then null`() {
        val holder = VrLaunchPayloadHolder()
        val token = holder.put("payload")
        assertEquals("payload", holder.consume<String>(token))
        assertNull(holder.consume<String>(token))
    }

    @Test
    fun `unknown or null token returns null`() {
        val holder = VrLaunchPayloadHolder()
        assertNull(holder.peek<String>("missing"))
        assertNull(holder.consume<String>(null))
    }

    @Test
    fun `eviction drops the oldest entry beyond the cap`() {
        val holder = VrLaunchPayloadHolder()
        val first = holder.put("first")
        // Cap is 16; 16 further puts make 17 total, evicting the oldest ("first").
        repeat(16) { index -> holder.put("filler-$index") }
        assertNull(holder.peek<String>(first))
    }
}
