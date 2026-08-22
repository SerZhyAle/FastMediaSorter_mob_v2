package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.data.local.db.NetworkCredentialsEntity
import com.sza.fastmediasorter.domain.repository.FakeNetworkCredentialsRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S1649: the deferral has no screen that reveals it, so these tests are the only place the rule
 * "orphaned less than the grace period ago is not offered for deletion" is actually held.
 *
 * The null case is covered deliberately: after the schema 51 migration every pre-existing row carries
 * no orphan moment until the background worker stamps it, so that path runs on every device that
 * updates, and treating it as expired would delete stored passwords on the spot.
 */
class DeleteUnusedCredentialsUseCaseTest {

    private fun credential(id: String, orphanedSince: Long?): NetworkCredentialsEntity =
        NetworkCredentialsEntity(
            credentialId = id,
            type = "SMB",
            server = "192.168.1.10",
            port = 445,
            username = "user",
            encryptedPassword = "enc",
            createdDate = ANCIENT,
            orphanedSince = orphanedSince
        )

    private fun useCase(repository: FakeNetworkCredentialsRepository): DeleteUnusedCredentialsUseCase {
        val policy = UnusedCredentialPolicy()
        return DeleteUnusedCredentialsUseCase(CredentialAuditor(repository, policy), repository)
    }

    @Test
    fun `an entry orphaned inside the grace period is not deleted`() = runTest {
        val repository = FakeNetworkCredentialsRepository(listOf(credential("fresh", NOW - ONE_DAY)), setOf("fresh"))

        val deleted = useCase(repository).deleteAllEligible()

        assertEquals(0, deleted)
        assertTrue("a credential inside its grace period must survive", repository.deleted.isEmpty())
    }

    @Test
    fun `an entry orphaned past the grace period is deleted`() = runTest {
        val repository = FakeNetworkCredentialsRepository(listOf(credential("stale", NOW - SIXTY_DAYS)), setOf("stale"))

        val deleted = useCase(repository).deleteAllEligible()

        assertEquals(1, deleted)
        assertEquals(listOf("stale"), repository.deleted)
    }

    @Test
    fun `an entry with no orphan moment is never deleted`() = runTest {
        val repository = FakeNetworkCredentialsRepository(listOf(credential("unstamped", null)), setOf("unstamped"))

        val deleted = useCase(repository).deleteAllEligible()

        assertEquals(0, deleted)
        assertTrue("a row awaiting its first stamp must survive", repository.deleted.isEmpty())
    }

    @Test
    fun `an entry still referenced by a resource is never deleted`() = runTest {
        val repository = FakeNetworkCredentialsRepository(listOf(credential("active", NOW - SIXTY_DAYS)), emptySet())

        val deleted = useCase(repository).deleteAllEligible()

        assertEquals(0, deleted)
        assertTrue("a referenced credential must survive regardless of its clock", repository.deleted.isEmpty())
    }

    @Test
    fun `an empty selection deletes nothing`() = runTest {
        val repository = FakeNetworkCredentialsRepository(listOf(credential("stale", NOW - SIXTY_DAYS)), setOf("stale"))

        val deleted = useCase(repository).delete(emptyList())

        assertEquals(0, deleted)
        assertTrue(repository.deleted.isEmpty())
    }

    @Test
    fun `a selected id the audit does not mark is refused`() = runTest {
        val repository = FakeNetworkCredentialsRepository(
            listOf(credential("stale", NOW - SIXTY_DAYS), credential("fresh", NOW - ONE_DAY)),
            setOf("stale", "fresh")
        )

        val deleted = useCase(repository).delete(listOf("stale", "fresh"))

        assertEquals("only the eligible id may be deleted", 1, deleted)
        assertEquals(listOf("stale"), repository.deleted)
    }

    private companion object {
        const val ONE_DAY = 24L * 60 * 60 * 1000
        const val SIXTY_DAYS = 60L * 24 * 60 * 60 * 1000
        val NOW: Long = System.currentTimeMillis()
        const val ANCIENT = 1_000L
    }
}
