package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.data.hash.CloudFileHasher
import com.sza.fastmediasorter.data.hash.FtpFileHasher
import com.sza.fastmediasorter.data.hash.LocalFileHasher
import com.sza.fastmediasorter.data.hash.SftpFileHasher
import com.sza.fastmediasorter.data.hash.SmbFileHasher
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.repository.DuplicateHashRepository
import com.sza.fastmediasorter.testing.createMediaFile
import com.sza.fastmediasorter.testing.createMediaResource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class ComputeFileHashUseCaseTest {

    private val local = mockk<LocalFileHasher>()
    private val smb = mockk<SmbFileHasher>()
    private val sftp = mockk<SftpFileHasher>()
    private val ftp = mockk<FtpFileHasher>()
    private val cloud = mockk<CloudFileHasher>()
    private val repo = mockk<DuplicateHashRepository>(relaxUnitFun = true)

    private lateinit var useCase: ComputeFileHashUseCase

    @Before
    fun setup() {
        useCase = ComputeFileHashUseCase(local, smb, sftp, ftp, cloud, repo)
    }

    private val file = createMediaFile(path = "/a/b.jpg")

    @Test
    fun `full-hash cache hit returns cached value without computing`() = runTest {
        coEvery { repo.getCachedFullHash(file) } returns "CACHED"

        val result = useCase.compute(file, createMediaResource(type = ResourceType.LOCAL), maxBytes = -1L)

        assertEquals("CACHED", result)
        coVerify(exactly = 0) { local.computeHash(any(), any(), any()) }
        coVerify(exactly = 0) { repo.saveFullHash(any(), any()) }
    }

    @Test
    fun `quick-hash cache hit reads quick cache not full cache`() = runTest {
        coEvery { repo.getCachedQuickHash(file) } returns "QUICK"

        val result = useCase.compute(file, createMediaResource(type = ResourceType.LOCAL), maxBytes = 4096L)

        assertEquals("QUICK", result)
        coVerify(exactly = 0) { repo.getCachedFullHash(any()) }
    }

    @Test
    fun `full hash miss computes via local hasher and persists to full cache`() = runTest {
        coEvery { repo.getCachedFullHash(file) } returns null
        coEvery { local.computeHash(file, any(), -1L) } returns "FULLHASH"

        val result = useCase.compute(file, createMediaResource(type = ResourceType.LOCAL), maxBytes = -1L)

        assertEquals("FULLHASH", result)
        coVerify { repo.saveFullHash(file, "FULLHASH") }
        coVerify(exactly = 0) { repo.saveQuickHash(any(), any()) }
    }

    @Test
    fun `quick hash miss computes and persists to quick cache`() = runTest {
        coEvery { repo.getCachedQuickHash(file) } returns null
        coEvery { local.computeHash(file, any(), 4096L) } returns "QH"

        val result = useCase.compute(file, createMediaResource(type = ResourceType.LOCAL), maxBytes = 4096L)

        assertEquals("QH", result)
        coVerify { repo.saveQuickHash(file, "QH") }
        coVerify(exactly = 0) { repo.saveFullHash(any(), any()) }
    }

    @Test
    fun `dispatch picks SMB hasher for SMB resource`() = runTest {
        coEvery { repo.getCachedFullHash(file) } returns null
        coEvery { smb.computeHash(file, any(), -1L) } returns "SMB"

        val result = useCase.compute(file, createMediaResource(type = ResourceType.SMB), maxBytes = -1L)

        assertEquals("SMB", result)
        coVerify { smb.computeHash(file, any(), -1L) }
    }

    @Test
    fun `dispatch picks SFTP FTP and CLOUD hashers`() = runTest {
        coEvery { repo.getCachedFullHash(file) } returns null
        coEvery { sftp.computeHash(file, any(), -1L) } returns "SFTP"
        coEvery { ftp.computeHash(file, any(), -1L) } returns "FTP"
        coEvery { cloud.computeHash(file, any(), -1L) } returns "CLOUD"

        assertEquals("SFTP", useCase.compute(file, createMediaResource(type = ResourceType.SFTP), -1L))
        assertEquals("FTP", useCase.compute(file, createMediaResource(type = ResourceType.FTP), -1L))
        assertEquals("CLOUD", useCase.compute(file, createMediaResource(type = ResourceType.CLOUD), -1L))
    }

    @Test
    fun `hasher exception is swallowed and returns null`() = runTest {
        coEvery { repo.getCachedFullHash(file) } returns null
        coEvery { local.computeHash(file, any(), -1L) } throws java.io.IOException("boom")

        val result = useCase.compute(file, createMediaResource(type = ResourceType.LOCAL), maxBytes = -1L)

        assertNull(result)
        coVerify(exactly = 0) { repo.saveFullHash(any(), any()) }
    }
}
