package com.sza.fastmediasorter.data.verifier

import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.testing.createMediaFile
import com.sza.fastmediasorter.testing.createMediaResource
import com.sza.fastmediasorter.testing.fakes.FakeResourceRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [QuickVerifierDispatcher]: empty/zero-n guards, unknown-resource skip, per-type
 * strategy selection, FTP intentional no-op, and first-N truncation before delegating.
 * Strategies are mocked; resource lookup uses [FakeResourceRepository].
 */
class QuickVerifierDispatcherTest {

    private val local = mockk<LocalQuickVerifier>()
    private val smb = mockk<SmbQuickVerifier>()
    private val sftp = mockk<SftpQuickVerifier>()
    private val cloud = mockk<CloudQuickVerifier>()
    private val repo = FakeResourceRepository()

    private val dispatcher = QuickVerifierDispatcher(local, smb, sftp, cloud, repo)

    @Test
    fun `returns empty when candidates empty`() = runBlocking {
        assertTrue(dispatcher.probe(1L, emptyList()).isEmpty())
    }

    @Test
    fun `returns empty when n is zero`() = runBlocking {
        assertTrue(dispatcher.probe(1L, listOf(createMediaFile()), n = 0).isEmpty())
    }

    @Test
    fun `returns empty when resource unknown`() = runBlocking {
        // repo has no resources
        assertTrue(dispatcher.probe(99L, listOf(createMediaFile())).isEmpty())
    }

    @Test
    fun `selects local verifier for LOCAL resource`() = runBlocking {
        repo.setResources(listOf(createMediaResource(id = 1L, type = ResourceType.LOCAL)))
        coEvery { local.missingFiles(1L, any()) } returns listOf("/missing")

        val result = dispatcher.probe(1L, listOf(createMediaFile(path = "/a")))

        assertEquals(listOf("/missing"), result)
        coVerify { local.missingFiles(1L, listOf("/a")) }
    }

    @Test
    fun `selects smb verifier for SMB resource`() = runBlocking {
        repo.setResources(listOf(createMediaResource(id = 2L, type = ResourceType.SMB)))
        coEvery { smb.missingFiles(2L, any()) } returns emptyList()

        dispatcher.probe(2L, listOf(createMediaFile(path = "smb://s/share/f")))
        coVerify { smb.missingFiles(2L, listOf("smb://s/share/f")) }
    }

    @Test
    fun `selects cloud verifier for CLOUD resource`() = runBlocking {
        repo.setResources(listOf(createMediaResource(id = 3L, type = ResourceType.CLOUD)))
        coEvery { cloud.missingFiles(3L, any()) } returns emptyList()

        dispatcher.probe(3L, listOf(createMediaFile(path = "cloud://gdrive/x/y")))
        coVerify { cloud.missingFiles(3L, any()) }
    }

    @Test
    fun `FTP resource is a no-op`() = runBlocking {
        repo.setResources(listOf(createMediaResource(id = 4L, type = ResourceType.FTP)))
        assertTrue(dispatcher.probe(4L, listOf(createMediaFile(path = "ftp://h/f"))).isEmpty())
    }

    @Test
    fun `truncates candidates to first n before delegating`() = runBlocking {
        repo.setResources(listOf(createMediaResource(id = 1L, type = ResourceType.LOCAL)))
        coEvery { local.missingFiles(1L, any()) } returns emptyList()

        val candidates = (1..20).map { createMediaFile(path = "/p$it") }
        dispatcher.probe(1L, candidates, n = 5)

        coVerify { local.missingFiles(1L, listOf("/p1", "/p2", "/p3", "/p4", "/p5")) }
    }
}
