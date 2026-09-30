package com.sza.fastmediasorter.domain.usecase

import android.content.Context
import com.sza.fastmediasorter.data.cloud.GoogleDriveRestClient
import com.sza.fastmediasorter.data.network.ConnectionThrottleManager
import com.sza.fastmediasorter.data.network.SmbClient
import com.sza.fastmediasorter.data.network.model.SmbResult
import com.sza.fastmediasorter.data.remote.ftp.FtpClient
import com.sza.fastmediasorter.data.remote.sftp.SftpClient
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.repository.NetworkCredentialsRepository
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import com.sza.fastmediasorter.testing.createMediaResource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * JVM coverage for [NetworkSpeedTestUseCase]: the local-disk speed test (real File under
 * [TemporaryFolder]), the SMB measurement flow with a mocked client, and error/result emission.
 * Recommended-thread/buffer derivation is exercised through the emitted [SpeedTestResult].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NetworkSpeedTestUseCaseTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val context = mockk<Context>(relaxed = true)
    private val smbClient = mockk<SmbClient>()
    private val sftpClient = mockk<SftpClient>(relaxed = true)
    private val ftpClient = mockk<FtpClient>(relaxed = true)
    private val driveClient = mockk<GoogleDriveRestClient>(relaxed = true)
    private val credentialsRepository = mockk<NetworkCredentialsRepository>(relaxed = true)
    private val resourceRepository = mockk<ResourceRepository>(relaxed = true)
    private val smbOps = mockk<SmbOperationsUseCase>()
    private lateinit var useCase: NetworkSpeedTestUseCase

    @Before
    fun setup() {
        useCase = NetworkSpeedTestUseCase(
            context, smbClient, sftpClient, ftpClient, driveClient,
            credentialsRepository, resourceRepository, smbOps, UnconfinedTestDispatcher(),
        )
    }

    @Test
    fun `local speed test measures disk and persists result`() = runTest {
        val dir = tempFolder.newFolder("media")
        val resource = createMediaResource(type = ResourceType.LOCAL, path = dir.absolutePath)

        val events = useCase.runSpeedTest(resource).toList()

        assertEquals(2, events.count { it is NetworkSpeedTestUseCase.SpeedTestStatus.Progress })
        assertTrue(events.last() is NetworkSpeedTestUseCase.SpeedTestStatus.Complete)
        coVerify { resourceRepository.updateSpeedTestResult(resource.id, any(), any(), any(), any()) }
        coVerify(exactly = 0) { resourceRepository.updateResource(any()) }
    }

    @Test
    fun `local speed test on missing directory emits error`() = runTest {
        val resource = createMediaResource(type = ResourceType.LOCAL, path = "/no/such/dir")

        val events = useCase.runSpeedTest(resource).toList()

        assertTrue(events.any { it is NetworkSpeedTestUseCase.SpeedTestStatus.Error })
    }

    @Test
    fun `smb speed test completes via mocked client`() = runTest {
        val connInfo = com.sza.fastmediasorter.data.network.model.SmbConnectionInfo(
            server = "host",
            shareName = "share",
            username = "u",
            password = "p",
        )
        coEvery { smbOps.getConnectionInfo("c1") } returns Result.success(connInfo)
        coEvery { smbClient.uploadFile(any(), any(), any(), any(), any()) } returns SmbResult.Success(Unit)
        coEvery { smbClient.downloadFile(any(), any(), any(), any(), any()) } returns SmbResult.Success(Unit)
        coEvery { smbClient.deleteFile(any(), any()) } returns SmbResult.Success(Unit)
        val resource = createMediaResource(
            type = ResourceType.SMB,
            path = "smb://host/share",
            credentialsId = "c1",
        )

        val events = useCase.runSpeedTest(resource).toList()

        assertEquals(2, events.count { it is NetworkSpeedTestUseCase.SpeedTestStatus.Progress })
        assertTrue(events.last() is NetworkSpeedTestUseCase.SpeedTestStatus.Complete)
        coVerify { smbClient.uploadFile(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `smb speed test publishes the measurement under the host-port throttle key`() = runTest {
        stubSmb(downloadResult = SmbResult.Success(Unit))
        val resource = createMediaResource(
            type = ResourceType.SMB,
            path = "smb://keyhost/share",
            credentialsId = "c1",
        )

        useCase.runSpeedTest(resource).toList()

        assertNotNull(ConnectionThrottleManager.getLastSpeedMbps("smb://keyhost:445"))
    }

    @Test
    fun `smb speed test removes the test file when the read measurement fails`() = runTest {
        stubSmb(downloadResult = SmbResult.Error("read failed"))
        val resource = createMediaResource(
            type = ResourceType.SMB,
            path = "smb://keyhost/share",
            credentialsId = "c1",
        )

        val events = useCase.runSpeedTest(resource).toList()

        assertTrue(events.last() is NetworkSpeedTestUseCase.SpeedTestStatus.Error)
        coVerify { smbClient.deleteFile(any(), any()) }
    }

    private fun stubSmb(downloadResult: SmbResult<Unit>) {
        val connInfo = com.sza.fastmediasorter.data.network.model.SmbConnectionInfo(
            server = "keyhost",
            shareName = "share",
            username = "u",
            password = "p",
        )
        coEvery { smbOps.getConnectionInfo("c1") } returns Result.success(connInfo)
        coEvery { smbClient.uploadFile(any(), any(), any(), any(), any()) } returns SmbResult.Success(Unit)
        coEvery { smbClient.downloadFile(any(), any(), any(), any(), any()) } returns downloadResult
        coEvery { smbClient.deleteFile(any(), any()) } returns SmbResult.Success(Unit)
    }

    @Test
    fun `smb speed test without credentials emits error`() = runTest {
        val resource = createMediaResource(
            type = ResourceType.SMB,
            path = "smb://host/share",
            credentialsId = null,
        )

        val events = useCase.runSpeedTest(resource).toList()

        assertTrue(events.any { it is NetworkSpeedTestUseCase.SpeedTestStatus.Error })
    }
}
