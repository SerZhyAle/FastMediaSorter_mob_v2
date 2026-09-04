package com.sza.fastmediasorter.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val EARLY = 1_000L
private const val LATE = 2_000L
private const val SKEW = 500L

class WearRecordMergeResolverTest {

    @Test
    fun `a sender that carries no stamps at all applies its record`() {
        val decision = resolver(senderCarriesStamps = false)
            .resolve(incomingStamp = null, localStamp = LATE)

        assertTrue(decision.apply)
        assertEquals(null, decision.stampEpochMillis)
    }

    @Test
    fun `an unstamped record loses to a stored record that carries a stamp`() {
        val decision = resolver().resolve(incomingStamp = null, localStamp = EARLY)

        assertFalse(decision.apply)
        assertEquals(null, decision.stampEpochMillis)
    }

    @Test
    fun `an unstamped record applies when neither side carries a stamp`() {
        val decision = resolver().resolve(incomingStamp = null, localStamp = null)

        assertTrue(decision.apply)
        assertEquals(null, decision.stampEpochMillis)
    }

    @Test
    fun `a record unknown to the receiver applies with the corrected stamp`() {
        val decision = resolver(skewMillis = SKEW).resolve(incomingStamp = EARLY, localStamp = null)

        assertTrue(decision.apply)
        assertEquals(EARLY + SKEW, decision.stampEpochMillis)
    }

    @Test
    fun `a later incoming stamp wins and is recorded in the receiver's time base`() {
        val decision = resolver(skewMillis = SKEW).resolve(incomingStamp = LATE, localStamp = EARLY)

        assertTrue(decision.apply)
        assertEquals(LATE + SKEW, decision.stampEpochMillis)
    }

    @Test
    fun `an equal stamp keeps the stored record`() {
        val decision = resolver().resolve(incomingStamp = LATE, localStamp = LATE)

        assertFalse(decision.apply)
    }

    @Test
    fun `an earlier incoming stamp keeps the stored record`() {
        val decision = resolver().resolve(incomingStamp = EARLY, localStamp = LATE)

        assertFalse(decision.apply)
    }

    @Test
    fun `the skew is applied before the comparison, not after it`() {
        // Raw, the incoming stamp loses by 500 ms. Corrected for a sender whose clock runs 800 ms
        // behind this receiver's, it wins - which is the whole reason the correction exists.
        val decision = resolver(skewMillis = 800L).resolve(incomingStamp = 1_500L, localStamp = 2_000L)

        assertTrue(decision.apply)
        assertEquals(2_300L, decision.stampEpochMillis)
    }

    private fun resolver(
        senderCarriesStamps: Boolean = true,
        skewMillis: Long = 0L
    ) = WearRecordMergeResolver(senderCarriesStamps = senderCarriesStamps, skewMillis = skewMillis)
}
