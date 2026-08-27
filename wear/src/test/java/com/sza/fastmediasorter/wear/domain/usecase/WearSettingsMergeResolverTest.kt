package com.sza.fastmediasorter.wear.domain.usecase

import com.sza.fastmediasorter.wear.domain.model.WearSettingsMergeResolver
import com.sza.fastmediasorter.wear.domain.model.WearSettingsRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val EARLY = 1_000L
private const val LATE = 2_000L
private const val SKEW = 500L
private const val FIELD = "viewMode"

class WearSettingsMergeResolverTest {

    @Test
    fun `no incoming stamps at all applies the incoming value`() {
        val resolver = resolver(incomingStamps = null, localStamps = mapOf(FIELD to LATE))

        val decision = resolver.resolve(FIELD)

        assertTrue(decision.apply)
        assertEquals(null, decision.stampEpochMillis)
    }

    @Test
    fun `a later incoming stamp wins and is recorded in the receiver's time base`() {
        val resolver = resolver(
            incomingStamps = mapOf(FIELD to LATE),
            localStamps = mapOf(FIELD to EARLY),
            skewMillis = SKEW
        )

        val decision = resolver.resolve(FIELD)

        assertTrue(decision.apply)
        assertEquals(LATE + SKEW, decision.stampEpochMillis)
    }

    @Test
    fun `an earlier incoming stamp keeps the stored value`() {
        val resolver = resolver(
            incomingStamps = mapOf(FIELD to EARLY),
            localStamps = mapOf(FIELD to LATE)
        )

        assertFalse(resolver.resolve(FIELD).apply)
    }

    @Test
    fun `an equal stamp keeps the stored value rather than rewriting it`() {
        val resolver = resolver(
            incomingStamps = mapOf(FIELD to LATE),
            localStamps = mapOf(FIELD to LATE)
        )

        assertFalse(resolver.resolve(FIELD).apply)
    }

    @Test
    fun `a stamped sender with nothing for this field loses to a stamped local edit`() {
        val resolver = resolver(
            incomingStamps = mapOf("audioEnabled" to LATE),
            localStamps = mapOf(FIELD to EARLY)
        )

        assertFalse(resolver.resolve(FIELD).apply)
    }

    @Test
    fun `a stamped sender with nothing for this field wins when the field was never edited here`() {
        val resolver = resolver(
            incomingStamps = mapOf("audioEnabled" to LATE),
            localStamps = emptyMap()
        )

        assertTrue(resolver.resolve(FIELD).apply)
    }

    @Test
    fun `a field the watch owns outright is never taken from the sender`() {
        // ADR-2: auto-rotation is WATCH_ONLY, so even a newer incoming stamp must not move it.
        val field = WearSettingsRegistry.watchOnlyFields.first()
        val resolver = resolver(
            incomingStamps = mapOf(field to LATE),
            localStamps = mapOf(field to EARLY)
        )

        assertFalse(resolver.resolve(field).apply)
    }

    private fun resolver(
        incomingStamps: Map<String, Long>?,
        localStamps: Map<String, Long>,
        skewMillis: Long = 0L
    ) = WearSettingsMergeResolver(
        incomingStamps = incomingStamps,
        localStamps = localStamps,
        skewMillis = skewMillis,
        rejectedFields = WearSettingsRegistry.watchOnlyFields
    )
}
