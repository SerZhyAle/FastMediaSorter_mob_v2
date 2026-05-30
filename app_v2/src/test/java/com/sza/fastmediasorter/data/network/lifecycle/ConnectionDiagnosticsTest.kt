package com.sza.fastmediasorter.data.network.lifecycle

import com.sza.fastmediasorter.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Unit tests for [ConnectionDiagnostics] instability-warning emission. */
@OptIn(ExperimentalCoroutinesApi::class)
class ConnectionDiagnosticsTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `emits InstabilityWarning once threshold of three recreates is reached`() = runTest {
        val diagnostics = ConnectionDiagnostics()
        val collected = mutableListOf<ConnectionDiagnostics.InstabilityWarning>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            diagnostics.events.toList(collected)
        }

        diagnostics.recordRecreate(NetworkProtocol.SMB, "smb://nas:445", TransientFailure.Reason.TRANSPORT)
        diagnostics.recordRecreate(NetworkProtocol.SMB, "smb://nas:445", TransientFailure.Reason.TRANSPORT)
        // Third recreate crosses INSTABILITY_THRESHOLD = 3.
        diagnostics.recordRecreate(NetworkProtocol.SMB, "smb://nas:445", TransientFailure.Reason.TRANSPORT)

        assertEquals(1, collected.size)
        assertEquals(NetworkProtocol.SMB, collected[0].protocol)
        assertEquals("smb://nas:445", collected[0].resourceKey)
        assertEquals(3, collected[0].recreateCount)
        job.cancel()
    }

    @Test
    fun `does not emit before threshold`() = runTest {
        val diagnostics = ConnectionDiagnostics()
        val collected = mutableListOf<ConnectionDiagnostics.InstabilityWarning>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            diagnostics.events.toList(collected)
        }

        diagnostics.recordRecreate(NetworkProtocol.FTP, "ftp://host:21", TransientFailure.Reason.TIMEOUT)
        diagnostics.recordRecreate(NetworkProtocol.FTP, "ftp://host:21", TransientFailure.Reason.TIMEOUT)

        assertTrue(collected.isEmpty())
        job.cancel()
    }

    @Test
    fun `recreates on distinct keys are counted independently`() = runTest {
        val diagnostics = ConnectionDiagnostics()
        val collected = mutableListOf<ConnectionDiagnostics.InstabilityWarning>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            diagnostics.events.toList(collected)
        }

        // Two on key A, one on key B - neither key reaches the threshold of 3.
        diagnostics.recordRecreate(NetworkProtocol.SMB, "smb://a:445", TransientFailure.Reason.TRANSPORT)
        diagnostics.recordRecreate(NetworkProtocol.SMB, "smb://a:445", TransientFailure.Reason.TRANSPORT)
        diagnostics.recordRecreate(NetworkProtocol.SMB, "smb://b:445", TransientFailure.Reason.TRANSPORT)

        assertTrue(collected.isEmpty())
        job.cancel()
    }

    @Test
    fun `recordSuccess and recordFailure do not emit warnings`() = runTest {
        val diagnostics = ConnectionDiagnostics()
        val collected = mutableListOf<ConnectionDiagnostics.InstabilityWarning>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            diagnostics.events.toList(collected)
        }

        diagnostics.recordSuccess(NetworkProtocol.SFTP, "sftp://host:22")
        diagnostics.recordFailure(NetworkProtocol.SFTP, "sftp://host:22", TransientFailure.Reason.TIMEOUT)
        diagnostics.recordFailure(NetworkProtocol.SFTP, "sftp://host:22", null)

        assertTrue(collected.isEmpty())
        job.cancel()
    }
}
