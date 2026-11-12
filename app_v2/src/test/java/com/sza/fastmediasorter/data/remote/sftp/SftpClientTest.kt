package com.sza.fastmediasorter.data.remote.sftp

import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.SftpException
import com.sza.fastmediasorter.core.network.NetworkReachabilityGate
import com.sza.fastmediasorter.core.network.NetworkStateMonitor
import com.sza.fastmediasorter.data.network.IdleDisconnectPolicy
import com.sza.fastmediasorter.data.network.exceptions.NetworkConnectionLostException
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SftpClientTest {

    @Test
    fun `an ordinary readable root never sends a local PIN`() = runTest {
        val registry = mockk<SftpRootProofRegistry>(relaxed = true)
        val client = spyk(testClient(mockk(relaxed = true), registry))
        val info = SftpClient.SftpConnectionInfo(host = "host", username = "user")
        coEvery { client.listFilesOnce(info, "/Music", true, false) } returns Result.success(emptyList())

        assertTrue(client.listFiles(info, "/Music").isSuccess)

        coVerify(exactly = 0) { registry.localCandidate(any(), any()) }
        coVerify(exactly = 0) { client.verifyRootProof(any(), any(), any()) }
    }

    @Test
    fun `missing ordinary root is opened after a successful PIN proof`() = runTest {
        val registry = mockk<SftpRootProofRegistry>(relaxed = true)
        val client = spyk(testClient(mockk(relaxed = true), registry))
        val info = SftpClient.SftpConnectionInfo(host = "host", username = "user")
        val proof = SftpRootProof("_iN", "1234")
        val missing = SftpException(ChannelSftp.SSH_FX_NO_SUCH_FILE, "missing")
        coEvery { client.listFilesOnce(info, "/_iN", true, false) } returnsMany
            listOf(Result.failure(missing), Result.success(emptyList()))
        coEvery { registry.localCandidate(info, "/_iN") } returns proof
        coEvery { client.verifyRootProof(info, "_iN", "1234") } returns Result.success(Unit)

        assertTrue(client.listFiles(info, "/_iN").isSuccess)

        coVerify(exactly = 2) { client.listFilesOnce(info, "/_iN", true, false) }
        coVerify(exactly = 1) { client.verifyRootProof(info, "_iN", "1234") }
        io.mockk.verify(exactly = 1) { registry.rememberLocal(info, proof) }
    }

    @Test
    fun `refused PIN never enables the protected root`() = runTest {
        val registry = mockk<SftpRootProofRegistry>(relaxed = true)
        val client = spyk(testClient(mockk(relaxed = true), registry))
        val info = SftpClient.SftpConnectionInfo(host = "host", username = "user")
        val proof = SftpRootProof("_iN", "1234")
        val missing = SftpException(ChannelSftp.SSH_FX_NO_SUCH_FILE, "missing")
        coEvery { client.listFilesOnce(info, "/_iN", true, false) } returns Result.failure(missing)
        coEvery { registry.localCandidate(info, "/_iN") } returns proof
        coEvery { client.verifyRootProof(info, "_iN", "1234") } returns Result.failure(missing)

        assertTrue(client.listFiles(info, "/_iN").isFailure)

        coVerify(exactly = 1) { client.listFilesOnce(info, "/_iN", true, false) }
        io.mockk.verify(exactly = 0) { registry.rememberLocal(any(), any()) }
    }

    @Test
    fun `verifying a PIN fails when the authenticated root is missing`() = runTest {
        val client = spyk(testClient(mockk(relaxed = true)))
        coEvery { client.exists(any(), "/Music") } returns Result.success(false)

        val info = SftpClient.SftpConnectionInfo(host = "host", username = "user")
        val result = client.verifyRootProof(info, "Music", "1234")

        assertTrue(result.isFailure)
    }

    @Test
    fun `verifying a PIN succeeds only when the authenticated root exists`() = runTest {
        val client = spyk(testClient(mockk(relaxed = true)))
        coEvery { client.exists(any(), "/Music") } returns Result.success(true)

        val info = SftpClient.SftpConnectionInfo(host = "host", username = "user")
        val result = client.verifyRootProof(info, "Music", "1234")

        assertFalse(result.isFailure)
    }

    @Test
    fun `getConnectionForExoPlayer throws synchronously when reachability gate denies`() {
        val gate = mockk<NetworkReachabilityGate>()
        every { gate.requireAnyNetwork("SFTP") } throws NetworkConnectionLostException()
        val client = testClient(gate)

        val info = SftpClient.SftpConnectionInfo(host = "anyhost", port = 22, username = "u", password = "p")

        val thrown = try {
            client.getConnectionForExoPlayer(info)
            null
        } catch (e: NetworkConnectionLostException) {
            e
        }

        assertNotNull(thrown)
    }

    private fun testClient(
        gate: NetworkReachabilityGate,
        proofRegistry: SftpRootProofRegistry = mockk(relaxed = true),
    ) = SftpClient(
        gate,
        dagger.Lazy { mockk(relaxed = true) },
        mockk<IdleDisconnectPolicy>(relaxed = true),
        mockk<NetworkStateMonitor>(relaxed = true),
        mockk<SftpHostKeyPinRegistry>(relaxed = true),
        com.sza.fastmediasorter.data.remote.sftp.anywhere.SftpRendezvousVerdicts(),
        proofRegistry,
        dagger.Lazy { mockk(relaxed = true) },
        dagger.Lazy { mockk(relaxed = true) },
    )
}
