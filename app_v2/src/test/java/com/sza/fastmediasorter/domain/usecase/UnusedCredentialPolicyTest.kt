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

    private fun entry(
        status: CredentialStatus,
        createdAt: Long,
        id: String = "id"
    ) = CredentialAuditEntry(
        credentialId = id,
        credentialType = "SMB",
        label = "host:445 (user)",
        status = status,
        createdAt = createdAt
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
    fun `non-positive createdAt is never eligible`() {
        val policy = UnusedCredentialPolicy()
        assertFalse(policy.isEligibleForCleanup(entry(CredentialStatus.ORPHANED, 0L), now))
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
