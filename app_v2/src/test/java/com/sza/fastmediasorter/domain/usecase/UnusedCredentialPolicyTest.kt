package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.domain.model.CredentialAuditEntry
import com.sza.fastmediasorter.domain.model.CredentialStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class UnusedCredentialPolicyTest {

    private val now = 100_000_000_000L

    // S1649: the policy measures the grace period from orphanedSince, so that is what a case must
    // set. createdAt is kept only to prove it no longer participates in the decision.
    private fun entry(
        status: CredentialStatus,
        orphanedSince: Long?,
        id: String = "id",
        createdAt: Long = 1L
    ) = CredentialAuditEntry(
        credentialId = id,
        credentialType = "SMB",
        label = "host:445 (user)",
        status = status,
        createdAt = createdAt,
        orphanedSince = orphanedSince
    )

    @Test
    fun `active credential is never eligible`() {
        val policy = UnusedCredentialPolicy()
        val old = now - UnusedCredentialPolicy.DEFAULT_GRACE_PERIOD_MS - 1
        assertFalse(policy.isEligibleForCleanup(entry(CredentialStatus.ACTIVE, old), now))
    }

    @Test
    fun `orphaned credential within grace period is not eligible`() {
        val policy = UnusedCredentialPolicy()
        val recent = now - 1L
        assertFalse(policy.isEligibleForCleanup(entry(CredentialStatus.ORPHANED, recent), now))
    }

    @Test
    fun `orphaned credential past grace period is eligible`() {
        val policy = UnusedCredentialPolicy()
        val old = now - UnusedCredentialPolicy.DEFAULT_GRACE_PERIOD_MS
        assertTrue(policy.isEligibleForCleanup(entry(CredentialStatus.ORPHANED, old), now))
    }

    @Test
    fun `non-positive orphanedSince is never eligible`() {
        val policy = UnusedCredentialPolicy()
        assertFalse(policy.isEligibleForCleanup(entry(CredentialStatus.ORPHANED, 0L), now))
    }

    // Every row carries null until the background worker stamps it, which is also the state right
    // after the schema 51 migration - a missing clock must not read as an expired one.
    @Test
    fun `unknown orphan moment is never eligible`() {
        val policy = UnusedCredentialPolicy()
        assertFalse(policy.isEligibleForCleanup(entry(CredentialStatus.ORPHANED, null), now))
    }

    // The credential was stored long before the grace period, but only just became orphaned: the
    // guarantee S1649 restored is that storage age grants no head start.
    @Test
    fun `long-stored credential orphaned just now is not eligible`() {
        val policy = UnusedCredentialPolicy()
        val entry = entry(
            status = CredentialStatus.ORPHANED,
            orphanedSince = now - 1L,
            createdAt = now - UnusedCredentialPolicy.DEFAULT_GRACE_PERIOD_MS * 12
        )
        assertFalse(policy.isEligibleForCleanup(entry, now))
    }

    @Test
    fun `custom grace period is honoured`() {
        val policy = UnusedCredentialPolicy().apply { gracePeriodMs = 1_000L }
        assertTrue(
            policy.isEligibleForCleanup(entry(CredentialStatus.ORPHANED, now - 1_000L), now)
        )
        assertFalse(
            policy.isEligibleForCleanup(entry(CredentialStatus.ORPHANED, now - 999L), now)
        )
    }

    @Test
    fun `non-positive grace period is rejected`() {
        val policy = UnusedCredentialPolicy()
        assertThrows(IllegalArgumentException::class.java) { policy.gracePeriodMs = 0L }
        assertThrows(IllegalArgumentException::class.java) { policy.gracePeriodMs = -5L }
    }

    @Test
    fun `filterEligible keeps only eligible entries`() {
        val policy = UnusedCredentialPolicy().apply { gracePeriodMs = 1_000L }
        val eligible = entry(CredentialStatus.ORPHANED, now - 2_000L, "a")
        val active = entry(CredentialStatus.ACTIVE, now - 2_000L, "b")
        val recent = entry(CredentialStatus.ORPHANED, now - 100L, "c")

        val result = policy.filterEligible(listOf(eligible, active, recent), now)

        assertEquals(listOf("a"), result.map { it.credentialId })
    }
}
