package com.sza.fastmediasorter.domain.model.transfer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * S3040: manifest roundtrip, defaults and TTL expiry.
 *
 * Robolectric supplies the real org.json implementation (S0223 pattern) - the manifest codec is
 * built on `JSONObject`, which the stubbed framework jar would refuse.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CrossDeviceTransferModelsTest {

    private val sample = CrossDevicePacketManifest(
        packetId = "3f2b1c4d-0000-4a00-8000-0123456789ab",
        createdAtEpochMs = 1_700_000_000_000L,
        senderDeviceName = "Pixel 8",
        targetDeviceName = "Living Room TV",
        payloadKind = CrossDevicePayloadKind.MEDIA_FILES,
        fileNames = listOf("clip.mp4", "cover.jpg"),
        totalSizeBytes = 2048L
    )

    @Test
    fun `manifest survives a json roundtrip`() {
        val restored = CrossDevicePacketManifest.fromJson(CrossDevicePacketManifest.toJson(sample))
        assertEquals(sample, restored)
    }

    @Test
    fun `ttl and status default when the json omits them`() {
        val raw = """
            {
              "packetId": "p1",
              "createdAtEpochMs": 10,
              "senderDeviceName": "Tablet",
              "payloadKind": "SETTINGS",
              "fileNames": ["settings.json"],
              "totalSizeBytes": 12
            }
        """.trimIndent()

        val parsed = CrossDevicePacketManifest.fromJson(raw)

        assertEquals(CrossDevicePacketManifest.DEFAULT_TTL_DAYS, parsed?.ttlDays)
        assertEquals(CrossDevicePacketStatus.PENDING, parsed?.status)
        assertNull("a broadcast packet names no target", parsed?.targetDeviceName)
    }

    @Test
    fun `a packet expires only past its ttl`() {
        val dayMs = 24L * 60L * 60L * 1000L
        val sixDaysLater = sample.createdAtEpochMs + 6 * dayMs
        val eightDaysLater = sample.createdAtEpochMs + 8 * dayMs

        assertFalse(sample.isExpiredAt(sixDaysLater))
        assertTrue(sample.isExpiredAt(eightDaysLater))
    }

    @Test
    fun `an unreadable payload kind is rejected rather than coerced`() {
        val raw = """
            {
              "packetId": "p2",
              "createdAtEpochMs": 10,
              "senderDeviceName": "Phone",
              "payloadKind": "HOLOGRAM",
              "fileNames": [],
              "totalSizeBytes": 0
            }
        """.trimIndent()

        assertNull(CrossDevicePacketManifest.fromJson(raw))
    }

    @Test
    fun `transfer options cover accept and accept-and-delete`() {
        assertEquals(2, CrossDeviceTransferOption.entries.size)
        assertTrue(CrossDeviceTransferOption.entries.contains(CrossDeviceTransferOption.ACCEPT_AND_DELETE))
    }
}
