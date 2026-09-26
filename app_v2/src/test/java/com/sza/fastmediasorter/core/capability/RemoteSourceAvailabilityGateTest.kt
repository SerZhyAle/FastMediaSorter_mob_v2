package com.sza.fastmediasorter.core.capability

import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The gate is built at injection time and its collector runs later on the application scope. An
 * unstarted [TestScope] holds that collector back, which reproduces the cold-start window where a
 * worker queries before the first settings emission.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RemoteSourceAvailabilityGateTest {

    private val capabilities = mockk<MediaCapabilities> {
        every { supportsCloud } returns true
        every { supportsLocalNetworkSources } returns true
    }
    private val settings = MutableStateFlow(AppSettings(smbEnabled = false, dropboxEnabled = false))
    private val repository = mockk<SettingsRepository> {
        every { getSettings() } returns settings
    }
    private val appScope = TestScope(StandardTestDispatcher())

    private fun gate() = RemoteSourceAvailabilityGate(capabilities, repository, appScope)

    @Test
    fun `query before the first emission reads the stored toggles`() {
        val gate = gate()

        assertFalse(gate.isEnabled(RemoteSourceId.SMB))
        assertFalse(gate.isEnabled(RemoteSourceId.DROPBOX))
        assertTrue(gate.isEnabled(RemoteSourceId.SFTP))
        assertTrue(gate.isEnabled(RemoteSourceId.GOOGLE_DRIVE))
    }

    @Test
    fun `network group follows the stored toggles before the first emission`() {
        settings.value = AppSettings(smbEnabled = false, sftpEnabled = false, ftpEnabled = false)

        assertFalse(gate().anyNetworkEnabled())
    }

    @Test
    fun `enabled set flow never emits a placeholder`() = runTest {
        val gate = gate()

        assertNull(withTimeoutOrNull(NO_EMISSION_WAIT_MS) { gate.enabledRemoteSources().first() })

        appScope.runCurrent()
        val first = gate.enabledRemoteSources().first()

        assertFalse(RemoteSourceId.SMB in first)
        assertFalse(RemoteSourceId.DROPBOX in first)
        assertTrue(RemoteSourceId.SFTP in first)
    }

    @Test
    fun `a later collector emission replaces the seed`() {
        val gate = gate()
        assertFalse(gate.isEnabled(RemoteSourceId.SMB))

        settings.value = AppSettings(smbEnabled = true)
        appScope.runCurrent()

        assertTrue(gate.isEnabled(RemoteSourceId.SMB))
        assertTrue(RemoteSourceId.entries.all { gate.isEnabled(it) })
    }

    private companion object {
        const val NO_EMISSION_WAIT_MS = 1_000L
    }
}
