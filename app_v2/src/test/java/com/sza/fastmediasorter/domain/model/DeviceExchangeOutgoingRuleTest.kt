package com.sza.fastmediasorter.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class DeviceExchangeOutgoingRuleTest {

    @Test
    fun `an empty list stays empty`() {
        assertEquals(emptyList<DeviceExchangeAccountEntry>(), DeviceExchangeOutgoingRule.normalize(emptyList()))
    }

    @Test
    fun `a member entry with no flag gets it`() {
        val normalized = DeviceExchangeOutgoingRule.normalize(listOf(entry("a")))

        assertEquals(listOf(true), normalized.map { it.outgoing })
    }

    @Test
    fun `a producing outgoing entry keeps the flag and the list is returned as it is`() {
        val held = listOf(entry("a"), entry("b", outgoing = true))

        assertSame(held, DeviceExchangeOutgoingRule.normalize(held))
    }

    @Test
    fun `a guest outgoing entry gives the flag to the first member entry`() {
        val held = listOf(entry("a", outgoing = true, kind = GUEST), entry("b"), entry("c"))

        assertEquals(listOf(false, true, false), DeviceExchangeOutgoingRule.normalize(held).map { it.outgoing })
    }

    @Test
    fun `a guest-only list keeps its flags`() {
        val held = listOf(entry("a", kind = GUEST), entry("b", outgoing = true, kind = GUEST))

        assertSame(held, DeviceExchangeOutgoingRule.normalize(held))
    }

    @Test
    fun `order and other fields are kept`() {
        val held = listOf(entry("a", kind = GUEST).copy(autoAttach = true), entry("b"))

        val normalized = DeviceExchangeOutgoingRule.normalize(held)

        assertEquals(listOf("a", "b"), normalized.map { it.host })
        assertEquals(true, normalized.first().autoAttach)
        assertEquals(listOf(false, true), normalized.map { it.outgoing })
    }

    private fun entry(host: String, outgoing: Boolean = false, kind: String = MEMBER) = DeviceExchangeAccountEntry(
        host = host,
        port = PORT,
        login = "alice",
        deviceId = "id-$host",
        pinnedCertificate = null,
        outgoing = outgoing,
        accountKind = kind,
    )

    private companion object {
        const val PORT = 8443
        const val MEMBER = "member"
        const val GUEST = "guest"
    }
}
