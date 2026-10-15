package com.sza.fastmediasorter.data.broadcast.exchange

import com.sza.fastmediasorter.data.broadcast.auth.BroadcastCredentialRegistry
import com.sza.fastmediasorter.domain.model.ExchangeBroadcastGrant
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExchangeBroadcastGrantsTest {

    private val credentials = BroadcastCredentialRegistry(Dispatchers.Unconfined)
    private val keys = ExchangeRelayKeys()
    private val grants = ExchangeBroadcastGrants(credentials, keys)

    private fun live() {
        credentials.begin(BROADCAST)
        keys.begin(BROADCAST)
    }

    @Test
    fun `a grant owns a pair and a relay key until it is removed`() {
        live()
        val grant = grants.add(BROADCAST, "bob")!!
        assertEquals(ExchangeBroadcastGrant.State.PENDING, grant.state)
        val pair = credentials.guestPair(grant.grantId)
        assertNotNull(pair)
        assertTrue(credentials.pairs()!!.contains(pair))
        val guestKey = keys.linkKey(BROADCAST, grant.grantId)
        assertTrue(keys.newEpoch(BROADCAST)!!.groupKeys.any { it.contentEquals(guestKey) })
        grants.remove(grant.grantId)
        assertFalse(credentials.pairs()!!.contains(pair))
        assertTrue(grants.grants.value.isEmpty())
    }

    @Test
    fun `nothing is granted outside a protected broadcast or twice to one guest`() {
        assertNull(grants.add(BROADCAST, "bob"))
        live()
        assertNotNull(grants.add(BROADCAST, "bob"))
        assertNull(grants.add(BROADCAST, "bob"))
        assertEquals(1, grants.grants.value.size)
    }

    @Test
    fun `answers mark the right grant`() {
        live()
        val first = grants.add(BROADCAST, "bob")!!
        val second = grants.add(BROADCAST, "eve")!!
        grants.markGranted(second.grantId)
        grants.markRefusedPending()
        val byId = grants.grants.value.associateBy { it.grantId }
        assertEquals(ExchangeBroadcastGrant.State.REFUSED, byId.getValue(first.grantId).state)
        assertEquals(ExchangeBroadcastGrant.State.GRANTED, byId.getValue(second.grantId).state)
    }

    @Test
    fun `clear drops every grant and its pair`() {
        live()
        val grant = grants.add(BROADCAST, "bob")!!
        grants.clear()
        assertNull(credentials.guestPair(grant.grantId))
        assertTrue(grants.grants.value.isEmpty())
    }

    private companion object {
        const val BROADCAST = "b1"
    }
}
