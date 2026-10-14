package com.sza.fastmediasorter.domain.usecase.exchange

import com.sza.fastmediasorter.domain.model.ExchangeDirectoryListing
import com.sza.fastmediasorter.domain.model.ExchangeListedBroadcast
import com.sza.fastmediasorter.domain.model.ExchangeListedDevice
import com.sza.fastmediasorter.domain.model.SftpRendezvousBroadcast
import com.sza.fastmediasorter.domain.model.SftpRendezvousDevice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ObserveDeviceBroadcastsUseCaseTest {

    @Test
    fun `every listed broadcast becomes a row carrying its descriptor, ordered by device then title`() {
        val listing = ExchangeDirectoryListing(
            devices = listOf(
                device("Watch", broadcast("b3", "Run", "AUDIO_ONLY")),
                device("Desk PC", broadcast("b2", "Screen", "VIDEO_AUDIO"), broadcast("b1", "Camera", "VIDEO_ONLY")),
            ),
            listedSources = setOf(SERVER),
            unreachableSources = emptySet(),
        )

        val rows = ObserveDeviceBroadcastsUseCase.toBroadcasts(listing)

        assertEquals(listOf("b1", "b2", "b3"), rows.items.map { it.broadcastId })
        assertEquals("{\"id\":\"b1\"}", rows.items.first().descriptorJson)
        assertEquals("Desk PC", rows.items.first().deviceName)
        assertFalse(rows.incomplete)
    }

    @Test
    fun `an unplayable mode stays listed and an unreachable store marks the list incomplete`() {
        val listing = ExchangeDirectoryListing(
            devices = listOf(device("PC", broadcast("b1", "Screen", "VIDEO_AUDIO"), playable = false)),
            listedSources = emptySet(),
            unreachableSources = setOf(SERVER),
            pending = true,
        )

        val rows = ObserveDeviceBroadcastsUseCase.toBroadcasts(listing)

        assertFalse(rows.items.single().playable)
        assertTrue(rows.incomplete)
        assertTrue(rows.pending)
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
