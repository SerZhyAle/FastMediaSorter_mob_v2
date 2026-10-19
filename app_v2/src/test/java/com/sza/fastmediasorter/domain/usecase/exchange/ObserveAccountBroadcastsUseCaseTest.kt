package com.sza.fastmediasorter.domain.usecase.exchange

import com.sza.fastmediasorter.data.repository.DeviceExchangeAccountRepository
import com.sza.fastmediasorter.data.repository.ExchangeDirectoryRepository
import com.sza.fastmediasorter.domain.model.DeviceExchangeAccountEntry
import com.sza.fastmediasorter.domain.model.ExchangeDirectoryListing
import com.sza.fastmediasorter.domain.model.ExchangeListedBroadcast
import com.sza.fastmediasorter.domain.model.ExchangeListedDevice
import com.sza.fastmediasorter.domain.model.ExchangeSealIssue
import com.sza.fastmediasorter.domain.model.SftpRendezvousBroadcast
import com.sza.fastmediasorter.domain.model.SftpRendezvousDevice
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ObserveAccountBroadcastsUseCaseTest {

    @Test
    fun `emits broadcasts including own and remote devices with valid seal`() = runTest {
        val entry = mockk<DeviceExchangeAccountEntry>()
        val accountsRepo = mockk<DeviceExchangeAccountRepository> {
            every { entries } returns flowOf(listOf(entry))
        }

        val listing = ExchangeDirectoryListing(
            devices = listOf(
                device("This Phone", broadcast("b_own", "Own Screen", "VIDEO_AUDIO")),
                device("Watch", broadcast("b_watch", "Heart Rate Stream", "AUDIO_ONLY")),
            ),
            listedSources = setOf(SERVER),
            unreachableSources = emptySet(),
        )

        val directoryRepo = mockk<ExchangeDirectoryRepository> {
            every { observeAllBroadcasts(any()) } returns flowOf(listing)
        }

        val useCase = ObserveAccountBroadcastsUseCase(accountsRepo, directoryRepo)

        assertTrue(useCase.hasAccount().first())

        val result = useCase().first()
        assertEquals(2, result.items.size)
        assertEquals(listOf("b_own", "b_watch"), result.items.map { it.broadcastId })
        assertEquals("This Phone", result.items[0].deviceName)
        assertEquals("Watch", result.items[1].deviceName)
        assertFalse(result.incomplete)
    }

    @Test
    fun `filters out broadcast with seal issue`() = runTest {
        val accountsRepo = mockk<DeviceExchangeAccountRepository> {
            every { entries } returns flowOf(emptyList())
        }

        val sealedWithIssue = SftpRendezvousBroadcast(
            broadcastId = "b_broken_seal",
            deviceId = "d",
            title = "Unopenable Box",
            mode = "VIDEO_ONLY",
            startedAtMs = null,
            updatedAtMs = 0L,
            descriptorJson = "{\"id\":\"b_broken_seal\"}",
            writtenAtMs = 0L,
            ttlSeconds = 0L,
            sealIssue = ExchangeSealIssue.SEALED_FAILED,
        )

        val listing = ExchangeDirectoryListing(
            devices = listOf(device("Remote", sealedWithIssue)),
            listedSources = setOf(SERVER),
            unreachableSources = emptySet(),
        )

        val directoryRepo = mockk<ExchangeDirectoryRepository> {
            every { observeAllBroadcasts(any()) } returns flowOf(listing)
        }

        val useCase = ObserveAccountBroadcastsUseCase(accountsRepo, directoryRepo)
        assertFalse(useCase.hasAccount().first())

        val result = useCase().first()
        assertTrue(result.items.isEmpty())
    }

    private fun device(name: String, vararg records: SftpRendezvousBroadcast, playable: Boolean = true) =
        ExchangeListedDevice(
            sourceKey = SERVER,
            deviceId = name,
            device = SftpRendezvousDevice(
                deviceId = name,
                deviceName = name,
                product = SftpRendezvousDevice.PRODUCT_FMS_ANDROID,
                productVersion = null,
                platform = null,
                roles = emptyList(),
                presence = SftpRendezvousDevice.PRESENCE_ONLINE,
                updatedAtMs = 0L,
                writtenAtMs = 0L,
                ttlSeconds = 0L,
            ),
            resources = emptyList(),
            broadcasts = records.map { ExchangeListedBroadcast(SERVER, it, playable) },
        )

    private fun broadcast(id: String, title: String, mode: String) = SftpRendezvousBroadcast(
        broadcastId = id,
        deviceId = "d",
        title = title,
        mode = mode,
        startedAtMs = null,
        updatedAtMs = 0L,
        descriptorJson = "{\"id\":\"$id\"}",
        writtenAtMs = 0L,
        ttlSeconds = 0L,
    )

    private companion object {
        const val SERVER = "server|login"
    }
}
