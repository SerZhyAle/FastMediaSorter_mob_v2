package com.sza.fastmediasorter.data.broadcast.exchange

import com.sza.fastmediasorter.data.remote.sftp.anywhere.SftpServerTunnelManager.BroadcastWire
import com.sza.fastmediasorter.domain.model.DeviceExchangeAccountEntry
import com.sza.fastmediasorter.domain.model.ExchangeBroadcastPublishState
import com.sza.fastmediasorter.domain.model.ExchangeBroadcastPublishState.LocalOnly
import com.sza.fastmediasorter.domain.model.ExchangeMessage
import org.junit.Assert.assertEquals
import org.junit.Test

class ExchangeBroadcastPublishStatesTest {

    @Test
    fun `nothing live is idle whatever the server said`() {
        assertEquals(
            ExchangeBroadcastPublishState.Idle,
            ExchangeBroadcastPublishStates.of(false, listOf(MEMBER), MEMBER, BroadcastWire.Started(ID), true),
        )
    }

    @Test
    fun `no entry at all is local only`() {
        assertEquals(
            LocalOnly(LocalOnly.Reason.NO_ENTRY),
            ExchangeBroadcastPublishStates.of(true, emptyList(), null, BroadcastWire.None, false),
        )
    }

    @Test
    fun `entries none of which may broadcast are local only as guests`() {
        assertEquals(
            LocalOnly(LocalOnly.Reason.GUEST_ONLY),
            ExchangeBroadcastPublishStates.of(true, listOf(GUEST), null, BroadcastWire.None, false),
        )
    }

    @Test
    fun `broadcast-started puts it on the server, relayed when the upload was accepted`() {
        assertEquals(
            ExchangeBroadcastPublishState.OnServer("exchange.example.org:8443", relayed = true),
            ExchangeBroadcastPublishStates.of(true, listOf(MEMBER), MEMBER, BroadcastWire.Started(ID), true),
        )
    }

    @Test
    fun `a failure carries its table row`() {
        assertEquals(
            ExchangeBroadcastPublishState.Failed("exchange.example.org:8443", ExchangeMessage.UNREACHABLE_SERVER),
            ExchangeBroadcastPublishStates.of(
                true,
                listOf(MEMBER),
                MEMBER,
                BroadcastWire.Failed(ExchangeMessage.UNREACHABLE_SERVER),
                false,
            ),
        )
    }

    @Test
    fun `sent or not yet sent is still being announced`() {
        val announcing = ExchangeBroadcastPublishState.Announcing("exchange.example.org:8443")
        assertEquals(
            announcing,
            ExchangeBroadcastPublishStates.of(true, listOf(MEMBER), MEMBER, BroadcastWire.Sent(ID), false),
        )
        assertEquals(
            announcing,
            ExchangeBroadcastPublishStates.of(true, listOf(MEMBER), MEMBER, BroadcastWire.None, false),
        )
    }

    @Test
    fun `server label is host and port, an IPv6 host in brackets`() {
        assertEquals("exchange.example.org:8443", ExchangeBroadcastPublishStates.serverLabel(MEMBER))
        val ipv4 = MEMBER.copy(host = "192.168.1.20")
        assertEquals("192.168.1.20:8443", ExchangeBroadcastPublishStates.serverLabel(ipv4))
        assertEquals("[fd00::1]:8443", ExchangeBroadcastPublishStates.serverLabel(MEMBER.copy(host = "fd00::1")))
    }

    private companion object {
        const val ID = "OHk_EmsLjquhyn6_Pdw5DQ"
        val MEMBER = DeviceExchangeAccountEntry(
            host = "exchange.example.org",
            port = 8443,
            login = "alice",
            deviceId = "AAAAAAAAAAAAAAAAAAAAAA",
            pinnedCertificate = null,
            outgoing = true,
        )
        val GUEST = MEMBER.copy(accountKind = DeviceExchangeAccountEntry.ACCOUNT_KIND_GUEST)
    }
}
