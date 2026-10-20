package com.sza.fastmediasorter.data.remote.sftp.anywhere

import com.sza.fastmediasorter.domain.model.SftpRendezvousDevice
import com.sza.fastmediasorter.domain.model.SftpRendezvousRequest
import com.sza.fastmediasorter.domain.model.SftpRendezvousResource
import com.sza.fastmediasorter.domain.model.isFreshAt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Drive file form of contract DEVICE-EXCHANGE: the section 8.1 wrapper around 6.1, 6.2 and 8.3 records. */
class SftpRendezvousCodecTest {

    private val device = SftpRendezvousDevice(
        deviceId = DEVICE_ID,
        deviceName = "Pixel",
        product = SftpRendezvousDevice.PRODUCT_FMS_ANDROID,
        productVersion = "2.6.1",
        platform = SftpRendezvousDevice.PLATFORM_ANDROID,
        roles = listOf(SftpRendezvousDevice.ROLE_RESOURCE_PRODUCER, SftpRendezvousDevice.ROLE_RESOURCE_CONSUMER),
        presence = SftpRendezvousDevice.PRESENCE_ONLINE,
        updatedAtMs = WRITTEN_MS,
        writtenAtMs = WRITTEN_MS,
        ttlSeconds = 1_800L,
    )

    private val resource = SftpRendezvousResource(
        resourceId = "resource-1",
        deviceId = DEVICE_ID,
        kind = SftpRendezvousResource.KIND_SFTP_SHARE,
        name = "Pixel",
        descriptor = "FMSSFTP1:h=192.168.1.20&p=2222&u=fms&pw=secret&fp=SHA256%3Aabc",
        updatedAtMs = WRITTEN_MS,
        writtenAtMs = WRITTEN_MS,
        ttlSeconds = 1_800L,
    )

    private val request = SftpRendezvousRequest(
        DEVICE_ID,
        SftpRendezvousRequest.ACTION_ANNOUNCE,
        "me",
        WRITTEN_MS,
        600L
    )

    @Test
    fun `device, resource and request records round-trip`() {
        assertEquals(device, SftpRendezvousCodec.decode(SftpRendezvousCodec.encode(device)))
        assertEquals(resource, SftpRendezvousCodec.decode(SftpRendezvousCodec.encode(resource)))
        assertEquals(request, SftpRendezvousCodec.decode(SftpRendezvousCodec.encode(request)))
    }

    @Test
    fun `the wrapper carries the section 8_1 members and a UTC timestamp with Z`() {
        val raw = SftpRendezvousCodec.encode(device)
        assertTrue(raw.contains("\"schemaVersion\":1"))
        assertTrue(raw.contains("\"type\":\"device\""))
        assertTrue(raw.contains("\"writtenAt\":\"2026-10-07T12:00:00.000Z\""))
        assertTrue(raw.contains("\"ttlSeconds\":1800"))
        assertTrue(SftpRendezvousCodec.encode(resource).contains("\"access\":{\"descriptor\":\"FMSSFTP1:"))
    }

    @Test
    fun `unknown members are ignored and a timestamp without milliseconds is read`() {
        val raw = wrap(
            "request",
            """{"toDeviceId":"$DEVICE_ID","action":"announce","future":[1,2]}""",
            writtenAt = "2026-10-07T12:00:00Z",
            extra = ""","later":{"x":1}""",
        )
        val decoded = SftpRendezvousCodec.decode(raw) as SftpRendezvousRequest
        assertEquals(DEVICE_ID, decoded.toDeviceId)
        assertEquals(WRITTEN_MS, decoded.writtenAtMs)
        assertNull(decoded.fromDeviceId)
    }

    @Test
    fun `a newer schema and a body above 16 KiB are refused`() {
        val body = """{"toDeviceId":"$DEVICE_ID","action":"announce"}"""
        assertNull(SftpRendezvousCodec.decode(wrap("request", body, schema = 2)))
        val padded = wrap("request", body, extra = ""","pad":"${"x".repeat(SftpRendezvousCodec.MAX_RECORD_BYTES)}"""")
        assertNull(SftpRendezvousCodec.decode(padded))
    }

    @Test
    fun `an unknown type, kind or action is skipped`() {
        assertNull(SftpRendezvousCodec.decode(wrap("broadcast", """{"broadcastId":"b","deviceId":"d","mode":"m"}""")))
        val folder = """{"resourceId":"r","deviceId":"d","kind":"folder","name":"n","access":{"descriptor":"x"}}"""
        assertNull(SftpRendezvousCodec.decode(wrap("resource", folder)))
        assertNull(SftpRendezvousCodec.decode(wrap("request", """{"toDeviceId":"d","action":"register"}""")))
    }

    @Test
    fun `a share without access is listed with no descriptor and re-encoded without access`() {
        val noAccess = """{"resourceId":"r","deviceId":"d","kind":"sftp-share","name":"n"}"""
        val decoded = SftpRendezvousCodec.decode(wrap("resource", noAccess)) as SftpRendezvousResource
        assertNull(decoded.descriptor)
        assertNull(decoded.root)
        assertFalse(SftpRendezvousCodec.encode(decoded).contains("\"access\""))
    }

    @Test
    fun `the identity members ride a record with and without access`() {
        val fingerprint = "SHA256:47DEQpj8HBSa+/TImW+5JCeuQeRkm5NMpJWZG3hSuFU"
        val identified = resource.copy(hostKeyFingerprint = fingerprint, identityRoot = "/")

        val back = SftpRendezvousCodec.decode(SftpRendezvousCodec.encode(identified)) as SftpRendezvousResource
        assertEquals(fingerprint, back.hostKeyFingerprint)
        assertEquals("/", back.identityRoot)

        val bare = """{"resourceId":"r","deviceId":"d","kind":"sftp-share","name":"n",""" +
            """"hostKeyFingerprint":"$fingerprint","root":"/Photos"}"""
        val listed = SftpRendezvousCodec.decode(wrap("resource", bare)) as SftpRendezvousResource
        assertNull(listed.descriptor)
        assertEquals(fingerprint, listed.hostKeyFingerprint)
        assertEquals("/Photos", listed.identityRoot)
    }

    @Test
    fun `missing required members, a bad timestamp and garbage are refused`() {
        assertNull(SftpRendezvousCodec.decode(wrap("device", """{"deviceName":"n","product":"fms-android"}""")))
        val noDevice = """{"resourceId":"r","kind":"sftp-share","name":"n","access":{"descriptor":"x"}}"""
        assertNull(SftpRendezvousCodec.decode(wrap("resource", noDevice)))
        val request = """{"toDeviceId":"d","action":"announce"}"""
        assertNull(SftpRendezvousCodec.decode(wrap("request", request, writtenAt = "yesterday")))
        assertNull(SftpRendezvousCodec.decode("{not json"))
    }

    @Test
    fun `another product's device record is read, an unknown presence reads offline`() {
        val body = """{"deviceId":"w1","deviceName":"PC","product":"fms-windows","presence":"sleeping"}"""
        val decoded = SftpRendezvousCodec.decode(wrap("device", body)) as SftpRendezvousDevice
        assertEquals("fms-windows", decoded.product)
        assertFalse(decoded.isOnline)
    }

    @Test
    fun `an offline device record carries offlineReason and reads it back, an online one never writes it`() {
        val paused = device.copy(
            presence = SftpRendezvousDevice.PRESENCE_OFFLINE,
            offlineReason = SftpRendezvousDevice.OFFLINE_REASON_TIME_LIMIT,
        )
        val raw = SftpRendezvousCodec.encode(paused)
        assertTrue(raw.contains("\"presence\":\"offline\",\"offlineReason\":\"time-limit\""))
        val decoded = SftpRendezvousCodec.decode(raw) as SftpRendezvousDevice
        assertEquals(paused, decoded)
        assertTrue(decoded.isSharePaused)

        val online = device.copy(offlineReason = SftpRendezvousDevice.OFFLINE_REASON_TIME_LIMIT)
        assertFalse(SftpRendezvousCodec.encode(online).contains("offlineReason"))
        assertFalse(SftpRendezvousCodec.encode(device.copy(presence = "offline")).contains("offlineReason"))
    }

    @Test
    fun `an unknown offlineReason or one beside online reads as none`() {
        val unknown = """{"deviceId":"w1","deviceName":"PC","product":"fms-windows","presence":"offline",""" +
            """"offlineReason":"asleep"}"""
        assertNull((SftpRendezvousCodec.decode(wrap("device", unknown)) as SftpRendezvousDevice).offlineReason)
        val online = """{"deviceId":"w1","deviceName":"PC","product":"fms-windows","presence":"online",""" +
            """"offlineReason":"time-limit"}"""
        val decoded = SftpRendezvousCodec.decode(wrap("device", online)) as SftpRendezvousDevice
        assertNull(decoded.offlineReason)
        assertFalse(decoded.isSharePaused)
    }

    @Test
    fun `freshness follows the TTL from Drive's modifiedTime when the listing has one`() {
        assertTrue(device.isFreshAt(WRITTEN_MS + 1_800_000L))
        assertFalse(device.isFreshAt(WRITTEN_MS + 1_800_001L))
        assertTrue(device.isFreshAt(WRITTEN_MS + 1_800_001L, modifiedMs = WRITTEN_MS + 1_000L))
    }

    private fun wrap(
        type: String,
        body: String,
        schema: Int = 1,
        writtenAt: String = "2026-10-07T12:00:00.000Z",
        extra: String = "",
    ) = """{"schemaVersion":$schema,"type":"$type","writtenAt":"$writtenAt","ttlSeconds":60,"record":$body$extra}"""

    private companion object {
        const val DEVICE_ID = "AAAAAAAAAAAAAAAAAAAAAA"

        // 2026-10-07T12:00:00Z
        const val WRITTEN_MS = 1_791_374_400_000L
    }
}
