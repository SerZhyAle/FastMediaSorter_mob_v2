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

    private companion object {
        const val LEAF = "SHA256:leaf"
        const val OTHER = "SHA256:other"
    }
}
