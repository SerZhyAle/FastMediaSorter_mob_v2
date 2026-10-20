package com.sza.fastmediasorter.data.remote.sftp.anywhere

import com.sza.fastmediasorter.data.cloud.GoogleDriveSftpRendezvousDataSource
import com.sza.fastmediasorter.data.remote.sftp.server.SftpServerIdentityStore
import com.sza.fastmediasorter.data.repository.settings.SftpRendezvousIdentityStore
import com.sza.fastmediasorter.data.repository.settings.SftpServerSettingsStore
import com.sza.fastmediasorter.domain.model.SftpRendezvousDevice
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A stop's offline record and the online record of the restart that follows it are written in generation order,
 * so the share that runs is never left advertised offline.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SftpDriveRendezvousPublisherTest {

    private val persisted = mutableListOf<String>()
    private val offlineGate = CompletableDeferred<Unit>()

    private val store = mockk<GoogleDriveSftpRendezvousDataSource> {
        coEvery { writeDevice(any()) } coAnswers {
            val device = firstArg<SftpRendezvousDevice>()
            if (device.presence == SftpRendezvousDevice.PRESENCE_OFFLINE) offlineGate.await()
            persisted += device.presence
            Result.success(Unit)
        }
        coEvery { writeResource(any()) } returns Result.success(Unit)
        coEvery { snapshot(any()) } returns Result.failure(IllegalStateException("no Drive in a unit test"))
    }
    private val identityStore = mockk<SftpServerIdentityStore> {
        coEvery { hostKeyFingerprint() } returns HOST_KEY
    }
    private val settings = mockk<SftpServerSettingsStore> {
        every { driveAccessShared } returns flowOf(false)
    }
    private val ids = mockk<SftpRendezvousIdentityStore> {
        coEvery { deviceId() } returns DEVICE_ID
        coEvery { resourceIdFor(any()) } returns SftpRendezvousIdentityStore.ResourceBinding(RESOURCE_ID, null)
    }

    @Test
    fun `an offline write still in flight cannot land after the restart's online record`() {
        val dispatcher = StandardTestDispatcher()
        runTest(dispatcher) {
            val appScope = TestScope(dispatcher)
            val publisher = SftpDriveRendezvousPublisher(store, identityStore, settings, ids, appScope, dispatcher)

            publisher.start(PORT) { emptyList() }
            runCurrent()
            publisher.stop()
            runCurrent()
            publisher.start(PORT) { emptyList() }
            runCurrent()
            offlineGate.complete(Unit)
            runCurrent()

            assertEquals(SftpRendezvousDevice.PRESENCE_ONLINE, persisted.last())
            publisher.stop()
            appScope.cancelScope()
        }
    }

    @Test
    fun `a restart before the offline write drops it`() {
        val dispatcher = StandardTestDispatcher()
        runTest(dispatcher) {
            val appScope = TestScope(dispatcher)
            val publisher = SftpDriveRendezvousPublisher(store, identityStore, settings, ids, appScope, dispatcher)
            offlineGate.complete(Unit)

            publisher.start(PORT) { emptyList() }
            runCurrent()
            publisher.stop()
            publisher.start(PORT) { emptyList() }
            runCurrent()

            assertEquals(
                listOf(SftpRendezvousDevice.PRESENCE_ONLINE, SftpRendezvousDevice.PRESENCE_ONLINE),
                persisted,
            )
            appScope.cancelScope()
        }
    }

    private fun TestScope.cancelScope() = coroutineContext[kotlinx.coroutines.Job]?.cancel()

    private companion object {
        const val PORT = 2222
        const val DEVICE_ID = "AAAAAAAAAAAAAAAAAAAAAA"
        const val RESOURCE_ID = "q3Vb7YtK0xP2mN9sLfR4wA"
        val HOST_KEY = "ab".repeat(32)
    }
}
