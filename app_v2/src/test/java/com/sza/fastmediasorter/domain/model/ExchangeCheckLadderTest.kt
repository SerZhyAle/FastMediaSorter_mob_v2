package com.sza.fastmediasorter.domain.model

import com.sza.fastmediasorter.domain.model.ExchangeCheckStep.Companion.of
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class ExchangeCheckLadderTest {

    private fun ids(spaced: String) = spaced.split(" ")

    private fun walk(
        message: ExchangeMessage,
        route: ExchangeCheckRoute = ExchangeCheckRoute.NETWORK,
        target: ExchangeCheckTarget = ExchangeCheckTarget.SERVER,
        evidence: ExchangeCheckEvidence = ExchangeCheckEvidence(),
        family: ExchangeAddressFamily? = null,
    ) = ExchangeCheckLadder.walk(message, target, route, evidence, family).map { it.id }

    @Test
    fun `a message with no step walks nothing`() {
        assertEquals(emptyList<String>(), walk(ExchangeMessage.LOGIN_TAKEN))
        assertEquals(emptyList<String>(), walk(ExchangeMessage.RESOURCE_LOCKED))
    }

    @Test
    fun `a message with a reason of its own offers its one step whatever the evidence says`() {
        val evidence = ExchangeCheckEvidence(vpnActive = true, certificateTimeInvalid = true)
        assertEquals(listOf("credentials"), walk(ExchangeMessage.BAD_CREDENTIALS, evidence = evidence))
        assertEquals(listOf("sign-in-rate"), walk(ExchangeMessage.RATE_LIMITED))
        assertEquals(listOf("pc-asleep"), walk(ExchangeMessage.SERVER_OFFLINE))
    }

    @Test
    fun `an unreachable server on the own network walks every applicable step to the end`() {
        assertEquals(
            ids(
                "pc-asleep address-lan guest-network vpn windows-public-network ip-family device-clock " +
                    "certificate credentials sign-in-rate invitation device-removed operator"
            ),
            walk(ExchangeMessage.UNREACHABLE_SERVER),
        )
    }

    @Test
    fun `an unreachable server reached from outside skips the steps of the own network`() {
        assertEquals(
            ids(
                "pc-asleep vpn ip-family server-internet port-forward device-clock certificate credentials " +
                    "sign-in-rate invitation device-removed operator"
            ),
            walk(ExchangeMessage.UNREACHABLE_SERVER, route = ExchangeCheckRoute.INTERNET),
        )
    }

    @Test
    fun `a device target has no windows step`() {
        val steps = walk(ExchangeMessage.UNREACHABLE_DEVICE, target = ExchangeCheckTarget.DEVICE)
        assertFalse("windows-public-network" in steps)
        assertEquals("pc-asleep", steps.first())
    }

    @Test
    fun `a failed handshake starts at the clock and goes on to the end`() {
        assertEquals(
            ids("device-clock certificate credentials sign-in-rate invitation device-removed operator"),
            walk(ExchangeMessage.TLS_FAILED),
        )
    }

    @Test
    fun `an active vpn is named first and an inactive one is left out`() {
        val active = walk(ExchangeMessage.UNREACHABLE_SERVER, evidence = ExchangeCheckEvidence(vpnActive = true))
        assertEquals(listOf("vpn", "pc-asleep"), active.take(2))
        assertEquals(1, active.count { it == "vpn" })

        val inactive = walk(ExchangeMessage.UNREACHABLE_SERVER, evidence = ExchangeCheckEvidence(vpnActive = false))
        assertFalse("vpn" in inactive)
    }

    @Test
    fun `a missing address family is named first and a present one is left out`() {
        val missing = walk(
            ExchangeMessage.UNREACHABLE_SERVER,
            evidence = ExchangeCheckEvidence(deviceHasIpv6 = false),
            family = ExchangeAddressFamily.IPV6,
        )
        assertEquals("ip-family", missing.first())

        val present = walk(
            ExchangeMessage.UNREACHABLE_SERVER,
            evidence = ExchangeCheckEvidence(deviceHasIpv6 = true),
            family = ExchangeAddressFamily.IPV6,
        )
        assertFalse("ip-family" in present)

        val otherFamily = walk(
            ExchangeMessage.UNREACHABLE_SERVER,
            evidence = ExchangeCheckEvidence(deviceHasIpv6 = false),
            family = ExchangeAddressFamily.IPV4,
        )
        assertEquals(true, "ip-family" in otherFamily)
        assertEquals("pc-asleep", otherFamily.first())
    }

    @Test
    fun `a certificate outside its validity period names the clock before the rest`() {
        val steps = walk(
            ExchangeMessage.TLS_FAILED,
            evidence = ExchangeCheckEvidence(vpnActive = true, certificateTimeInvalid = true),
        )
        assertEquals(listOf("vpn", "device-clock", "certificate"), steps.take(3))
    }

    @Test
    fun `the evidence of a step the failure does not walk does not add it`() {
        val steps = walk(ExchangeMessage.CERTIFICATE_CHANGED, evidence = ExchangeCheckEvidence(vpnActive = true))
        assertEquals(listOf("certificate"), steps)
    }

    @Test
    fun `private link-local and unique-local literals and local names are the own network`() {
        ids(
            "192.168.1.20 10.0.0.5 172.16.4.4 172.31.255.1 169.254.1.1 127.0.0.1 100.64.0.9 100.127.255.254 " +
                "[fe80::1] fd12:3456::1 [fc00::5] ::1 nas nas.local box.lan pc.home x.internal Server.LOCAL"
        ).forEach { host ->
            assertEquals(host, ExchangeCheckRoute.NETWORK, ExchangeCheckLadder.routeOf(host))
        }
    }

    @Test
    fun `public literals and ordinary names are reached from outside`() {
        ids(
            "8.8.8.8 172.32.0.1 172.15.0.1 100.128.0.1 100.63.255.255 2001:db8::1 example.com my.dyndns.org " +
                "[2a00:1450::1]"
        ).forEach { host ->
            assertEquals(host, ExchangeCheckRoute.INTERNET, ExchangeCheckLadder.routeOf(host))
        }
    }

    @Test
    fun `only a literal address has a family`() {
        assertEquals(ExchangeAddressFamily.IPV4, ExchangeCheckLadder.familyOf("192.168.1.2"))
        assertEquals(ExchangeAddressFamily.IPV6, ExchangeCheckLadder.familyOf("[2001:db8::1]"))
        assertEquals(ExchangeAddressFamily.IPV6, ExchangeCheckLadder.familyOf("fe80::1%wlan0"))
        assertNull(ExchangeCheckLadder.familyOf("example.com"))
        assertNull(ExchangeCheckLadder.familyOf("999.1.1.1"))
    }

    @Test
    fun `the step ids used here exist`() {
        assertEquals(ExchangeCheckStep.VPN, of("vpn"))
    }
}
