package com.sza.fastmediasorter.data.network.lifecycle

import com.sza.fastmediasorter.data.network.SmbConnectionManager
import com.sza.fastmediasorter.data.remote.sftp.SftpClient
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Unit tests for the lifecycle [NetworkConnectionGate] adapters
 * ([SmbConnectionGate], [SftpConnectionGate], [FtpConnectionGate], [CloudConnectionGate]).
 *
 * Each gate's lease path (acquire/withRetry) is a documented UnsupportedOperationException stub;
 * only closeFor (UI vs worker split) and lastRecreateMs (tracker delegation) carry behaviour.
 */
class ConnectionGatesTest {

    // ── SmbConnectionGate ────────────────────────────────────────────────────

    @Test
    fun `smb gate exposes SMB protocol`() {
        val gate = SmbConnectionGate(mockk(), SmbRecreateTracker(), ConnectionDiagnostics())
        assertEquals(NetworkProtocol.SMB, gate.protocol)
    }

    @Test
    fun `smb gate acquire throws unsupported`() {
        val gate = SmbConnectionGate(mockk(), SmbRecreateTracker(), ConnectionDiagnostics())
        assertThrows(UnsupportedOperationException::class.java) {
            runBlocking { gate.acquire(ConsumerType.UI_PLAYER, "smb://nas:445") }
        }
    }

    @Test
    fun `smb gate closeFor UI delegates to manager closeUiConnections`() {
        val manager = mockk<SmbConnectionManager>(relaxed = true)
        val gate = SmbConnectionGate(manager, SmbRecreateTracker(), ConnectionDiagnostics())
        gate.closeFor(ConsumerType.UI_SCANNER)
        verify(exactly = 1) { manager.closeUiConnections() }
    }

    @Test
    fun `smb gate closeFor worker does not close UI connections`() {
        val manager = mockk<SmbConnectionManager>(relaxed = true)
        val gate = SmbConnectionGate(manager, SmbRecreateTracker(), ConnectionDiagnostics())
        gate.closeFor(ConsumerType.BACKGROUND_WORKER)
        verify(exactly = 0) { manager.closeUiConnections() }
    }

    @Test
    fun `smb gate lastRecreateMs delegates to tracker`() {
        val tracker = SmbRecreateTracker()
        val gate = SmbConnectionGate(mockk(), tracker, ConnectionDiagnostics())
        assertNull(gate.lastRecreateMs("smb://nas:445"))
        tracker.recordRecreate("smb://nas:445")
        assertEquals(tracker.lastRecreateMs("smb://nas:445"), gate.lastRecreateMs("smb://nas:445"))
    }

    // ── SftpConnectionGate ───────────────────────────────────────────────────

    @Test
    fun `sftp gate closeFor UI disconnects pool`() {
        val client = mockk<SftpClient>(relaxed = true)
        coEvery { client.disconnectAllPool() } returns Unit
        val gate = SftpConnectionGate(client, SftpRecreateTracker(), CoroutineScope(Dispatchers.Unconfined), ConnectionDiagnostics())
        gate.closeFor(ConsumerType.UI_PLAYER)
        coVerify(exactly = 1) { client.disconnectAllPool() }
    }

    @Test
    fun `sftp gate closeFor worker is a no-op`() {
        val client = mockk<SftpClient>(relaxed = true)
        val gate = SftpConnectionGate(client, SftpRecreateTracker(), CoroutineScope(Dispatchers.Unconfined), ConnectionDiagnostics())
        gate.closeFor(ConsumerType.BACKGROUND_WORKER)
        coVerify(exactly = 0) { client.disconnectAllPool() }
    }

    @Test
    fun `sftp gate swallows disconnect failure`() {
        val client = mockk<SftpClient>()
        coEvery { client.disconnectAllPool() } throws RuntimeException("boom")
        val gate = SftpConnectionGate(client, SftpRecreateTracker(), CoroutineScope(Dispatchers.Unconfined), ConnectionDiagnostics())
        // closeFor must not propagate the exception.
        gate.closeFor(ConsumerType.UI_OPERATION)
    }

    // ── FtpConnectionGate ────────────────────────────────────────────────────

    @Test
    fun `ftp gate closeFor is a no-op for any consumer`() {
        // S0904: the ExoPlayer FTP pool was never populated (connections are created per-acquire and
        // disconnected on release), so closeFor no longer sweeps an idle pool - it must simply not throw.
        val gate = FtpConnectionGate(FtpRecreateTracker(), ConnectionDiagnostics())
        gate.closeFor(ConsumerType.UI_SCANNER)
        gate.closeFor(ConsumerType.BACKGROUND_WORKER)
        assertEquals(NetworkProtocol.FTP, gate.protocol)
    }

    // ── CloudConnectionGate ──────────────────────────────────────────────────

    @Test
    fun `cloud gate closeFor is always a no-op for sockets`() {
        val tracker = CloudRecreateTracker()
        val gate = CloudConnectionGate(tracker, ConnectionDiagnostics())
        // No interaction to assert - simply must not throw for either consumer type.
        gate.closeFor(ConsumerType.UI_PLAYER)
        gate.closeFor(ConsumerType.BACKGROUND_WORKER)
        assertEquals(NetworkProtocol.CLOUD, gate.protocol)
    }

    @Test
    fun `cloud gate acquire throws unsupported`() {
        val gate = CloudConnectionGate(CloudRecreateTracker(), ConnectionDiagnostics())
        assertThrows(UnsupportedOperationException::class.java) {
            runBlocking { gate.acquire(ConsumerType.UI_PLAYER, "cloud://gdrive") }
        }
    }

    @Test
    fun `cloud gate lastRecreateMs delegates to tracker`() {
        val tracker = CloudRecreateTracker()
        val gate = CloudConnectionGate(tracker, ConnectionDiagnostics())
        assertNull(gate.lastRecreateMs("cloud://gdrive"))
        tracker.recordRecreate("cloud://gdrive")
        assertEquals(tracker.lastRecreateMs("cloud://gdrive"), gate.lastRecreateMs("cloud://gdrive"))
    }
}
