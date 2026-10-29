package com.sza.fastmediasorter.wear.data.exchange

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Contract DEVICE-EXCHANGE 1.6 item DS on the watch. */
class WearExchangeOutgoingRuleTest {

    @Test
    fun `an empty list stays empty`() {
        assertTrue(WearExchangeOutgoingRule.normalize(emptyList()).isEmpty())
    }

    @Test
    fun `the one member entry with no flag gets it`() {
        assertEquals(listOf(true), WearExchangeOutgoingRule.normalize(listOf(MEMBER)).map { it.outgoing })
    }

    @Test
    fun `a second entry leaves the flag on the first`() {
        val held = listOf(MEMBER.copy(outgoing = true), SECOND)

        assertEquals(held, WearExchangeOutgoingRule.normalize(held))
    }

    @Test
    fun `a guest outgoing entry passes the flag to the member`() {
        val held = listOf(GUEST.copy(outgoing = true), MEMBER)

        assertEquals(listOf(false, true), WearExchangeOutgoingRule.normalize(held).map { it.outgoing })
    }

    @Test
    fun `a guest-only list keeps its flags`() {
        val held = listOf(GUEST.copy(outgoing = true), GUEST.copy(host = "guest2.example.net"))

        assertEquals(held, WearExchangeOutgoingRule.normalize(held))
    }

    @Test
    fun `order and every other field are kept`() {
        val held = listOf(GUEST, MEMBER.copy(features = listOf("sealed")), SECOND)

        val normalized = WearExchangeOutgoingRule.normalize(held)

        assertEquals(held.map { it.identity }, normalized.map { it.identity })
        assertEquals(held.map { it.copy(outgoing = false) }, normalized.map { it.copy(outgoing = false) })
        assertEquals(listOf(false, true, false), normalized.map { it.outgoing })
    }

    private companion object {
        val MEMBER = WearExchangeEntry("exchange.example.net", 44_022, "owner", "AAAAAAAAAAAAAAAAAAAAAA", null)
        val SECOND = MEMBER.copy(host = "other.example.net", deviceId = "BBBBBBBBBBBBBBBBBBBBBB")
        val GUEST = MEMBER.copy(
            host = "guest.example.net",
            deviceId = "CCCCCCCCCCCCCCCCCCCCCC",
            accountKind = WearExchangeEntry.ACCOUNT_KIND_GUEST,
        )
    }
}
