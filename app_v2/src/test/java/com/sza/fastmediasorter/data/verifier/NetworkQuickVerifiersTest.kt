package com.sza.fastmediasorter.data.verifier

import com.sza.fastmediasorter.data.transfer.strategy.CloudOperationStrategy
import com.sza.fastmediasorter.data.transfer.strategy.SftpOperationStrategy
import com.sza.fastmediasorter.data.transfer.strategy.SmbOperationStrategy
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the network [com.sza.fastmediasorter.domain.verifier.QuickVerifier]s
 * (SMB/SFTP/Cloud). Shared policy: empty-paths guard returns empty; only a confirmed
 * Result.success(false) marks a path missing; Result.success(true), Result.failure, and
 * thrown exceptions all leave the path out of the missing set. Strategies are mocked - the
 * real ConnectionThrottleManager runs each block in-process with no sockets.
 */
class NetworkQuickVerifiersTest {

    // region SMB

    @Test
    fun `smb empty input yields empty`() = runBlocking {
        val verifier = SmbQuickVerifier(mockk())
        assertTrue(verifier.missingFiles(1L, emptyList()).isEmpty())
    }

    @Test
    fun `smb reports only confirmed-missing paths`() = runBlocking {
        val strategy = mockk<SmbOperationStrategy>()
        coEvery { strategy.exists("smb://s/share/present") } returns Result.success(true)
        coEvery { strategy.exists("smb://s/share/gone") } returns Result.success(false)
        coEvery { strategy.exists("smb://s/share/errored") } returns Result.failure(RuntimeException("x"))

        val verifier = SmbQuickVerifier(strategy)
        val missing = verifier.missingFiles(
            1L,
            listOf("smb://s/share/present", "smb://s/share/gone", "smb://s/share/errored")
        )
        assertEquals(listOf("smb://s/share/gone"), missing)
    }

    @Test
    fun `smb thrown exception leaves path present`() = runBlocking {
        val strategy = mockk<SmbOperationStrategy>()
        coEvery { strategy.exists(any()) } throws RuntimeException("boom")
        val verifier = SmbQuickVerifier(strategy)
        assertTrue(verifier.missingFiles(1L, listOf("smb://s/share/a")).isEmpty())
    }

    // endregion

    // region SFTP

    @Test
    fun `sftp empty input yields empty`() = runBlocking {
        val verifier = SftpQuickVerifier(mockk())
        assertTrue(verifier.missingFiles(1L, emptyList()).isEmpty())
    }

    @Test
    fun `sftp reports only confirmed-missing paths`() = runBlocking {
        val strategy = mockk<SftpOperationStrategy>()
        coEvery { strategy.exists("sftp://h:22/present") } returns Result.success(true)
        coEvery { strategy.exists("sftp://h:22/gone") } returns Result.success(false)

        val verifier = SftpQuickVerifier(strategy)
        val missing = verifier.missingFiles(1L, listOf("sftp://h:22/present", "sftp://h:22/gone"))
        assertEquals(listOf("sftp://h:22/gone"), missing)
    }

    // endregion

    // region Cloud

    @Test
    fun `cloud empty input yields empty`() = runBlocking {
        val verifier = CloudQuickVerifier(mockk())
        assertTrue(verifier.missingFiles(1L, emptyList()).isEmpty())
    }

    @Test
    fun `cloud reports only confirmed-missing paths`() = runBlocking {
        val strategy = mockk<CloudOperationStrategy>()
        coEvery { strategy.exists("cloud://gdrive/f/present") } returns Result.success(true)
        coEvery { strategy.exists("cloud://gdrive/f/gone") } returns Result.success(false)
        coEvery { strategy.exists("cloud://gdrive/f/failed") } returns Result.failure(IllegalStateException())

        val verifier = CloudQuickVerifier(strategy)
        val missing = verifier.missingFiles(
            1L,
            listOf("cloud://gdrive/f/present", "cloud://gdrive/f/gone", "cloud://gdrive/f/failed")
        )
        assertEquals(listOf("cloud://gdrive/f/gone"), missing)
    }

    @Test
    fun `cloud falls back to resource key when path not parseable`() = runBlocking {
        val strategy = mockk<CloudOperationStrategy>()
        coEvery { strategy.exists(any()) } returns Result.success(false)
        // non-cloud path triggers the verify://cloud/<id> fallback key path
        val verifier = CloudQuickVerifier(strategy)
        val missing = verifier.missingFiles(7L, listOf("/local/path"))
        assertEquals(listOf("/local/path"), missing)
    }

    // endregion
}
