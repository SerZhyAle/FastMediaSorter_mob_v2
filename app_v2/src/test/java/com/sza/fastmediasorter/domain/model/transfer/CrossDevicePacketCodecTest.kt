package com.sza.fastmediasorter.domain.model.transfer

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * S4231: the DEVICE-EXCHANGE section 24 conformance vectors (`drive-packets-v1.json`) against the codec.
 * Robolectric supplies the real org.json implementation the codec is built on.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CrossDevicePacketCodecTest {

    private val cases: List<JSONObject> by lazy {
        val raw = requireNotNull(javaClass.classLoader?.getResourceAsStream(VECTORS)) { "missing $VECTORS" }
            .bufferedReader()
            .use { it.readText() }
        val array = JSONObject(raw).getJSONArray("cases")
        (0 until array.length()).map(array::getJSONObject)
    }

    @Test
    fun `every vector reads or skips as declared`() {
        assertTrue(cases.size >= MIN_CASES)
        cases.forEach { case ->
            val id = case.getString("id")
            val parsed = CrossDevicePacketCodec.fromJson(case.getJSONObject("manifest").toString())
            if (case.getString("expect") == "skip") {
                assertNull("$id must be skipped", parsed)
            } else {
                assertNotNull("$id must be read", parsed)
                assertFields(id, requireNotNull(parsed), case.optJSONObject("fields") ?: JSONObject())
            }
        }
    }

    @Test
    fun `every readable vector survives a write and read`() {
        cases.filter { it.getString("expect") == "read" }.forEach { case ->
            val parsed = requireNotNull(CrossDevicePacketCodec.fromJson(case.getJSONObject("manifest").toString()))
            val rewritten = CrossDevicePacketCodec.fromJson(CrossDevicePacketCodec.toJson(parsed))
            assertEquals(case.getString("id"), parsed, rewritten)
        }
    }

    @Test
    fun `a v1 manifest is written without version 2 members`() {
        val v1 = CrossDevicePacketManifest(
            packetId = "p1",
            createdAtEpochMs = 10,
            senderDeviceName = "Tablet",
            targetDeviceName = null,
            payloadKind = CrossDevicePayloadKind.SETTINGS,
            fileNames = listOf("fms_settings.json"),
            totalSizeBytes = 12
        )

        val json = JSONObject(CrossDevicePacketCodec.toJson(v1))

        assertTrue(!json.has("schemaVersion") && !json.has("files"))
    }

    @Test
    fun `a new kind is written as schema version 2`() {
        val request = CrossDevicePacketManifest(
            packetId = "req1",
            createdAtEpochMs = 10,
            senderDeviceName = "Phone",
            targetDeviceName = "Tablet",
            payloadKind = CrossDevicePayloadKind.REQUEST,
            fileNames = emptyList(),
            totalSizeBytes = 0,
            request = DriveChannelRequest.ListFolder("res", "")
        )

        assertEquals(2, JSONObject(CrossDevicePacketCodec.toJson(request)).getInt("schemaVersion"))
    }

    @Test
    fun `relative paths refuse climbing and control characters`() {
        assertEquals(listOf("a", "b"), DriveRelativePath.parse("/a/b/"))
        assertEquals(emptyList<String>(), DriveRelativePath.parse(""))
        assertNull(DriveRelativePath.parse("a/../b"))
        assertNull(DriveRelativePath.parse("a//b"))
        assertNull(DriveRelativePath.parse("a\\b"))
        assertNull(DriveRelativePath.parse("a/\u0001"))
    }

    @Test
    fun `a listing survives a write and read`() {
        val listing = DriveListing(
            asOf = "2026-10-15T08:00:00Z",
            entries = listOf(DriveListingEntry("Camera", dir = true, size = 0, modifiedAt = "2026-10-14T08:00:00Z"))
        )

        assertEquals(listing, CrossDevicePacketCodec.listingFromJson(CrossDevicePacketCodec.listingToJson(listing)))
    }

    private fun assertFields(id: String, manifest: CrossDevicePacketManifest, fields: JSONObject) {
        fields.keys().forEach { key ->
            val expected = fields.get(key).takeUnless { it == JSONObject.NULL }?.toString()
            assertEquals("$id.$key", expected, actual(manifest, key))
        }
    }

    private fun actual(manifest: CrossDevicePacketManifest, key: String): String? {
        val request = manifest.request
        return when (key) {
            "payloadKind" -> manifest.payloadKind.name
            "schemaVersion" -> manifest.schemaVersion.toString()
            "targetDeviceName" -> manifest.targetDeviceName
            "targetDeviceId" -> manifest.targetDeviceId
            "status" -> manifest.status.name
            "ttlDays" -> manifest.ttlDays.toString()
            "ttlSeconds" -> manifest.ttlSeconds?.toString()
            "filesCount" -> manifest.files.size.toString()
            "clipKind" -> manifest.files.firstOrNull()?.clipKind?.wire
            "inReplyTo" -> manifest.inReplyTo
            "outcome" -> manifest.reply?.outcome?.wire
            "reason" -> manifest.reply?.reason?.wire
            "final" -> manifest.reply?.final?.toString()
            "action" -> actionOf(request)
            "pathsCount" -> (request as? DriveChannelRequest.Fetch)?.paths?.size?.toString()
            "modifiedAfter" -> (request as? DriveChannelRequest.Changes)?.modifiedAfter
            else -> error("vector field $key has no reader in this test")
        }
    }

    private fun actionOf(request: DriveChannelRequest?): String? = when (request) {
        is DriveChannelRequest.ListFolder -> "list"
        is DriveChannelRequest.Fetch -> "fetch"
        is DriveChannelRequest.Changes -> "changes"
        null -> null
    }

    private companion object {
        const val VECTORS = "drive-packets-v1.json"
        const val MIN_CASES = 20
    }
}
