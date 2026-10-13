package com.sza.fastmediasorter.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExchangeDirectoryRulesTest {

    @Test
    fun `a record both stores carry keeps the later updatedAt under the server entry`() {
        val server = source(SERVER, resources = listOf(resource("r1", "pc", "Old", updatedAt = 100)))
        val drive = source(
            ExchangeDirectorySource.DRIVE_SOURCE,
            resources = listOf(resource("r1", "pc", "New", updatedAt = 200), resource("r2", "pc", "Drive only")),
        )

        val listing = ExchangeDirectoryRules.listing(listOf(server), drive, emptySet(), emptySet())

        assertEquals("New", listing.resource(SERVER, "r1")?.record?.name)
        assertEquals("Drive only", listing.resource(ExchangeDirectorySource.DRIVE_SOURCE, "r2")?.record?.name)
        assertEquals(null, listing.resource(ExchangeDirectorySource.DRIVE_SOURCE, "r1"))
    }

    @Test
    fun `an older Drive record does not replace the server one`() {
        val server = source(SERVER, resources = listOf(resource("r1", "pc", "Server", updatedAt = 300)))
        val drive = source(
            ExchangeDirectorySource.DRIVE_SOURCE,
            resources = listOf(resource("r1", "pc", "Drive", updatedAt = 200)),
        )

        val listing = ExchangeDirectoryRules.listing(listOf(server), drive, emptySet(), emptySet())

        assertEquals("Server", listing.resource(SERVER, "r1")?.record?.name)
    }

    @Test
    fun `this device's own records are hidden`() {
        val server = source(
            SERVER,
            devices = listOf(device("me"), device("pc")),
            resources = listOf(resource("mine", "me", "Mine"), resource("r1", "pc", "Theirs")),
        )

        val listing = ExchangeDirectoryRules.listing(listOf(server), null, setOf("me"), emptySet())

        assertEquals(listOf("pc"), listing.devices.map { it.deviceId })
        assertEquals(listOf("Theirs"), listing.devices.single().resources.map { it.record.name })
    }

    @Test
    fun `a broadcast in a mode this build cannot play is marked, not hidden`() {
        val server = source(
            SERVER,
            devices = listOf(device("pc")),
            broadcasts = listOf(broadcast("b1", "VIDEO_AUDIO"), broadcast("b2", "AUDIO_ONLY")),
        )

        val listing = ExchangeDirectoryRules.listing(listOf(server), null, emptySet(), setOf("AUDIO_ONLY"))

        val playable = listing.devices.single().broadcasts.associate { it.record.broadcastId to it.playable }
        assertEquals(mapOf("b1" to false, "b2" to true), playable)
    }

    @Test
    fun `access is judged from the record`() {
        val open = ExchangeListedResource(SERVER, resource("r1", "pc", "Open", descriptor = "FMSSFTP1:h=a"), true)
        val keptOff = ExchangeListedResource(SERVER, resource("r2", "pc", "No access"), true)
        val locked = ExchangeListedResource(SERVER, resource("r3", "pc", "Locked").copy(pinProtected = true), true)

        assertEquals(ExchangeResourceAccess.ATTACHABLE, open.access)
        assertEquals(ExchangeResourceAccess.NO_ACCESS, keptOff.access)
        assertEquals(ExchangeResourceAccess.PIN_LOCKED, locked.access)
    }

    @Test
    fun `an offline producer leaves the attachment unavailable`() {
        val offline = resource("r1", "pc", "Photos").copy(presence = SftpRendezvousDevice.PRESENCE_OFFLINE)
        val listing = listingOf(offline)

        assertEquals(ExchangeAttachmentState.UNAVAILABLE, ExchangeDirectoryRules.attachmentState(listing, SERVER, "r1"))
        assertFalse(listing.devices.single().online)
    }

    @Test
    fun `an online producer leaves the attachment available`() {
        val online = resource("r1", "pc", "Photos").copy(presence = SftpRendezvousDevice.PRESENCE_ONLINE)
        val listing = listingOf(online)

        assertEquals(ExchangeAttachmentState.AVAILABLE, ExchangeDirectoryRules.attachmentState(listing, SERVER, "r1"))
        assertTrue(listing.devices.single().online)
    }

    @Test
    fun `a record gone from a store that was read is no longer published`() {
        val listing = ExchangeDirectoryRules.listing(listOf(source(SERVER)), null, emptySet(), emptySet())

        assertEquals(
            ExchangeAttachmentState.NO_LONGER_PUBLISHED,
            ExchangeDirectoryRules.attachmentState(listing, SERVER, "r1"),
        )
    }

    @Test
    fun `a store that could not be read never declares a record gone`() {
        val listing = ExchangeDirectoryRules.listing(
            listOf(ExchangeDirectorySource.unreachable(SERVER)),
            ExchangeDirectorySource.unreachable(ExchangeDirectorySource.DRIVE_SOURCE),
            emptySet(),
            emptySet(),
        )

        assertEquals(ExchangeAttachmentState.UNAVAILABLE, ExchangeDirectoryRules.attachmentState(listing, SERVER, "r1"))
        assertEquals(setOf(SERVER, ExchangeDirectorySource.DRIVE_SOURCE), listing.unreachableSources)
    }

    private fun listingOf(record: SftpRendezvousResource) =
        ExchangeDirectoryRules.listing(listOf(source(SERVER, resources = listOf(record))), null, emptySet(), emptySet())

    private fun source(
        key: String,
        devices: List<SftpRendezvousDevice> = emptyList(),
        resources: List<SftpRendezvousResource> = emptyList(),
        broadcasts: List<SftpRendezvousBroadcast> = emptyList(),
    ) = ExchangeDirectorySource(key, devices, resources, broadcasts)

    private fun device(id: String) = SftpRendezvousDevice(
        deviceId = id,
        deviceName = id,
        product = SftpRendezvousDevice.PRODUCT_FMS_ANDROID,
        productVersion = null,
        platform = null,
        roles = emptyList(),
        presence = SftpRendezvousDevice.PRESENCE_ONLINE,
        updatedAtMs = 0L,
        writtenAtMs = 0L,
        ttlSeconds = 0L,
    )

    private fun resource(
        id: String,
        deviceId: String,
        name: String,
        updatedAt: Long = 0L,
        descriptor: String? = null,
    ) = SftpRendezvousResource(
        resourceId = id,
        deviceId = deviceId,
        kind = SftpRendezvousResource.KIND_SFTP_SHARE,
        name = name,
        descriptor = descriptor,
        updatedAtMs = updatedAt,
        writtenAtMs = 0L,
        ttlSeconds = 0L,
    )

    private fun broadcast(id: String, mode: String) = SftpRendezvousBroadcast(
        broadcastId = id,
        deviceId = "pc",
        title = null,
        mode = mode,
        startedAtMs = null,
        updatedAtMs = 0L,
        descriptorJson = "{}",
        writtenAtMs = 0L,
        ttlSeconds = 0L,
    )

    private companion object {
        const val SERVER = "exchange.example:7443/user"
    }
}
