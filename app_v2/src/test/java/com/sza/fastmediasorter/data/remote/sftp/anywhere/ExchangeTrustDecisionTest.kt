package com.sza.fastmediasorter.data.remote.sftp.anywhere

import com.sza.fastmediasorter.data.remote.sftp.anywhere.ExchangeTrustDecision.Verdict
import org.junit.Assert.assertEquals
import org.junit.Test

/** Amendment D of contract DEVICE-EXCHANGE: platform trust first, a pin only for what the platform refuses. */
class ExchangeTrustDecisionTest {

    @Test
    fun `a CA-signed server is accepted with nothing pinned`() {
        assertEquals(Verdict.ACCEPT, ExchangeTrustDecision.decide(ExchangeTrust.Verified(null), true, LEAF))
    }

    @Test
    fun `a self-signed server needs the user to accept its fingerprint`() {
        assertEquals(Verdict.UNTRUSTED, ExchangeTrustDecision.decide(ExchangeTrust.Verified(null), false, LEAF))
    }

    @Test
    fun `the accepted fingerprint lets the self-signed server in`() {
        assertEquals(Verdict.ACCEPT, ExchangeTrustDecision.decide(ExchangeTrust.Verified(LEAF), false, LEAF))
    }

    @Test
    fun `a changed pinned certificate is refused even when the platform would trust it`() {
        assertEquals(Verdict.CHANGED, ExchangeTrustDecision.decide(ExchangeTrust.Verified(LEAF), false, OTHER))
        assertEquals(Verdict.CHANGED, ExchangeTrustDecision.decide(ExchangeTrust.Verified(LEAF), true, OTHER))
    }

    @Test
    fun `another leaf with the pinned key is a renewal`() {
        val trust = ExchangeTrust.Verified(LEAF, KEY)

        assertEquals(Verdict.RENEWED, ExchangeTrustDecision.decide(trust, false, OTHER, KEY))
    }

    @Test
    fun `another leaf with another key is refused`() {
        val trust = ExchangeTrust.Verified(LEAF, KEY)

        assertEquals(Verdict.CHANGED, ExchangeTrustDecision.decide(trust, true, OTHER, OTHER_KEY))
        assertEquals(Verdict.CHANGED, ExchangeTrustDecision.decide(trust, false, OTHER, null))
    }

    @Test
    fun `a pin whose key was never learned refuses any other leaf`() {
        val trust = ExchangeTrust.Verified(LEAF)

        assertEquals(Verdict.CHANGED, ExchangeTrustDecision.decide(trust, false, OTHER, KEY))
    }

    @Test
    fun `a pinned leaf is never refused for what the platform thinks of it`() {
        assertEquals(Verdict.ACCEPT, ExchangeTrustDecision.decide(ExchangeTrust.Verified(LEAF, KEY), false, LEAF, KEY))
    }

    @Test
    fun `only TLS 1_3 and 1_2 are allowed, most preferred first`() {
        val supported = arrayOf("TLSv1", "TLSv1.1", "TLSv1.2", "TLSv1.3")

        assertEquals(listOf("TLSv1.3", "TLSv1.2"), ExchangeTlsProtocols.allowed(supported).toList())
        assertEquals(emptyList<String>(), ExchangeTlsProtocols.allowed(arrayOf("TLSv1", "TLSv1.1")).toList())
    }

    private companion object {
        const val LEAF = "SHA256:leaf"
        const val OTHER = "SHA256:other"
        const val KEY = "SHA256:key"
        const val OTHER_KEY = "SHA256:otherKey"
    }
}
