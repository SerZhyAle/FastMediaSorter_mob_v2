package com.sza.fastmediasorter.data.broadcast.auth

import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BroadcastCredentialRegistryTest {

    private val registry = BroadcastCredentialRegistry(Dispatchers.Unconfined)

    @Test
    fun `a broadcast is unprotected until it begins and after it ends`() {
        assertNull(registry.pairs())
        assertNull(registry.authMarker())
        registry.begin("b1")
        assertTrue(registry.isProtected)
        assertEquals(3, registry.pairs()!!.size)
        assertEquals(registry.linkPair!!.user, registry.authMarker()!!.user)
        registry.end()
        assertFalse(registry.isProtected)
        assertNull(registry.pairs())
    }

    @Test
    fun `renewing the link drops the old pair and keeps the user`() {
        registry.begin("b1")
        val old = registry.linkPair!!
        val revision = registry.revision.value
        val renewed = registry.renewLink()
        assertNotNull(renewed)
        assertEquals(old.user, renewed!!.user)
        assertNotEquals(old.password, renewed.password)
        assertFalse(registry.pairs()!!.contains(old))
        assertTrue(registry.pairs()!!.contains(registry.ownPair))
        assertTrue(registry.revision.value > revision)
    }

    @Test
    fun `a guest pair is accepted and verified until it is removed`() = kotlinx.coroutines.test.runTest {
        registry.begin("b1")
        val revision = registry.revision.value
        val guest = registry.addGuest("g1")!!
        assertTrue(registry.revision.value > revision)
        assertEquals(guest, registry.guestPair("g1"))
        assertTrue(registry.pairs()!!.contains(guest))
        assertTrue(registry.verifiers().any { it.user == guest.user })
        val afterAdd = registry.revision.value
        registry.removeGuest("g1")
        assertTrue(registry.revision.value > afterAdd)
        assertFalse(registry.pairs()!!.contains(guest))
        assertFalse(registry.verifiers().any { it.user == guest.user })
    }

    @Test
    fun `no guest pair exists outside a protected broadcast and ending drops them`() {
        assertNull(registry.addGuest("g1"))
        registry.begin("b1")
        registry.addGuest("g1")
        registry.end()
        registry.begin("b2")
        assertNull(registry.guestPair("g1"))
        assertEquals(3, registry.pairs()!!.size)
    }

    @Test
    fun `beginning the same broadcast again keeps its pairs`() {
        registry.begin("b1")
        val own = registry.ownPair
        registry.begin("b1")
        assertEquals(own, registry.ownPair)
    }
}
