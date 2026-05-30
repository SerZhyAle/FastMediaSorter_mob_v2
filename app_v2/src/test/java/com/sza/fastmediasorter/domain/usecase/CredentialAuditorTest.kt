package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.data.local.db.NetworkCredentialsEntity
import com.sza.fastmediasorter.domain.model.CredentialStatus
import com.sza.fastmediasorter.domain.repository.NetworkCredentialsRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class CredentialAuditorTest {

    private val repository = mockk<NetworkCredentialsRepository>()
    private lateinit var auditor: CredentialAuditor

    private fun cred(id: String, createdDate: Long = 0L) = NetworkCredentialsEntity(
        credentialId = id,
        type = "SMB",
        server = "host",
        port = 445,
        username = "user",
        encryptedPassword = "enc",
        createdDate = createdDate,
    )

    @Before
    fun setup() {
        // Tiny grace period so an old createdDate flips eligibleForCleanup.
        val policy = UnusedCredentialPolicy().apply { gracePeriodMs = 1L }
        auditor = CredentialAuditor(repository, policy)
    }

    @Test
    fun `audit marks orphaned credentials and counts them`() = runTest {
        val active = cred("active")
        val orphan = cred("orphan", createdDate = 1L)
        every { repository.getAllCredentials() } returns flowOf(listOf(active, orphan))
        coEvery { repository.getOrphanedCredentials() } returns listOf(orphan)

        val report = auditor.audit()

        assertEquals(2, report.totalCount)
        assertEquals(1, report.orphanedCount)
        val byId = report.entries.associateBy { it.credentialId }
        assertEquals(CredentialStatus.ACTIVE, byId.getValue("active").status)
        assertEquals(CredentialStatus.ORPHANED, byId.getValue("orphan").status)
    }

    @Test
    fun `audit flags orphaned credential past grace period as eligible`() = runTest {
        val orphan = cred("orphan", createdDate = 1L) // far in the past vs now
        every { repository.getAllCredentials() } returns flowOf(listOf(orphan))
        coEvery { repository.getOrphanedCredentials() } returns listOf(orphan)

        val report = auditor.audit()

        assertEquals(1, report.eligibleForCleanupCount)
        assertEquals(true, report.entries.first().eligibleForCleanup)
    }

    @Test
    fun `audit builds human readable label from entity fields`() = runTest {
        val c = NetworkCredentialsEntity(
            credentialId = "id",
            type = "SFTP",
            server = "10.0.0.5",
            port = 22,
            username = "alice",
            encryptedPassword = "enc",
        )
        every { repository.getAllCredentials() } returns flowOf(listOf(c))
        coEvery { repository.getOrphanedCredentials() } returns emptyList()

        val report = auditor.audit()

        assertEquals("10.0.0.5:22 (alice)", report.entries.first().label)
    }

    @Test
    fun `auditAsFlow emits a report per credentials emission`() = runTest {
        val orphan = cred("orphan", createdDate = 1L)
        every { repository.getAllCredentials() } returns flowOf(listOf(orphan))
        coEvery { repository.getOrphanedCredentials() } returns listOf(orphan)

        val report = auditor.auditAsFlow().first()

        assertEquals(1, report.orphanedCount)
    }
}
