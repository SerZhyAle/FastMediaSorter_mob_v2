package com.sza.fastmediasorter.data.remote.exchange

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.sza.fastmediasorter.data.remote.sftp.anywhere.SftpRendezvousCodec
import com.sza.fastmediasorter.domain.model.SftpRendezvousDevice
import com.sza.fastmediasorter.domain.model.SftpRendezvousRecord
import com.sza.fastmediasorter.domain.model.SftpRendezvousResource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceExchangeDirectoryStateTest {

    private val pc = device("pc")
    private val tablet = device("tablet")
    private val photos = resource("r-photos", "pc", "Photos")
    private val music = resource("r-music", "tablet", "Music")

    private val initial = DeviceExchangeDirectoryState.of(
        DeviceExchangeDirectory(
            revision = 7,
            devices = listOf(pc, tablet),
            resources = listOf(photos, music),
            broadcasts = emptyList(),
        )
    )

    @Test
    fun `the next revision is applied as upserts and removals`() {
        val renamed = photos.copy(name = "Photos 2026")
        val video = resource("r-video", "pc", "Video")
        val changed = DeviceExchangeChanged.from(
            changed(8, upserts = listOf(renamed, video), removedResources = listOf("r-music"))
        )

        val outcome = initial.apply(changed)

        val state = (outcome as DeviceExchangeDirectoryState.Outcome.Applied).state
        assertEquals(8L, state.revision)
        assertEquals(listOf("Photos 2026", "Video"), state.directory.resources.map { it.name })
    }

    @Test
    fun `a skipped revision is a gap and changes nothing`() {
        val skipping = changed(9, upserts = listOf(resource("r-x", "pc", "X")))

        val outcome = initial.apply(DeviceExchangeChanged.from(skipping))

        assertEquals(DeviceExchangeDirectoryState.Outcome.Gap, outcome)
        assertEquals(7L, initial.revision)
    }

    @Test
    fun `a push not newer than the held revision is stale`() {
        assertEquals(DeviceExchangeDirectoryState.Outcome.Stale, initial.apply(DeviceExchangeChanged.from(changed(7))))
        assertEquals(DeviceExchangeDirectoryState.Outcome.Stale, initial.apply(DeviceExchangeChanged.from(changed(3))))
    }

    @Test
    fun `a removed device takes its records along`() {
        val outcome = initial.apply(DeviceExchangeChanged.from(changed(8, removedDevices = listOf("tablet"))))

        val state = (outcome as DeviceExchangeDirectoryState.Outcome.Applied).state
        assertEquals(listOf("pc"), state.directory.devices.map { it.deviceId })
        assertEquals(listOf("r-photos"), state.directory.resources.map { it.resourceId })
    }

    @Test
    fun `an unreadable upsert is dropped alone`() {
        val envelope = changed(8, upserts = listOf(resource("r-ok", "pc", "Ok")))
        envelope.obj("upserts")!!.getAsJsonArray("resources").add(JsonObject().apply { addProperty("kind", "future") })

        val parsed = DeviceExchangeChanged.from(envelope)

        assertEquals(listOf("r-ok"), parsed.resources.map { it.resourceId })
        assertTrue(parsed.removedResourceIds.isEmpty())
    }

    private fun changed(
        revision: Long,
        upserts: List<SftpRendezvousRecord> = emptyList(),
        removedResources: List<String> = emptyList(),
        removedDevices: List<String> = emptyList(),
    ) = DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_CHANGED) {
        addProperty("revision", revision)
        add(
            "upserts",
            JsonObject().apply {
                add("devices", array(upserts.filterIsInstance<SftpRendezvousDevice>()))
                add("resources", array(upserts.filterIsInstance<SftpRendezvousResource>()))
            }
        )
        add(
            "removals",
            JsonObject().apply {
                add("deviceIds", JsonArray().apply { removedDevices.forEach(::add) })
                add("resourceIds", JsonArray().apply { removedResources.forEach(::add) })
            }
        )
    }

    private fun array(records: List<SftpRendezvousRecord>) =
        JsonArray().apply { records.forEach { add(SftpRendezvousCodec.body(it)) } }

    private fun device(id: String) = SftpRendezvousDevice(
        deviceId = id,
        deviceName = id,
        product = SftpRendezvousDevice.PRODUCT_FMS_ANDROID,
        productVersion = null,
        platform = null,
        roles = listOf(SftpRendezvousDevice.ROLE_RESOURCE_PRODUCER),
        presence = SftpRendezvousDevice.PRESENCE_ONLINE,
        updatedAtMs = UPDATED_AT,
        writtenAtMs = 0L,
        ttlSeconds = 0L,
    )

    private fun resource(id: String, deviceId: String, name: String) = SftpRendezvousResource(
        resourceId = id,
        deviceId = deviceId,
        kind = SftpRendezvousResource.KIND_SFTP_SHARE,
        name = name,
        descriptor = null,
        updatedAtMs = UPDATED_AT,
        writtenAtMs = 0L,
        ttlSeconds = 0L,
    )

    private companion object {
        const val UPDATED_AT = 1_791_000_000_000L
    }
}
